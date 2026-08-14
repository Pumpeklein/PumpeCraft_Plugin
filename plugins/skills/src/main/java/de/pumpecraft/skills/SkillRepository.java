package de.pumpecraft.skills;

import de.pumpecraft.database.DatabaseService;
import de.pumpecraft.database.Databases;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
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

    boolean reserveReward(UUID playerId, Skill skill, int milestoneLevel, long score) {
        return database.inTransaction(connection -> {
            try (PreparedStatement scoreStatement = connection.prepareStatement(
                """
                INSERT INTO pc_skill_stats (player_uuid, skill, stat_key, amount)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE amount = GREATEST(amount, VALUES(amount))
                """
            )) {
                scoreStatement.setString(1, playerId.toString());
                scoreStatement.setString(2, skill.id());
                scoreStatement.setString(3, Skill.SCORE);
                scoreStatement.setLong(4, score);
                scoreStatement.executeUpdate();
            }
            try (PreparedStatement rewardStatement = connection.prepareStatement(
                """
                INSERT IGNORE INTO pc_skill_rewards
                    (player_uuid, skill, milestone_level, earned_at, delivered_at)
                VALUES (?, ?, ?, ?, NULL)
                """
            )) {
                rewardStatement.setString(1, playerId.toString());
                rewardStatement.setString(2, skill.id());
                rewardStatement.setInt(3, milestoneLevel);
                rewardStatement.setLong(4, Instant.now().toEpochMilli());
                return rewardStatement.executeUpdate() > 0;
            }
        });
    }

    List<RewardClaim> reserveReachedRewards(UUID playerId, Map<Skill, List<Integer>> reached) {
        return database.inTransaction(connection -> {
            List<RewardClaim> candidates = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT IGNORE INTO pc_skill_rewards
                    (player_uuid, skill, milestone_level, earned_at, delivered_at)
                VALUES (?, ?, ?, ?, NULL)
                """
            )) {
                long now = Instant.now().toEpochMilli();
                for (Map.Entry<Skill, List<Integer>> entry : reached.entrySet()) {
                    for (int milestone : entry.getValue()) {
                        candidates.add(new RewardClaim(entry.getKey(), milestone));
                        statement.setString(1, playerId.toString());
                        statement.setString(2, entry.getKey().id());
                        statement.setInt(3, milestone);
                        statement.setLong(4, now);
                        statement.addBatch();
                    }
                }
                int[] results = statement.executeBatch();
                List<RewardClaim> inserted = new ArrayList<>();
                for (int index = 0; index < results.length; index++) {
                    if (results[index] != 0 && results[index] != Statement.EXECUTE_FAILED) {
                        inserted.add(candidates.get(index));
                    }
                }
                return inserted;
            }
        });
    }

    List<RewardClaim> pendingRewards(UUID playerId) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT skill, milestone_level
                  FROM pc_skill_rewards
                 WHERE player_uuid = ? AND delivered_at IS NULL
                 ORDER BY earned_at, skill, milestone_level
                """
            )) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    List<RewardClaim> rewards = new ArrayList<>();
                    while (result.next()) {
                        Skill skill = Skill.byId(result.getString("skill"));
                        if (skill != null) {
                            rewards.add(new RewardClaim(skill, result.getInt("milestone_level")));
                        }
                    }
                    return rewards;
                }
            }
        });
    }

    void markRewardDelivered(UUID playerId, Skill skill, int milestoneLevel) {
        database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                UPDATE pc_skill_rewards
                   SET delivered_at = ?
                 WHERE player_uuid = ? AND skill = ? AND milestone_level = ?
                   AND delivered_at IS NULL
                """
            )) {
                statement.setLong(1, Instant.now().toEpochMilli());
                statement.setString(2, playerId.toString());
                statement.setString(3, skill.id());
                statement.setInt(4, milestoneLevel);
                statement.executeUpdate();
            }
            return null;
        });
    }

    /** Schreibspiegel der Config; die Tabelle wird bei jedem Start vollständig ersetzt. */
    void syncRewardDefinitions(List<RewardDefinition> definitions) {
        database.inTransaction(connection -> {
            try (PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM pc_skill_reward_definitions"
            )) {
                delete.executeUpdate();
            }
            if (definitions.isEmpty()) {
                return null;
            }
            try (PreparedStatement insert = connection.prepareStatement(
                """
                INSERT INTO pc_skill_reward_definitions
                    (skill, milestone_level, label, items, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """
            )) {
                long now = Instant.now().toEpochMilli();
                for (RewardDefinition definition : definitions) {
                    insert.setString(1, definition.skill());
                    insert.setInt(2, definition.milestoneLevel());
                    insert.setString(3, definition.label());
                    insert.setString(4, definition.items());
                    insert.setLong(5, now);
                    insert.addBatch();
                }
                insert.executeBatch();
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

    record RewardClaim(Skill skill, int milestoneLevel) {
    }

    record RewardDefinition(String skill, int milestoneLevel, String label, String items) {
    }
}
