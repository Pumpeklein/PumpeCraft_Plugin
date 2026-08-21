package de.pumpecraft.enchants.fall;

import de.pumpecraft.enchants.EnchantRegistry;
import de.pumpecraft.enchants.EnchantService;
import de.pumpecraft.enchants.EnchantSettings;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

public final class FallProtection {
    private final EnchantService enchants;
    private final EnchantSettings settings;

    public FallProtection(EnchantService enchants, EnchantSettings settings) {
        this.enchants = enchants;
        this.settings = settings;
    }

    public boolean protects(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL
            || !(event.getEntity() instanceof Player player)) {
            return false;
        }
        ItemStack boots = player.getInventory().getBoots();
        int level = enchants.activeLevel(boots, EnchantRegistry.FEATHERWEIGHT);
        if (level < 1) {
            return false;
        }
        return player.getFallDistance() <= settings.featherweightDistance(level);
    }
}
