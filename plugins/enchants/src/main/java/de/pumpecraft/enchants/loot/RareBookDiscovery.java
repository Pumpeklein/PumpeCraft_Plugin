package de.pumpecraft.enchants.loot;

import de.pumpecraft.enchants.EnchantRegistry;
import de.pumpecraft.enchants.EnchantService;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class RareBookDiscovery {
    private static final NamespacedKey PENDING = key("rare_book_pending");
    private static final NamespacedKey TYPES = key("rare_book_types");
    private static final NamespacedKey FOUND_BY = key("rare_book_found_by");
    private static final NamespacedKey FOUND_AT = key("rare_book_found_at");
    private static final ZoneId SERVER_ZONE = ZoneId.of("Europe/Berlin");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy")
        .withZone(SERVER_ZONE);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(SERVER_ZONE);
    private static final List<String> ANNOUNCEMENTS = List.of(
        "Seltener Fund! %s hat das Buch %s entdeckt!",
        "Die Schatzsuche hat sich gelohnt: %s fand %s!",
        "Was für ein Glück! %s nimmt das seltene Buch %s an sich!",
        "Ein besonderer Fund: %s besitzt nun das Buch %s!",
        "Diese Kiste hatte es in sich: %s hat %s gefunden!"
    );

    private final Plugin plugin;
    private final EnchantService enchants;
    private final Enchantment mending;

    public RareBookDiscovery(Plugin plugin, EnchantService enchants) {
        this.plugin = plugin;
        this.enchants = enchants;
        this.mending = RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.ENCHANTMENT)
            .get(NamespacedKey.minecraft("mending"));
    }

    public void markGeneratedLoot(Collection<ItemStack> loot) {
        for (ItemStack item : loot) {
            List<String> types = rareTypes(item);
            if (types.isEmpty()) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            PersistentDataContainer data = meta.getPersistentDataContainer();
            if (data.has(FOUND_AT, PersistentDataType.LONG)) {
                continue;
            }
            data.set(PENDING, PersistentDataType.BYTE, (byte) 1);
            data.set(TYPES, PersistentDataType.STRING, String.join(",", types));
            item.setItemMeta(meta);
        }
    }

    public void discover(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (discover(player, item)) {
                player.getInventory().setItem(slot, item);
            }
        }
    }

    private boolean discover(Player player, ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        if (!data.has(PENDING, PersistentDataType.BYTE)
            || data.has(FOUND_AT, PersistentDataType.LONG)) {
            return false;
        }

        long foundAt = System.currentTimeMillis();
        String types = data.getOrDefault(TYPES, PersistentDataType.STRING, "seltenes Buch");
        data.remove(PENDING);
        data.set(FOUND_BY, PersistentDataType.STRING, player.getName());
        data.set(FOUND_AT, PersistentDataType.LONG, foundAt);

        List<Component> lore = meta.lore() == null
            ? new ArrayList<>()
            : new ArrayList<>(meta.lore());
        lore.add(Component.text("Gefunden von: ", NamedTextColor.DARK_GRAY)
            .append(Component.text(player.getName(), NamedTextColor.GOLD))
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Datum: ", NamedTextColor.DARK_GRAY)
            .append(Component.text(DATE.format(Instant.ofEpochMilli(foundAt)), NamedTextColor.GRAY))
            .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Uhrzeit: ", NamedTextColor.DARK_GRAY)
            .append(Component.text(TIME.format(Instant.ofEpochMilli(foundAt)) + " Uhr",
                NamedTextColor.GRAY))
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);

        String bookNames = germanList(List.of(types.split(",", -1)));
        String template = ANNOUNCEMENTS.get(
            ThreadLocalRandom.current().nextInt(ANNOUNCEMENTS.size()));
        plugin.getServer().broadcast(Component.text(
            template.formatted(player.getName(), bookNames), NamedTextColor.GOLD));
        return true;
    }

    private List<String> rareTypes(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK
            || !(item.getItemMeta() instanceof EnchantmentStorageMeta storage)) {
            return List.of();
        }
        List<String> types = new ArrayList<>();
        if (enchants.level(item, EnchantRegistry.SOULBOUND) > 0) {
            types.add("Soulbound");
        }
        if (enchants.level(item, EnchantRegistry.LUCKY) > 0) {
            types.add("Lucky");
        }
        if (mending != null && storage.hasStoredEnchant(mending)) {
            types.add("Mending");
        }
        return List.copyOf(types);
    }

    private String germanList(List<String> values) {
        if (values.size() < 2) {
            return values.getFirst();
        }
        return String.join(", ", values.subList(0, values.size() - 1))
            + " und " + values.getLast();
    }

    private static NamespacedKey key(String value) {
        return new NamespacedKey("pumpeenchants", value);
    }
}
