package de.pumpecraft.playtime;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import de.pumpecraft.database.DatabaseService;
import de.pumpecraft.database.Databases;

final class PlaytimeRepository {
    private static final String LEGACY_IMPORT_KEY = "playtime-yaml-v1";

    private final PumpePlaytimePlugin plugin;
    private final DatabaseService database;
    private final Map<UUID, PlaytimeRecord> records = new HashMap<>();
    private final Set<UUID> dirtyRecords = new HashSet<>();

    PlaytimeRepository(PumpePlaytimePlugin plugin) {
        this.plugin = plugin;
        this.database = Databases.require(plugin);
    }

    void load() {
        importLegacyYaml();
        database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT player_uuid, total_seconds, afk_seconds FROM pc_playtime"
            ); ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long totalSeconds = Math.max(0L, result.getLong("total_seconds"));
                    long afkSeconds = Math.min(
                        Math.max(0L, result.getLong("afk_seconds")),
                        totalSeconds
                    );
                    records.put(
                        UUID.fromString(result.getString("player_uuid")),
                        new PlaytimeRecord(totalSeconds - afkSeconds, afkSeconds)
                    );
                }
            }
            return null;
        });
    }

    synchronized PlaytimeRecord get(UUID playerId) {
        return records.getOrDefault(playerId, new PlaytimeRecord(0L, 0L));
    }

    synchronized void addSecond(UUID playerId, boolean afk) {
        PlaytimeRecord current = get(playerId);
        PlaytimeRecord record = afk ? current.addAfk(1L) : current.addActive(1L);
        records.put(playerId, record);
        dirtyRecords.add(playerId);
    }

    synchronized void save() {
        if (dirtyRecords.isEmpty()) {
            return;
        }

        Map<UUID, PlaytimeRecord> snapshot = new HashMap<>();
        for (UUID playerId : dirtyRecords) {
            snapshot.put(playerId, records.get(playerId));
        }

        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_playtime
                    (player_uuid, total_seconds, afk_seconds, active_seconds)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    total_seconds = VALUES(total_seconds),
                    afk_seconds = VALUES(afk_seconds),
                    active_seconds = VALUES(active_seconds)
                """
            )) {
                for (Map.Entry<UUID, PlaytimeRecord> entry : snapshot.entrySet()) {
                    PlaytimeRecord record = entry.getValue();
                    statement.setString(1, entry.getKey().toString());
                    statement.setLong(2, record.totalSeconds());
                    statement.setLong(3, record.afkSeconds());
                    statement.setLong(4, record.activeSeconds());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_playtime_history
                    (player_uuid, snapshot_date, total_seconds, active_seconds,
                     afk_seconds, captured_at)
                VALUES (?, CURRENT_DATE, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    total_seconds = VALUES(total_seconds),
                    active_seconds = VALUES(active_seconds),
                    afk_seconds = VALUES(afk_seconds),
                    captured_at = VALUES(captured_at)
                """
            )) {
                long capturedAt = System.currentTimeMillis();
                for (Map.Entry<UUID, PlaytimeRecord> entry : snapshot.entrySet()) {
                    PlaytimeRecord record = entry.getValue();
                    statement.setString(1, entry.getKey().toString());
                    statement.setLong(2, record.totalSeconds());
                    statement.setLong(3, record.activeSeconds());
                    statement.setLong(4, record.afkSeconds());
                    statement.setLong(5, capturedAt);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
        dirtyRecords.removeAll(snapshot.keySet());
    }

    private void importLegacyYaml() {
        File file = new File(plugin.getDataFolder(), "playtime-data.yml");
        if (!file.isFile()) {
            return;
        }

        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = legacy.getConfigurationSection("players");
        boolean imported = database.inTransaction(connection -> {
            if (wasImported(connection)) {
                return false;
            }

            if (players != null) {
                try (PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO pc_playtime
                        (player_uuid, total_seconds, afk_seconds, active_seconds)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        total_seconds = GREATEST(total_seconds, VALUES(total_seconds)),
                        afk_seconds = GREATEST(afk_seconds, VALUES(afk_seconds)),
                        active_seconds = GREATEST(active_seconds, VALUES(active_seconds))
                    """
                )) {
                    for (String key : players.getKeys(false)) {
                        try {
                            statement.setString(1, UUID.fromString(key).toString());
                        } catch (IllegalArgumentException ignored) {
                            continue;
                        }
                        String path = "players." + key;
                        long totalSeconds = Math.max(0L, legacy.getLong(path + ".total-seconds"));
                        long afkSeconds = Math.min(
                            Math.max(0L, legacy.getLong(path + ".afk-seconds")),
                            totalSeconds
                        );
                        statement.setLong(2, totalSeconds);
                        statement.setLong(3, afkSeconds);
                        statement.setLong(4, totalSeconds - afkSeconds);
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
            }
            markImported(connection);
            return true;
        });
        if (imported) {
            plugin.getLogger().info("Imported legacy playtime-data.yml into MariaDB.");
        }
    }

    private boolean wasImported(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT 1 FROM pc_legacy_imports WHERE import_key = ?"
        )) {
            statement.setString(1, LEGACY_IMPORT_KEY);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private void markImported(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO pc_legacy_imports (import_key) VALUES (?)"
        )) {
            statement.setString(1, LEGACY_IMPORT_KEY);
            statement.executeUpdate();
        }
    }
}
