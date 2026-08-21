package de.pumpecraft.enchants.mining;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;

final class FortuneApplicator {
    List<ItemStack> apply(
        List<ItemStack> smeltedDrops,
        List<ItemStack> baseDrops,
        List<ItemStack> fortunateDrops
    ) {
        int baseAmount = amount(baseDrops);
        int fortunateAmount = amount(fortunateDrops);
        if (baseAmount == 0 || fortunateAmount <= baseAmount) {
            return smeltedDrops;
        }

        List<ItemStack> results = new ArrayList<>(smeltedDrops.size());
        int assigned = 0;
        int smeltedAmount = amount(smeltedDrops);
        for (int index = 0; index < smeltedDrops.size(); index++) {
            ItemStack result = smeltedDrops.get(index).clone();
            int scaled = index == smeltedDrops.size() - 1
                ? Math.max(1, Math.round((float) smeltedAmount * fortunateAmount / baseAmount) - assigned)
                : Math.max(1, Math.round((float) result.getAmount() * fortunateAmount / baseAmount));
            result.setAmount(scaled);
            assigned += scaled;
            results.add(result);
        }
        return results;
    }

    private int amount(List<ItemStack> drops) {
        return drops.stream().mapToInt(ItemStack::getAmount).sum();
    }
}
