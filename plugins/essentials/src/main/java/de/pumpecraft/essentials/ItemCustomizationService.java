package de.pumpecraft.essentials;

import de.pumpecraft.transactions.core.Currency;
import de.pumpecraft.transactions.core.PointsService;
import de.pumpecraft.transactions.core.TransactionType;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

final class ItemCustomizationService {
    private static final DateTimeFormatter SIGNED_AT = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");

    private final PumpeEssentialsPlugin plugin;
    private final PointsService points;
    private final ItemServicePricing pricing;
    private final NamespacedKey signerKey;
    private final NamespacedKey signedAtKey;
    private final NamespacedKey signatureMessageKey;

    ItemCustomizationService(PumpeEssentialsPlugin plugin, PointsService points, ItemServicePricing pricing) {
        this.plugin = plugin;
        this.points = points;
        this.pricing = pricing;
        signerKey = new NamespacedKey(plugin, "item_signer");
        signedAtKey = new NamespacedKey(plugin, "item_signed_at");
        signatureMessageKey = new NamespacedKey(plugin, "item_signature_message");
    }

    void rename(Player player, String name) {
        ItemStack item = heldItem(player);
        if (item == null) return;
        purchase(player, item, pricing.rename(item, name), "Item umbenannt: " + item.getType(), changed -> {
            ItemMeta meta = changed.getItemMeta();
            meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            changed.setItemMeta(meta);
        });
    }

    void sign(Player player, String message) {
        ItemStack item = heldItem(player);
        if (item == null) return;
        if (isSigned(item)) {
            player.sendMessage(Component.text("Dieses Item wurde bereits unterschrieben.", NamedTextColor.RED));
            return;
        }
        purchase(
            player,
            item,
            pricing.sign(item, message),
            "Item unterschrieben: " + item.getType(),
            changed -> applySignature(changed, player, message)
        );
    }

    private void purchase(Player player, ItemStack original, long cost, String reason, Consumer<ItemStack> mutation) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        ItemStack snapshot = original.clone();
        points.runAsync(() -> {
            try {
                boolean paid = points.withdraw(
                    playerId,
                    playerName,
                    cost,
                    TransactionType.ESSENTIALS_SERVICE,
                    playerName,
                    reason
                );
                points.runSync(() -> finishPurchase(playerId, playerName, snapshot, cost, paid, mutation));
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not charge item service for " + playerName + ".", exception);
                points.runSync(() -> message(playerId, "Die PP-Abbuchung ist fehlgeschlagen.", NamedTextColor.RED));
            }
        });
    }

    private void finishPurchase(
        UUID playerId,
        String playerName,
        ItemStack snapshot,
        long cost,
        boolean paid,
        Consumer<ItemStack> mutation
    ) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (!paid) {
            if (player != null) {
                player.sendMessage(Component.text(
                    "Du benötigst " + Currency.format(cost) + " für diese Änderung.",
                    NamedTextColor.RED
                ));
            }
            return;
        }
        if (player == null || !player.isOnline() || !snapshot.equals(player.getInventory().getItemInMainHand())) {
            refund(playerId, playerName, cost);
            if (player != null) {
                player.sendMessage(Component.text("Das Item wurde verändert; die PP werden zurückerstattet.", NamedTextColor.RED));
            }
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        mutation.accept(item);
        player.getInventory().setItemInMainHand(item);
        player.sendMessage(Component.text("Item angepasst für ", NamedTextColor.GREEN)
            .append(Currency.component(cost))
            .append(Component.text(".", NamedTextColor.GREEN)));
    }

    private void refund(UUID playerId, String playerName, long cost) {
        points.runAsync(() -> {
            try {
                points.deposit(
                    playerId,
                    playerName,
                    cost,
                    TransactionType.ESSENTIALS_SERVICE,
                    "PumpeEssentials",
                    "Erstattung: Item während der Bearbeitung verändert"
                );
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not refund failed item service for " + playerId + ".", exception);
            }
        });
    }

    private ItemStack heldItem(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR || item.getAmount() <= 0) {
            player.sendMessage(Component.text("Du musst ein Item in der Haupthand halten.", NamedTextColor.RED));
            return null;
        }
        return item;
    }

    private boolean isSigned(ItemStack item) {
        return item.getItemMeta().getPersistentDataContainer().has(signerKey, PersistentDataType.STRING);
    }

    private void applySignature(ItemStack item, Player player, String message) {
        ZonedDateTime now = ZonedDateTime.now();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        if (!lore.isEmpty()) lore.add(Component.empty());
        lore.add(lore("Unterschrieben von " + player.getName(), NamedTextColor.GOLD));
        lore.add(lore(SIGNED_AT.format(now), NamedTextColor.GRAY));
        if (!message.isBlank()) lore.add(lore("„" + message + "“", NamedTextColor.WHITE));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(signerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        meta.getPersistentDataContainer().set(signedAtKey, PersistentDataType.LONG, now.toInstant().toEpochMilli());
        if (!message.isBlank()) {
            meta.getPersistentDataContainer().set(signatureMessageKey, PersistentDataType.STRING, message);
        }
        item.setItemMeta(meta);
    }

    private Component lore(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private void message(UUID playerId, String text, NamedTextColor color) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) player.sendMessage(Component.text(text, color));
    }
}
