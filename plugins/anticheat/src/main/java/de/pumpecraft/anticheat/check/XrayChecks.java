package de.pumpecraft.anticheat.check;

import de.pumpecraft.anticheat.core.CheckType;
import de.pumpecraft.anticheat.core.PlayerState;
import de.pumpecraft.anticheat.core.PlayerStateStore;
import de.pumpecraft.anticheat.core.ViolationService;
import de.pumpecraft.utils.Texts;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.Plugin;

public final class XrayChecks extends AbstractCheck {
    private static final long SAME_VEIN_MILLIS = 30_000L;
    private static final double SAME_VEIN_DISTANCE_SQUARED = 16.0;
    private static final BlockFace[] FACES = {
        BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
        BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };
    private Set<Material> valuableBlocks;
    private Set<Material> naturalBlocks;

    public XrayChecks(Plugin plugin, PlayerStateStore states, ViolationService violations) {
        super(plugin, states, violations);
    }

    public void reload() {
        valuableBlocks = null;
        naturalBlocks = null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!violations.enabled(CheckType.XRAY) || exempt(player)) {
            return;
        }

        Block block = event.getBlock();
        Material material = block.getType();
        if (!naturalBlocks().contains(material) && !valuableBlocks().contains(material)) {
            return;
        }

        PlayerState.Mining mining = state(player).mining;
        long now = System.currentTimeMillis();
        resetExpiredWindow(mining, now);
        mining.naturalBreaks++;

        if (!valuableBlocks().contains(material)) {
            if (mining.blocksSinceVein < Integer.MAX_VALUE) {
                mining.blocksSinceVein++;
            }
            return;
        }

        Location oreLocation = block.getLocation();
        if (sameVein(mining, oreLocation, now)) {
            mining.lastOreLocation = oreLocation;
            mining.lastOreMillis = now;
            return;
        }

        boolean directPath = mining.lastOreLocation != null
            && mining.blocksSinceVein
                <= settings.platformInteger(player, CheckType.XRAY, "maximum-direct-blocks", 8)
            && exposedFaces(block) <= 1;

        mining.veinDiscoveries++;
        if (directPath) {
            mining.directPaths++;
        }
        mining.blocksSinceVein = 0;
        mining.lastOreLocation = oreLocation;
        mining.lastOreMillis = now;

        evaluate(player, mining);
    }

    private Set<Material> valuableBlocks() {
        if (valuableBlocks == null) {
            valuableBlocks = materials("valuable-blocks");
        }
        return valuableBlocks;
    }

    private Set<Material> naturalBlocks() {
        if (naturalBlocks == null) {
            naturalBlocks = materials("natural-blocks");
        }
        return naturalBlocks;
    }

    private Set<Material> materials(String setting) {
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String value : settings.strings(CheckType.XRAY, setting)) {
            Material material = Material.matchMaterial(value.trim().toUpperCase(Locale.ROOT));
            if (material != null) {
                materials.add(material);
            } else {
                plugin.getLogger().warning(
                    "Unknown material in checks.xray." + setting + ": " + value
                );
            }
        }
        return materials;
    }

    private void evaluate(Player player, PlayerState.Mining mining) {
        int minimumBreaks = settings.platformInteger(player, CheckType.XRAY, "minimum-natural-breaks", 40);
        int minimumVeins = settings.platformInteger(player, CheckType.XRAY, "minimum-vein-discoveries", 5);
        double maximumRatio = settings.platformDecimal(player, CheckType.XRAY, "maximum-vein-ratio", 0.12);
        int suspiciousPaths = settings.platformInteger(player, CheckType.XRAY, "suspicious-direct-paths", 3);

        double currentRatio = mining.naturalBreaks == 0
            ? 0.0
            : (double) mining.veinDiscoveries / mining.naturalBreaks;
        debug(CheckType.XRAY, player, mining.naturalBreaks + "/" + minimumBreaks + " Blöcke, "
            + mining.veinDiscoveries + "/" + minimumVeins + " Adern, Quote "
            + Texts.percent(currentRatio) + " (max " + Texts.percent(maximumRatio) + "), "
            + mining.directPaths + "/" + suspiciousPaths + " direkte Wege");

        if (mining.naturalBreaks >= minimumBreaks && mining.veinDiscoveries >= minimumVeins) {
            double ratio = currentRatio;
            if (ratio > maximumRatio) {
                violations.flag(
                    player,
                    CheckType.XRAY,
                    Math.min(2.0, 0.5 + (ratio - maximumRatio) * 10.0),
                    mining.veinDiscoveries + " Erzadern bei " + mining.naturalBreaks
                        + " Blöcken (" + Texts.percent(ratio) + ")"
                );
            } else {
                violations.reward(player, CheckType.XRAY, 0.05);
            }
        }

        if (mining.directPaths >= suspiciousPaths) {
            violations.flag(
                player,
                CheckType.XRAY,
                settings.decimal(CheckType.XRAY, "violation-amount", 2.0),
                mining.directPaths + " auffällig direkte Wege zu seltenen Erzen"
            );
            mining.directPaths = Math.max(0, suspiciousPaths - 1);
        }
    }

    private void resetExpiredWindow(PlayerState.Mining mining, long now) {
        long windowMillis = Math.max(
            60_000L,
            settings.duration(CheckType.XRAY, "window-seconds", 600L) * 1_000L
        );
        if (mining.windowStarted != 0L && now - mining.windowStarted < windowMillis) {
            return;
        }
        mining.windowStarted = now;
        mining.naturalBreaks = 0;
        mining.veinDiscoveries = 0;
        mining.directPaths = 0;
        mining.blocksSinceVein = Integer.MAX_VALUE;
        mining.lastOreLocation = null;
        mining.lastOreMillis = 0L;
    }

    private boolean sameVein(PlayerState.Mining mining, Location location, long now) {
        return mining.lastOreLocation != null
            && mining.lastOreLocation.getWorld() == location.getWorld()
            && now - mining.lastOreMillis <= SAME_VEIN_MILLIS
            && mining.lastOreLocation.distanceSquared(location) <= SAME_VEIN_DISTANCE_SQUARED;
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
}
