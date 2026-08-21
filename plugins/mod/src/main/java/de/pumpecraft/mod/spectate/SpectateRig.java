package de.pumpecraft.mod.spectate;

import io.papermc.paper.entity.TeleportFlag;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

/**
 * Die Aufhängung der Kamera: ein unsichtbarer Marker, auf dem der Zuschauer reitet.
 *
 * <p>Warum überhaupt ein Fahrzeug? Ein Spieler, den der Server jeden Tick teleportiert, springt in
 * Zwanzigstelschritten - der Client interpoliert die eigene Position nicht -, und jeder Teleport
 * schreibt zugleich seine Blickrichtung neu, weshalb sich die Kamera gegen die Maus stemmt. Als
 * Passagier ist beides gelöst: Der Client glättet die Bewegung fremder Wesen zwischen den Ticks,
 * und vom Reiter übernimmt der Server ausschließlich die Blickrichtung, niemals die Position.
 */
final class SpectateRig {
    private static final double SANE_OFFSET = 3.0D;
    private static final double MOVE_EPSILON = 1.0E-4D;

    ArmorStand attach(Player viewer, Location cameraEye, double mountOffset) {
        World world = cameraEye.getWorld();
        ArmorStand stand = world.spawn(
            standLocation(cameraEye, viewer, mountOffset), ArmorStand.class, marker -> {
                marker.setInvisible(true);
                marker.setMarker(true);
                marker.setBasePlate(false);
                marker.setGravity(false);
                marker.setInvulnerable(true);
                marker.setSilent(true);
                marker.setPersistent(false);
            });
        if (!stand.addPassenger(viewer)) {
            stand.remove();
            return null;
        }
        return stand;
    }

    void follow(ArmorStand stand, Player viewer, Location cameraEye, double mountOffset) {
        Location wanted = standLocation(cameraEye, viewer, mountOffset);
        if (stand.getLocation().distanceSquared(wanted) <= MOVE_EPSILON) {
            return;
        }
        // Ohne RETAIN_PASSENGERS wirft der Teleport den Reiter ab, statt ihn mitzunehmen.
        stand.teleport(wanted, TeleportCause.PLUGIN, TeleportFlag.EntityState.RETAIN_PASSENGERS);
    }

    void detach(ArmorStand stand, Player viewer) {
        stand.removePassenger(viewer);
        stand.remove();
    }

    /**
     * Wie weit ein Reiter unter seinem Fahrzeug sitzt, hängt an Werten des Spiels. Statt sie
     * abzuschreiben, misst der erste Tick nach dem Aufsitzen den tatsächlichen Abstand; ein
     * unplausibles Ergebnis - etwa weil der Zuschauer gar nicht aufsitzt - wird verworfen.
     */
    double measureOffset(ArmorStand stand, Player viewer, double fallback) {
        if (!stand.getPassengers().contains(viewer)) {
            return fallback;
        }
        double measured = viewer.getLocation().getY() - stand.getLocation().getY();
        return Math.abs(measured) > SANE_OFFSET ? fallback : measured;
    }

    private Location standLocation(Location cameraEye, Player viewer, double mountOffset) {
        Location standLocation = cameraEye.clone();
        standLocation.subtract(0.0D, viewer.getEyeHeight() + mountOffset, 0.0D);
        standLocation.setYaw(0.0F);
        standLocation.setPitch(0.0F);
        return standLocation;
    }
}
