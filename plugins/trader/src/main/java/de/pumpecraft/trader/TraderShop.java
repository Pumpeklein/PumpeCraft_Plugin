package de.pumpecraft.trader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.TradeSelectEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import de.pumpecraft.transactions.core.Currency;
import de.pumpecraft.transactions.core.PointsService;
import de.pumpecraft.transactions.core.TransactionType;
import io.papermc.paper.event.player.PlayerTradeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

final class TraderShop implements Listener {
    private static final long LIGHT_PRICE = 4_500L;
    private static final long INVISIBLE_FRAME_PRICE = 1_500L;
    private static final long INVISIBLE_GLOW_FRAME_PRICE = 2_250L;
    private static final long SPONGE_PRICE = 500L;

    private final PumpeTraderPlugin plugin;
    private final PointsService points;
    private final TraderItems items;
    private final NamespacedKey traderKey;
    private final NamespacedKey priceKey;

    TraderShop(PumpeTraderPlugin plugin, PointsService points, TraderItems items) {
        this.plugin = plugin;
        this.points = points;
        this.items = items;
        traderKey = new NamespacedKey(plugin, "event_trader");
        priceKey = new NamespacedKey(plugin, "trade_price");
    }

    List<MerchantRecipe> createRecipes() {
        List<MerchantRecipe> recipes = new ArrayList<>();
        recipes.add(recipe(
            items.item(Material.LIGHT, 1, "Light Block", "Unsichtbare Lichtquelle für Builder."),
            LIGHT_PRICE
        ));
        recipes.add(recipe(items.invisibleFrame(Material.ITEM_FRAME, 2), INVISIBLE_FRAME_PRICE));
        recipes.add(recipe(items.invisibleFrame(Material.GLOW_ITEM_FRAME, 1), INVISIBLE_GLOW_FRAME_PRICE));
        recipes.add(recipe(
            items.item(Material.SPONGE, 2, "Sponges", "Wasser weg, Problem kleiner."),
            SPONGE_PRICE
        ));
        return recipes;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory() instanceof MerchantInventory inventory && isEventTrader(inventory.getMerchant())) {
            select(inventory, 0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSelect(TradeSelectEvent event) {
        if (isEventTrader(event.getMerchant())) {
            select(event.getInventory(), event.getIndex());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory() instanceof MerchantInventory inventory)
            || !isEventTrader(inventory.getMerchant())) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot == 0
            || rawSlot == 1
            || event.isShiftClick() && rawSlot >= inventory.getSize()
            || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory() instanceof MerchantInventory inventory)
            || !isEventTrader(inventory.getMerchant())) {
            return;
        }
        if (event.getRawSlots().stream().anyMatch(slot -> slot == 0 || slot == 1)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory() instanceof MerchantInventory inventory && isEventTrader(inventory.getMerchant())) {
            inventory.setItem(0, null);
            inventory.setItem(1, null);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPurchase(PlayerTradeEvent event) {
        if (!isEventTrader(event.getVillager())) {
            return;
        }
        event.setCancelled(true);

        MerchantRecipe trade = event.getTrade();
        long price = price(trade);
        if (price <= 0L) {
            plugin.getLogger().warning("Event trader recipe without a valid PP price was blocked.");
            event.getPlayer().sendMessage(Component.text("Dieser Handel ist gerade nicht verfügbar.", NamedTextColor.RED));
            return;
        }

        Player player = event.getPlayer();
        ItemStack result = trade.getResult();
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();
        points.runAsync(() -> charge(playerId, playerName, result, price));
    }

    private MerchantRecipe recipe(ItemStack result, long unitPrice) {
        long totalPrice = Math.multiplyExact(unitPrice, result.getAmount());
        MerchantRecipe recipe = new MerchantRecipe(result, 999_999);
        recipe.addIngredient(priceToken(totalPrice, unitPrice, result.getAmount()));
        return recipe;
    }

    private ItemStack priceToken(long totalPrice, long unitPrice, int amount) {
        ItemStack token = new ItemStack(Material.PAPER);
        ItemMeta meta = token.getItemMeta();
        meta.displayName(Component.text("Preis: " + Currency.format(totalPrice), Currency.COLOR)
            .decoration(TextDecoration.ITALIC, false));
        if (amount > 1) {
            meta.lore(List.of(Component.text(
                Currency.format(unitPrice) + " pro Stück · " + amount + " Stück",
                NamedTextColor.GRAY
            ).decoration(TextDecoration.ITALIC, false)));
        } else {
            meta.lore(List.of(Component.text("Wird direkt mit PP bezahlt.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        }
        meta.getPersistentDataContainer().set(priceKey, PersistentDataType.LONG, totalPrice);
        token.setItemMeta(meta);
        return token;
    }

    private void select(MerchantInventory inventory, int index) {
        List<MerchantRecipe> recipes = inventory.getMerchant().getRecipes();
        if (index < 0 || index >= recipes.size()) {
            return;
        }
        List<ItemStack> ingredients = recipes.get(index).getIngredients();
        if (ingredients.isEmpty()) {
            return;
        }
        inventory.setItem(0, ingredients.getFirst());
        inventory.setItem(1, null);
    }

    private long price(MerchantRecipe recipe) {
        List<ItemStack> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) {
            return 0L;
        }
        ItemStack token = ingredients.getFirst();
        if (!token.hasItemMeta()) {
            return 0L;
        }
        Long price = token.getItemMeta().getPersistentDataContainer().get(priceKey, PersistentDataType.LONG);
        return price == null ? 0L : price;
    }

    private void charge(UUID playerId, String playerName, ItemStack result, long price) {
        try {
            boolean paid = points.withdraw(
                playerId,
                playerName,
                price,
                TransactionType.TRADER_PURCHASE,
                playerName,
                "Trader-Kauf: " + result.getAmount() + "x " + result.getType()
            );
            points.runSync(() -> finish(playerId, playerName, result, price, paid));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not charge trader purchase for " + playerName + ".", exception);
            points.runSync(() -> message(playerId, "Die PP-Abbuchung ist fehlgeschlagen.", NamedTextColor.RED));
        }
    }

    private void finish(UUID playerId, String playerName, ItemStack result, long price, boolean paid) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (!paid) {
            if (player != null) {
                player.sendMessage(Component.text("Du benötigst ", NamedTextColor.RED)
                    .append(Currency.component(price))
                    .append(Component.text(" für diesen Kauf.", NamedTextColor.RED)));
            }
            return;
        }
        if (player == null || !player.isOnline()) {
            refund(playerId, playerName, price);
            return;
        }

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(result.clone());
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage(Component.text("Gekauft: ", NamedTextColor.GREEN)
            .append(Component.text(result.getAmount() + "x " + displayName(result), NamedTextColor.GOLD))
            .append(Component.text(" für ", NamedTextColor.GREEN))
            .append(Currency.component(price))
            .append(Component.text(".", NamedTextColor.GREEN)));
    }

    private void refund(UUID playerId, String playerName, long price) {
        points.runAsync(() -> {
            try {
                points.deposit(
                    playerId,
                    playerName,
                    price,
                    TransactionType.TRADER_PURCHASE,
                    "PumpeTrader",
                    "Erstattung: Spieler während Trader-Kauf offline"
                );
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not refund trader purchase for " + playerId + ".", exception);
            }
        });
    }

    private String displayName(ItemStack item) {
        return switch (item.getType()) {
            case LIGHT -> "Light Block";
            case ITEM_FRAME -> "Invisible Item Frame";
            case GLOW_ITEM_FRAME -> "Invisible Glow Item Frame";
            case SPONGE -> "Schwamm";
            default -> item.getType().name();
        };
    }

    private boolean isEventTrader(Merchant merchant) {
        return merchant instanceof Entity entity
            && entity.getPersistentDataContainer().has(traderKey, PersistentDataType.BYTE);
    }

    private void message(UUID playerId, String text, NamedTextColor color) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            player.sendMessage(Component.text(text, color));
        }
    }
}
