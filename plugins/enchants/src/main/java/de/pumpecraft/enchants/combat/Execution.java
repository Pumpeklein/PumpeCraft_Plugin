package de.pumpecraft.enchants.combat;

import de.pumpecraft.enchants.EnchantRegistry;
import de.pumpecraft.enchants.EnchantService;
import de.pumpecraft.enchants.EnchantSettings;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

/** Extra damage against a wounded target. */
public final class Execution {
    private final EnchantService enchants;
    private final EnchantSettings settings;

    public Execution(EnchantService enchants, EnchantSettings settings) {
        this.enchants = enchants;
        this.settings = settings;
    }

    public double bonus(ItemStack weapon, LivingEntity victim) {
        int level = enchants.activeLevel(weapon, EnchantRegistry.EXECUTION);
        if (level < 1) {
            return 0.0;
        }
        double maximum = victim.getAttribute(Attribute.MAX_HEALTH) == null
            ? victim.getHealth()
            : victim.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (maximum <= 0.0) {
            return 0.0;
        }
        double threshold = settings.value(
            EnchantRegistry.EXECUTION, "health-threshold-percent", 30.0) / 100.0;
        if (victim.getHealth() / maximum > threshold) {
            return 0.0;
        }
        return settings.perLevel(
            EnchantRegistry.EXECUTION, "bonus-percent", level, 15.0, 25.0, 40.0) / 100.0;
    }
}
