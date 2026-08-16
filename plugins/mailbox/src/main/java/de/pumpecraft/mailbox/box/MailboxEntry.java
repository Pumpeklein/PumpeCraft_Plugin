package de.pumpecraft.mailbox.box;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Where the mailbox of one player stands. Kept in the database so the position is known even while
 * its chunk is unloaded - a delivery has to find its way there without a player nearby.
 */
public record MailboxEntry(UUID owner, String ownerName, UUID bodyId, String world, int x, int y, int z) {
    public Location location() {
        World target = Bukkit.getWorld(world);
        return target == null ? null : new Location(target, x + 0.5D, y, z + 0.5D);
    }

    public String coordinates() {
        return world + " " + x + ", " + y + ", " + z;
    }
}
