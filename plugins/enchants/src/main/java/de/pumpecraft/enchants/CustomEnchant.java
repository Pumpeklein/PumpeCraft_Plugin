package de.pumpecraft.enchants;

import java.util.Set;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

public record CustomEnchant(
    NamespacedKey key,
    String displayName,
    int maximumLevel,
    EnchantRarity rarity,
    Set<Material> allowedMaterials,
    Set<NamespacedKey> incompatibleKeys
) {
    public CustomEnchant {
        if (maximumLevel < 1) {
            throw new IllegalArgumentException("maximumLevel must be positive.");
        }
        allowedMaterials = Set.copyOf(allowedMaterials);
        incompatibleKeys = Set.copyOf(incompatibleKeys);
    }

    public String id() {
        return key.getKey();
    }

    /** The text a lore line and every message use, so both stay comparable. */
    public String label(int level) {
        return displayName + " " + RomanNumerals.format(level);
    }

    public boolean supports(Material material) {
        return material == Material.ENCHANTED_BOOK || allowedMaterials.contains(material);
    }
}
