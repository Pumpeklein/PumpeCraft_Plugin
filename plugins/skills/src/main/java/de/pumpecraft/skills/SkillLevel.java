package de.pumpecraft.skills;

/**
 * Levelkurve: Level {@code n} beginnt bei {@code BASE * (n-1)^2} Punkten.
 * Level 2 ab 50, Level 5 ab 800, Level 10 ab 4.050, Level 50 ab 120.050.
 */
final class SkillLevel {
    static final int MAX_LEVEL = 100;
    private static final long BASE = 50L;

    private SkillLevel() {
    }

    static int levelOf(long score) {
        if (score < BASE) {
            return 1;
        }
        int level = (int) Math.floor(Math.sqrt((double) score / BASE)) + 1;
        return Math.max(1, Math.min(MAX_LEVEL, level));
    }

    static long scoreForLevel(int level) {
        long steps = Math.max(0L, level - 1L);
        return BASE * steps * steps;
    }

    /** Punkte bis zum nächsten Level; 0 wenn {@link #MAX_LEVEL} erreicht ist. */
    static long scoreToNextLevel(long score) {
        int level = levelOf(score);
        if (level >= MAX_LEVEL) {
            return 0L;
        }
        return Math.max(0L, scoreForLevel(level + 1) - score);
    }

    /** Fortschritt im aktuellen Level als 0.0 bis 1.0. */
    static double progress(long score) {
        int level = levelOf(score);
        if (level >= MAX_LEVEL) {
            return 1.0d;
        }
        long start = scoreForLevel(level);
        long end = scoreForLevel(level + 1);
        if (end <= start) {
            return 1.0d;
        }
        double progress = (double) (score - start) / (double) (end - start);
        return Math.max(0.0d, Math.min(1.0d, progress));
    }
}
