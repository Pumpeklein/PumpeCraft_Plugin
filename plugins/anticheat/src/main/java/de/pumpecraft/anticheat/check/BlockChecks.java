package de.pumpecraft.anticheat.check;

import de.pumpecraft.anticheat.core.CheckType;
import de.pumpecraft.anticheat.core.PlayerState;
import de.pumpecraft.anticheat.core.PlayerStateStore;
import de.pumpecraft.anticheat.core.ViolationService;
import de.pumpecraft.enchants.EnchantService;
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
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.potion.PotionEffectType;

public final class BlockChecks extends AbstractCheck {
    private static final long RATE_WINDOW_MILLIS = 1_000L;

    private EnchantService enchants;
    private boolean enchantsResolved;

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
        if (exempt(player) || breakingChain(player)) {
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

    /**
     * Eine Verzauberung wie Aderabbau bricht bis zu 32 Blöcke in einem Tick, viele davon außer
     * Reichweite. Ohne diese Ausnahme meldet jeder Erzgang Reichweite und Abbaurate.
     */
    private boolean breakingChain(Player player) {
        if (!enchantsResolved) {
            enchantsResolved = true;
            RegisteredServiceProvider<EnchantService> registration = plugin.getServer()
                .getServicesManager().getRegistration(EnchantService.class);
            enchants = registration == null ? null : registration.getProvider();
        }
        return enchants != null && enchants.breakingChain(player);
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

    /**
     * Abbaugeschwindigkeit und Streuung trennen einen Nuker nicht von normalem Graben - ein
     * 3x3-Aushub ist beides. Unterscheidend ist die Blickrichtung: legitim lässt sich nur
     * abbauen, was der Spieler anvisiert, ein Nuker räumt auch hinter sich ab.
     */
    private void checkNuker(Player player, Block block) {
        if (!violations.enabled(CheckType.NUKER)) {
            return;
        }

        PlayerState.Blocks blocks = state(player).blocks;
        long now = System.currentTimeMillis();
        long window = settings.duration(CheckType.NUKER, "window-millis", 1_000L);
        int breaks = Rates.record(blocks.nukerBreaks, now, window);
        Rates.trim(blocks.nukerOutsideView, now, window);

        Location eye = player.getEyeLocation();
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        double distance = eye.distance(center);
        double aim = Locations.aimDot(eye, center);
        double minimumDistance = settings.decimal(CheckType.NUKER, "minimum-aim-distance", 1.5);
        double minimumAim = settings.decimal(CheckType.NUKER, "minimum-aim-dot", 0.4);
        if (distance >= minimumDistance && aim < minimumAim) {
            blocks.nukerOutsideView.addLast(now);
        }

        int minimumBreaks = settings.platformInteger(player, CheckType.NUKER, "minimum-breaks", 6);
        int minimumOutside = settings.integer(CheckType.NUKER, "minimum-outside-view", 3);
        int outside = blocks.nukerOutsideView.size();
        debug(CheckType.NUKER, player, breaks + "/" + minimumBreaks + " Blöcke, "
            + outside + "/" + minimumOutside + " außerhalb der Blickrichtung, Aim "
            + Texts.decimal(aim) + " auf " + Texts.decimal(distance) + " Blöcken");

        if (breaks < minimumBreaks || outside < minimumOutside) {
            return;
        }

        violations.flag(
            player,
            CheckType.NUKER,
            settings.decimal(CheckType.NUKER, "violation-amount", 3.0),
            outside + " von " + breaks + " Blöcken in " + window
                + "ms außerhalb der Blickrichtung"
        );
        blocks.nukerBreaks.clear();
        blocks.nukerOutsideView.clear();
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
