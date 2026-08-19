package de.pumpecraft.essentials.pose;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.Plugin;

/**
 * Sitzen heißt reiten: Der Spieler wird Passagier eines unsichtbaren Markers. Nur so sehen
 * andere Spieler die Sitzhaltung und der Sitzende bleibt an Ort und Stelle. Der Marker wird
 * nicht gespeichert, ein Absturz hinterlässt also keine Leichen in der Welt.
 */
public final class SeatService {
    private final Plugin plugin;
    private final PoseSettings settings;
    private final Map<UUID, Seat> seats = new HashMap<>();

    public SeatService(Plugin plugin, PoseSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public boolean isSitting(Player player) {
        return seats.containsKey(player.getUniqueId());
    }

    /**
     * Der Sitz steht dort, wo der Spieler steht - ohne Rasterung. Die Standhöhe kennt als
     * Einzige die Höhe von Stufen, Platten, Wegen und Ackerland, und das Zentrieren auf die
     * Blockmitte hätte den Sitzenden von der unteren Stufe einer Treppe seitlich weggesetzt.
     */
    public boolean sit(Player player) {
        Location origin = player.getLocation();
        World world = origin.getWorld();
        if (world == null || settings.requireGround() && !player.isOnGround()) {
            return false;
        }

        Location seat = new Location(
            world,
            origin.getX(),
            origin.getY() + settings.seatOffset(),
            origin.getZ(),
            origin.getYaw(),
            0.0F
        );
        ArmorStand marker = world.spawn(seat, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setBasePlate(false);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setPersistent(false);
        });
        if (!marker.addPassenger(player)) {
            marker.remove();
            return false;
        }
        seats.put(player.getUniqueId(), new Seat(marker, origin));
        return true;
    }

    public void stand(Player player) {
        Seat seat = seats.remove(player.getUniqueId());
        if (seat == null) {
            return;
        }
        seat.marker().removePassenger(player);
        seat.marker().remove();
        restore(player, seat.origin());
    }

    /**
     * Aus dem Absteigen heraus: Der Server setzt den Aussteigenden gleich selbst um, weshalb
     * die Rückgabe an den Ausgangspunkt erst im nächsten Tick greift. Beim Herunterfahren wirft
     * der Scheduler die Aufgabe ab - dort zählt nur noch, dass der Marker verschwindet.
     */
    public void dismounted(Player player) {
        Seat seat = seats.remove(player.getUniqueId());
        if (seat == null) {
            return;
        }
        seat.marker().remove();
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> restore(player, seat.origin()));
        }
    }

    /** Für Spieler, die ohnehin nicht mehr dort stehen - Tod und Verlassen des Servers. */
    public void discard(Player player) {
        Seat seat = seats.remove(player.getUniqueId());
        if (seat != null) {
            seat.marker().remove();
        }
    }

    /**
     * Der leere Fall steigt aus, bevor das Lambda aufgelöst wird: Sonst müsste {@link Seat}
     * beim Herunterfahren zum ersten Mal geladen werden, und dort ist der Klassenpfad des
     * Plugins nicht mehr verlässlich - ein während des Betriebs ersetztes Jar reicht schon.
     */
    public void clear() {
        if (seats.isEmpty()) {
            return;
        }
        seats.values().forEach(seat -> seat.marker().remove());
        seats.clear();
    }

    /**
     * Der Sitzende steht mit seinem Rumpf unter der Oberfläche; ohne diese Rückgabe steckt er
     * nach dem Aufstehen in dem Block, auf dem er vorher stand. {@link TeleportCause#DISMOUNT}
     * hält den Sprung aus dem Verlauf von {@code /back} heraus.
     */
    private void restore(Player player, Location origin) {
        if (!player.isOnline() || player.isDead() || !origin.getWorld().equals(player.getWorld())) {
            return;
        }
        Location target = origin.clone();
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());
        player.teleport(target, TeleportCause.DISMOUNT);
    }

    private record Seat(ArmorStand marker, Location origin) {
    }
}
