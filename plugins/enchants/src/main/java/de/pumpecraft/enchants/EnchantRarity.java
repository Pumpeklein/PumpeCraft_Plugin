package de.pumpecraft.enchants;

import net.kyori.adventure.text.format.NamedTextColor;

public enum EnchantRarity {
    COMMON(NamedTextColor.GRAY),
    RARE(NamedTextColor.AQUA),
    EPIC(NamedTextColor.LIGHT_PURPLE);

    private final NamedTextColor color;

    EnchantRarity(NamedTextColor color) {
        this.color = color;
    }

    public NamedTextColor color() {
        return color;
    }
}
