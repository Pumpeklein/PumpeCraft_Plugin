package de.pumpecraft.enchants.combat;

import de.pumpecraft.enchants.EnchantRegistry;
import de.pumpecraft.enchants.EnchantService;
import de.pumpecraft.enchants.EnchantSettings;
import de.pumpecraft.utils.Cooldowns;
import java.util.UUID;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** Drags the target towards the attacker, rate limited per player. */
public final class Barb {
    private final EnchantService enchants;
    private final EnchantSettings settings;
    private final Cooldowns<UUID> cooldowns = new Cooldowns<>();

    public Barb(EnchantService enchants, EnchantSettings settings) {
        this.enchants = enchants;
        this.settings = settings;
    }

    public void pull(Player player, ItemStack weapon, LivingEntity victim) {
        int level = enchants.activeLevel(weapon, EnchantRegistry.BARB);
        if (level < 1) {
            return;
        }
        long cooldown = (long) (settings.perLevel(
            EnchantRegistry.BARB, "cooldown-seconds", level, 6.0, 4.0) * 1000L);
        if (!cooldowns.tryAcquire(player.getUniqueId(), cooldown)) {
            return;
        }
        Vector pull = player.getLocation().toVector()
            .subtract(victim.getLocation().toVector());
        if (pull.lengthSquared() < 0.01) {
            return;
        }
        double strength = settings.value(EnchantRegistry.BARB, "pull-strength", 0.8);
        victim.setVelocity(pull.normalize().multiply(strength).setY(0.25));
    }
}
