package de.pumpecraft.enchants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class ItemEnchants {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final NamespacedKey BOOK_FORMAT = new NamespacedKey(
        "pumpeenchants", "book_format");
    private static final NamespacedKey BOOK_RENDERED_LORE = new NamespacedKey(
        "pumpeenchants", "book_rendered_lore");
    private static final int BOOK_FORMAT_VERSION = 1;
    private static final String LORE_SEPARATOR = "\u001f";

    private final EnchantRegistry registry;
    private final Set<String> legacyLines;

    public ItemEnchants(EnchantRegistry registry) {
        this.registry = registry;
        Set<String> lines = new HashSet<>();
        for (CustomEnchant enchant : registry.all()) {
            for (int level = 1; level <= enchant.maximumLevel(); level++) {
                lines.add(enchant.legacyLabel(level));
                lines.add(enchant.displayName() + " " + RomanNumerals.format(level));
            }
        }
        legacyLines = Set.copyOf(lines);
    }

    public int level(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        ItemMeta meta = migrateLegacy(item);
        return level(meta, key);
    }

    public Map<CustomEnchant, Integer> list(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Map.of();
        }
        ItemMeta meta = migrateLegacy(item);
        Map<CustomEnchant, Integer> found = new LinkedHashMap<>();
        for (CustomEnchant enchant : registry.all()) {
            int level = level(meta, enchant.key());
            if (level > 0) {
                found.put(enchant, level);
            }
        }
        return Collections.unmodifiableMap(found);
    }

    public void set(ItemStack item, CustomEnchant enchant, int level) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(enchant.key());
        put(meta, enchant.key(), level);
        cleanLegacyLore(meta);
        meta.setEnchantmentGlintOverride(null);
        if (meta instanceof EnchantmentStorageMeta storage) {
            renderBook(storage);
        }
        item.setItemMeta(meta);
    }

    public boolean remove(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta() || level(item, key) == 0) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        Enchantment enchantment = nativeEnchant(key);
        meta.removeEnchant(enchantment);
        if (meta instanceof EnchantmentStorageMeta storage) {
            storage.removeStoredEnchant(enchantment);
        }
        meta.getPersistentDataContainer().remove(key);
        cleanLegacyLore(meta);
        meta.setEnchantmentGlintOverride(null);
        if (meta instanceof EnchantmentStorageMeta storage) {
            renderBook(storage);
        }
        item.setItemMeta(meta);
        return true;
    }

    public void refreshBook(ItemStack item) {
        if (!(item.getItemMeta() instanceof EnchantmentStorageMeta storage)) {
            return;
        }
        renderBook(storage);
        item.setItemMeta(storage);
    }

    public boolean upgradeBook(ItemStack item) {
        if (item == null || item.getType() != org.bukkit.Material.ENCHANTED_BOOK
            || !(item.getItemMeta() instanceof EnchantmentStorageMeta)) {
            return false;
        }
        ItemStack before = item.clone();
        ItemMeta migrated = migrateLegacy(item);
        if (!(migrated instanceof EnchantmentStorageMeta storage)) {
            return false;
        }

        List<Map.Entry<CustomEnchant, Integer>> present = new ArrayList<>();
        for (CustomEnchant enchant : registry.all()) {
            Enchantment nativeEnchant = nativeEnchant(enchant.key());
            int level = storage.getStoredEnchantLevel(nativeEnchant);
            if (level <= 0) {
                continue;
            }
            int currentLevel = Math.min(level, enchant.maximumLevel());
            if (currentLevel != level) {
                storage.removeStoredEnchant(nativeEnchant);
                storage.addStoredEnchant(nativeEnchant, currentLevel, true);
            }
            present.add(Map.entry(enchant, currentLevel));
        }
        if (present.isEmpty()) {
            return false;
        }

        if (isGeneratedBookName(storage)) {
            Map.Entry<CustomEnchant, Integer> primary = present.getFirst();
            storage.displayName(Component.text(
                    primary.getKey().label(primary.getValue()), NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        }
        renderBook(storage);
        item.setItemMeta(storage);
        return !item.equals(before);
    }

    private ItemMeta migrateLegacy(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        boolean changed = false;
        for (CustomEnchant enchant : registry.all()) {
            int legacyLevel = data.getOrDefault(
                enchant.key(), PersistentDataType.INTEGER, 0);
            if (legacyLevel <= 0) {
                continue;
            }
            if (level(meta, enchant.key()) == 0) {
                put(meta, enchant.key(), Math.min(legacyLevel, enchant.maximumLevel()));
            }
            data.remove(enchant.key());
            migrateDisplayName(meta, enchant, legacyLevel);
            changed = true;
        }
        if (!changed) {
            return meta;
        }
        cleanLegacyLore(meta);
        meta.setEnchantmentGlintOverride(null);
        if (meta instanceof EnchantmentStorageMeta storage) {
            renderBook(storage);
        }
        item.setItemMeta(meta);
        return meta;
    }

    private int level(ItemMeta meta, NamespacedKey key) {
        Enchantment enchantment = nativeEnchant(key);
        int direct = meta.getEnchantLevel(enchantment);
        int stored = meta instanceof EnchantmentStorageMeta storage
            ? storage.getStoredEnchantLevel(enchantment)
            : 0;
        return Math.max(direct, stored);
    }

    private void put(ItemMeta meta, NamespacedKey key, int level) {
        Enchantment enchantment = nativeEnchant(key);
        if (meta instanceof EnchantmentStorageMeta storage) {
            storage.addStoredEnchant(enchantment, level, true);
        } else {
            meta.addEnchant(enchantment, level, true);
        }
    }

    private Enchantment nativeEnchant(NamespacedKey key) {
        Enchantment enchantment = RegistryAccess.registryAccess()
            .getRegistry(RegistryKey.ENCHANTMENT)
            .get(key);
        if (enchantment == null) {
            throw new IllegalStateException("Enchantment is not registered: " + key);
        }
        return enchantment;
    }

    private void renderBook(EnchantmentStorageMeta meta) {
        removePreviouslyRenderedLore(meta);
        cleanLegacyLore(meta);
        List<Component> lore = meta.lore() == null
            ? new ArrayList<>()
            : new ArrayList<>(meta.lore());
        Set<Component> previousVanillaLines = meta.getStoredEnchants().entrySet().stream()
            .filter(entry -> entry.getKey().getKey().getNamespace().equals("minecraft"))
            .map(entry -> entry.getKey().displayName(entry.getValue())
                .decoration(TextDecoration.ITALIC, false))
            .collect(java.util.stream.Collectors.toSet());
        lore.removeIf(previousVanillaLines::contains);
        List<Component> descriptions = new ArrayList<>();
        for (CustomEnchant enchant : registry.all()) {
            if (meta.hasStoredEnchant(nativeEnchant(enchant.key()))) {
                descriptions.add(Component.text(enchant.description(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            }
        }
        meta.getStoredEnchants().entrySet().stream()
            .filter(entry -> entry.getKey().getKey().getNamespace().equals("minecraft"))
            .map(entry -> entry.getKey().displayName(entry.getValue())
                .decoration(TextDecoration.ITALIC, false))
            .forEach(descriptions::add);
        List<String> renderedLore = descriptions.stream().map(PLAIN::serialize).toList();
        List<Component> completeLore = new ArrayList<>(descriptions);
        completeLore.addAll(lore);
        meta.lore(completeLore.isEmpty() ? null : completeLore);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(BOOK_FORMAT, PersistentDataType.INTEGER, BOOK_FORMAT_VERSION);
        if (renderedLore.isEmpty()) {
            data.remove(BOOK_RENDERED_LORE);
        } else {
            data.set(BOOK_RENDERED_LORE, PersistentDataType.STRING,
                String.join(LORE_SEPARATOR, renderedLore));
        }
        if (completeLore.isEmpty()) {
            meta.removeItemFlags(ItemFlag.HIDE_STORED_ENCHANTS);
        } else {
            meta.addItemFlags(ItemFlag.HIDE_STORED_ENCHANTS);
        }
    }

    private void removePreviouslyRenderedLore(ItemMeta meta) {
        if (meta.lore() == null) {
            return;
        }
        String stored = meta.getPersistentDataContainer().get(
            BOOK_RENDERED_LORE, PersistentDataType.STRING);
        if (stored == null || stored.isEmpty()) {
            return;
        }
        Set<String> generated = new HashSet<>(List.of(stored.split(LORE_SEPARATOR, -1)));
        List<Component> lore = new ArrayList<>(meta.lore());
        lore.removeIf(line -> generated.contains(PLAIN.serialize(line)));
        meta.lore(lore.isEmpty() ? null : lore);
    }

    private boolean isGeneratedBookName(ItemMeta meta) {
        if (meta.displayName() == null) {
            return true;
        }
        String name = PLAIN.serialize(meta.displayName());
        for (CustomEnchant enchant : registry.all()) {
            for (int level = 1; level <= enchant.maximumLevel(); level++) {
                if (name.equals(enchant.label(level))
                    || name.equals(enchant.legacyLabel(level))
                    || name.equals(enchant.displayName() + " " + RomanNumerals.format(level))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void cleanLegacyLore(ItemMeta meta) {
        if (meta.lore() == null) {
            return;
        }
        List<Component> lore = new ArrayList<>(meta.lore());
        lore.removeIf(line -> legacyLines.contains(PLAIN.serialize(line))
            || registry.all().stream().anyMatch(enchant ->
                enchant.description().equals(PLAIN.serialize(line))));
        meta.lore(lore.isEmpty() ? null : lore);
    }

    private void migrateDisplayName(ItemMeta meta, CustomEnchant enchant, int level) {
        if (meta.displayName() == null) {
            return;
        }
        String current = PLAIN.serialize(meta.displayName());
        if (!current.equals(enchant.legacyLabel(level))) {
            return;
        }
        meta.displayName(Component.text(enchant.label(level), NamedTextColor.LIGHT_PURPLE)
            .decoration(TextDecoration.ITALIC, false));
    }
}
