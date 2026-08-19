package de.pumpecraft.essentials.pose;

import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;

public record PoseSettings(double seatOffset, boolean requireGround, BlockData crawlCover) {
    private static final Material DEFAULT_COVER = Material.BARRIER;

    /**
     * Ein Reiter wird {@code Avatar.DEFAULT_VEHICLE_ATTACHMENT} von 0.6 unter dem Anhängepunkt
     * seines Fahrzeugs abgesetzt. Genau so weit über seiner Entity-Position liegt beim
     * Spielermodell auch der Fuß eines Sitzenden: Die Beine knicken auf Hüfthöhe 0.75 um 81°
     * ab und alles wird mit 0.9375 skaliert gerendert, macht 0.594. Beide Werte heben sich
     * also auf - der Sitz gehört genau auf die Standhöhe, nicht darunter.
     */
    public static final double DEFAULT_SEAT_OFFSET = 0.0D;

    public static PoseSettings from(FileConfiguration config, Logger logger) {
        return new PoseSettings(
            Math.clamp(config.getDouble("sit.seat-offset", DEFAULT_SEAT_OFFSET), -1.0D, 1.0D),
            config.getBoolean("sit.require-ground", true),
            cover(config.getString("crawl.cover-block", DEFAULT_COVER.name()), logger)
        );
    }

    /**
     * Der Deckblock wird nur an den krabbelnden Spieler geschickt und muss deshalb kollidieren,
     * aber unsichtbar sein - sonst steht für ihn eine Wand, die sonst niemand sieht.
     */
    private static BlockData cover(String name, Logger logger) {
        Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        if (material == null || !material.isBlock() || !material.isSolid()) {
            logger.warning("Unusable crawl.cover-block " + name + "; falling back to " + DEFAULT_COVER.name() + ".");
            material = DEFAULT_COVER;
        }
        return material.createBlockData();
    }
}
