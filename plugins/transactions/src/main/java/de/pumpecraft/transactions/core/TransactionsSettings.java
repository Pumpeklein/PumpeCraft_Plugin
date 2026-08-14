package de.pumpecraft.transactions.core;

import org.bukkit.configuration.file.FileConfiguration;

public record TransactionsSettings(
    boolean payoutEnabled,
    long payoutAmount,
    int payoutIntervalSeconds,
    int maxIdleSeconds,
    boolean announcePayout,
    boolean transferEnabled,
    long transferMinimum,
    long transferMaximum,
    int historySize,
    int leaderboardSize
) {
    public static TransactionsSettings from(FileConfiguration config) {
        return new TransactionsSettings(
            config.getBoolean("payout.enabled", true),
            clamp(config.getLong("payout.amount", 500L), 0L, Currency.MAX_AMOUNT),
            (int) clamp(config.getInt("payout.interval-minutes", 30), 1L, 24L * 60L) * 60,
            (int) clamp(config.getInt("payout.max-idle-minutes", 5), 0L, 24L * 60L) * 60,
            config.getBoolean("payout.announce", true),
            config.getBoolean("transfer.enabled", true),
            clamp(config.getLong("transfer.minimum", 1L), 1L, Currency.MAX_AMOUNT),
            clamp(config.getLong("transfer.maximum", 1_000_000L), 0L, Currency.MAX_AMOUNT),
            (int) clamp(config.getInt("history.size", 10), 1L, 50L),
            (int) clamp(config.getInt("leaderboard.size", 10), 1L, 50L)
        );
    }

    public int payoutIntervalMinutes() {
        return payoutIntervalSeconds / 60;
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.min(Math.max(value, minimum), maximum);
    }
}
