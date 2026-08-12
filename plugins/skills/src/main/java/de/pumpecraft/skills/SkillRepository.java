package de.pumpecraft.skills;

import de.pumpecraft.database.DatabaseService;
import de.pumpecraft.database.Databases;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SkillRepository {
    private final DatabaseService database;

    SkillRepository(PumpeSkillsPlugin plugin) {
        this.database = Databases.require(plugin);
    }

    /** Merkt sich den zuletzt bekannten Namen, damit Bestenlisten ohne Mojang-Abfrage auskommen. */
    void touchPlayer(UUID playerId, String playerName) {
        long now = Instant.now().toEpochMilli();
        database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_players (player_uuid, player_name, first_seen, last_seen)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    player_name = VALUES(player_name),
                    last_seen = VALUES(last_seen)
                """
            )) {
                statement.setString(1, playerId.toString());
                statement.setString(2, playerName);
                statement.setLong(3, now);
                statement.setLong(4, now);
                statement.executeUpdate();
            }
            return null;
        });
    }

    Map<StatKey, Long> loadPlayer(UUID playerId) {
        return database.withConnection(connection -> {
            Map<StatKey, Long> values = new HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT skill, stat_key, amount FROM pc_skill_stats WHERE player_uuid = ?"
            )) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        Skill skill = Skill.byId(result.getString("skill"));
                        if (skill == null) {
                            continue;
                        }
                        values.put(
                            new StatKey(skill, result.getString("stat_key")),
                            result.getLong("amount")
                        );
                    }
                }
            }
            return values;
        });
    }

    void save(UUID playerId, Map<StatKey, Long> values) {
        if (values.isEmpty()) {
            return;
        }
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_skill_stats (player_uuid, skill, stat_key, amount)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE amount = VALUES(amount)
                """
            )) {
                for (Map.Entry<StatKey, Long> entry : values.entrySet()) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, entry.getKey().skill().id());
                    statement.setString(3, entry.getKey().key());
                    statement.setLong(4, entry.getValue());
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    /** @return {@code true}, wenn mit diesem Villager zum ersten Mal gehandelt wurde. */
    boolean recordVillagePartner(UUID playerId, UUID villagerId) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT IGNORE INTO pc_skill_village_partners
                    (player_uuid, villager_uuid, first_trade_at)
                VALUES (?, ?, ?)
                """
            )) {
                statement.setString(1, playerId.toString());
                statement.setString(2, villagerId.toString());
                statement.setLong(3, Instant.now().toEpochMilli());
                return statement.executeUpdate() > 0;
            }
        });
    }

    List<LeaderboardEntry> topPlayers(Skill skill, String statKey, int limit) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT s.player_uuid, s.amount, p.player_name
                  FROM pc_skill_stats s
                  LEFT JOIN pc_players p ON p.player_uuid = s.player_uuid
                 WHERE s.skill = ? AND s.stat_key = ? AND s.amount > 0
                 ORDER BY s.amount DESC, p.player_name ASC
                 LIMIT ?
                """
            )) {
                statement.setString(1, skill.id());
                statement.setString(2, statKey);
                statement.setInt(3, limit);
                try (ResultSet result = statement.executeQuery()) {
                    List<LeaderboardEntry> entries = new ArrayList<>();
                    while (result.next()) {
                        String uuid = result.getString("player_uuid");
                        String name = result.getString("player_name");
                        entries.add(new LeaderboardEntry(
                            UUID.fromString(uuid),
                            name == null || name.isBlank() ? uuid.substring(0, 8) : name,
                            result.getLong("amount")
                        ));
                    }
                    return entries;
                }
            }
        });
    }

    /** Platzierung eines Spielers in einem Skill; 0 wenn er dort keine Punkte hat. */
    int rankOf(UUID playerId, Skill skill, String statKey) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT COUNT(*) + 1 AS position
                  FROM pc_skill_stats
                 WHERE skill = ? AND stat_key = ?
                   AND amount > COALESCE(
                       (SELECT amount FROM pc_skill_stats
                         WHERE player_uuid = ? AND skill = ? AND stat_key = ?), -1)
                """
            )) {
                statement.setString(1, skill.id());
                statement.setString(2, statKey);
                statement.setString(3, playerId.toString());
                statement.setString(4, skill.id());
                statement.setString(5, statKey);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? result.getInt("position") : 0;
                }
            }
        });
    }

    UUID findPlayerByName(String name) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT player_uuid FROM pc_players WHERE player_name = ? ORDER BY last_seen DESC LIMIT 1"
            )) {
                statement.setString(1, name);
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? UUID.fromString(result.getString("player_uuid")) : null;
                }
            }
        });
    }

    record LeaderboardEntry(UUID playerId, String playerName, long amount) {
    }
}
