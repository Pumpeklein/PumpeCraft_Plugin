package de.pumpecraft.anticheat;

import java.util.Deque;
import java.util.Locale;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.util.BoundingBox;

final class CombatChecks implements Listener {
    private static final long CLICK_WINDOW_MILLIS = 1_000L;
    private static final long COMBAT_WINDOW_MILLIS = 2_000L;

    private final PumpeAntiCheatPlugin plugin;
    private final PlayerStateStore states;
    private final ViolationService violations;
    private final BedrockDetector bedrockDetector;

    CombatChecks(
        PumpeAntiCheatPlugin plugin,
        PlayerStateStore states,
        ViolationService violations,
        BedrockDetector bedrockDetector
    ) {
        this.plugin = plugin;
        this.states = states;
        this.violations = violations;
        this.bedrockDetector = bedrockDetector;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || exempt(player)) {
            return;
        }

        PlayerState state = states.get(player);
        state.lastAttackMillis = System.currentTimeMillis();
        checkReach(event, player, event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
            return;
        }

        Player player = event.getPlayer();
        PlayerState state = states.get(player);
        long now = System.currentTimeMillis();
        record(state.swingTimes, now);

        if (exempt(player)
            || now - state.lastAttackMillis > COMBAT_WINDOW_MILLIS
            || !violations.enabled(CheckType.AUTO_CLICKER)
            || (!bedrockDetector.isAvailable()
                && plugin.getConfig().getBoolean("bedrock.disable-autoclicker-without-bedrock-api", false))) {
            return;
        }

        int minimumSamples = plugin.getConfig().getInt("checks.autoclicker.minimum-samples", 12);
        int maximum = integerThreshold("autoclicker.maximum-cps", player);
        int clicks = state.swingTimes.size();
        if (clicks >= minimumSamples && clicks > maximum) {
            violations.flag(
                player,
                CheckType.AUTO_CLICKER,
                Math.min(2.0, (clicks - maximum) * 0.5),
                clicks + " CPS > " + maximum
            );
        } else if (clicks < maximum - 2) {
            violations.reward(player, CheckType.AUTO_CLICKER, 0.05);
        }
    }

    private void checkReach(EntityDamageByEntityEvent event, Player player, Entity target) {
        if (!violations.enabled(CheckType.REACH)
            || player.getWorld() != target.getWorld()) {
            return;
        }

        double reach = distanceToBox(player.getEyeLocation(), target.getBoundingBox());
        double maximum = decimalThreshold("reach.maximum", player);
        maximum += Math.min(0.25, player.getPing() / 1_000.0);
        if (reach > maximum) {
            double level = violations.flag(
                player,
                CheckType.REACH,
                Math.min(2.0, reach - maximum + 0.5),
                format(reach) + " Blöcke > " + format(maximum)
            );
            if (violations.shouldCancel(player, CheckType.REACH, level)) {
                event.setCancelled(true);
            }
        } else {
            violations.reward(player, CheckType.REACH, 0.1);
        }
    }

    private double distanceToBox(Location origin, BoundingBox box) {
        double x = clamp(origin.getX(), box.getMinX(), box.getMaxX());
        double y = clamp(origin.getY(), box.getMinY(), box.getMaxY());
        double z = clamp(origin.getZ(), box.getMinZ(), box.getMaxZ());
        double deltaX = origin.getX() - x;
        double deltaY = origin.getY() - y;
        double deltaZ = origin.getZ() - z;
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void record(Deque<Long> times, long now) {
        times.addLast(now);
        while (!times.isEmpty() && now - times.peekFirst() > CLICK_WINDOW_MILLIS) {
            times.removeFirst();
        }
    }

    private boolean exempt(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }

    private int integerThreshold(String path, Player player) {
        String platform = bedrockDetector.isBedrock(player.getUniqueId()) ? "-bedrock" : "-java";
        return plugin.getConfig().getInt("checks." + path + platform);
    }

    private double decimalThreshold(String path, Player player) {
        String platform = bedrockDetector.isBedrock(player.getUniqueId()) ? "-bedrock" : "-java";
        return plugin.getConfig().getDouble("checks." + path + platform);
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
