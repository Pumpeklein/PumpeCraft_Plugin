package de.pumpecraft.enchants.combat;

import de.pumpecraft.enchants.EnchantRegistry;
import de.pumpecraft.enchants.EnchantService;
import de.pumpecraft.enchants.EnchantSettings;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

/**
 * Only the visual strike plus extra damage on the running hit. A real lightning bolt would set
 * fire and hit through region protection, and dealing the damage separately would let the strike
 * proc on its own damage.
 */
public final class ThunderStrike {
    private final EnchantService enchants;
    private final EnchantSettings settings;

    public ThunderStrike(EnchantService enchants, EnchantSettings settings) {
        this.enchants = enchants;
        this.settings = settings;
    }

    public double strike(ItemStack weapon, LivingEntity victim) {
        int level = enchants.activeLevel(weapon, EnchantRegistry.THUNDER);
        if (level < 1) {
            return 0.0;
        }
        double chance = settings.perLevel(EnchantRegistry.THUNDER, "chance-percent", level, 5.0, 10.0);
        if (ThreadLocalRandom.current().nextDouble() * 100.0 >= chance) {
            return 0.0;
        }
        victim.getWorld().strikeLightningEffect(victim.getLocation());
        return settings.value(EnchantRegistry.THUNDER, "bonus-damage", 4.0);
    }
}
