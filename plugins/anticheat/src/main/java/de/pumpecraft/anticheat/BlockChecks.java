package de.pumpecraft.anticheat;

import java.util.Deque;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.potion.PotionEffectType;

final class BlockChecks implements Listener {
    private static final long RATE_WINDOW_MILLIS = 1_000L;

    private final PumpeAntiCheatPlugin plugin;
    private final PlayerStateStore states;
    private final ViolationService violations;
    private final BedrockDetector bedrockDetector;

    BlockChecks(
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
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (exempt(player)) {
            return;
        }

        PlayerState state = states.get(player);
        long now = System.currentTimeMillis();
        int places = record(state.placeTimes, now);
        int maximum = integerThreshold("fastplace.max-places-per-second", player);
        if (violations.enabled(CheckType.FAST_PLACE)
            && places > maximum) {
            double level = violations.flag(
                player,
                CheckType.FAST_PLACE,
                1.0,
                places + " Platzierungen/s > " + maximum
            );
            if (violations.shouldCancel(player, CheckType.FAST_PLACE, level)) {
                event.setCancelled(true);
                return;
            }
        }

        checkScaffold(event, state);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (exempt(player)
            || !violations.enabled(CheckType.FAST_BREAK)) {
            return;
        }

        PlayerState state = states.get(player);
        int breaks = record(state.breakTimes, System.currentTimeMillis());
        int maximum = integerThreshold("fastbreak.max-breaks-per-second", player);
        if (player.getPotionEffect(PotionEffectType.HASTE) != null
            || player.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.EFFICIENCY) >= 4) {
            maximum = Math.max(maximum, 24);
        }
        if (breaks > maximum) {
            double level = violations.flag(
                player,
                CheckType.FAST_BREAK,
                1.0,
                breaks + " Blöcke/s > " + maximum
            );
            if (violations.shouldCancel(player, CheckType.FAST_BREAK, level)) {
                event.setCancelled(true);
            }
        } else {
            violations.reward(player, CheckType.FAST_BREAK, 0.05);
        }
    }

    private void checkScaffold(BlockPlaceEvent event, PlayerState state) {
        Player player = event.getPlayer();
        if (!violations.enabled(CheckType.SCAFFOLD)
        ) {
            return;
        }

        Location playerLocation = player.getLocation();
        Location blockLocation = event.getBlockPlaced().getLocation().add(0.5, 0.5, 0.5);
        double horizontalVelocity = Math.hypot(player.getVelocity().getX(), player.getVelocity().getZ());
        boolean belowFeet = blockLocation.getY() <= playerLocation.getY() + 0.15;
        boolean close = horizontalDistance(playerLocation, blockLocation) <= 1.65;
        boolean notLookingDown = playerLocation.getPitch() < 55.0f;
        boolean moving = horizontalVelocity > 0.08 || state.recentHorizontalMovement > 0.04;

        if (belowFeet && close && notLookingDown && moving) {
            state.scaffoldStreak++;
        } else {
            state.scaffoldStreak = Math.max(0, state.scaffoldStreak - 2);
            violations.reward(player, CheckType.SCAFFOLD, 0.1);
            return;
        }

        int maximum = integerThreshold("scaffold.suspicious-placements", player);
        if (state.scaffoldStreak >= maximum) {
            violations.flag(
                player,
                CheckType.SCAFFOLD,
                1.0,
                state.scaffoldStreak + " verdächtige Platzierungen in Folge"
            );
            state.scaffoldStreak = maximum / 2;
        }
    }

    private int record(Deque<Long> times, long now) {
        times.addLast(now);
        while (!times.isEmpty() && now - times.peekFirst() > RATE_WINDOW_MILLIS) {
            times.removeFirst();
        }
        return times.size();
    }

    private boolean exempt(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }

    private int integerThreshold(String path, Player player) {
        String platform = bedrockDetector.isBedrock(player.getUniqueId()) ? "-bedrock" : "-java";
        return plugin.getConfig().getInt("checks." + path + platform);
    }

    private double horizontalDistance(Location first, Location second) {
        double x = first.getX() - second.getX();
        double z = first.getZ() - second.getZ();
        return Math.sqrt(x * x + z * z);
    }
}
