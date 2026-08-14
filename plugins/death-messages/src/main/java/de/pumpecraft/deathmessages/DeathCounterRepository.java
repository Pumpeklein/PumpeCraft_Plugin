package de.pumpecraft.deathmessages;

import de.pumpecraft.database.DatabaseService;
import de.pumpecraft.database.Databases;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class DeathCounterRepository {
    private static final String LEGACY_IMPORT_KEY = "death-messages-yaml-v1";

    private final PumpeDeathMessagesPlugin plugin;
    private final DatabaseService database;

    DeathCounterRepository(PumpeDeathMessagesPlugin plugin) {
        this.plugin = plugin;
        this.database = Databases.require(plugin);
    }

    void load() {
        importLegacyYaml();
    }

    int incrementDeaths(UUID playerId, String dimension) {
        return database.inTransaction(connection -> {
            int deaths = findDeathsForUpdate(connection, playerId) + 1;
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO pc_death_counts (player_uuid, death_count)
                            VALUES (?, ?)
                            ON DUPLICATE KEY UPDATE death_count = VALUES(death_count)
                            """)) {
                statement.setString(1, playerId.toString());
                statement.setInt(2, deaths);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                            INSERT INTO pc_dimension_death_counts
                                (player_uuid, dimension, death_count)
                            VALUES (?, ?, 1)
                            ON DUPLICATE KEY UPDATE death_count = death_count + 1
                            """)) {
                statement.setString(1, playerId.toString());
                statement.setString(2, dimension);
                statement.executeUpdate();
            }
            return deaths;
        });
    }

    private int findDeathsForUpdate(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT death_count FROM pc_death_counts WHERE player_uuid = ? FOR UPDATE")) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt("death_count") : 0;
            }
        }
    }

    private void importLegacyYaml() {
        File file = new File(plugin.getDataFolder(), "death-message-data.yml");
        if (!file.isFile()) {
            return;
        }

        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection counts = legacy.getConfigurationSection("death-counts");
        database.inTransaction(connection -> {
            if (wasImported(connection)) {
                return null;
            }

            if (counts != null) {
                try (PreparedStatement statement = connection.prepareStatement(
                        """
                                INSERT INTO pc_death_counts (player_uuid, death_count)
                                VALUES (?, ?)
                                ON DUPLICATE KEY UPDATE death_count = GREATEST(death_count, VALUES(death_count))
                                """)) {
                    for (String key : counts.getKeys(false)) {
                        try {
                            statement.setString(1, UUID.fromString(key).toString());
                        } catch (IllegalArgumentException ignored) {
                            continue;
                        }
                        statement.setInt(2, Math.max(0, counts.getInt(key)));
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
            }
            markImported(connection);
            return null;
        });
        plugin.getLogger().info("Imported legacy death-message-data.yml into MariaDB.");
    }

    private boolean wasImported(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM pc_legacy_imports WHERE import_key = ?")) {
            statement.setString(1, LEGACY_IMPORT_KEY);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void markImported(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO pc_legacy_imports (import_key) VALUES (?)")) {
            statement.setString(1, LEGACY_IMPORT_KEY);
            statement.executeUpdate();
        }
    }
}
