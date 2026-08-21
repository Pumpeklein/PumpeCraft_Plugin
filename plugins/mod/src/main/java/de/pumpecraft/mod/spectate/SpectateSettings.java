package de.pumpecraft.mod.spectate;

import org.bukkit.configuration.file.FileConfiguration;

public final class SpectateSettings {
    private final double minDistance;
    private final double maxDistance;
    private final double growth;
    private final double wallMargin;

    public SpectateSettings(FileConfiguration config) {
        minDistance = Math.max(0.5D, config.getDouble("spectate.zoom.min-distance", 2.0D));
        maxDistance = Math.max(minDistance, config.getDouble("spectate.zoom.max-distance", 48.0D));
        growth = Math.max(1.05D, config.getDouble("spectate.zoom.growth", 1.35D));
        wallMargin = Math.max(0.0D, config.getDouble("spectate.zoom.wall-margin", 0.35D));
    }

    public double minDistance() {
        return minDistance;
    }

    public double maxDistance() {
        return maxDistance;
    }

    public double growth() {
        return growth;
    }

    public double wallMargin() {
        return wallMargin;
    }

    /**
     * Die Stufen wachsen geometrisch: nah am Spieler bewegt ein Mausradschritt die Kamera kaum,
     * weit draußen um viele Blöcke. Linear gestufter Zoom fühlt sich in beiden Bereichen falsch an.
     */
    public double distanceOf(int zoomLevel) {
        if (zoomLevel <= 0) {
            return 0.0D;
        }
        return Math.min(maxDistance, minDistance * Math.pow(growth, zoomLevel - 1));
    }

    public int maxZoomLevel() {
        int level = 1;
        while (distanceOf(level) < maxDistance && level < 64) {
            level++;
        }
        return level;
    }
}
