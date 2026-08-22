package de.pumpecraft.ai.moderation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;

/** Baut den Request-Body des Moderations-Endpunkts und liest die Bewertungen wieder heraus. */
final class ModerationPayload {
    private ModerationPayload() {
    }

    static String request(String model, String text) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("input", text);
        return body.toString();
    }

    static ModerationScores parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root.has("error")) {
            throw new IllegalStateException("OpenAI returned an error: " + errorMessage(root));
        }

        JsonArray results = root.getAsJsonArray("results");
        if (results == null || results.isEmpty()) {
            throw new IllegalStateException("OpenAI returned no moderation results.");
        }

        JsonObject result = results.get(0).getAsJsonObject();
        JsonObject categoryScores = result.getAsJsonObject("category_scores");
        Map<String, Double> values = new LinkedHashMap<>();
        if (categoryScores != null) {
            categoryScores.entrySet().forEach(entry -> values.put(entry.getKey(), entry.getValue().getAsDouble()));
        }
        return new ModerationScores(result.get("flagged").getAsBoolean(), Map.copyOf(values));
    }

    private static String errorMessage(JsonObject root) {
        JsonObject error = root.getAsJsonObject("error");
        return error.has("message") ? error.get("message").getAsString() : error.toString();
    }
}
