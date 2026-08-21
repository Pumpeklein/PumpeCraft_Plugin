package de.pumpecraft.enchants.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public final class CombatRules {
    private final Execution execution;
    private final ClanBond clanBond;
    private final ThunderStrike thunder;
    private final LifeSteal lifeSteal;
    private final Barb barb;

    public CombatRules(
        Execution execution,
        ClanBond clanBond,
        ThunderStrike thunder,
        LifeSteal lifeSteal,
        Barb barb
    ) {
        this.execution = execution;
        this.clanBond = clanBond;
        this.thunder = thunder;
        this.lifeSteal = lifeSteal;
        this.barb = barb;
    }

    public void apply(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)
            || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        ItemStack weapon = player.getInventory().getItemInMainHand();

        double factor = 1.0 + execution.bonus(weapon, victim) + clanBond.bonus(player, weapon);
        double flat = thunder.strike(weapon, victim);
        if (factor != 1.0 || flat > 0.0) {
            event.setDamage(event.getDamage() * factor + flat);
        }

        barb.pull(player, weapon, victim);
        lifeSteal.heal(player, weapon, event.getFinalDamage());
    }
}
