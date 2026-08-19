package de.pumpecraft.chatcontrol;

import de.pumpecraft.database.DatabaseService;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.entity.Player;

final class ChatMessageRepository {
    private final PumpeChatControlPlugin plugin;
    private final DatabaseService database;

    ChatMessageRepository(PumpeChatControlPlugin plugin, DatabaseService database) {
        this.plugin = plugin;
        this.database = database;
    }

    String recordAccepted(Player sender, String message, String type, Player recipient) {
        String messageId = UUID.randomUUID().toString();
        persist(messageId, sender, message, type, recipient, false, null, null);
        return messageId;
    }

    // Angehalten heisst: noch nicht zugestellt, aber auch nicht verworfen. held_at
    // trennt diesen Zustand vom harten Block, der nie beim Team landet.
    String recordFlagged(Player sender, String message, String type, Player recipient, String reason) {
        String messageId = UUID.randomUUID().toString();
        persist(messageId, sender, message, type, recipient, true, reason, System.currentTimeMillis());
        return messageId;
    }

    void recordBlocked(Player sender, String message, String type, Player recipient, String reason) {
        persist(UUID.randomUUID().toString(), sender, message, type, recipient, true, reason, null);
    }

    void markDeleted(String messageId, Player staff) {
        String staffId = staff.getUniqueId().toString();
        String staffName = staff.getName();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                database.withConnection(connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE pc_chat_messages
                           SET deleted_at = ?, deleted_by_uuid = ?, deleted_by_name = ?
                         WHERE message_id = ? AND deleted_at IS NULL
                        """)) {
                        statement.setLong(1, System.currentTimeMillis());
                        statement.setString(2, staffId);
                        statement.setString(3, staffName);
                        statement.setString(4, messageId);
                        statement.executeUpdate();
                    }
                    return null;
                });
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not mark chat message as deleted.", exception);
            }
        });
    }

    void markApproved(String messageId, Player staff) {
        String staffId = staff.getUniqueId().toString();
        String staffName = staff.getName();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                database.withConnection(connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                        UPDATE pc_chat_messages
                           SET blocked = FALSE,
                               approved_at = ?,
                               approved_by_uuid = ?,
                               approved_by_name = ?
                         WHERE message_id = ? AND deleted_at IS NULL AND approved_at IS NULL
                        """)) {
                        statement.setLong(1, System.currentTimeMillis());
                        statement.setString(2, staffId);
                        statement.setString(3, staffName);
                        statement.setString(4, messageId);
                        statement.executeUpdate();
                    }
                    return null;
                });
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not mark held chat message as approved.", exception);
            }
        });
    }

    private void persist(
        String messageId,
        Player sender,
        String message,
        String type,
        Player recipient,
        boolean blocked,
        String blockReason,
        Long heldAt
    ) {
        long createdAt = System.currentTimeMillis();
        String senderId = sender.getUniqueId().toString();
        String senderName = sender.getName();
        String recipientId = recipient == null ? null : recipient.getUniqueId().toString();
        String recipientName = recipient == null ? null : recipient.getName();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                database.withConnection(connection -> {
                    try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO pc_chat_messages
                            (message_id, player_uuid, player_name, message, message_type,
                             recipient_uuid, recipient_name, blocked, block_reason, created_at, held_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                        statement.setString(1, messageId);
                        statement.setString(2, senderId);
                        statement.setString(3, senderName);
                        statement.setString(4, message);
                        statement.setString(5, type);
                        if (recipientId == null) {
                            statement.setNull(6, Types.CHAR);
                            statement.setNull(7, Types.VARCHAR);
                        } else {
                            statement.setString(6, recipientId);
                            statement.setString(7, recipientName);
                        }
                        statement.setBoolean(8, blocked);
                        if (blockReason == null) statement.setNull(9, Types.VARCHAR);
                        else statement.setString(9, blockReason);
                        statement.setLong(10, createdAt);
                        if (heldAt == null) statement.setNull(11, Types.BIGINT);
                        else statement.setLong(11, heldAt);
                        statement.executeUpdate();
                    }
                    return null;
                });
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not persist chat message from " + senderName + ".", exception);
            }
        });
    }
}
