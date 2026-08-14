package de.pumpecraft.utils;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class Locations {
    private Locations() {
    }

    public static double horizontalDistance(Location from, Location to) {
        return Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
    }

    public static boolean samePosition(Location from, Location to) {
        return from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ();
    }

    public static boolean sameWorld(Location first, Location second) {
        return first != null && second != null && first.getWorld() == second.getWorld();
    }

    public static double distanceToBox(Location origin, BoundingBox box) {
        double deltaX = origin.getX() - clamp(origin.getX(), box.getMinX(), box.getMaxX());
        double deltaY = origin.getY() - clamp(origin.getY(), box.getMinY(), box.getMaxY());
        double deltaZ = origin.getZ() - clamp(origin.getZ(), box.getMinZ(), box.getMaxZ());
        return Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
    }

    public static double aimDot(Location eye, Location target) {
        Vector toTarget = target.toVector().subtract(eye.toVector());
        if (toTarget.lengthSquared() == 0.0) {
            return 1.0;
        }
        return eye.getDirection().normalize().dot(toTarget.normalize());
    }

    public static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
