package de.pumpecraft.transactions.storage;

import de.pumpecraft.database.DatabaseService;
import de.pumpecraft.transactions.core.BalanceEntry;
import de.pumpecraft.transactions.core.Currency;
import de.pumpecraft.transactions.core.Transaction;
import de.pumpecraft.transactions.core.TransactionType;
import de.pumpecraft.transactions.core.TransferOutcome;
import de.pumpecraft.transactions.core.TransferResult;
import de.pumpecraft.utils.Texts;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AccountRepository {
    private static final int MAX_NAME_LENGTH = 16;
    private static final int MAX_REASON_LENGTH = 120;
    private static final int MAX_ACTOR_LENGTH = 32;

    private final DatabaseService database;

    public AccountRepository(DatabaseService database) {
        this.database = database;
    }

    public long balance(UUID playerId) {
        return database.withConnection(connection -> readBalance(connection, playerId, false));
    }

    public long apply(
        UUID playerId,
        String playerName,
        long delta,
        TransactionType type,
        String actorName,
        String reason
    ) {
        return database.inTransaction(connection -> credit(
            connection, playerId, playerName, delta, type, actorName, reason, null, null));
    }

    public boolean withdraw(
        UUID playerId,
        String playerName,
        long amount,
        TransactionType type,
        String actorName,
        String reason
    ) {
        return database.inTransaction(connection -> {
            long balance = readBalance(connection, playerId, true);
            if (balance < amount) {
                return false;
            }
            write(connection, playerId, playerName, balance, -amount, type, actorName, reason, null, null);
            return true;
        });
    }

    public long set(UUID playerId, String playerName, long target, String actorName, String reason) {
        return database.inTransaction(connection -> {
            long balance = readBalance(connection, playerId, true);
            return write(
                connection,
                playerId,
                playerName,
                balance,
                target - balance,
                TransactionType.ADMIN_SET,
                actorName,
                reason,
                null,
                null
            );
        });
    }

    public TransferResult transfer(
        UUID senderId,
        String senderName,
        UUID receiverId,
        String receiverName,
        long amount,
        String reason
    ) {
        return database.inTransaction(connection -> {
            // Beide Konten immer in derselben Reihenfolge sperren, sonst können sich zwei
            // gegenläufige Überweisungen gegenseitig blockieren.
            boolean senderFirst = senderId.compareTo(receiverId) <= 0;
            long firstBalance = readBalance(connection, senderFirst ? senderId : receiverId, true);
            long secondBalance = readBalance(connection, senderFirst ? receiverId : senderId, true);
            long senderBalance = senderFirst ? firstBalance : secondBalance;
            long receiverBalance = senderFirst ? secondBalance : firstBalance;

            if (senderBalance < amount) {
                return TransferResult.failed(TransferOutcome.INSUFFICIENT_FUNDS);
            }

            long updatedSender = write(
                connection, senderId, senderName, senderBalance, -amount,
                TransactionType.TRANSFER_OUT, senderName, reason, receiverId, receiverName
            );
            long updatedReceiver = write(
                connection, receiverId, receiverName, receiverBalance, amount,
                TransactionType.TRANSFER_IN, senderName, reason, senderId, senderName
            );
            return new TransferResult(TransferOutcome.OK, updatedSender, updatedReceiver);
        });
    }

    public List<Transaction> history(UUID playerId, int limit) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT id, player_uuid, counterparty_uuid, counterparty_name, amount,
                       balance_after, transaction_type, actor_name, reason, created_at
                  FROM pc_transactions
                 WHERE player_uuid = ?
                 ORDER BY created_at DESC, id DESC
                 LIMIT ?
                """
            )) {
                statement.setString(1, playerId.toString());
                statement.setInt(2, limit);
                try (ResultSet result = statement.executeQuery()) {
                    List<Transaction> entries = new ArrayList<>();
                    while (result.next()) {
                        entries.add(readTransaction(result));
                    }
                    return entries;
                }
            }
        });
    }

    public List<BalanceEntry> top(int limit) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT player_uuid, player_name, balance
                  FROM pc_currency_accounts
                 WHERE balance > 0
                 ORDER BY balance DESC, player_name ASC
                 LIMIT ?
                """
            )) {
                statement.setInt(1, limit);
                try (ResultSet result = statement.executeQuery()) {
                    List<BalanceEntry> entries = new ArrayList<>();
                    while (result.next()) {
                        String name = result.getString("player_name");
                        UUID playerId = UUID.fromString(result.getString("player_uuid"));
                        entries.add(new BalanceEntry(
                            playerId,
                            name == null || name.isBlank()
                                ? playerId.toString().substring(0, 8)
                                : name,
                            result.getLong("balance")
                        ));
                    }
                    return entries;
                }
            }
        });
    }

    long credit(
        Connection connection,
        UUID playerId,
        String playerName,
        long delta,
        TransactionType type,
        String actorName,
        String reason,
        UUID counterpartyId,
        String counterpartyName
    ) throws SQLException {
        long balance = readBalance(connection, playerId, true);
        return write(
            connection, playerId, playerName, balance, delta,
            type, actorName, reason, counterpartyId, counterpartyName
        );
    }

    private long readBalance(Connection connection, UUID playerId, boolean lock) throws SQLException {
        String sql = "SELECT balance FROM pc_currency_accounts WHERE player_uuid = ?"
            + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong("balance") : 0L;
            }
        }
    }

    /**
     * Schreibt den Kontostand und die passende Buchung. Ein Konto kann nicht ins Minus
     * laufen; gebucht wird deshalb die tatsächlich angewandte Differenz, nicht die gewünschte.
     */
    private long write(
        Connection connection,
        UUID playerId,
        String playerName,
        long currentBalance,
        long delta,
        TransactionType type,
        String actorName,
        String reason,
        UUID counterpartyId,
        String counterpartyName
    ) throws SQLException {
        long updated = Math.max(0L, Math.min(currentBalance + delta, Currency.MAX_AMOUNT));
        long applied = updated - currentBalance;
        long now = System.currentTimeMillis();

        try (PreparedStatement statement = connection.prepareStatement(
            """
            INSERT INTO pc_currency_accounts (player_uuid, player_name, balance, updated_at)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                player_name = IF(VALUES(player_name) = '', player_name, VALUES(player_name)),
                balance = VALUES(balance),
                updated_at = VALUES(updated_at)
            """
        )) {
            statement.setString(1, playerId.toString());
            statement.setString(2, name(playerName));
            statement.setLong(3, updated);
            statement.setLong(4, now);
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
            """
            INSERT INTO pc_transactions
                (player_uuid, counterparty_uuid, counterparty_name, amount, balance_after,
                 transaction_type, actor_name, reason, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
        )) {
            statement.setString(1, playerId.toString());
            statement.setString(2, counterpartyId == null ? null : counterpartyId.toString());
            statement.setString(3, counterpartyName == null ? null : name(counterpartyName));
            statement.setLong(4, applied);
            statement.setLong(5, updated);
            statement.setString(6, type.name());
            statement.setString(7, Texts.truncate(
                actorName == null || actorName.isBlank() ? "Server" : actorName, MAX_ACTOR_LENGTH));
            statement.setString(8, reason == null ? null : Texts.truncate(reason, MAX_REASON_LENGTH));
            statement.setLong(9, now);
            statement.executeUpdate();
        }
        return updated;
    }

    private Transaction readTransaction(ResultSet result) throws SQLException {
        String counterparty = result.getString("counterparty_uuid");
        TransactionType type = TransactionType.byId(result.getString("transaction_type"));
        return new Transaction(
            result.getLong("id"),
            UUID.fromString(result.getString("player_uuid")),
            counterparty == null ? null : UUID.fromString(counterparty),
            result.getString("counterparty_name"),
            result.getLong("amount"),
            result.getLong("balance_after"),
            type,
            result.getString("actor_name"),
            result.getString("reason"),
            result.getLong("created_at")
        );
    }

    private String name(String playerName) {
        return playerName == null ? "" : Texts.truncate(playerName, MAX_NAME_LENGTH);
    }
}
