package de.pumpecraft.enchants;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;

public final class EnchantSettings {
    private final FileConfiguration config;

    public EnchantSettings(FileConfiguration config) {
        this.config = config;
    }

    public boolean enabled(NamespacedKey key) {
        return config.getBoolean(path(key, "enabled"), true);
    }

    /** A value that grows with the level; the fallbacks are listed level by level. */
    public int perLevel(NamespacedKey key, String option, int level, int... fallbacks) {
        int fallback = fallbacks[Math.min(Math.max(level, 1), fallbacks.length) - 1];
        return config.getInt(path(key, option) + ".level-" + level, fallback);
    }

    public double perLevel(NamespacedKey key, String option, int level, double... fallbacks) {
        double fallback = fallbacks[Math.min(Math.max(level, 1), fallbacks.length) - 1];
        return config.getDouble(path(key, option) + ".level-" + level, fallback);
    }

    public int amount(NamespacedKey key, String option, int fallback) {
        return config.getInt(path(key, option), fallback);
    }

    public double value(NamespacedKey key, String option, double fallback) {
        return config.getDouble(path(key, option), fallback);
    }

    public double value(String path, double fallback) {
        return config.getDouble(path, fallback);
    }

    public int anvilLevelCost() {
        return Math.max(1, config.getInt("anvil.level-cost", 5));
    }

    public int maxEnchantsPerItem() {
        return Math.max(1, config.getInt("anvil.max-enchants-per-item", 2));
    }

    private String path(NamespacedKey key, String option) {
        return "enchants." + key.getKey() + "." + option;
    }
}
