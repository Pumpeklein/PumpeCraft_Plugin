package de.pumpecraft.enchants.integration;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The cap that keeps a lucky pickaxe from becoming a points farm. It lives in memory only - a
 * restart hands out a fresh day, which is the cheap half of the trade and still stops the grind
 * that matters, a player standing at one spot for hours.
 */
final class DailyBudget {
    private record Spent(long day, int amount) {
    }

    private final Map<UUID, Spent> spent = new ConcurrentHashMap<>();

    int take(UUID playerId, int wanted, int dailyLimit) {
        if (dailyLimit <= 0) {
            return wanted;
        }
        long today = LocalDate.now().toEpochDay();
        int[] granted = new int[1];
        spent.compute(playerId, (key, current) -> {
            int used = current == null || current.day() != today ? 0 : current.amount();
            granted[0] = Math.max(0, Math.min(wanted, dailyLimit - used));
            return new Spent(today, used + granted[0]);
        });
        return granted[0];
    }
}
