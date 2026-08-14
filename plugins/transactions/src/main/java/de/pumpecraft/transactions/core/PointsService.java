package de.pumpecraft.transactions.core;

import de.pumpecraft.transactions.storage.AccountRepository;
import java.util.List;
import java.util.UUID;
import org.bukkit.plugin.Plugin;

/**
 * Öffentlicher Zugang zu den PumpePoints. Jede Methode, die Konten liest oder ändert,
 * spricht direkt mit der Datenbank und gehört deshalb in einen asynchronen Task -
 * {@link #runAsync(Runnable)} und {@link #runSync(Runnable)} liefern das passende Gespann.
 */
public final class PointsService {
    private final Plugin plugin;
    private final AccountRepository accounts;
    private final TransactionsSettings settings;

    public PointsService(Plugin plugin, AccountRepository accounts, TransactionsSettings settings) {
        this.plugin = plugin;
        this.accounts = accounts;
        this.settings = settings;
    }

    public TransactionsSettings settings() {
        return settings;
    }

    public long balance(UUID playerId) {
        return accounts.balance(playerId);
    }

    public long deposit(
        UUID playerId,
        String playerName,
        long amount,
        TransactionType type,
        String actorName,
        String reason
    ) {
        requirePositive(amount);
        return accounts.apply(playerId, playerName, amount, type, actorName, reason);
    }

    public boolean withdraw(
        UUID playerId,
        String playerName,
        long amount,
        TransactionType type,
        String actorName,
        String reason
    ) {
        requirePositive(amount);
        return accounts.withdraw(playerId, playerName, amount, type, actorName, reason);
    }

    public long set(UUID playerId, String playerName, long target, String actorName, String reason) {
        if (target < 0L || target > Currency.MAX_AMOUNT) {
            throw new IllegalArgumentException("Balance out of range: " + target);
        }
        return accounts.set(playerId, playerName, target, actorName, reason);
    }

    public TransferResult transfer(
        UUID senderId,
        String senderName,
        UUID receiverId,
        String receiverName,
        long amount,
        String reason
    ) {
        if (!settings.transferEnabled()) {
            return TransferResult.failed(TransferOutcome.DISABLED);
        }
        if (amount <= 0L || amount > Currency.MAX_AMOUNT) {
            return TransferResult.failed(TransferOutcome.INVALID_AMOUNT);
        }
        if (senderId.equals(receiverId)) {
            return TransferResult.failed(TransferOutcome.SELF);
        }
        if (amount < settings.transferMinimum()) {
            return TransferResult.failed(TransferOutcome.BELOW_MINIMUM);
        }
        if (settings.transferMaximum() > 0L && amount > settings.transferMaximum()) {
            return TransferResult.failed(TransferOutcome.ABOVE_MAXIMUM);
        }
        return accounts.transfer(senderId, senderName, receiverId, receiverName, amount, reason);
    }

    public List<Transaction> history(UUID playerId, int limit) {
        return accounts.history(playerId, limit);
    }

    public List<BalanceEntry> top(int limit) {
        return accounts.top(limit);
    }

    public void runAsync(Runnable action) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, action);
    }

    public void runSync(Runnable action) {
        plugin.getServer().getScheduler().runTask(plugin, action);
    }

    private void requirePositive(long amount) {
        if (amount <= 0L || amount > Currency.MAX_AMOUNT) {
            throw new IllegalArgumentException("Amount out of range: " + amount);
        }
    }
}
