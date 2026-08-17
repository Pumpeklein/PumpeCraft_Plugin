package de.pumpecraft.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/** Blockierender HTTP-Aufruf der Chat-Completions-API; wird nur vom Executor genutzt. */
final class DeepSeekClient {
    private final AiSettings settings;
    private final HttpClient http;

    DeepSeekClient(AiSettings settings) {
        this.settings = settings;
        this.http = HttpClient.newBuilder()
            .connectTimeout(settings.requestTimeout())
            .build();
    }

    String complete(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(settings.baseUrl() + "/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + settings.apiKey())
            .timeout(settings.requestTimeout())
            .POST(HttpRequest.BodyPublishers.ofString(
                ChatPayload.request(settings, systemPrompt, userPrompt), StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            // Der Body enthält die Fehlerursache, aber niemals den Schlüssel - er steht nur im Header.
            throw new IOException("DeepSeek answered with HTTP " + response.statusCode() + ": " + response.body());
        }
        return ChatPayload.reply(response.body());
    }
}
