package de.pumpecraft.subessentials;

import de.pumpecraft.database.DatabaseService;
import de.pumpecraft.database.Databases;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.plugin.Plugin;

final class TwitchLinkRepository {
    private final DatabaseService database;

    TwitchLinkRepository(Plugin plugin) {
        this.database = Databases.require(plugin);
    }

    void createRequest(
        UUID playerId,
        String playerName,
        String tokenHash,
        long createdAt,
        long expiresAt
    ) {
        database.inTransaction(connection -> {
            try (PreparedStatement cleanup = connection.prepareStatement(
                "DELETE FROM pc_twitch_link_requests WHERE player_uuid = ? OR expires_at < ?"
            )) {
                cleanup.setString(1, playerId.toString());
                cleanup.setLong(2, createdAt);
                cleanup.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO pc_twitch_link_requests
                    (token_hash, player_uuid, player_name, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?)
                """)) {
                insert.setString(1, tokenHash);
                insert.setString(2, playerId.toString());
                insert.setString(3, playerName);
                insert.setLong(4, createdAt);
                insert.setLong(5, expiresAt);
                insert.executeUpdate();
            }
            return null;
        });
    }

    Optional<TwitchLink> find(UUID playerId) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                SELECT player_uuid, player_name, twitch_user_id, twitch_login,
                       twitch_display_name, is_subscriber, linked_at, subscription_checked_at,
                       game_notified_at, subscription_notified_state
                  FROM pc_twitch_links
                 WHERE player_uuid = ?
                """)) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Optional.of(readLink(result)) : Optional.empty();
                }
            }
        });
    }

    boolean unlink(UUID playerId) {
        return database.inTransaction(connection -> {
            try (PreparedStatement requests = connection.prepareStatement(
                "DELETE FROM pc_twitch_link_requests WHERE player_uuid = ?"
            )) {
                requests.setString(1, playerId.toString());
                requests.executeUpdate();
            }
            try (PreparedStatement link = connection.prepareStatement(
                "DELETE FROM pc_twitch_links WHERE player_uuid = ?"
            )) {
                link.setString(1, playerId.toString());
                return link.executeUpdate() == 1;
            }
        });
    }

    boolean updateSubscription(UUID playerId, boolean subscriber, long checkedAt) {
        return database.inTransaction(connection -> {
            boolean previous;
            try (PreparedStatement select = connection.prepareStatement("""
                SELECT is_subscriber
                  FROM pc_twitch_links
                 WHERE player_uuid = ?
                   FOR UPDATE
                """)) {
                select.setString(1, playerId.toString());
                try (ResultSet result = select.executeQuery()) {
                    if (!result.next()) return false;
                    previous = result.getBoolean("is_subscriber");
                }
            }
            try (PreparedStatement update = connection.prepareStatement("""
                UPDATE pc_twitch_links
                   SET subscription_notified_state = CASE
                           WHEN is_subscriber <> ? THEN NULL
                           ELSE subscription_notified_state
                       END,
                       is_subscriber = ?,
                       subscription_checked_at = ?
                 WHERE player_uuid = ?
                """)) {
                update.setBoolean(1, subscriber);
                update.setBoolean(2, subscriber);
                update.setLong(3, checkedAt);
                update.setString(4, playerId.toString());
                update.executeUpdate();
            }
            return previous != subscriber;
        });
    }

    boolean markGameNotificationDelivered(
        UUID playerId,
        long deliveredAt,
        boolean subscriber
    ) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE pc_twitch_links
                   SET game_notified_at = ?, subscription_notified_state = ?
                 WHERE player_uuid = ?
                   AND game_notified_at IS NULL
                   AND is_subscriber = ?
                """)) {
                statement.setLong(1, deliveredAt);
                statement.setBoolean(2, subscriber);
                statement.setString(3, playerId.toString());
                statement.setBoolean(4, subscriber);
                return statement.executeUpdate() == 1;
            }
        });
    }

    boolean markSubscriptionNotificationDelivered(UUID playerId, boolean subscriber) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE pc_twitch_links
                   SET subscription_notified_state = ?
                 WHERE player_uuid = ?
                   AND is_subscriber = ?
                   AND subscription_notified_state IS NULL
                """)) {
                statement.setBoolean(1, subscriber);
                statement.setString(2, playerId.toString());
                statement.setBoolean(3, subscriber);
                return statement.executeUpdate() == 1;
            }
        });
    }

    private TwitchLink readLink(ResultSet result) throws SQLException {
        return new TwitchLink(
            UUID.fromString(result.getString("player_uuid")),
            result.getString("player_name"),
            result.getString("twitch_user_id"),
            result.getString("twitch_login"),
            result.getString("twitch_display_name"),
            result.getBoolean("is_subscriber"),
            result.getLong("linked_at"),
            result.getLong("subscription_checked_at"),
            result.getObject("game_notified_at", Long.class),
            result.getObject("subscription_notified_state", Boolean.class)
        );
    }
}
