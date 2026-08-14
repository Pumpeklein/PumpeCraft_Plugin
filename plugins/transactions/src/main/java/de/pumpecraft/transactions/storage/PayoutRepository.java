package de.pumpecraft.transactions.storage;

import de.pumpecraft.database.DatabaseService;
import de.pumpecraft.transactions.core.TransactionType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;

public final class PayoutRepository {
    private final DatabaseService database;
    private final AccountRepository accounts;

    public PayoutRepository(DatabaseService database, AccountRepository accounts) {
        this.database = database;
        this.accounts = accounts;
    }

    public int loadAccruedSeconds(UUID playerId) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT accrued_seconds FROM pc_currency_payouts WHERE player_uuid = ?"
            )) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    return result.next() ? Math.max(0, result.getInt("accrued_seconds")) : 0;
                }
            }
        });
    }

    public void saveAccruedSeconds(Map<UUID, Integer> accruedSeconds) {
        if (accruedSeconds.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_currency_payouts (player_uuid, accrued_seconds, updated_at)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    accrued_seconds = VALUES(accrued_seconds),
                    updated_at = VALUES(updated_at)
                """
            )) {
                for (Map.Entry<UUID, Integer> entry : accruedSeconds.entrySet()) {
                    statement.setString(1, entry.getKey().toString());
                    statement.setInt(2, Math.max(0, entry.getValue()));
                    statement.setLong(3, now);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    /** Gutschrift und Fortschritt in einer Transaktion, damit keins von beidem allein steht. */
    public long award(
        UUID playerId,
        String playerName,
        long amount,
        int remainingSeconds,
        String reason
    ) {
        long now = System.currentTimeMillis();
        return database.inTransaction(connection -> {
            long balance = accounts.credit(
                connection, playerId, playerName, amount,
                TransactionType.PAYOUT, "Server", reason, null, null
            );
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_currency_payouts
                    (player_uuid, accrued_seconds, payout_count, total_paid, last_payout_at, updated_at)
                VALUES (?, ?, 1, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    accrued_seconds = VALUES(accrued_seconds),
                    payout_count = payout_count + 1,
                    total_paid = total_paid + VALUES(total_paid),
                    last_payout_at = VALUES(last_payout_at),
                    updated_at = VALUES(updated_at)
                """
            )) {
                statement.setString(1, playerId.toString());
                statement.setInt(2, Math.max(0, remainingSeconds));
                statement.setLong(3, amount);
                statement.setLong(4, now);
                statement.setLong(5, now);
                statement.executeUpdate();
            }
            return balance;
        });
    }
}
