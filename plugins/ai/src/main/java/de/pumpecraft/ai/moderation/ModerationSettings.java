package de.pumpecraft.ai.moderation;

import de.pumpecraft.ai.support.JsonHttp;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;

record ModerationSettings(
    boolean enabled,
    String apiKey,
    String baseUrl,
    String model,
    Duration requestTimeout,
    Duration failureCooldown,
    int maxCharacters,
    double defaultThreshold,
    Map<String, Double> thresholds,
    double defaultHoldThreshold,
    Map<String, Double> holdThresholds,
    Set<String> ignoredCategories
) {
    private static final String API_KEY_ENVIRONMENT = "OPENAI_API_KEY";

    static ModerationSettings from(ConfigurationSection section) {
        return new ModerationSettings(
            section == null || section.getBoolean("enabled", true),
            apiKey(section),
            JsonHttp.base(string(section, "base-url", "https://api.openai.com/v1")),
            string(section, "model", "omni-moderation-latest"),
            Duration.ofSeconds(section == null ? 5L : section.getLong("request-timeout-seconds", 5L)),
            Duration.ofSeconds(section == null ? 120L : section.getLong("failure-cooldown-seconds", 120L)),
            section == null ? 500 : section.getInt("max-characters", 500),
            section == null ? 0.3D : section.getDouble("default-threshold", 0.3D),
            thresholds(section, "thresholds"),
            section == null ? 0.7D : section.getDouble("default-hold-threshold", 0.7D),
            thresholds(section, "hold-thresholds"),
            ignored(section)
        );
    }

    boolean usable() {
        return enabled && !apiKey.isBlank();
    }

    double thresholdFor(String category) {
        return thresholds.getOrDefault(category, defaultThreshold);
    }

    /** Nie unter der ersten Schwelle: sonst waere ein Treffer schwer, ohne einer zu sein. */
    double holdThresholdFor(String category) {
        return Math.max(holdThresholds.getOrDefault(category, defaultHoldThreshold), thresholdFor(category));
    }

    boolean ignored(String category) {
        return ignoredCategories.contains(category);
    }

    private static Map<String, Double> thresholds(ConfigurationSection section, String path) {
        ConfigurationSection values = section == null ? null : section.getConfigurationSection(path);
        if (values == null) {
            return Map.of();
        }
        Map<String, Double> thresholds = new HashMap<>();
        for (String key : values.getKeys(false)) {
            thresholds.put(key.toLowerCase(Locale.ROOT), values.getDouble(key));
        }
        return Map.copyOf(thresholds);
    }

    private static Set<String> ignored(ConfigurationSection section) {
        if (section == null) {
            return Set.of();
        }
        return section.getStringList("ignored-categories").stream()
            .map(entry -> entry.toLowerCase(Locale.ROOT).trim())
            .filter(entry -> !entry.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }

    private static String apiKey(ConfigurationSection section) {
        String configured = string(section, "api-key", "").trim();
        if (!configured.isEmpty()) {
            return configured;
        }
        String fromEnvironment = System.getenv(API_KEY_ENVIRONMENT);
        return fromEnvironment == null ? "" : fromEnvironment.trim();
    }

    private static String string(ConfigurationSection section, String path, String fallback) {
        String value = section == null ? null : section.getString(path, fallback);
        return value == null || value.isBlank() ? fallback : value;
    }
}
