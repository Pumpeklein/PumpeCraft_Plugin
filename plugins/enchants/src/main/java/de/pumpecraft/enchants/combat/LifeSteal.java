package de.pumpecraft.enchants.combat;

import de.pumpecraft.enchants.EnchantRegistry;
import de.pumpecraft.enchants.EnchantService;
import de.pumpecraft.enchants.EnchantSettings;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Turns a share of the dealt damage into health, capped per hit. */
public final class LifeSteal {
    private final EnchantService enchants;
    private final EnchantSettings settings;

    public LifeSteal(EnchantService enchants, EnchantSettings settings) {
        this.enchants = enchants;
        this.settings = settings;
    }

    public void heal(Player player, ItemStack weapon, double dealtDamage) {
        int level = enchants.activeLevel(weapon, EnchantRegistry.LIFESTEAL);
        if (level < 1 || dealtDamage <= 0.0) {
            return;
        }
        double share = settings.perLevel(
            EnchantRegistry.LIFESTEAL, "percent", level, 5.0, 10.0, 15.0) / 100.0;
        double cap = settings.value(EnchantRegistry.LIFESTEAL, "max-heal", 4.0);
        double healed = Math.min(dealtDamage * share, cap);
        if (healed <= 0.0) {
            return;
        }
        double maximum = player.getAttribute(Attribute.MAX_HEALTH) == null
            ? player.getHealth()
            : player.getAttribute(Attribute.MAX_HEALTH).getValue();
        player.setHealth(Math.min(maximum, player.getHealth() + healed));
    }
}
