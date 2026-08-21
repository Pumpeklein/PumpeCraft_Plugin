package de.pumpecraft.bases.plot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;

/**
 * Die Fläche eines Grundstücks, wahlweise mit Höhengrenzen.
 *
 * <p>Ohne Grenzen reicht ein Grundstück von der untersten bis zur obersten Schicht - das ist der
 * Normalfall und macht die Zugehörigkeit eines Blocks zu einer Frage von zwei Koordinaten. Nur das
 * Team schränkt die Höhe ein; {@code null} steht dann für "nach unten" beziehungsweise "nach oben
 * offen", nicht für einen konkreten Wert der Welt.
 */
public record PlotArea(
    UUID worldId,
    String worldName,
    int minX,
    int minZ,
    int maxX,
    int maxZ,
    Integer minY,
    Integer maxY
) {
    public static PlotArea between(Location first, Location second) {
        return new PlotArea(
            first.getWorld().getUID(),
            first.getWorld().getName(),
            Math.min(first.getBlockX(), second.getBlockX()),
            Math.min(first.getBlockZ(), second.getBlockZ()),
            Math.max(first.getBlockX(), second.getBlockX()),
            Math.max(first.getBlockZ(), second.getBlockZ()),
            null,
            null
        );
    }

    public PlotArea withHeight(Integer newMinY, Integer newMaxY) {
        return new PlotArea(worldId, worldName, minX, minZ, maxX, maxZ, newMinY, newMaxY);
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int depth() {
        return maxZ - minZ + 1;
    }

    public long area() {
        return (long) width() * depth();
    }

    public boolean fullHeight() {
        return minY == null && maxY == null;
    }

    public int centerX() {
        return Math.floorDiv(minX + maxX, 2);
    }

    public int centerZ() {
        return Math.floorDiv(minZ + maxZ, 2);
    }

    /** Abstand der Mitte zum Ursprung 0/0 - die Grundlage des Lagefaktors. */
    public double distanceFromOrigin() {
        double x = centerX();
        double z = centerZ();
        return Math.sqrt(x * x + z * z);
    }

    public boolean containsColumn(UUID world, int x, int z) {
        return worldId.equals(world) && x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean contains(UUID world, int x, int y, int z) {
        return containsColumn(world, x, z)
            && (minY == null || y >= minY)
            && (maxY == null || y <= maxY);
    }

    public boolean contains(Location location) {
        return location.getWorld() != null && contains(
            location.getWorld().getUID(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        );
    }

    public boolean overlaps(PlotArea other) {
        if (!worldId.equals(other.worldId)) {
            return false;
        }
        boolean columns = minX <= other.maxX && maxX >= other.minX
            && minZ <= other.maxZ && maxZ >= other.minZ;
        if (!columns) {
            return false;
        }
        // Zwei Grundstücke dürfen übereinander liegen, solange sich ihre Höhen nicht berühren.
        boolean belowOther = maxY != null && other.minY != null && maxY < other.minY;
        boolean aboveOther = minY != null && other.maxY != null && minY > other.maxY;
        return !belowOther && !aboveOther;
    }

    /**
     * Die Schlüssel aller berührten Chunks. Der Index legt Grundstücke darunter ab, damit ein
     * Blockereignis nicht jedes Grundstück der Welt durchsehen muss.
     */
    public List<Long> chunkKeys() {
        List<Long> keys = new ArrayList<>();
        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                keys.add(chunkKey(chunkX, chunkZ));
            }
        }
        return keys;
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public String corners() {
        return minX + " / " + minZ + "  bis  " + maxX + " / " + maxZ;
    }

    public String heightLabel() {
        if (fullHeight()) {
            return "volle Höhe";
        }
        return (minY == null ? "unten" : String.valueOf(minY))
            + " bis " + (maxY == null ? "oben" : String.valueOf(maxY));
    }
}
