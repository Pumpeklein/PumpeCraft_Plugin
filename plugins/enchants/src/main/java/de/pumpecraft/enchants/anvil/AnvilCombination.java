package de.pumpecraft.enchants.anvil;

import org.bukkit.inventory.ItemStack;

public record AnvilCombination(ItemStack result, String rejection) {
    public static AnvilCombination ignored() {
        return new AnvilCombination(null, null);
    }

    public static AnvilCombination rejected(String message) {
        return new AnvilCombination(null, message);
    }

    public static AnvilCombination accepted(ItemStack item) {
        return new AnvilCombination(item, null);
    }

    public boolean handled() {
        return result != null || rejection != null;
    }
}
