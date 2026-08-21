package de.pumpecraft.subessentials;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

record TwitchSettings(
    String linkUrl,
    long linkLifetimeMillis,
    String clientId,
    String authToken,
    String channelLogin,
    long subscriptionRefreshTicks,
    long databasePollTicks
) {
    static TwitchSettings load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new TwitchSettings(
            config.getString("website.twitch-link-url", "https://support.pumpe-klein.de/auth/twitch/minecraft"),
            Math.max(1L, config.getLong("website.link-valid-minutes", 10L)) * 60_000L,
            environmentOrConfig("TWITCH_CLIENT_ID", config.getString("twitch.client-id", "")),
            normalizeToken(environmentOrConfig("TWITCH_AUTH_TOKEN", config.getString("twitch.auth-token", ""))),
            environmentOrConfig("TWITCH_USER_LOGIN", config.getString("twitch.channel-login", "")),
            Math.max(1L, config.getLong("twitch.subscription-refresh-minutes", 5L)) * 60L * 20L,
            Math.max(5L, config.getLong("cache.database-poll-seconds", 5L)) * 20L
        );
    }

    boolean apiConfigured() {
        return !clientId.isBlank() && !authToken.isBlank() && !channelLogin.isBlank();
    }

    private static String environmentOrConfig(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback.trim() : value.trim();
    }

    private static String normalizeToken(String token) {
        String trimmed = token.trim();
        return trimmed.regionMatches(true, 0, "oauth:", 0, 6) ? trimmed.substring(6) : trimmed;
    }
}
