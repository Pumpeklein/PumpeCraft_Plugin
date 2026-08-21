package de.pumpecraft.mod.spectate;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Rechnet aus Ziel und Zoomstufe den Standort der Kamera.
 *
 * <p>Stufe 0 sitzt genau im Auge des Ziels; zusammen mit dem für den Zuschauer ausgeblendeten Ziel
 * ergibt das die Ego-Perspektive. Ab Stufe 1 kreist die Kamera um das Ziel: Sie liegt der
 * Blickrichtung des <em>Zuschauers</em> entgegengesetzt, wodurch das Ziel in der Bildmitte bleibt
 * und die Maus die Kamera um ihn herum führt. Beide Fälle sind dieselbe Rechnung, nur mit Abstand
 * null beziehungsweise größer null.
 */
final class SpectateCamera {
    private static final double MINIMUM_WALL_DISTANCE = 0.4D;

    private final SpectateSettings settings;

    SpectateCamera(SpectateSettings settings) {
        this.settings = settings;
    }

    /**
     * Wo das Auge der Kamera sitzt. Die Winkel der zurückgegebenen Position sind bedeutungslos -
     * die Blickrichtung gehört dem Zuschauer und wird nirgends gesetzt.
     */
    Location location(Player target, Player viewer, int zoomLevel) {
        Location eye = target.getEyeLocation();
        double requested = settings.distanceOf(zoomLevel);
        if (requested <= 0.0D) {
            return eye;
        }
        Vector backwards = viewer.getLocation().getDirection().multiply(-1.0D);
        return eye.clone().add(backwards.clone().multiply(distance(eye, backwards, requested)));
    }

    /** Ein Teleport setzt die Füße; sichtbar ist aber das Auge, also muss die Augenhöhe herunter. */
    Location eyeToFeet(Location cameraEye, Player viewer) {
        Location feet = cameraEye.clone();
        feet.subtract(0.0D, viewer.getEyeHeight(), 0.0D);
        return feet;
    }

    private double distance(Location eye, Vector backwards, double requested) {
        RayTraceResult collision = eye.getWorld().rayTraceBlocks(
            eye,
            backwards,
            requested + settings.wallMargin(),
            FluidCollisionMode.NEVER,
            true
        );
        if (collision == null) {
            return requested;
        }
        double blocked = collision.getHitPosition().distance(eye.toVector()) - settings.wallMargin();
        return Math.max(MINIMUM_WALL_DISTANCE, Math.min(requested, blocked));
    }
}
