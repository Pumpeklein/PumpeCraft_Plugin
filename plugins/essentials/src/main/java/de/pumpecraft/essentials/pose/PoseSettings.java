package de.pumpecraft.essentials.pose;

import org.bukkit.configuration.file.FileConfiguration;

public record PoseSettings(double seatOffset, boolean requireGround) {

    /**
     * Ein Reiter wird {@code Avatar.DEFAULT_VEHICLE_ATTACHMENT} von 0.6 unter dem Anhängepunkt
     * seines Fahrzeugs abgesetzt. Genau so weit über seiner Entity-Position liegt beim
     * Spielermodell auch der Fuß eines Sitzenden: Die Beine knicken auf Hüfthöhe 0.75 um 81°
     * ab und alles wird mit 0.9375 skaliert gerendert, macht 0.594. Beide Werte heben sich
     * also auf - der Sitz gehört genau auf die Standhöhe, nicht darunter.
     */
    public static final double DEFAULT_SEAT_OFFSET = 0.0D;

    public static PoseSettings from(FileConfiguration config) {
        return new PoseSettings(
            Math.clamp(config.getDouble("sit.seat-offset", DEFAULT_SEAT_OFFSET), -1.0D, 1.0D),
            config.getBoolean("sit.require-ground", true)
        );
    }
}
