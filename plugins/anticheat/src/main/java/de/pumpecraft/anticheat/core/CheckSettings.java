package de.pumpecraft.anticheat.core;

import de.pumpecraft.anticheat.platform.BedrockDetector;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Single entry point for {@code checks.<key>.<setting>} lookups. Settings ending in
 * {@code -java} / {@code -bedrock} are resolved per player, because Geyser clients move and
 * click differently enough that shared thresholds produce false positives.
 */
public final class CheckSettings {
    private final Plugin plugin;
    private final BedrockDetector bedrockDetector;

    public CheckSettings(Plugin plugin, BedrockDetector bedrockDetector) {
        this.plugin = plugin;
        this.bedrockDetector = bedrockDetector;
    }

    public boolean isBedrock(Player player) {
        return bedrockDetector.isBedrock(player.getUniqueId());
    }

    public boolean bedrockApiAvailable() {
        return bedrockDetector.isAvailable();
    }

    public String platform(Player player) {
        return isBedrock(player) ? "Bedrock" : "Java";
    }

    public boolean enabled(CheckType check) {
        return bool(check, "enabled", true);
    }

    public double alertLevel(CheckType check) {
        return decimal(check, "alert-level", 1.0);
    }

    public boolean hasCancelLevel(CheckType check) {
        return plugin.getConfig().contains(path(check, "cancel-level"));
    }

    public double cancelLevel(CheckType check) {
        return decimal(check, "cancel-level", Double.MAX_VALUE);
    }

    public boolean bool(CheckType check, String setting, boolean fallback) {
        return plugin.getConfig().getBoolean(path(check, setting), fallback);
    }

    public int integer(CheckType check, String setting, int fallback) {
        return plugin.getConfig().getInt(path(check, setting), fallback);
    }

    public long duration(CheckType check, String setting, long fallback) {
        return plugin.getConfig().getLong(path(check, setting), fallback);
    }

    public double decimal(CheckType check, String setting, double fallback) {
        return plugin.getConfig().getDouble(path(check, setting), fallback);
    }

    public List<String> strings(CheckType check, String setting) {
        return plugin.getConfig().getStringList(path(check, setting));
    }

    public ConfigurationSection section(CheckType check) {
        return plugin.getConfig().getConfigurationSection("checks." + check.configKey());
    }

    public int platformInteger(Player player, CheckType check, String setting, int fallback) {
        return plugin.getConfig().getInt(platformPath(player, check, setting), fallback);
    }

    public double platformDecimal(Player player, CheckType check, String setting, double fallback) {
        return plugin.getConfig().getDouble(platformPath(player, check, setting), fallback);
    }

    public long platformDuration(Player player, CheckType check, String setting, long fallback) {
        return plugin.getConfig().getLong(platformPath(player, check, setting), fallback);
    }

    private String platformPath(Player player, CheckType check, String setting) {
        return path(check, setting + (isBedrock(player) ? "-bedrock" : "-java"));
    }

    private String path(CheckType check, String setting) {
        return "checks." + check.configKey() + "." + setting;
    }
}
