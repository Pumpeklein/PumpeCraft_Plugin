package de.pumpecraft.enchants;

import net.kyori.adventure.text.format.NamedTextColor;

public enum EnchantRarity {
    COMMON("Common", NamedTextColor.GRAY, 10, 1),
    RARE("Rare", NamedTextColor.AQUA, 5, 2),
    EPIC("Epic", NamedTextColor.LIGHT_PURPLE, 2, 3),
    LEGENDARY("Legendary", NamedTextColor.GOLD, 1, 4);

    private final String displayName;
    private final NamedTextColor color;
    private final int weight;
    private final int anvilCost;

    EnchantRarity(String displayName, NamedTextColor color, int weight, int anvilCost) {
        this.displayName = displayName;
        this.color = color;
        this.weight = weight;
        this.anvilCost = anvilCost;
    }

    public String displayName() {
        return displayName;
    }

    public NamedTextColor color() {
        return color;
    }

    public int weight() {
        return weight;
    }

    public int anvilCost() {
        return anvilCost;
    }
}
