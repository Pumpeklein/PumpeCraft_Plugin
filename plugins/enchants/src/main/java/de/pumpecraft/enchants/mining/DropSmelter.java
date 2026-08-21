package de.pumpecraft.enchants.mining;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

final class DropSmelter {
    List<ItemStack> smelt(Material blockType, List<ItemStack> drops) {
        if (!isOre(blockType)) {
            return drops;
        }
        List<ItemStack> results = new ArrayList<>(drops.size());
        boolean transformed = false;
        for (ItemStack drop : drops) {
            ItemStack cooked = furnaceResult(drop);
            transformed |= cooked != null;
            results.add(cooked == null ? drop.clone() : cooked);
        }
        if (transformed) {
            return results;
        }
        ItemStack blockResult = furnaceResult(new ItemStack(blockType));
        return blockResult == null ? drops : List.of(blockResult);
    }

    private ItemStack furnaceResult(ItemStack input) {
        for (Recipe recipe : Bukkit.getRecipesFor(input)) {
            if (recipe instanceof FurnaceRecipe furnace) {
                ItemStack result = furnace.getResult().clone();
                result.setAmount(result.getAmount() * input.getAmount());
                return result;
            }
        }
        return null;
    }

    private boolean isOre(Material material) {
        return material.name().endsWith("_ORE") || material == Material.ANCIENT_DEBRIS;
    }
}
