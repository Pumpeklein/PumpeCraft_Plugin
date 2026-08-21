package de.pumpecraft.enchants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class ItemEnchants {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final EnchantRegistry registry;
    // Own lore lines are recognised by their text, not by a marker in the style: PumpeAntiCheat
    // rebuilds overlong lines as plain text and would drop any marker together with the formatting.
    private final Set<String> renderedLines;

    public ItemEnchants(EnchantRegistry registry) {
        this.registry = registry;
        Set<String> lines = new HashSet<>();
        for (CustomEnchant enchant : registry.all()) {
            for (int level = 1; level <= enchant.maximumLevel(); level++) {
                lines.add(enchant.label(level));
            }
        }
        renderedLines = Set.copyOf(lines);
    }

    public int level(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        return item.getItemMeta().getPersistentDataContainer()
            .getOrDefault(key, PersistentDataType.INTEGER, 0);
    }

    public Map<CustomEnchant, Integer> list(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Map.of();
        }
        // Reading the meta once matters: getItemMeta clones, and this runs per item of an inventory.
        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        Map<CustomEnchant, Integer> found = new LinkedHashMap<>();
        for (CustomEnchant enchant : registry.all()) {
            int level = data.getOrDefault(enchant.key(), PersistentDataType.INTEGER, 0);
            if (level > 0) {
                found.put(enchant, level);
            }
        }
        return Collections.unmodifiableMap(found);
    }

    public void set(ItemStack item, CustomEnchant enchant, int level) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(enchant.key(), PersistentDataType.INTEGER, level);
        render(meta);
        item.setItemMeta(meta);
    }

    public boolean remove(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta() || level(item, key) == 0) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(key);
        render(meta);
        item.setItemMeta(meta);
        return true;
    }

    private void render(ItemMeta meta) {
        List<Component> foreign = meta.lore() == null
            ? new ArrayList<>()
            : new ArrayList<>(meta.lore());
        foreign.removeIf(line -> renderedLines.contains(PLAIN.serialize(line)));

        List<Component> lore = new ArrayList<>();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        for (CustomEnchant enchant : registry.all()) {
            int level = data.getOrDefault(enchant.key(), PersistentDataType.INTEGER, 0);
            if (level > 0) {
                lore.add(Component.text(enchant.label(level), enchant.rarity().color())
                    .decoration(TextDecoration.ITALIC, false));
            }
        }
        lore.addAll(foreign);
        meta.lore(lore.isEmpty() ? null : lore);
    }
}
