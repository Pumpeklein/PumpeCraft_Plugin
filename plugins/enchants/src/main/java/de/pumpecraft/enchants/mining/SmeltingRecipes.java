package de.pumpecraft.enchants.mining;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;

/**
 * Bukkit can only look recipes up by their result, so the furnace results have to be indexed by
 * their input once. The index is built on first use because datapacks still register recipes
 * while the plugins enable.
 */
final class SmeltingRecipes {
    private Map<Material, ItemStack> results;

    ItemStack resultFor(Material input) {
        if (results == null) {
            results = index();
        }
        ItemStack result = results.get(input);
        return result == null ? null : result.clone();
    }

    private Map<Material, ItemStack> index() {
        Map<Material, ItemStack> index = new HashMap<>();
        Iterator<Recipe> recipes = Bukkit.recipeIterator();
        while (recipes.hasNext()) {
            Recipe recipe = recipes.next();
            if (!(recipe instanceof FurnaceRecipe furnace)) {
                continue;
            }
            for (Material input : inputs(furnace.getInputChoice())) {
                index.putIfAbsent(input, furnace.getResult());
            }
        }
        return index;
    }

    private Collection<Material> inputs(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice materials) {
            return materials.getChoices();
        }
        if (choice instanceof RecipeChoice.ExactChoice exact) {
            return exact.getChoices().stream().map(ItemStack::getType).toList();
        }
        return List.of();
    }
}
