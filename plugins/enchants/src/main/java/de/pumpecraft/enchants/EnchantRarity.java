package de.pumpecraft.enchants;

import net.kyori.adventure.text.format.NamedTextColor;

public enum EnchantRarity {
    COMMON("Gewöhnlich", NamedTextColor.GRAY),
    RARE("Selten", NamedTextColor.AQUA),
    EPIC("Episch", NamedTextColor.LIGHT_PURPLE),
    LEGENDARY("Legendär", NamedTextColor.GOLD);

    private final String displayName;
    private final NamedTextColor color;

    EnchantRarity(String displayName, NamedTextColor color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    public NamedTextColor color() {
        return color;
    }
}
