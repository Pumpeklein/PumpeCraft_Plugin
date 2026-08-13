package de.pumpecraft.anticheat;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

final class XrayChecks implements Listener {
    private static final long SAME_VEIN_MILLIS = 30_000L;
    private static final double SAME_VEIN_DISTANCE_SQUARED = 16.0;
    private static final BlockFace[] FACES = {
        BlockFace.UP,
        BlockFace.DOWN,
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.EAST,
        BlockFace.WEST
    };
    private static final Set<Material> VALUABLE_BLOCKS = EnumSet.of(
        Material.DIAMOND_ORE,
        Material.DEEPSLATE_DIAMOND_ORE,
        Material.EMERALD_ORE,
        Material.DEEPSLATE_EMERALD_ORE,
        Material.ANCIENT_DEBRIS
    );
    private static final Set<Material> NATURAL_MINING_BLOCKS = EnumSet.of(
        Material.STONE,
        Material.DEEPSLATE,
        Material.TUFF,
        Material.GRANITE,
        Material.DIORITE,
        Material.ANDESITE,
        Material.NETHERRACK,
        Material.BASALT,
        Material.BLACKSTONE,
        Material.DIAMOND_ORE,
        Material.DEEPSLATE_DIAMOND_ORE,
        Material.EMERALD_ORE,
        Material.DEEPSLATE_EMERALD_ORE,
        Material.ANCIENT_DEBRIS
    );

    private final PumpeAntiCheatPlugin plugin;
    private final PlayerStateStore states;
    private final ViolationService violations;
    private final BedrockDetector bedrockDetector;

    XrayChecks(
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!violations.enabled(CheckType.XRAY) || exempt(player)) {
            return;
        }

        Block block = event.getBlock();
        Material material = block.getType();
        if (!NATURAL_MINING_BLOCKS.contains(material)) {
            return;
        }

        PlayerState state = states.get(player);
        long now = System.currentTimeMillis();
        resetExpiredWindow(state, now);
        state.xrayNaturalBreaks++;

        if (!VALUABLE_BLOCKS.contains(material)) {
            if (state.xrayBlocksSinceVein < Integer.MAX_VALUE) {
                state.xrayBlocksSinceVein++;
            }
            return;
        }

        Location oreLocation = block.getLocation();
        if (sameVein(state, oreLocation, now)) {
            state.lastOreLocation = oreLocation;
            state.lastOreMillis = now;
            return;
        }

        boolean directPath = state.lastOreLocation != null
            && state.xrayBlocksSinceVein <= integerThreshold("xray.maximum-direct-blocks", player)
            && exposedFaces(block) <= 1;

        state.xrayVeinDiscoveries++;
        if (directPath) {
            state.xrayDirectPaths++;
        }
        state.xrayBlocksSinceVein = 0;
        state.lastOreLocation = oreLocation;
        state.lastOreMillis = now;

        evaluate(player, state);
    }

    private void evaluate(Player player, PlayerState state) {
        int minimumBreaks = integerThreshold("xray.minimum-natural-breaks", player);
        int minimumVeins = integerThreshold("xray.minimum-vein-discoveries", player);
        double maximumRatio = decimalThreshold("xray.maximum-vein-ratio", player);
        int suspiciousPaths = integerThreshold("xray.suspicious-direct-paths", player);

        if (state.xrayNaturalBreaks >= minimumBreaks
            && state.xrayVeinDiscoveries >= minimumVeins) {
            double ratio = (double) state.xrayVeinDiscoveries / state.xrayNaturalBreaks;
            if (ratio > maximumRatio) {
                violations.flag(
                    player,
                    CheckType.XRAY,
                    Math.min(2.0, 0.5 + (ratio - maximumRatio) * 10.0),
                    state.xrayVeinDiscoveries + " Erzadern bei " + state.xrayNaturalBreaks
                        + " Blöcken (" + percent(ratio) + ")"
                );
            } else {
                violations.reward(player, CheckType.XRAY, 0.05);
            }
        }

        if (state.xrayDirectPaths >= suspiciousPaths) {
            violations.flag(
                player,
                CheckType.XRAY,
                1.0,
                state.xrayDirectPaths + " auffällig direkte Wege zu seltenen Erzen"
            );
            state.xrayDirectPaths = Math.max(0, suspiciousPaths - 1);
        }
    }

    private void resetExpiredWindow(PlayerState state, long now) {
        long windowMillis = Math.max(
            60_000L,
            plugin.getConfig().getLong("checks.xray.window-seconds", 600L) * 1_000L
        );
        if (state.xrayWindowStarted != 0L && now - state.xrayWindowStarted < windowMillis) {
            return;
        }
        state.xrayWindowStarted = now;
        state.xrayNaturalBreaks = 0;
        state.xrayVeinDiscoveries = 0;
        state.xrayDirectPaths = 0;
        state.xrayBlocksSinceVein = Integer.MAX_VALUE;
        state.lastOreLocation = null;
        state.lastOreMillis = 0L;
    }

    private boolean sameVein(PlayerState state, Location location, long now) {
        return state.lastOreLocation != null
            && state.lastOreLocation.getWorld() == location.getWorld()
            && now - state.lastOreMillis <= SAME_VEIN_MILLIS
            && state.lastOreLocation.distanceSquared(location) <= SAME_VEIN_DISTANCE_SQUARED;
    }

    private int exposedFaces(Block block) {
        int exposed = 0;
        for (BlockFace face : FACES) {
            Block relative = block.getRelative(face);
            if (!relative.getType().isOccluding() || relative.isLiquid()) {
                exposed++;
            }
        }
        return exposed;
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

    private String percent(double ratio) {
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100.0);
    }
}
