package de.pumpecraft.essentials;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

final class ItemServicePricing {
    private final long renameBase;
    private final long renamePerCharacter;
    private final int renamePercent;
    private final long signBase;
    private final long signPerCharacter;
    private final int signPercent;

    ItemServicePricing(FileConfiguration config) {
        renameBase = Math.max(1L, config.getLong("item-services.rename.base-cost", 175L));
        renamePerCharacter = Math.max(1L, config.getLong("item-services.rename.per-character", 15L));
        renamePercent = Math.max(0, config.getInt("item-services.rename.value-percent", 15));
        signBase = Math.max(1L, config.getLong("item-services.sign.base-cost", 100L));
        signPerCharacter = Math.max(1L, config.getLong("item-services.sign.per-character", 8L));
        signPercent = Math.max(0, config.getInt("item-services.sign.value-percent", 10));
    }

    long rename(ItemStack item, String name) {
        return price(item, renameBase, renamePerCharacter, characters(name), renamePercent);
    }

    long sign(ItemStack item, String message) {
        return price(item, signBase, signPerCharacter, characters(message), signPercent);
    }

    private long price(ItemStack item, long base, long perCharacter, long characters, int percent) {
        long value = Math.max(1L, Math.round(unitValue(item.getType()) * durabilityFactor(item)))
            * item.getAmount();
        value += enchantmentValue(item.getEnchantments());
        if (item.getItemMeta() instanceof EnchantmentStorageMeta stored) {
            value += enchantmentValue(stored.getStoredEnchants());
        }
        long valueSurcharge = (long) Math.ceil(value * percent / 100.0D);
        return base + perCharacter * characters + valueSurcharge;
    }

    private long characters(String text) {
        return text.codePoints().filter(character -> !Character.isWhitespace(character)).count();
    }

    private long enchantmentValue(Map<Enchantment, Integer> enchantments) {
        return enchantments.values().stream().mapToLong(level -> level * 10L).sum();
    }

    private double durabilityFactor(ItemStack item) {
        if (!(item.getItemMeta() instanceof Damageable damageable) || item.getType().getMaxDurability() <= 0) {
            return 1.0D;
        }
        double remaining = 1.0D - (double) damageable.getDamage() / item.getType().getMaxDurability();
        return Math.max(0.25D, remaining);
    }

    private long unitValue(Material material) {
        return switch (material) {
            case DRAGON_EGG -> 500L;
            case BEACON -> 250L;
            case NETHER_STAR -> 200L;
            case ELYTRA -> 150L;
            case MACE, ENCHANTED_GOLDEN_APPLE -> 120L;
            case TRIDENT -> 80L;
            case NETHERITE_INGOT -> 50L;
            case NETHERITE_SCRAP -> 15L;
            case DIAMOND -> 20L;
            case EMERALD -> 10L;
            case NETHERITE_BLOCK -> 450L;
            case DIAMOND_BLOCK -> 180L;
            case EMERALD_BLOCK -> 90L;
            default -> inferredValue(material);
        };
    }

    private long inferredValue(Material material) {
        String name = material.name();
        if (name.contains("NETHERITE")) return 100L;
        if (name.contains("DIAMOND")) return 50L;
        if (name.contains("SHULKER_BOX")) return 40L;
        if (name.contains("GOLDEN") || name.contains("GOLD_")) return 15L;
        if (name.contains("IRON_")) return 10L;
        return material.getMaxStackSize() == 1 ? 10L : 2L;
    }
}
