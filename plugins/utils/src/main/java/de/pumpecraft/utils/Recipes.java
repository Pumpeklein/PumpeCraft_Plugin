package de.pumpecraft.utils;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Recipe;

public final class Recipes {
    private Recipes() {
    }

    /**
     * Registers a recipe under its key and hands it to everyone already online. Recipes survive a
     * plugin disable, so a second enable - Reload, Plugin-Manager - would be rejected as duplicate
     * key without removing the old one first.
     */
    public static boolean register(NamespacedKey key, Recipe recipe) {
        Bukkit.removeRecipe(key);
        return Bukkit.addRecipe(recipe, true);
    }

    public static boolean unregister(NamespacedKey key) {
        return Bukkit.removeRecipe(key);
    }
}
