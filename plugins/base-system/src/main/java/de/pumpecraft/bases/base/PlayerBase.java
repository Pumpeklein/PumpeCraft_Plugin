package de.pumpecraft.bases.base;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record PlayerBase(
    UUID ownerId,
    String ownerName,
    BaseLocation location,
    boolean publicBase,
    long visitCount,
    long likeCount,
    long uniqueVisitors,
    long createdAt,
    long updatedAt
) {
    /** Leer, solange die Welt der Base nicht geladen ist - dann ist kein Besuch möglich. */
    public Location bukkitLocation() {
        World world = Bukkit.getWorld(location.worldId());
        if (world == null) {
            world = Bukkit.getWorld(location.worldName());
        }
        if (world == null) {
            return null;
        }
        return new Location(
            world, location.x(), location.y(), location.z(), location.yaw(), location.pitch());
    }
}
