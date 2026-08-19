package de.pumpecraft.essentials.back;

import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record BackLocation(
    BackCause cause,
    String world,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    long createdAt
) {
    public static BackLocation of(Location location, BackCause cause, long createdAt) {
        World world = location.getWorld();
        return new BackLocation(
            cause,
            world == null ? "" : world.getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch(),
            createdAt
        );
    }

    /** Leer, solange die Welt nicht geladen ist - etwa nach dem Entfernen einer Dimension. */
    public Optional<Location> resolve() {
        World loaded = Bukkit.getWorld(world);
        return loaded == null ? Optional.empty() : Optional.of(new Location(loaded, x, y, z, yaw, pitch));
    }

    public boolean sameWorld(Location location) {
        World other = location.getWorld();
        return other != null && other.getName().equals(world);
    }

    public double distanceSquared(Location location) {
        double dx = location.getX() - x;
        double dy = location.getY() - y;
        double dz = location.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
