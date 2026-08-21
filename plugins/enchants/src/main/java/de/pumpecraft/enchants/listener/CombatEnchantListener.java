package de.pumpecraft.enchants.listener;

import de.pumpecraft.enchants.combat.CombatRules;
import de.pumpecraft.enchants.integration.LuckyDrops;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public final class CombatEnchantListener implements Listener {
    private final CombatRules rules;
    private final LuckyDrops lucky;

    public CombatEnchantListener(CombatRules rules, LuckyDrops lucky) {
        this.rules = rules;
        this.lucky = lucky;
    }

    // HIGH and ignoreCancelled: plot protection cancels a forbidden hit at LOW, so a fight inside a
    // protected plot never reaches the combat enchantments in the first place.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        rules.apply(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            lucky.roll(killer, killer.getInventory().getItemInMainHand());
        }
    }
}
