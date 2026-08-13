package de.pumpecraft.anticheat;

import de.pumpecraft.database.DatabaseService;
import java.sql.PreparedStatement;
import java.util.logging.Level;
import org.bukkit.entity.Player;

final class AntiCheatEventRepository {
    private static final int MAX_DETAIL_LENGTH = 500;

    private final PumpeAntiCheatPlugin plugin;
    private final DatabaseService database;

    AntiCheatEventRepository(PumpeAntiCheatPlugin plugin, DatabaseService database) {
        this.plugin = plugin;
        this.database = database;
    }

    void record(
        Player player,
        CheckType check,
        double violationLevel,
        String detail,
        String platform
    ) {
        String playerId = player.getUniqueId().toString();
        String playerName = player.getName();
        String checkType = check.displayName();
        String storedDetail = detail.substring(0, Math.min(detail.length(), MAX_DETAIL_LENGTH));
        long createdAt = System.currentTimeMillis();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                database.withConnection(connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO pc_anticheat_events
                            (player_uuid, player_name, check_type, violation_level,
                             detail, platform, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)) {
                        statement.setString(1, playerId);
                        statement.setString(2, playerName);
                        statement.setString(3, checkType);
                        statement.setDouble(4, violationLevel);
                        statement.setString(5, storedDetail);
                        statement.setString(6, platform);
                        statement.setLong(7, createdAt);
                        statement.executeUpdate();
                    }
                    return null;
                });
            } catch (RuntimeException exception) {
                plugin.getLogger().log(
                    Level.WARNING,
                    "Could not persist AntiCheat event for " + playerName + ".",
                    exception
                );
            }
        });
    }
}
