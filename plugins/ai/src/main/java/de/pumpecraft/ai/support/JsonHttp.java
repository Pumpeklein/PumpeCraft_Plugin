package de.pumpecraft.ai.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Blockierender JSON-POST mit Bearer-Token; wird nur von den Executor-Threads genutzt. */
public final class JsonHttp {
    private final String service;
    private final Duration timeout;
    private final HttpClient http;

    public JsonHttp(String service, Duration timeout) {
        this.service = service;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public String post(String url, String apiKey, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            // Der Body enthält die Fehlerursache, aber niemals den Schlüssel - er steht nur im Header.
            throw new IOException(service + " answered with HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    public static String base(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
