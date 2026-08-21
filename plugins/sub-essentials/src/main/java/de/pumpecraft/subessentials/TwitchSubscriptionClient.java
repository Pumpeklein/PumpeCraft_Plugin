package de.pumpecraft.subessentials;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

final class TwitchSubscriptionClient {
    private static final Pattern FIRST_ID = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern EMPTY_DATA = Pattern.compile("\\\"data\\\"\\s*:\\s*\\[\\s*]", Pattern.DOTALL);

    private final TwitchSettings settings;
    private final Logger logger;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(8))
        .build();
    private volatile String broadcasterId;

    TwitchSubscriptionClient(TwitchSettings settings, Logger logger) {
        this.settings = settings;
        this.logger = logger;
    }

    Optional<Boolean> isSubscriber(String twitchUserId) {
        if (!settings.apiConfigured()) {
            return Optional.empty();
        }
        try {
            String channelId = broadcasterId;
            if (channelId == null) {
                channelId = resolveBroadcasterId();
                if (channelId == null) return Optional.empty();
                broadcasterId = channelId;
            }

            String query = "broadcaster_id=" + encode(channelId) + "&user_id=" + encode(twitchUserId);
            HttpResponse<String> response = send("https://api.twitch.tv/helix/subscriptions?" + query);
            if (response.statusCode() == 200) {
                return Optional.of(!EMPTY_DATA.matcher(response.body()).find());
            }
            logger.warning("Twitch subscription request failed with HTTP " + response.statusCode() + ".");
        } catch (IOException exception) {
            logger.warning("Twitch subscription request failed: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        return Optional.empty();
    }

    private String resolveBroadcasterId() throws IOException, InterruptedException {
        HttpResponse<String> response = send(
            "https://api.twitch.tv/helix/users?login=" + encode(settings.channelLogin())
        );
        if (response.statusCode() != 200) {
            logger.warning("Twitch broadcaster lookup failed with HTTP " + response.statusCode() + ".");
            return null;
        }
        Matcher matcher = FIRST_ID.matcher(response.body());
        if (!matcher.find()) {
            logger.warning("Twitch broadcaster was not found for the configured login.");
            return null;
        }
        return matcher.group(1);
    }

    private HttpResponse<String> send(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(12))
            .header("Client-ID", settings.clientId())
            .header("Authorization", "Bearer " + settings.authToken())
            .header("Accept", "application/json")
            .GET()
            .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
