package de.pumpecraft.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.bukkit.inventory.ItemStack;

public final class Items {
    private Items() {
    }

    /**
     * Packs loose items into as few stacks as possible, respecting the maximum stack size of each
     * item - 27 single diamonds become one stack of 27, 20 ender pearls become 16 plus 4, tools
     * stay single. Needed wherever a number of stacks decides about slots or price.
     */
    public static List<ItemStack> merge(Collection<ItemStack> items) {
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }

            ItemStack rest = item.clone();
            for (ItemStack target : merged) {
                if (rest.getAmount() <= 0) {
                    break;
                }
                int space = target.getMaxStackSize() - target.getAmount();
                if (space <= 0 || !target.isSimilar(rest)) {
                    continue;
                }
                int moved = Math.min(space, rest.getAmount());
                target.setAmount(target.getAmount() + moved);
                rest.setAmount(rest.getAmount() - moved);
            }

            while (rest.getAmount() > 0) {
                ItemStack piece = rest.clone();
                piece.setAmount(Math.min(Math.max(1, rest.getMaxStackSize()), rest.getAmount()));
                merged.add(piece);
                rest.setAmount(rest.getAmount() - piece.getAmount());
            }
        }
        return merged;
    }

    public static int count(Collection<ItemStack> items) {
        int total = 0;
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                total += item.getAmount();
            }
        }
        return total;
    }
}
