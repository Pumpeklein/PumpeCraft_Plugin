package de.pumpecraft.enchants.listener;

import de.pumpecraft.enchants.armor.Endurance;
import de.pumpecraft.enchants.armor.FallProtection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public final class DamageEnchantListener implements Listener {
    private final FallProtection fall;
    private final Endurance endurance;

    public DamageEnchantListener(FallProtection fall, Endurance endurance) {
        this.fall = fall;
        this.endurance = endurance;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (fall.protects(event)) {
            event.setCancelled(true);
            return;
        }
        endurance.check(event);
    }
}
