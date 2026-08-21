package de.pumpecraft.bases.plot;

import de.pumpecraft.bases.base.PlayerIdentity;
import de.pumpecraft.database.DatabaseService;
import de.pumpecraft.database.Databases;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.plugin.Plugin;

public final class PlotRepository {
    private final DatabaseService database;

    public PlotRepository(Plugin plugin) {
        database = Databases.require(plugin);
    }

    /** Lädt alles in einem Rutsch; danach beantwortet der Index jede Frage aus dem Speicher. */
    public List<Plot> loadAll() {
        return database.withConnection(connection -> {
            Map<Long, Plot> plots = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM pc_plots");
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Plot plot = readPlot(result);
                    plots.put(plot.id(), plot);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM pc_plot_members");
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Plot plot = plots.get(result.getLong("plot_id"));
                    if (plot == null) {
                        continue;
                    }
                    UUID playerId = UUID.fromString(result.getString("player_uuid"));
                    plot.members().put(playerId, new PlotMember(
                        playerId,
                        result.getString("player_name"),
                        PlotRole.byId(result.getString("member_role")),
                        result.getLong("added_at")
                    ));
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM pc_plot_flags");
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Plot plot = plots.get(result.getLong("plot_id"));
                    PlotFlag flag = PlotFlag.byId(result.getString("flag_id"));
                    if (plot != null && flag != null) {
                        plot.flags().put(flag, result.getBoolean("flag_value"));
                    }
                }
            }
            return List.copyOf(plots.values());
        });
    }

    public long create(
        String name,
        PlayerIdentity owner,
        PlotArea area,
        boolean adminPlot,
        long pricePaid,
        long now
    ) {
        return database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_plots
                    (plot_name, owner_uuid, owner_name, world_uuid, world_name,
                     min_x, min_z, max_x, max_z, min_y, max_y,
                     admin_plot, price_paid, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            )) {
                statement.setString(1, name);
                statement.setString(2, owner == null ? null : owner.playerId().toString());
                statement.setString(3, owner == null ? null : owner.playerName());
                statement.setString(4, area.worldId().toString());
                statement.setString(5, area.worldName());
                statement.setInt(6, area.minX());
                statement.setInt(7, area.minZ());
                statement.setInt(8, area.maxX());
                statement.setInt(9, area.maxZ());
                setNullableInt(statement, 10, area.minY());
                setNullableInt(statement, 11, area.maxY());
                statement.setBoolean(12, adminPlot);
                statement.setLong(13, pricePaid);
                statement.setLong(14, now);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    return keys.next() ? keys.getLong(1) : 0L;
                }
            }
        });
    }

    public boolean nameTaken(String name) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM pc_plots WHERE plot_name = ?"
            )) {
                statement.setString(1, name);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next();
                }
            }
        });
    }

    public void delete(long plotId) {
        database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM pc_plots WHERE id = ?"
            )) {
                statement.setLong(1, plotId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public void setMember(long plotId, PlayerIdentity player, PlotRole role, long now) {
        database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_plot_members (plot_id, player_uuid, player_name, member_role, added_at)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    player_name = VALUES(player_name),
                    member_role = VALUES(member_role)
                """
            )) {
                statement.setLong(1, plotId);
                statement.setString(2, player.playerId().toString());
                statement.setString(3, player.playerName());
                statement.setString(4, role.name());
                statement.setLong(5, now);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public void removeMember(long plotId, UUID playerId) {
        database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM pc_plot_members WHERE plot_id = ? AND player_uuid = ?"
            )) {
                statement.setLong(1, plotId);
                statement.setString(2, playerId.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public void setFlag(long plotId, PlotFlag flag, boolean value) {
        database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_plot_flags (plot_id, flag_id, flag_value)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE flag_value = VALUES(flag_value)
                """
            )) {
                statement.setLong(1, plotId);
                statement.setString(2, flag.id());
                statement.setBoolean(3, value);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public void clearFlag(long plotId, PlotFlag flag) {
        database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM pc_plot_flags WHERE plot_id = ? AND flag_id = ?"
            )) {
                statement.setLong(1, plotId);
                statement.setString(2, flag.id());
                statement.executeUpdate();
            }
            return null;
        });
    }

    public void setHeight(long plotId, Integer minY, Integer maxY) {
        database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pc_plots SET min_y = ?, max_y = ? WHERE id = ?"
            )) {
                setNullableInt(statement, 1, minY);
                setNullableInt(statement, 2, maxY);
                statement.setLong(3, plotId);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private static void setNullableInt(PreparedStatement statement, int index, Integer value)
        throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static Integer nullableInt(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    public void syncPlayerName(PlayerIdentity player) {
        database.inTransaction(connection -> {
            update(connection,
                "UPDATE pc_plots SET owner_name = ? WHERE owner_uuid = ?", player);
            update(connection,
                "UPDATE pc_plot_members SET player_name = ? WHERE player_uuid = ?", player);
            return null;
        });
    }

    private void update(Connection connection, String sql, PlayerIdentity player)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, player.playerName());
            statement.setString(2, player.playerId().toString());
            statement.executeUpdate();
        }
    }

    private Plot readPlot(ResultSet result) throws SQLException {
        String ownerUuid = result.getString("owner_uuid");
        PlotArea area = new PlotArea(
            UUID.fromString(result.getString("world_uuid")),
            result.getString("world_name"),
            result.getInt("min_x"),
            result.getInt("min_z"),
            result.getInt("max_x"),
            result.getInt("max_z"),
            nullableInt(result, "min_y"),
            nullableInt(result, "max_y")
        );
        return new Plot(
            result.getLong("id"),
            result.getString("plot_name"),
            ownerUuid == null ? null : UUID.fromString(ownerUuid),
            result.getString("owner_name"),
            area,
            result.getBoolean("admin_plot"),
            result.getLong("price_paid"),
            result.getLong("created_at")
        );
    }
}
