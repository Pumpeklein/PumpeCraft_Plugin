package de.pumpecraft.mailbox.box;

import de.pumpecraft.database.DatabaseService;
import de.pumpecraft.database.Databases;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.plugin.Plugin;

public final class MailboxRepository {
    private final DatabaseService database;

    public MailboxRepository(Plugin plugin) {
        this.database = Databases.require(plugin);
    }

    public List<MailboxEntry> loadAll() {
        return database.withConnection(connection -> {
            List<MailboxEntry> entries = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT owner_uuid, owner_name, body_uuid, world, x, y, z FROM pc_mailboxes"
            ); ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    entries.add(new MailboxEntry(
                        UUID.fromString(result.getString("owner_uuid")),
                        result.getString("owner_name"),
                        UUID.fromString(result.getString("body_uuid")),
                        result.getString("world"),
                        result.getInt("x"),
                        result.getInt("y"),
                        result.getInt("z")
                    ));
                }
            }
            return entries;
        });
    }

    public void insert(MailboxEntry entry, long now) {
        database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO pc_mailboxes (owner_uuid, owner_name, body_uuid, world, x, y, z, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE owner_name = VALUES(owner_name), body_uuid = VALUES(body_uuid), "
                    + "world = VALUES(world), x = VALUES(x), y = VALUES(y), z = VALUES(z)"
            )) {
                statement.setString(1, entry.owner().toString());
                statement.setString(2, entry.ownerName());
                statement.setString(3, entry.bodyId().toString());
                statement.setString(4, entry.world());
                statement.setInt(5, entry.x());
                statement.setInt(6, entry.y());
                statement.setInt(7, entry.z());
                statement.setLong(8, now);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public void delete(UUID owner) {
        database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM pc_mailboxes WHERE owner_uuid = ?"
            )) {
                statement.setString(1, owner.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }
}
