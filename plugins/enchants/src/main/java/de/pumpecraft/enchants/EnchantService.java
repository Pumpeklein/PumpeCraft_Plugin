package de.pumpecraft.enchants;

import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

public final class EnchantService {
    public enum ApplyResult {
        APPLIED,
        UNKNOWN,
        DISABLED,
        INVALID_LEVEL,
        INVALID_ITEM,
        INCOMPATIBLE,
        LIMIT_REACHED
    }

    private final EnchantRegistry registry;
    private final ItemEnchants items;
    private final int maxPerItem;

    public EnchantService(EnchantRegistry registry, ItemEnchants items, int maxPerItem) {
        this.registry = registry;
        this.items = items;
        this.maxPerItem = maxPerItem;
    }

    public int level(ItemStack item, NamespacedKey key) {
        return items.level(item, key);
    }

    public int activeLevel(ItemStack item, NamespacedKey key) {
        return registry.isEnabled(key) ? items.level(item, key) : 0;
    }

    public Map<CustomEnchant, Integer> list(ItemStack item) {
        return items.list(item);
    }

    public Optional<CustomEnchant> find(String id) {
        return registry.find(id);
    }

    public ApplyResult set(ItemStack item, NamespacedKey key, int level) {
        Optional<CustomEnchant> definition = registry.find(key);
        if (definition.isEmpty()) {
            return ApplyResult.UNKNOWN;
        }
        CustomEnchant enchant = definition.get();
        if (!registry.isEnabled(key)) {
            return ApplyResult.DISABLED;
        }
        if (level < 1 || level > enchant.maximumLevel()) {
            return ApplyResult.INVALID_LEVEL;
        }
        if (item == null || item.getType().isAir() || !enchant.supports(item.getType())) {
            return ApplyResult.INVALID_ITEM;
        }
        Map<CustomEnchant, Integer> present = items.list(item);
        if (incompatible(item, present, enchant)) {
            return ApplyResult.INCOMPATIBLE;
        }
        // A book carries a single enchantment and is only a container, so the per item limit
        // applies to the gear it ends up on, not to the book.
        if (!present.containsKey(enchant)
            && present.size() >= maxPerItem
            && item.getType() != Material.ENCHANTED_BOOK) {
            return ApplyResult.LIMIT_REACHED;
        }
        items.set(item, enchant, level);
        return ApplyResult.APPLIED;
    }

    public boolean remove(ItemStack item, NamespacedKey key) {
        return items.remove(item, key);
    }

    public ItemStack createBook(NamespacedKey key, int level) {
        CustomEnchant enchant = registry.find(key)
            .orElseThrow(() -> new IllegalArgumentException("Unknown enchantment: " + key));
        if (level < 1 || level > enchant.maximumLevel()) {
            throw new IllegalArgumentException("Invalid level " + level + " for " + key + ".");
        }
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        items.set(book, enchant, level);
        ItemMeta meta = book.getItemMeta();
        meta.displayName(Component.text(enchant.label(level), NamedTextColor.GOLD)
            .decoration(TextDecoration.ITALIC, false));
        book.setItemMeta(meta);
        return book;
    }

    private boolean incompatible(
        ItemStack item,
        Map<CustomEnchant, Integer> present,
        CustomEnchant candidate
    ) {
        for (CustomEnchant existing : present.keySet()) {
            if (candidate.incompatibleKeys().contains(existing.key())
                || existing.incompatibleKeys().contains(candidate.key())) {
                return true;
            }
        }
        for (Enchantment enchantment : item.getEnchantments().keySet()) {
            if (candidate.incompatibleKeys().contains(enchantment.getKey())) {
                return true;
            }
        }
        if (item.getItemMeta() instanceof EnchantmentStorageMeta storage) {
            for (Enchantment enchantment : storage.getStoredEnchants().keySet()) {
                if (candidate.incompatibleKeys().contains(enchantment.getKey())) {
                    return true;
                }
            }
        }
        return false;
    }
}
