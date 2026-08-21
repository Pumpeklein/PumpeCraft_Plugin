package de.pumpecraft.enchants.listener;

import de.pumpecraft.enchants.fall.FallProtection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

public final class FallEnchantListener implements Listener {
    private final FallProtection protection;

    public FallEnchantListener(FallProtection protection) {
        this.protection = protection;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (protection.protects(event)) {
            event.setCancelled(true);
        }
    }
}
