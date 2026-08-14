package de.pumpecraft.anticheat.check;

import de.pumpecraft.anticheat.core.CheckType;
import de.pumpecraft.anticheat.core.PlayerState;
import de.pumpecraft.anticheat.core.PlayerStateStore;
import de.pumpecraft.anticheat.core.ViolationService;
import de.pumpecraft.utils.Locations;
import de.pumpecraft.utils.Rates;
import de.pumpecraft.utils.Texts;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.plugin.Plugin;

public final class CombatChecks extends AbstractCheck {
    private static final long CLICK_WINDOW_MILLIS = 1_000L;
    private static final long DUPLICATE_EVENT_MILLIS = 25L;

    public CombatChecks(Plugin plugin, PlayerStateStore states, ViolationService violations) {
        super(plugin, states, violations);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || exempt(player)) {
            return;
        }
        checkReach(event, player, event.getEntity());
        checkKillAura(player, event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAttackAttempt(PrePlayerAttackEntityEvent event) {
        if (event.willAttack()) {
            checkAutoClicker(event.getPlayer(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getTargetBlockExact(5) == null) {
            checkAutoClicker(player, System.currentTimeMillis());
        }
    }

    private void checkAutoClicker(Player player, long now) {
        boolean blindToBedrock = !settings.bedrockApiAvailable()
            && plugin.getConfig().getBoolean("bedrock.disable-autoclicker-without-bedrock-api", false);
        if (exempt(player) || !violations.enabled(CheckType.AUTO_CLICKER) || blindToBedrock) {
            return;
        }

        PlayerState.Combat combat = state(player).combat;
        if (now - combat.lastRecordedClickMillis <= DUPLICATE_EVENT_MILLIS) {
            return;
        }
        combat.lastRecordedClickMillis = now;
        int clicks = Rates.record(combat.clickTimes, now, CLICK_WINDOW_MILLIS);

        int minimumSamples = settings.integer(CheckType.AUTO_CLICKER, "minimum-samples", 10);
        if (clicks < minimumSamples) {
            return;
        }

        int maximum = settings.platformInteger(player, CheckType.AUTO_CLICKER, "maximum-cps", 15);
        Rates.Spread spread = Rates.spread(combat.clickTimes);
        double minimumConsistentCps = settings.platformDecimal(
            player, CheckType.AUTO_CLICKER, "minimum-consistent-cps", 9.0
        );
        double maximumVariation = settings.platformDecimal(
            player, CheckType.AUTO_CLICKER, "maximum-interval-variation", 0.22
        );

        if (clicks > maximum) {
            violations.flag(
                player,
                CheckType.AUTO_CLICKER,
                Math.min(2.0, (clicks - maximum) * 0.5),
                clicks + " Angriffe/s > " + maximum
            );
        } else if (spread.perSecond() >= minimumConsistentCps
            && spread.variation() <= maximumVariation) {
            violations.flag(
                player,
                CheckType.AUTO_CLICKER,
                0.5,
                Texts.decimal(spread.perSecond()) + " CPS mit "
                    + Texts.decimal(spread.variation() * 100.0) + "% Abweichung"
            );
        } else {
            violations.reward(player, CheckType.AUTO_CLICKER, 0.05);
        }
    }

    private void checkReach(EntityDamageByEntityEvent event, Player player, Entity target) {
        if (!violations.enabled(CheckType.REACH) || player.getWorld() != target.getWorld()) {
            return;
        }

        double reach = Locations.distanceToBox(player.getEyeLocation(), target.getBoundingBox());
        double maximum = settings.platformDecimal(player, CheckType.REACH, "maximum", 3.35)
            + Math.min(0.25, player.getPing() / 1_000.0);
        if (reach > maximum) {
            double level = violations.flag(
                player,
                CheckType.REACH,
                Math.min(2.0, reach - maximum + 0.5),
                Texts.decimal(reach) + " Blöcke > " + Texts.decimal(maximum)
            );
            if (violations.shouldCancel(player, CheckType.REACH, level)) {
                event.setCancelled(true);
            }
        } else {
            violations.reward(player, CheckType.REACH, 0.1);
        }
    }

    /** Sweeping edge only ever damages entities inside the view cone, so the aim test is safe. */
    private void checkKillAura(Player player, Entity target) {
        if (!violations.enabled(CheckType.KILL_AURA)) {
            return;
        }

        PlayerState.Combat combat = state(player).combat;
        long now = System.currentTimeMillis();
        long window = settings.duration(CheckType.KILL_AURA, "target-window-millis", 800L);
        if (now - combat.targetWindowStarted > window) {
            combat.targetWindowStarted = now;
            combat.recentTargets.clear();
        }
        combat.recentTargets.add(target.getUniqueId());
        double amount = settings.decimal(CheckType.KILL_AURA, "violation-amount", 2.0);

        int maximumTargets = settings.integer(CheckType.KILL_AURA, "maximum-targets", 3);
        if (combat.recentTargets.size() > maximumTargets) {
            violations.flag(
                player,
                CheckType.KILL_AURA,
                amount,
                combat.recentTargets.size() + " Ziele in " + window + "ms"
            );
            combat.recentTargets.clear();
        }

        Location eye = player.getEyeLocation();
        Location center = target.getBoundingBox().getCenter().toLocation(target.getWorld());
        double distance = eye.distance(center);
        double minimumDistance = settings.decimal(CheckType.KILL_AURA, "minimum-aim-distance", 2.0);
        double minimumAimDot = settings.platformDecimal(
            player, CheckType.KILL_AURA, "minimum-aim-dot", 0.55
        );
        double aim = Locations.aimDot(eye, center);
        debug(CheckType.KILL_AURA, player, combat.recentTargets.size() + " Ziele, Aim "
            + Texts.decimal(aim) + " (ab " + Texts.decimal(minimumAimDot) + ") auf "
            + Texts.decimal(distance) + " Blöcken");

        // Ein Ziel direkt am Spieler steht fast senkrecht unter der Blickachse; der Aim-Wert
        // ist dann auch bei einem sauberen Treffer niedrig und taugt nicht als Merkmal.
        if (distance >= minimumDistance && aim < minimumAimDot) {
            violations.flag(
                player,
                CheckType.KILL_AURA,
                amount,
                "Treffer außerhalb der Blickrichtung (Aim " + Texts.decimal(aim)
                    + " auf " + Texts.decimal(distance) + " Blöcken)"
            );
        }
    }
}
