package de.pumpecraft.mod;

import de.pumpecraft.database.DatabaseService;
import de.pumpecraft.database.Databases;
import java.io.File;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class ModerationRepository {
    private static final String LEGACY_IMPORT_KEY = "moderation-yaml-v1";
    private static final String PUNISHMENT_ID_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PumpeModPlugin plugin;
    private final DatabaseService database;

    ModerationRepository(PumpeModPlugin plugin) {
        this.plugin = plugin;
        this.database = Databases.require(plugin);
    }

    void load() {
        importLegacyYaml();
    }

    ReportRecord createReport(
        UUID reporterId,
        String reporterName,
        UUID targetId,
        String targetName,
        String reason
    ) {
        long createdAt = Instant.now().toEpochMilli();
        int id = database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_reports
                    (reporter_uuid, reporter_name, target_uuid, target_name, reason, created_at, is_open)
                VALUES (?, ?, ?, ?, ?, ?, TRUE)
                """,
                Statement.RETURN_GENERATED_KEYS
            )) {
                statement.setString(1, reporterId.toString());
                statement.setString(2, reporterName);
                statement.setString(3, targetId.toString());
                statement.setString(4, targetName);
                statement.setString(5, reason);
                statement.setLong(6, createdAt);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Creating a report returned no generated ID.");
                    }
                    return keys.getInt(1);
                }
            }
        });
        return new ReportRecord(id, reporterName, targetName, reason, createdAt, true);
    }

    int countOpenReports() {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM pc_reports WHERE is_open = TRUE"
            ); ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        });
    }

    int countUnseenOpenReports(UUID staffId) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT COUNT(*)
                FROM pc_reports r
                LEFT JOIN pc_report_staff_seen s ON s.staff_uuid = ?
                WHERE r.is_open = TRUE AND r.id > COALESCE(s.last_seen_report_id, 0)
                """
            )) {
                statement.setString(1, staffId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return result.getInt(1);
                }
            }
        });
    }

    void markOpenReportsSeen(UUID staffId) {
        database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_report_staff_seen (staff_uuid, last_seen_report_id)
                SELECT ?, COALESCE(MAX(id), 0) FROM pc_reports WHERE is_open = TRUE
                ON DUPLICATE KEY UPDATE
                    last_seen_report_id = GREATEST(last_seen_report_id, VALUES(last_seen_report_id))
                """
            )) {
                statement.setString(1, staffId.toString());
                statement.executeUpdate();
            }
            return null;
        });
    }

    int addWarning(UUID targetId, String targetName, String staffName, String reason) {
        return database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_warnings
                    (target_uuid, target_name, staff_name, reason, created_at)
                VALUES (?, ?, ?, ?, ?)
                """
            )) {
                statement.setString(1, targetId.toString());
                statement.setString(2, targetName);
                statement.setString(3, staffName);
                statement.setString(4, reason);
                statement.setLong(5, Instant.now().toEpochMilli());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM pc_warnings WHERE target_uuid = ?"
            )) {
                statement.setString(1, targetId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    result.next();
                    return result.getInt(1);
                }
            }
        });
    }

    MuteRecord setMute(
        UUID targetId,
        String targetName,
        String staffName,
        Duration duration,
        String reason
    ) {
        long mutedAt = Instant.now().toEpochMilli();
        long expiresAt = mutedAt + duration.toMillis();
        database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_mutes
                    (target_uuid, target_name, staff_name, reason, muted_at, expires_at,
                     unmuted_at, unmuted_by)
                VALUES (?, ?, ?, ?, ?, ?, NULL, NULL)
                ON DUPLICATE KEY UPDATE
                    target_name = VALUES(target_name),
                    staff_name = VALUES(staff_name),
                    reason = VALUES(reason),
                    muted_at = VALUES(muted_at),
                    expires_at = VALUES(expires_at),
                    unmuted_at = NULL,
                    unmuted_by = NULL
                """
            )) {
                statement.setString(1, targetId.toString());
                statement.setString(2, targetName);
                statement.setString(3, staffName);
                statement.setString(4, reason);
                statement.setLong(5, mutedAt);
                statement.setLong(6, expiresAt);
                statement.executeUpdate();
            }
            return null;
        });
        return new MuteRecord(reason, staffName, mutedAt, expiresAt);
    }

    /**
     * Liest den aktiven Mute. Abgelaufene oder aufgehobene Einträge bleiben als
     * Historie in der Tabelle stehen und liefern hier {@code null}.
     */
    MuteRecord getActiveMute(UUID targetId) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT reason, staff_name, muted_at, expires_at
                FROM pc_mutes
                WHERE target_uuid = ? AND unmuted_at IS NULL AND expires_at > ?
                """
            )) {
                statement.setString(1, targetId.toString());
                statement.setLong(2, Instant.now().toEpochMilli());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return null;
                    }
                    return new MuteRecord(
                        result.getString("reason"),
                        result.getString("staff_name"),
                        result.getLong("muted_at"),
                        result.getLong("expires_at")
                    );
                }
            }
        });
    }

    /** @return {@code true}, wenn tatsächlich ein aktiver Mute aufgehoben wurde. */
    boolean clearMute(UUID targetId, String staffName) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                UPDATE pc_mutes
                   SET unmuted_at = ?, unmuted_by = ?
                 WHERE target_uuid = ? AND unmuted_at IS NULL AND expires_at > ?
                """
            )) {
                long now = Instant.now().toEpochMilli();
                statement.setLong(1, now);
                statement.setString(2, staffName);
                statement.setString(3, targetId.toString());
                statement.setLong(4, now);
                return statement.executeUpdate() > 0;
            }
        });
    }

    BanRecord createBanPunishment(
        UUID targetId,
        String targetName,
        String staffName,
        String reason,
        Instant expiresAt
    ) {
        long createdAt = Instant.now().toEpochMilli();
        String punishmentId = database.withConnection(connection -> {
            for (int attempt = 0; attempt < 20; attempt++) {
                String candidate = generatePunishmentId();
                try (PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO pc_punishments
                        (punishment_id, punishment_type, target_uuid, target_name, staff_name,
                         reason, created_at, expires_at)
                    VALUES (?, 'BAN', ?, ?, ?, ?, ?, ?)
                    """
                )) {
                    statement.setString(1, candidate);
                    statement.setString(2, targetId.toString());
                    statement.setString(3, targetName);
                    statement.setString(4, staffName);
                    statement.setString(5, reason);
                    statement.setLong(6, createdAt);
                    if (expiresAt == null) {
                        statement.setNull(7, java.sql.Types.BIGINT);
                    } else {
                        statement.setLong(7, expiresAt.toEpochMilli());
                    }
                    statement.executeUpdate();
                    return candidate;
                } catch (SQLException exception) {
                    if (!"23000".equals(exception.getSQLState())) {
                        throw exception;
                    }
                }
            }
            throw new SQLException("Could not generate a unique punishment ID after 20 attempts.");
        });
        return new BanRecord(
            punishmentId,
            reason,
            staffName,
            createdAt,
            expiresAt == null ? null : expiresAt.toEpochMilli()
        );
    }

    /**
     * Der aktive Ban eines Spielers. Permanente Bans haben Vorrang vor
     * befristeten, danach gilt der am spätesten endende.
     */
    BanRecord getActiveBan(UUID targetId) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT punishment_id, reason, staff_name, created_at, expires_at
                FROM pc_punishments
                WHERE target_uuid = ?
                  AND punishment_type = 'BAN'
                  AND revoked_at IS NULL
                  AND (expires_at IS NULL OR expires_at > ?)
                ORDER BY (expires_at IS NULL) DESC, expires_at DESC
                LIMIT 1
                """
            )) {
                statement.setString(1, targetId.toString());
                statement.setLong(2, Instant.now().toEpochMilli());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return null;
                    }
                    long expiresAtValue = result.getLong("expires_at");
                    Long expiresAt = result.wasNull() ? null : expiresAtValue;
                    return new BanRecord(
                        result.getString("punishment_id"),
                        result.getString("reason"),
                        result.getString("staff_name"),
                        result.getLong("created_at"),
                        expiresAt
                    );
                }
            }
        });
    }

    ModerationSnapshot activePunishments(Collection<UUID> targetIds) {
        if (targetIds.isEmpty()) {
            return new ModerationSnapshot(Map.of(), Map.of());
        }
        return database.withConnection(connection -> {
            long now = Instant.now().toEpochMilli();
            String placeholders = String.join(",", Collections.nCopies(targetIds.size(), "?"));
            Map<UUID, BanRecord> bans = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT target_uuid, punishment_id, reason, staff_name, created_at, expires_at
                  FROM pc_punishments
                 WHERE punishment_type = 'BAN'
                   AND revoked_at IS NULL
                   AND (expires_at IS NULL OR expires_at > ?)
                   AND target_uuid IN (%s)
                 ORDER BY target_uuid, (expires_at IS NULL) DESC, expires_at DESC
                """.formatted(placeholders)
            )) {
                statement.setLong(1, now);
                bindTargetIds(statement, targetIds, 2);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        UUID targetId = UUID.fromString(result.getString("target_uuid"));
                        long expiresAtValue = result.getLong("expires_at");
                        Long expiresAt = result.wasNull() ? null : expiresAtValue;
                        bans.putIfAbsent(targetId, new BanRecord(
                            result.getString("punishment_id"),
                            result.getString("reason"),
                            result.getString("staff_name"),
                            result.getLong("created_at"),
                            expiresAt
                        ));
                    }
                }
            }

            Map<UUID, MuteRecord> mutes = new LinkedHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT target_uuid, reason, staff_name, muted_at, expires_at
                  FROM pc_mutes
                 WHERE unmuted_at IS NULL
                   AND expires_at > ?
                   AND target_uuid IN (%s)
                """.formatted(placeholders)
            )) {
                statement.setLong(1, now);
                bindTargetIds(statement, targetIds, 2);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        mutes.put(
                            UUID.fromString(result.getString("target_uuid")),
                            new MuteRecord(
                                result.getString("reason"),
                                result.getString("staff_name"),
                                result.getLong("muted_at"),
                                result.getLong("expires_at")
                            )
                        );
                    }
                }
            }
            return new ModerationSnapshot(bans, mutes);
        });
    }

    private void bindTargetIds(
        PreparedStatement statement,
        Collection<UUID> targetIds,
        int startIndex
    ) throws SQLException {
        int index = startIndex;
        for (UUID targetId : targetIds) {
            statement.setString(index++, targetId.toString());
        }
    }

    PunishmentTarget findActiveBanTarget(String punishmentId) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT target_uuid, target_name, punishment_id
                FROM pc_punishments
                WHERE punishment_id = ?
                  AND punishment_type = 'BAN'
                  AND revoked_at IS NULL
                  AND (expires_at IS NULL OR expires_at > ?)
                LIMIT 1
                """
            )) {
                statement.setString(1, punishmentId);
                statement.setLong(2, Instant.now().toEpochMilli());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return null;
                    }
                    return new PunishmentTarget(
                        UUID.fromString(result.getString("target_uuid")),
                        result.getString("target_name"),
                        result.getString("punishment_id")
                    );
                }
            }
        });
    }

    /** @return Anzahl der aufgehobenen Bans. */
    int revokeActiveBans(UUID targetId, String staffName, String reason) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                UPDATE pc_punishments
                   SET revoked_at = ?, revoked_by = ?, revoke_reason = ?
                 WHERE target_uuid = ?
                   AND punishment_type = 'BAN'
                   AND revoked_at IS NULL
                   AND (expires_at IS NULL OR expires_at > ?)
                """
            )) {
                long now = Instant.now().toEpochMilli();
                statement.setLong(1, now);
                statement.setString(2, staffName);
                statement.setString(3, reason);
                statement.setString(4, targetId.toString());
                statement.setLong(5, now);
                return statement.executeUpdate();
            }
        });
    }

    private String generatePunishmentId() {
        StringBuilder builder = new StringBuilder(8);
        for (int index = 0; index < 8; index++) {
            builder.append(PUNISHMENT_ID_CHARACTERS.charAt(RANDOM.nextInt(PUNISHMENT_ID_CHARACTERS.length())));
        }
        return builder.toString();
    }

    private void importLegacyYaml() {
        File file = new File(plugin.getDataFolder(), "moderation-data.yml");
        if (!file.isFile()) {
            return;
        }

        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(file);
        boolean imported = database.inTransaction(connection -> {
            if (wasImported(connection)) {
                return false;
            }
            importReports(connection, legacy);
            importStaffSeen(connection, legacy);
            importWarnings(connection, legacy);
            importMutes(connection, legacy);
            importPunishments(connection, legacy);
            markImported(connection);
            return true;
        });
        if (imported) {
            plugin.getLogger().info("Imported legacy moderation-data.yml into MariaDB.");
        }
    }

    private void importReports(Connection connection, YamlConfiguration legacy) throws SQLException {
        ConfigurationSection section = legacy.getConfigurationSection("reports");
        if (section == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
            """
            INSERT INTO pc_reports
                (id, reporter_uuid, reporter_name, target_uuid, target_name, reason, created_at, is_open)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE id = id
            """
        )) {
            for (String key : section.getKeys(false)) {
                String path = "reports." + key;
                try {
                    statement.setInt(1, legacy.getInt(path + ".id", Integer.parseInt(key)));
                    statement.setString(2, UUID.fromString(
                        legacy.getString(path + ".reporter-id", "")
                    ).toString());
                    statement.setString(4, UUID.fromString(
                        legacy.getString(path + ".target-id", "")
                    ).toString());
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                statement.setString(3, legacy.getString(path + ".reporter-name", "Unbekannt"));
                statement.setString(5, legacy.getString(path + ".target-name", "Unbekannt"));
                statement.setString(6, legacy.getString(path + ".reason", "Kein Grund angegeben"));
                statement.setLong(7, legacy.getLong(path + ".created-at"));
                statement.setBoolean(8, legacy.getBoolean(path + ".open", true));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void importStaffSeen(Connection connection, YamlConfiguration legacy) throws SQLException {
        ConfigurationSection section = legacy.getConfigurationSection("staff-seen");
        if (section == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
            """
            INSERT INTO pc_report_staff_seen (staff_uuid, last_seen_report_id)
            VALUES (?, ?)
            ON DUPLICATE KEY UPDATE
                last_seen_report_id = GREATEST(last_seen_report_id, VALUES(last_seen_report_id))
            """
        )) {
            for (String key : section.getKeys(false)) {
                try {
                    statement.setString(1, UUID.fromString(key).toString());
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                statement.setInt(2, Math.max(0, section.getInt(key)));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void importWarnings(Connection connection, YamlConfiguration legacy) throws SQLException {
        ConfigurationSection section = legacy.getConfigurationSection("warnings");
        if (section == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
            """
            INSERT INTO pc_warnings
                (target_uuid, target_name, staff_name, reason, created_at)
            VALUES (?, ?, ?, ?, ?)
            """
        )) {
            for (String key : section.getKeys(false)) {
                UUID targetId;
                try {
                    targetId = UUID.fromString(key);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                String path = "warnings." + key;
                String targetName = legacy.getString(path + ".name", "Unbekannt");
                int expectedCount = Math.max(0, legacy.getInt(path + ".count"));
                ConfigurationSection entries = legacy.getConfigurationSection(path + ".entries");
                int importedCount = 0;
                if (entries != null) {
                    for (String timestamp : entries.getKeys(false)) {
                        statement.setString(1, targetId.toString());
                        statement.setString(2, targetName);
                        statement.setString(3, legacy.getString(
                            path + ".entries." + timestamp + ".staff",
                            "Legacy-Import"
                        ));
                        statement.setString(4, legacy.getString(
                            path + ".entries." + timestamp + ".reason",
                            "Kein Grund angegeben"
                        ));
                        statement.setLong(5, parseLong(timestamp, 0L));
                        statement.addBatch();
                        importedCount++;
                    }
                }
                while (importedCount++ < expectedCount) {
                    statement.setString(1, targetId.toString());
                    statement.setString(2, targetName);
                    statement.setString(3, "Legacy-Import");
                    statement.setString(4, "Historische Verwarnung");
                    statement.setLong(5, 0L);
                    statement.addBatch();
                }
            }
            statement.executeBatch();
        }
    }

    private void importMutes(Connection connection, YamlConfiguration legacy) throws SQLException {
        ConfigurationSection section = legacy.getConfigurationSection("mutes");
        if (section == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
            """
            INSERT INTO pc_mutes
                (target_uuid, target_name, staff_name, reason, muted_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                target_name = VALUES(target_name),
                staff_name = VALUES(staff_name),
                reason = VALUES(reason),
                muted_at = VALUES(muted_at),
                expires_at = VALUES(expires_at)
            """
        )) {
            for (String key : section.getKeys(false)) {
                String path = "mutes." + key;
                try {
                    statement.setString(1, UUID.fromString(key).toString());
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                statement.setString(2, legacy.getString(path + ".name", "Unbekannt"));
                statement.setString(3, legacy.getString(path + ".staff", "Unbekannt"));
                statement.setString(4, legacy.getString(path + ".reason", "Kein Grund angegeben"));
                statement.setLong(5, legacy.getLong(path + ".muted-at"));
                statement.setLong(6, legacy.getLong(path + ".expires-at"));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void importPunishments(Connection connection, YamlConfiguration legacy) throws SQLException {
        ConfigurationSection section = legacy.getConfigurationSection("punishments");
        if (section == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
            """
            INSERT INTO pc_punishments
                (punishment_id, punishment_type, target_uuid, target_name, staff_name,
                 reason, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE punishment_id = punishment_id
            """
        )) {
            for (String key : section.getKeys(false)) {
                String path = "punishments." + key;
                try {
                    statement.setString(3, UUID.fromString(
                        legacy.getString(path + ".target-id", "")
                    ).toString());
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                statement.setString(1, key);
                statement.setString(2, legacy.getString(path + ".type", "BAN"));
                statement.setString(4, legacy.getString(path + ".target-name", "Unbekannt"));
                statement.setString(5, legacy.getString(path + ".staff", "Unbekannt"));
                statement.setString(6, legacy.getString(path + ".reason", "Kein Grund angegeben"));
                statement.setLong(7, legacy.getLong(path + ".created-at"));
                if (legacy.isSet(path + ".expires-at")) {
                    statement.setLong(8, legacy.getLong(path + ".expires-at"));
                } else {
                    statement.setNull(8, java.sql.Types.BIGINT);
                }
                statement.addBatch();
            }
            statement.executeBatch();
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

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
