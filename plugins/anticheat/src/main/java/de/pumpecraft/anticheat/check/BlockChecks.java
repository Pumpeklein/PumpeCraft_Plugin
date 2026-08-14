package de.pumpecraft.anticheat.check;

import de.pumpecraft.anticheat.core.CheckType;
import de.pumpecraft.anticheat.core.PlayerState;
import de.pumpecraft.anticheat.core.PlayerStateStore;
import de.pumpecraft.anticheat.core.ViolationService;
import de.pumpecraft.utils.Locations;
import de.pumpecraft.utils.Rates;
import de.pumpecraft.utils.Texts;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

public final class BlockChecks extends AbstractCheck {
    private static final long RATE_WINDOW_MILLIS = 1_000L;

    public BlockChecks(Plugin plugin, PlayerStateStore states, ViolationService violations) {
        super(plugin, states, violations);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (exempt(player)) {
            return;
        }

        PlayerState.Blocks blocks = state(player).blocks;
        long now = System.currentTimeMillis();
        long previousPlace = blocks.lastPlaceMillis;

        if (checkBlockReach(player, event.getBlockPlaced(), "platziert")) {
            event.setCancelled(true);
            return;
        }

        int places = Rates.record(blocks.placeTimes, now, RATE_WINDOW_MILLIS);
        int maximum = settings.platformInteger(player, CheckType.FAST_PLACE, "max-places-per-second", 8);
        if (violations.enabled(CheckType.FAST_PLACE) && places > maximum) {
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

        checkScaffold(event, state(player), now, previousPlace);
        blocks.lastPlaceMillis = now;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (exempt(player)) {
            return;
        }

        if (checkBlockReach(player, event.getBlock(), "abgebaut")) {
            event.setCancelled(true);
            return;
        }

        checkNuker(player, event.getBlock());

        if (!violations.enabled(CheckType.FAST_BREAK)) {
            return;
        }
        PlayerState.Blocks blocks = state(player).blocks;
        int breaks = Rates.record(blocks.breakTimes, System.currentTimeMillis(), RATE_WINDOW_MILLIS);
        int maximum = settings.platformInteger(player, CheckType.FAST_BREAK, "max-breaks-per-second", 14);
        if (player.getPotionEffect(PotionEffectType.HASTE) != null
            || player.getInventory().getItemInMainHand()
                .getEnchantmentLevel(Enchantment.EFFICIENCY) >= 4) {
            maximum = Math.max(maximum, settings.integer(CheckType.FAST_BREAK, "boosted-maximum", 24));
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

    /** Beyond the configured range no vanilla client can interact at all; returns true to undo. */
    private boolean checkBlockReach(Player player, Block block, String action) {
        if (!violations.enabled(CheckType.BLOCK_REACH)) {
            return false;
        }

        double distance = player.getEyeLocation()
            .distance(block.getLocation().add(0.5, 0.5, 0.5));
        double maximum = settings.platformDecimal(player, CheckType.BLOCK_REACH, "maximum", 6.0);
        if (distance <= maximum) {
            return false;
        }

        double level = violations.flag(
            player,
            CheckType.BLOCK_REACH,
            settings.decimal(CheckType.BLOCK_REACH, "violation-amount", 2.0),
            "Block " + action + " auf " + Texts.decimal(distance) + " Blöcken > " + Texts.decimal(maximum)
        );
        return violations.shouldCancel(player, CheckType.BLOCK_REACH, level);
    }

    /** Spread separates nuker from a fast miner, who keeps hitting the same spot. */
    private void checkNuker(Player player, Block block) {
        if (!violations.enabled(CheckType.NUKER)) {
            return;
        }

        PlayerState.Blocks blocks = state(player).blocks;
        long now = System.currentTimeMillis();
        long window = settings.duration(CheckType.NUKER, "window-millis", 400L);
        Location location = block.getLocation();

        if (blocks.nukerAnchor == null
            || blocks.nukerAnchor.getWorld() != location.getWorld()
            || now - blocks.nukerWindowStarted > window) {
            blocks.nukerWindowStarted = now;
            blocks.nukerAnchor = location;
            blocks.nukerBreaks = 1;
            blocks.nukerSpread = 0.0;
            return;
        }

        blocks.nukerBreaks++;
        blocks.nukerSpread = Math.max(blocks.nukerSpread, blocks.nukerAnchor.distance(location));

        int minimumBreaks = settings.platformInteger(player, CheckType.NUKER, "minimum-breaks", 4);
        double minimumSpread = settings.decimal(CheckType.NUKER, "minimum-spread", 2.5);
        debug(CheckType.NUKER, player, blocks.nukerBreaks + " Blöcke (ab " + minimumBreaks
            + ") mit " + Texts.decimal(blocks.nukerSpread) + " Streuung (ab "
            + Texts.decimal(minimumSpread) + ") in " + window + "ms");
        if (blocks.nukerBreaks < minimumBreaks || blocks.nukerSpread < minimumSpread) {
            return;
        }

        violations.flag(
            player,
            CheckType.NUKER,
            settings.decimal(CheckType.NUKER, "violation-amount", 3.0),
            blocks.nukerBreaks + " Blöcke in " + window + "ms über "
                + Texts.decimal(blocks.nukerSpread) + " Blöcke Reichweite"
        );
        blocks.nukerWindowStarted = now;
        blocks.nukerAnchor = location;
        blocks.nukerBreaks = 0;
        blocks.nukerSpread = 0.0;
    }

    private void checkScaffold(
        BlockPlaceEvent event,
        PlayerState state,
        long now,
        long previousPlace
    ) {
        Player player = event.getPlayer();
        if (!violations.enabled(CheckType.SCAFFOLD)) {
            return;
        }

        PlayerState.Blocks blocks = state.blocks;
        Location playerLocation = player.getLocation();
        Location blockLocation = event.getBlockPlaced().getLocation().add(0.5, 0.5, 0.5);
        double horizontalVelocity = Math.hypot(player.getVelocity().getX(), player.getVelocity().getZ());
        double horizontalDistance = Locations.horizontalDistance(playerLocation, blockLocation);
        boolean belowFeet = blockLocation.getY() <= playerLocation.getY() + 0.2;
        boolean close = horizontalDistance <= 1.9;
        boolean underFeet = horizontalDistance <= 1.35;
        boolean verticalTower = Math.abs(player.getVelocity().getY()) > 0.08 && underFeet;
        boolean moving = horizontalVelocity > 0.08
            || state.movement.recentHorizontal > 0.04
            || verticalTower;
        long maximumDelay = settings.platformDuration(
            player, CheckType.SCAFFOLD, "maximum-delay-millis", 260L
        );
        boolean rapid = previousPlace > 0L && now - previousPlace <= maximumDelay;
        double minimumAimDot = settings.platformDecimal(player, CheckType.SCAFFOLD, "minimum-aim-dot", 0.3);
        boolean aimMismatch = Locations.aimDot(player.getEyeLocation(), blockLocation) < minimumAimDot;

        if (!(belowFeet && close && moving && rapid && (underFeet || aimMismatch))) {
            blocks.scaffoldStreak = Math.max(0, blocks.scaffoldStreak - 1);
            violations.reward(player, CheckType.SCAFFOLD, 0.1);
            return;
        }

        blocks.scaffoldStreak += aimMismatch ? 2 : 1;
        if (player.isSprinting()) {
            blocks.scaffoldStreak++;
        }

        int maximum = settings.platformInteger(player, CheckType.SCAFFOLD, "suspicious-placements", 6);
        if (blocks.scaffoldStreak >= maximum) {
            violations.flag(
                player,
                CheckType.SCAFFOLD,
                1.0,
                blocks.scaffoldStreak + " Scaffold-Punkte in schneller Platzierungsfolge"
            );
            blocks.scaffoldStreak = maximum / 2;
        }
    }
}
