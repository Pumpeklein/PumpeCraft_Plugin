package de.pumpecraft.anticheat;

import de.pumpecraft.database.DatabaseService;
import java.sql.PreparedStatement;
import java.util.logging.Level;
import org.bukkit.entity.Player;

final class PlayerPlatformRepository {
    private final PumpeAntiCheatPlugin plugin;
    private final DatabaseService database;

    PlayerPlatformRepository(PumpeAntiCheatPlugin plugin, DatabaseService database) {
        this.plugin = plugin;
        this.database = database;
    }

    void record(Player player, boolean bedrock) {
        String playerId = player.getUniqueId().toString();
        String playerName = player.getName();
        String platform = bedrock ? "BEDROCK" : "JAVA";
        long seenAt = System.currentTimeMillis();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                database.withConnection(connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO pc_players
                            (player_uuid, player_name, platform, first_seen, last_seen)
                        VALUES (?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            player_name = VALUES(player_name),
                            platform = VALUES(platform),
                            last_seen = VALUES(last_seen)
                        """)) {
                        statement.setString(1, playerId);
                        statement.setString(2, playerName);
                        statement.setString(3, platform);
                        statement.setLong(4, seenAt);
                        statement.setLong(5, seenAt);
                        statement.executeUpdate();
                    }
                    return null;
                });
            } catch (RuntimeException exception) {
                plugin.getLogger().log(
                    Level.WARNING,
                    "Could not update the platform for " + playerName + ".",
                    exception
                );
            }
        });
    }
}
