package de.pumpecraft.bases.base;

import de.pumpecraft.database.DatabaseService;
import de.pumpecraft.database.Databases;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.plugin.Plugin;

public final class BaseRepository {
    private final DatabaseService database;

    public BaseRepository(Plugin plugin) {
        database = Databases.require(plugin);
    }

    public void setBase(PlayerIdentity owner, BaseLocation location, boolean publicBase, long now) {
        database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_player_bases
                    (owner_uuid, owner_name, world_uuid, world_name, x, y, z,
                     yaw, pitch, is_public, visit_count, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                ON DUPLICATE KEY UPDATE
                    owner_name = VALUES(owner_name),
                    world_uuid = VALUES(world_uuid),
                    world_name = VALUES(world_name),
                    x = VALUES(x), y = VALUES(y), z = VALUES(z),
                    yaw = VALUES(yaw), pitch = VALUES(pitch),
                    is_public = VALUES(is_public),
                    updated_at = VALUES(updated_at)
                """
            )) {
                statement.setString(1, owner.playerId().toString());
                statement.setString(2, owner.playerName());
                statement.setString(3, location.worldId().toString());
                statement.setString(4, location.worldName());
                statement.setDouble(5, location.x());
                statement.setDouble(6, location.y());
                statement.setDouble(7, location.z());
                statement.setFloat(8, location.yaw());
                statement.setFloat(9, location.pitch());
                statement.setBoolean(10, publicBase);
                statement.setLong(11, now);
                statement.setLong(12, now);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public Optional<PlayerBase> baseOf(UUID ownerId) {
        return database.withConnection(
            connection -> findBase(connection, "b.owner_uuid = ?", ownerId.toString()));
    }

    public Optional<PlayerBase> baseOf(String ownerName) {
        return database.withConnection(
            connection -> findBase(connection, "LOWER(b.owner_name) = LOWER(?)", ownerName));
    }

    public boolean setVisibility(UUID ownerId, boolean publicBase, long now) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pc_player_bases SET is_public = ?, updated_at = ? WHERE owner_uuid = ?"
            )) {
                statement.setBoolean(1, publicBase);
                statement.setLong(2, now);
                statement.setString(3, ownerId.toString());
                return statement.executeUpdate() > 0;
            }
        });
    }

    public boolean deleteBase(UUID ownerId) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM pc_player_bases WHERE owner_uuid = ?"
            )) {
                statement.setString(1, ownerId.toString());
                return statement.executeUpdate() > 0;
            }
        });
    }

    public boolean hasLiked(UUID ownerId, UUID likerId) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM pc_base_likes WHERE owner_uuid = ? AND liker_uuid = ?"
            )) {
                statement.setString(1, ownerId.toString());
                statement.setString(2, likerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            }
        });
    }

    /** @return {@code true}, wenn das Like danach gesetzt ist, {@code false} nach dem Entfernen. */
    public boolean toggleLike(UUID ownerId, PlayerIdentity liker, long now) {
        return database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM pc_base_likes WHERE owner_uuid = ? AND liker_uuid = ?"
            )) {
                statement.setString(1, ownerId.toString());
                statement.setString(2, liker.playerId().toString());
                if (statement.executeUpdate() > 0) {
                    return false;
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_base_likes (owner_uuid, liker_uuid, liker_name, created_at)
                VALUES (?, ?, ?, ?)
                """
            )) {
                statement.setString(1, ownerId.toString());
                statement.setString(2, liker.playerId().toString());
                statement.setString(3, liker.playerName());
                statement.setLong(4, now);
                statement.executeUpdate();
            }
            return true;
        });
    }

    public void recordVisit(UUID ownerId, PlayerIdentity visitor, long now) {
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pc_player_bases SET visit_count = visit_count + 1 WHERE owner_uuid = ?"
            )) {
                statement.setString(1, ownerId.toString());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_base_visitors
                    (owner_uuid, visitor_uuid, visitor_name, visit_count, last_visited_at)
                VALUES (?, ?, ?, 1, ?)
                ON DUPLICATE KEY UPDATE
                    visitor_name = VALUES(visitor_name),
                    visit_count = visit_count + 1,
                    last_visited_at = VALUES(last_visited_at)
                """
            )) {
                statement.setString(1, ownerId.toString());
                statement.setString(2, visitor.playerId().toString());
                statement.setString(3, visitor.playerName());
                statement.setLong(4, now);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public List<BaseEntry> browse(BaseSort sort, boolean includePrivate, UUID viewerId, int limit) {
        return database.withConnection(connection -> {
            String visibility = includePrivate
                ? "1 = 1"
                : "(b.is_public = TRUE OR b.owner_uuid = ?)";
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT b.owner_uuid, b.owner_name, b.world_name, b.is_public,
                       b.visit_count, b.updated_at,
                       (SELECT COUNT(*) FROM pc_base_likes l WHERE l.owner_uuid = b.owner_uuid)
                           AS like_count
                  FROM pc_player_bases b
                 WHERE %s
                 ORDER BY %s
                 LIMIT ?
                """.formatted(visibility, sort.orderBy())
            )) {
                int index = 1;
                if (!includePrivate) {
                    statement.setString(index++, viewerId.toString());
                }
                statement.setInt(index, limit);
                try (ResultSet result = statement.executeQuery()) {
                    List<BaseEntry> entries = new ArrayList<>();
                    while (result.next()) {
                        entries.add(new BaseEntry(
                            UUID.fromString(result.getString("owner_uuid")),
                            result.getString("owner_name"),
                            result.getString("world_name"),
                            result.getBoolean("is_public"),
                            result.getLong("visit_count"),
                            result.getLong("like_count"),
                            result.getLong("updated_at")
                        ));
                    }
                    return List.copyOf(entries);
                }
            }
        });
    }

    public List<BaseVisitor> visitors(UUID ownerId, int limit) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT visitor_uuid, visitor_name, visit_count, last_visited_at
                  FROM pc_base_visitors
                 WHERE owner_uuid = ?
                 ORDER BY visit_count DESC, last_visited_at DESC
                 LIMIT ?
                """
            )) {
                statement.setString(1, ownerId.toString());
                statement.setInt(2, limit);
                try (ResultSet result = statement.executeQuery()) {
                    List<BaseVisitor> visitors = new ArrayList<>();
                    while (result.next()) {
                        visitors.add(new BaseVisitor(
                            UUID.fromString(result.getString("visitor_uuid")),
                            result.getString("visitor_name"),
                            result.getLong("visit_count"),
                            result.getLong("last_visited_at")
                        ));
                    }
                    return List.copyOf(visitors);
                }
            }
        });
    }

    public List<BaseLike> likes(UUID ownerId, int limit) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT liker_uuid, liker_name, created_at
                  FROM pc_base_likes
                 WHERE owner_uuid = ?
                 ORDER BY created_at DESC
                 LIMIT ?
                """
            )) {
                statement.setString(1, ownerId.toString());
                statement.setInt(2, limit);
                try (ResultSet result = statement.executeQuery()) {
                    List<BaseLike> likes = new ArrayList<>();
                    while (result.next()) {
                        likes.add(new BaseLike(
                            UUID.fromString(result.getString("liker_uuid")),
                            result.getString("liker_name"),
                            result.getLong("created_at")
                        ));
                    }
                    return List.copyOf(likes);
                }
            }
        });
    }

    public List<String> ownerNames() {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT owner_name FROM pc_player_bases ORDER BY owner_name"
            ); ResultSet result = statement.executeQuery()) {
                List<String> names = new ArrayList<>();
                while (result.next()) {
                    names.add(result.getString("owner_name"));
                }
                return List.copyOf(names);
            }
        });
    }

    /**
     * Die Base-Tabellen führen den Spielernamen mit, damit Listen ohne Profilabfrage auskommen.
     * Nach einer Namensänderung stimmt er erst wieder, wenn der Spieler den Server betritt.
     */
    public void syncPlayerName(PlayerIdentity player) {
        database.inTransaction(connection -> {
            updateName(connection, "pc_player_bases", "owner_uuid", "owner_name", player);
            updateName(connection, "pc_base_visitors", "visitor_uuid", "visitor_name", player);
            updateName(connection, "pc_base_likes", "liker_uuid", "liker_name", player);
            return null;
        });
    }

    private void updateName(
        Connection connection,
        String table,
        String idColumn,
        String nameColumn,
        PlayerIdentity player
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "UPDATE " + table + " SET " + nameColumn + " = ? WHERE " + idColumn + " = ?"
        )) {
            statement.setString(1, player.playerName());
            statement.setString(2, player.playerId().toString());
            statement.executeUpdate();
        }
    }

    private Optional<PlayerBase> findBase(Connection connection, String where, String value)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            """
            SELECT b.*,
                   (SELECT COUNT(*) FROM pc_base_likes l WHERE l.owner_uuid = b.owner_uuid)
                       AS like_count,
                   (SELECT COUNT(*) FROM pc_base_visitors v WHERE v.owner_uuid = b.owner_uuid)
                       AS unique_visitors
              FROM pc_player_bases b
             WHERE %s
             ORDER BY b.updated_at DESC
             LIMIT 1
            """.formatted(where)
        )) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PlayerBase(
                    UUID.fromString(result.getString("owner_uuid")),
                    result.getString("owner_name"),
                    new BaseLocation(
                        UUID.fromString(result.getString("world_uuid")),
                        result.getString("world_name"),
                        result.getDouble("x"),
                        result.getDouble("y"),
                        result.getDouble("z"),
                        result.getFloat("yaw"),
                        result.getFloat("pitch")
                    ),
                    result.getBoolean("is_public"),
                    result.getLong("visit_count"),
                    result.getLong("like_count"),
                    result.getLong("unique_visitors"),
                    result.getLong("created_at"),
                    result.getLong("updated_at")
                ));
            }
        }
    }
}
