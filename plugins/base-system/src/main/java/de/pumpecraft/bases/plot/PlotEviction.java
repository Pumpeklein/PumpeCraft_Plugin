package de.pumpecraft.bases.plot;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Setzt jemanden vor das Grundstück, statt ihn darauf einzusperren.
 *
 * <p>Wem die Rechte entzogen werden, während er auf dem Grundstück steht, dem einfach jeden Schritt
 * zu verbieten hieße, ihn dort festzuhalten. Er wird deshalb über die nächste Kante hinausgesetzt -
 * und zwar auf einen Platz, der ihn nicht umbringt.
 */
public final class PlotEviction {
    private static final int MARGIN = 2;
    private static final int SEARCH_RADIUS = 24;

    private PlotEviction() {
    }

    public static Location outside(Location inside, PlotArea area) {
        World world = inside.getWorld();
        if (world == null) {
            return null;
        }
        int x = inside.getBlockX();
        int z = inside.getBlockZ();

        // Die vier Kanten, die nächste zuerst - der kürzeste Weg hinaus ist der am wenigsten
        // überraschende.
        int[][] candidates = {
            {area.minX() - MARGIN, z},
            {area.maxX() + MARGIN, z},
            {x, area.minZ() - MARGIN},
            {x, area.maxZ() + MARGIN}
        };
        int[] distances = {
            x - area.minX(),
            area.maxX() - x,
            z - area.minZ(),
            area.maxZ() - z
        };
        Integer[] order = {0, 1, 2, 3};
        java.util.Arrays.sort(order, (first, second) -> distances[first] - distances[second]);

        for (int index : order) {
            Location spot = safeSpot(world, candidates[index][0], candidates[index][1]);
            if (spot != null) {
                spot.setYaw(inside.getYaw());
                spot.setPitch(inside.getPitch());
                return spot;
            }
        }
        return world.getSpawnLocation();
    }

    /** Sucht in der Säule nach zwei freien Blöcken über festem, ungefährlichem Grund. */
    private static Location safeSpot(World world, int x, int z) {
        int surface = world.getHighestBlockYAt(x, z);
        int lowest = Math.max(world.getMinHeight() + 1, surface - SEARCH_RADIUS);
        for (int y = surface + 1; y >= lowest; y--) {
            if (standable(world, x, y, z)) {
                return new Location(world, x + 0.5D, y, z + 0.5D);
            }
        }
        return null;
    }

    private static boolean standable(World world, int x, int y, int z) {
        Block ground = world.getBlockAt(x, y - 1, z);
        if (!ground.getType().isSolid() || dangerous(ground.getType())) {
            return false;
        }
        return world.getBlockAt(x, y, z).isPassable()
            && world.getBlockAt(x, y + 1, z).isPassable()
            && !dangerous(world.getBlockAt(x, y, z).getType());
    }

    private static boolean dangerous(Material material) {
        return material == Material.LAVA
            || material == Material.FIRE
            || material == Material.SOUL_FIRE
            || material == Material.MAGMA_BLOCK
            || material == Material.CAMPFIRE
            || material == Material.SOUL_CAMPFIRE
            || material == Material.SWEET_BERRY_BUSH
            || material == Material.POWDER_SNOW
            || material == Material.CACTUS;
    }
}
