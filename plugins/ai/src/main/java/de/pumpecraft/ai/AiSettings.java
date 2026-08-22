package de.pumpecraft.ai;

import de.pumpecraft.ai.support.JsonHttp;
import java.time.Duration;
import org.bukkit.configuration.file.FileConfiguration;

record AiSettings(
    boolean enabled,
    String apiKey,
    String baseUrl,
    String model,
    String systemPrompt,
    double temperature,
    int maxTokens,
    Duration requestTimeout,
    Duration failureCooldown
) {
    private static final String API_KEY_ENVIRONMENT = "DEEPSEEK_API_KEY";
    private static final String DEFAULT_SYSTEM_PROMPT = """
        Du bist der Stimmungsmacher des deutschen Minecraft-Servers PumpeCraft und schreibst kurze \
        Servermeldungen. Ton: frech, provokant und witzig, mit einem Augenzwinkern - aber niemals \
        beleidigend. Verboten sind Beschimpfungen, Vulgärsprache und alles zu Aussehen, Herkunft, \
        Familie, Religion, Politik oder echten Personen. Du ziehst die Situation auf, nie den \
        Menschen dahinter. Regeln: eine Meldung pro Zeile, höchstens 100 Zeichen, ein Satz \
        (maximal zwei kurze), keine Emojis, keine Anführungszeichen, keine Nummerierung, keine \
        Farbcodes, kein Markdown. Platzhalter in geschweiften Klammern wie {player} übernimmst du \
        wörtlich und übersetzt sie nie.""";

    static AiSettings from(FileConfiguration config) {
        return new AiSettings(
            config.getBoolean("enabled", true),
            apiKey(config),
            JsonHttp.base(config.getString("base-url", "https://api.deepseek.com")),
            config.getString("model", "deepseek-chat"),
            systemPrompt(config),
            config.getDouble("temperature", 1.3D),
            config.getInt("max-tokens", 900),
            Duration.ofSeconds(config.getLong("request-timeout-seconds", 20L)),
            Duration.ofSeconds(config.getLong("failure-cooldown-seconds", 180L))
        );
    }

    boolean usable() {
        return enabled && !apiKey.isBlank();
    }

    private static String apiKey(FileConfiguration config) {
        String configured = config.getString("api-key", "").trim();
        if (!configured.isEmpty()) {
            return configured;
        }

        String fromEnvironment = System.getenv(API_KEY_ENVIRONMENT);
        return fromEnvironment == null ? "" : fromEnvironment.trim();
    }

    private static String systemPrompt(FileConfiguration config) {
        String configured = config.getString("system-prompt", "").trim();
        return configured.isEmpty() ? DEFAULT_SYSTEM_PROMPT : configured;
    }
}
