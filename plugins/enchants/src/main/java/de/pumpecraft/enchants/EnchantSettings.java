package de.pumpecraft.enchants;

import org.bukkit.configuration.file.FileConfiguration;

public record EnchantSettings(
    boolean telekinesisEnabled,
    boolean furnaceEnabled,
    boolean featherweightEnabled,
    double featherweightLevelOneDistance,
    double featherweightLevelTwoDistance,
    int anvilLevelCost
) {
    static EnchantSettings from(FileConfiguration config) {
        return new EnchantSettings(
            config.getBoolean("enchants.telekinesis.enabled", true),
            config.getBoolean("enchants.furnace.enabled", true),
            config.getBoolean("enchants.featherweight.enabled", true),
            Math.max(0.0, config.getDouble(
                "enchants.featherweight.safe-fall-distance.level-1", 6.0)),
            Math.max(0.0, config.getDouble(
                "enchants.featherweight.safe-fall-distance.level-2", 12.0)),
            Math.max(0, config.getInt("anvil.level-cost", 5))
        );
    }
}
