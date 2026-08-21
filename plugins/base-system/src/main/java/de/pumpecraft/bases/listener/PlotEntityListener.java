package de.pumpecraft.bases.listener;

import de.pumpecraft.bases.BaseText;
import de.pumpecraft.bases.plot.Plot;
import de.pumpecraft.bases.plot.PlotFlag;
import de.pumpecraft.bases.plot.PlotGuard;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Enderman;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Vehicle;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Der Teil des Schutzes, der an Wesen hängt: Kampf, Tiere, Monster und alles, was Blöcke ohne
 * Blockereignis verändert - ein Enderman, der einen Block mitnimmt, ist kein Blockabbau.
 */
public final class PlotEntityListener implements Listener {
    private final PlotGuard guard;

    public PlotEntityListener(PlotGuard guard) {
        this.guard = guard;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = attackerOf(event.getDamager());
        if (attacker == null) {
            return;
        }
        Entity victim = event.getEntity();
        Location location = victim.getLocation();
        if (victim instanceof Player) {
            // Beide Seiten zählen: Sonst wäre ein Grundstück ohne PvP ein Ort, von dem aus man
            // gefahrlos nach draußen schießt.
            if (!guard.canFight(attacker, attacker.getLocation(), location)) {
                event.setCancelled(true);
                attacker.sendActionBar(BaseText.error("Hier ist PvP abgeschaltet."));
            }
            return;
        }
        if (peaceful(victim)) {
            if (!guard.canHurtAnimals(attacker, location)) {
                event.setCancelled(true);
                attacker.sendActionBar(BaseText.error("Hier stehen Tiere unter Schutz."));
            }
            return;
        }
        // Rüstungsständer und Fahrzeuge sind kein Tier und kein Block; ohne diese Zeile fielen sie
        // durch jede Flagge und ließen sich auf fremdem Grundstück abräumen.
        if ((victim instanceof ArmorStand || victim instanceof Vehicle)
            && !guard.canBuild(attacker, location)) {
            event.setCancelled(true);
            attacker.sendActionBar(BaseText.error("Das gehört nicht dir."));
        }
    }

    /** Rüstungsständer und Fahrzeuge tragen ihren Inhalt mit sich; Anfassen zählt wie Bauen. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Entity target = event.getRightClicked();
        if ((target instanceof ArmorStand || target instanceof Vehicle)
            && !guard.canBuild(event.getPlayer(), target.getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(BaseText.error("Das gehört nicht dir."));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player remover = attackerOf(event.getRemover());
        Hanging hanging = event.getEntity();
        if (remover != null) {
            if (!guard.canBuild(remover, hanging.getLocation())) {
                event.setCancelled(true);
            }
            return;
        }
        if (!allows(hanging.getLocation(), PlotFlag.MOB_GRIEFING)) {
            event.setCancelled(true);
        }
    }

    /**
     * Enderman, Creeper, Wither und trampelndes Vieh verändern Blöcke ohne Blockereignis - und
     * ein Sandblock, der zu fallen beginnt, ebenfalls: Er wird in diesem Augenblick zum Wesen.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        Location location = event.getBlock().getLocation();
        if (entity instanceof FallingBlock) {
            if (!allows(location, PlotFlag.FALLING_BLOCKS)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getBlock().getType() == Material.FARMLAND && !allows(location, PlotFlag.TRAMPLE)) {
            event.setCancelled(true);
            return;
        }
        boolean griefer = entity instanceof Enderman
            || entity instanceof Creeper
            || entity instanceof Wither
            || entity instanceof EnderDragon;
        if (griefer && !allows(location, PlotFlag.MOB_GRIEFING)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }
        if (!allows(event.getLocation(), PlotFlag.MOB_SPAWNING)) {
            event.setCancelled(true);
        }
    }

    private boolean allows(Location location, PlotFlag flag) {
        Plot plot = guard.at(location);
        return plot == null || plot.flag(flag);
    }

    /**
     * Alles, was ein Wesen mit Verstand ist und nicht angreift: Kühe ebenso wie Fische, Dorfbewohner
     * und Eisengolems. Eine Aufzählung einzelner Sorten hätte immer eine vergessen.
     */
    private boolean peaceful(Entity entity) {
        return entity instanceof Mob && !(entity instanceof Enemy);
    }

    private Player attackerOf(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Player player ? player : null;
        }
        return null;
    }
}
