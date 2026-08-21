package de.pumpecraft.enchants.anvil;

import de.pumpecraft.enchants.CustomEnchant;
import de.pumpecraft.enchants.EnchantService;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class AnvilCombiner {
    private final EnchantService enchants;

    public AnvilCombiner(EnchantService enchants) {
        this.enchants = enchants;
    }

    public AnvilCombination combine(ItemStack target, ItemStack addition) {
        if (addition == null || addition.getType() != Material.ENCHANTED_BOOK) {
            return AnvilCombination.ignored();
        }
        Map<CustomEnchant, Integer> bookEnchants = enchants.list(addition);
        if (bookEnchants.isEmpty()) {
            return AnvilCombination.ignored();
        }
        if (target == null || target.getType().isAir()) {
            return AnvilCombination.rejected("Lege links ein passendes Item ein.");
        }

        ItemStack result = target.clone();
        for (Map.Entry<CustomEnchant, Integer> entry : bookEnchants.entrySet()) {
            CustomEnchant enchant = entry.getKey();
            int current = enchants.level(result, enchant.key());
            int level = current == entry.getValue()
                ? Math.min(enchant.maximumLevel(), current + 1)
                : Math.max(current, entry.getValue());
            EnchantService.ApplyResult outcome = enchants.set(result, enchant.key(), level);
            if (outcome == EnchantService.ApplyResult.INVALID_ITEM) {
                return AnvilCombination.rejected(
                    enchant.displayName() + " passt nicht auf dieses Item.");
            }
            if (outcome == EnchantService.ApplyResult.INCOMPATIBLE) {
                return AnvilCombination.rejected(
                    "Diese Verzauberungen sind nicht miteinander kompatibel.");
            }
            if (outcome != EnchantService.ApplyResult.APPLIED) {
                return AnvilCombination.rejected(
                    enchant.displayName() + " ist derzeit nicht verfügbar.");
            }
        }
        return AnvilCombination.accepted(result);
    }
}
