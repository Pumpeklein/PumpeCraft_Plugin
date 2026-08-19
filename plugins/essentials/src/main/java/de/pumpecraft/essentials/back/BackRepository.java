package de.pumpecraft.essentials.back;

import de.pumpecraft.database.DatabaseService;
import de.pumpecraft.database.Databases;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.plugin.Plugin;

public final class BackRepository {
    private static final String INSERT = """
        INSERT INTO pc_back_locations
            (player_uuid, cause, world, x, y, z, yaw, pitch, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

    /**
     * MariaDB erlaubt kein LIMIT in einer IN-Unterabfrage; die zusätzliche Ableitung umgeht
     * das, statt die zu behaltenden Ids erst in die Anwendung zu holen.
     */
    private static final String TRIM = """
        DELETE FROM pc_back_locations
         WHERE player_uuid = ?
           AND id NOT IN (
               SELECT id FROM (
                   SELECT id FROM pc_back_locations WHERE player_uuid = ? ORDER BY id DESC LIMIT ?
               ) AS keep
           )
        """;

    private static final String SELECT = """
        SELECT cause, world, x, y, z, yaw, pitch, created_at
          FROM pc_back_locations
         WHERE player_uuid = ?
         ORDER BY id DESC
         LIMIT ?
        """;

    private final DatabaseService database;

    public BackRepository(Plugin plugin) {
        this.database = Databases.require(plugin);
    }

    public void append(UUID playerId, BackLocation entry, int keep) {
        String id = playerId.toString();
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(INSERT)) {
                statement.setString(1, id);
                statement.setString(2, entry.cause().name());
                statement.setString(3, entry.world());
                statement.setDouble(4, entry.x());
                statement.setDouble(5, entry.y());
                statement.setDouble(6, entry.z());
                statement.setFloat(7, entry.yaw());
                statement.setFloat(8, entry.pitch());
                statement.setLong(9, entry.createdAt());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(TRIM)) {
                statement.setString(1, id);
                statement.setString(2, id);
                statement.setInt(3, keep);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public List<BackLocation> load(UUID playerId, int limit) {
        return database.withConnection(connection -> {
            List<BackLocation> entries = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(SELECT)) {
                statement.setString(1, playerId.toString());
                statement.setInt(2, limit);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        entries.add(new BackLocation(
                            BackCause.of(result.getString("cause")),
                            result.getString("world"),
                            result.getDouble("x"),
                            result.getDouble("y"),
                            result.getDouble("z"),
                            result.getFloat("yaw"),
                            result.getFloat("pitch"),
                            result.getLong("created_at")
                        ));
                    }
                }
            }
            return List.copyOf(entries);
        });
    }
}
