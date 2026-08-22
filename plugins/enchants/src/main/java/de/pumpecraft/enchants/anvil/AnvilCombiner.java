package de.pumpecraft.enchants.anvil;

import de.pumpecraft.enchants.CustomEnchant;
import de.pumpecraft.enchants.EnchantService;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

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
        if (target == null || target.getType().isAir()) {
            return AnvilCombination.ignored();
        }

        ItemStack result = target.clone();
        if (bookEnchants.isEmpty()) {
            if (enchants.list(target).isEmpty() || !mergeVanillaEnchantments(result, addition)) {
                return AnvilCombination.ignored();
            }
            return AnvilCombination.accepted(result);
        }
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
        changed |= mergeVanillaEnchantments(result, addition);
        if (!changed) {
            return AnvilCombination.rejected("Dieses Item hat die Verzauberung bereits.");
        }
        return AnvilCombination.accepted(result);
    }

    private boolean mergeVanillaEnchantments(ItemStack result, ItemStack addition) {
        if (!(addition.getItemMeta() instanceof EnchantmentStorageMeta source)) {
            return false;
        }
        ItemMeta target = result.getItemMeta();
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> entry : source.getStoredEnchants().entrySet()) {
            Enchantment enchantment = entry.getKey();
            if (!enchantment.getKey().getNamespace().equals("minecraft")
                || !supports(target, result, enchantment)
                || conflicts(target, enchantment)) {
                continue;
            }
            int current = target instanceof EnchantmentStorageMeta storage
                ? storage.getStoredEnchantLevel(enchantment)
                : target.getEnchantLevel(enchantment);
            int level = current == entry.getValue()
                ? Math.min(enchantment.getMaxLevel(), current + 1)
                : Math.max(current, entry.getValue());
            if (level == current) {
                continue;
            }
            if (target instanceof EnchantmentStorageMeta storage) {
                storage.addStoredEnchant(enchantment, level, false);
            } else {
                target.addEnchant(enchantment, level, false);
            }
            changed = true;
        }
        if (changed) {
            result.setItemMeta(target);
            if (result.getType() == Material.ENCHANTED_BOOK) {
                enchants.refreshBook(result);
            }
        }
        return changed;
    }

    private boolean supports(ItemMeta target, ItemStack result, Enchantment enchantment) {
        return target instanceof EnchantmentStorageMeta || enchantment.canEnchantItem(result);
    }

    private boolean conflicts(ItemMeta target, Enchantment enchantment) {
        return target instanceof EnchantmentStorageMeta storage
            ? storage.hasConflictingStoredEnchant(enchantment)
            : target.hasConflictingEnchant(enchantment);
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
