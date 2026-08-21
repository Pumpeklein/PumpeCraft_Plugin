package de.pumpecraft.enchants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class ItemEnchants {
    private static final String LORE_MARKER_PREFIX = "pumpeenchants:";

    private final EnchantRegistry registry;

    public ItemEnchants(EnchantRegistry registry) {
        this.registry = registry;
    }

    public int level(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        return item.getItemMeta().getPersistentDataContainer()
            .getOrDefault(key, PersistentDataType.INTEGER, 0);
    }

    public Map<CustomEnchant, Integer> list(ItemStack item) {
        Map<CustomEnchant, Integer> found = new LinkedHashMap<>();
        for (CustomEnchant enchant : registry.all()) {
            int level = level(item, enchant.key());
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
        List<Component> lore = meta.lore() == null
            ? new ArrayList<>()
            : new ArrayList<>(meta.lore());
        lore.removeIf(this::isRenderedEnchantLine);

        List<Component> rendered = new ArrayList<>();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        for (CustomEnchant enchant : registry.all()) {
            int level = data.getOrDefault(enchant.key(), PersistentDataType.INTEGER, 0);
            if (level > 0) {
                rendered.add(Component.text(
                        enchant.displayName() + " " + RomanNumerals.format(level), enchant.rarity().color())
                    .decoration(TextDecoration.ITALIC, false)
                    .insertion(LORE_MARKER_PREFIX + enchant.id()));
            }
        }
        rendered.addAll(lore);
        meta.lore(rendered.isEmpty() ? null : rendered);
    }

    private boolean isRenderedEnchantLine(Component line) {
        String insertion = line.style().insertion();
        return insertion != null && insertion.startsWith(LORE_MARKER_PREFIX);
    }
}
