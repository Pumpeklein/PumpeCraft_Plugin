package de.pumpecraft.enchants;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;

public final class EnchantSettings {
    private final FileConfiguration config;

    public EnchantSettings(FileConfiguration config) {
        this.config = config;
    }

    public boolean enabled(NamespacedKey key) {
        return config.getBoolean("enchants." + key.getKey() + ".enabled", true);
    }

    public double featherweightDistance(int level) {
        double fallback = level >= 2 ? 12.0 : 6.0;
        return Math.max(0.0, config.getDouble(
            "enchants.featherweight.safe-fall-distance.level-" + level, fallback));
    }

    public int anvilLevelCost() {
        return Math.max(1, config.getInt("anvil.level-cost", 5));
    }

    public int maxEnchantsPerItem() {
        return Math.max(1, config.getInt("anvil.max-enchants-per-item", 2));
    }
}
