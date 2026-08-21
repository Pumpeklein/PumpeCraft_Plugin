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
        if (bookEnchants.isEmpty() || target == null || target.getType().isAir()) {
            return AnvilCombination.ignored();
        }

        ItemStack result = target.clone();
        boolean changed = false;
        for (Map.Entry<CustomEnchant, Integer> entry : bookEnchants.entrySet()) {
            CustomEnchant enchant = entry.getKey();
            int current = enchants.level(result, enchant.key());
            int level = current == entry.getValue()
                ? Math.min(enchant.maximumLevel(), current + 1)
                : Math.max(current, entry.getValue());
            if (level == current) {
                continue;
            }
            EnchantService.ApplyResult outcome = enchants.set(result, enchant.key(), level);
            if (outcome != EnchantService.ApplyResult.APPLIED) {
                return AnvilCombination.rejected(message(enchant, outcome));
            }
            changed = true;
        }
        if (!changed) {
            return AnvilCombination.rejected("Dieses Item hat die Verzauberung bereits.");
        }
        return AnvilCombination.accepted(result);
    }

    private String message(CustomEnchant enchant, EnchantService.ApplyResult outcome) {
        return switch (outcome) {
            case INVALID_ITEM -> enchant.displayName() + " passt nicht auf dieses Item.";
            case INCOMPATIBLE -> enchant.displayName() + " verträgt sich nicht mit diesem Item.";
            case LIMIT_REACHED -> "Dieses Item trägt bereits genug eigene Verzauberungen.";
            case DISABLED -> enchant.displayName() + " ist derzeit abgeschaltet.";
            default -> enchant.displayName() + " lässt sich hier nicht anwenden.";
        };
    }
}
