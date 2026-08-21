package de.pumpecraft.enchants.mining;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

final class DropSmelter {
    private final SmeltingRecipes recipes = new SmeltingRecipes();

    List<ItemStack> smelt(Material blockType, List<ItemStack> drops) {
        if (!smeltable(blockType)) {
            return drops;
        }
        List<ItemStack> results = new ArrayList<>(drops.size());
        for (ItemStack drop : drops) {
            ItemStack cooked = recipes.resultFor(drop.getType());
            if (cooked == null) {
                results.add(drop);
                continue;
            }
            cooked.setAmount(cooked.getAmount() * drop.getAmount());
            results.add(cooked);
        }
        return results;
    }

    /**
     * Only blocks whose furnace result is what a player expects from an ore run. Every smeltable
     * block would turn logs into charcoal, which nobody asked for.
     */
    private boolean smeltable(Material material) {
        return material.name().endsWith("_ORE")
            || material == Material.ANCIENT_DEBRIS
            || material == Material.SAND
            || material == Material.RED_SAND;
    }
}
