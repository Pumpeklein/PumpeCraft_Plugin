package de.pumpecraft.essentials.pose;

import net.minecraft.world.entity.Pose;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

/**
 * Der Block, der einen krabbelnden Spieler in seiner eigenen Spiellogik unten hält.
 *
 * <p>Die Pose des eigenen Spielers entscheidet allein der Client: Passt er stehend nicht hin,
 * probiert {@code Player#updatePlayerPose} zuerst {@link Pose#CROUCHING} und erst danach
 * {@link Pose#SWIMMING}. Die Unterkante des Deckblocks muss deshalb unter der Hockhöhe liegen -
 * eine Lücke, die nur das Stehen verbietet, lässt den Spieler hocken statt krabbeln. Nach unten
 * begrenzt die Krabbelhöhe: Berührt der Block die Krabbelbox, bricht der Client die Posenwahl
 * ganz ab und der Spieler bleibt stehen.
 *
 * <p>Beide Höhen kommen vom Server statt aus Konstanten und tragen damit sowohl die Skalierung
 * des Spielers als auch die Maße der Serverversion.
 */
record CrawlCover(Block block, BlockData blockData) {
    /** Ein voller Block deckt unsichtbar ab, die obere Stufe erreicht dafür die halben Höhen. */
    private static final double FULL_BLOCK_BOTTOM = 0.0D;
    private static final double TOP_SLAB_BOTTOM = 0.5D;
    private static final BlockData TOP_SLAB = topSlab();

    /** Der Client verkleinert seine Box um 1.0E-7, bevor er auf Kollision prüft. */
    private static final double FIT_MARGIN = 1.0E-4D;

    static CrawlCover at(Player player, Location location, BlockData coverBlock) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        double feet = location.getY();
        double lowest = feet + poseHeight(player, Pose.SWIMMING);
        double highest = feet + poseHeight(player, Pose.CROUCHING) - FIT_MARGIN;

        CrawlCover fullBlock = at(world, location, lowest, highest, FULL_BLOCK_BOTTOM, coverBlock);
        return fullBlock != null
            ? fullBlock
            : at(world, location, lowest, highest, TOP_SLAB_BOTTOM, TOP_SLAB);
    }

    private static CrawlCover at(
        World world,
        Location location,
        double lowest,
        double highest,
        double shapeBottom,
        BlockData blockData
    ) {
        int blockY = (int) Math.ceil(lowest - shapeBottom);
        if (blockY + shapeBottom > highest || blockY < world.getMinHeight() || blockY >= world.getMaxHeight()) {
            return null;
        }
        Block block = world.getBlockAt(location.getBlockX(), blockY, location.getBlockZ());
        return block.isPassable() && !block.isLiquid() ? new CrawlCover(block, blockData) : null;
    }

    private static double poseHeight(Player player, Pose pose) {
        return ((CraftPlayer) player).getHandle().getDimensions(pose).height();
    }

    private static BlockData topSlab() {
        Slab slab = (Slab) Material.SMOOTH_STONE_SLAB.createBlockData();
        slab.setType(Slab.Type.TOP);
        return slab;
    }
}
