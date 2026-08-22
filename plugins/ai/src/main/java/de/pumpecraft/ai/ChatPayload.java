package de.pumpecraft.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Baut den Request-Body der Chat-Completions-API und holt die Antwort wieder heraus. */
final class ChatPayload {
    private ChatPayload() {
    }

    static String request(AiSettings settings, String systemPrompt, String userPrompt) {
        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt));
        messages.add(message("user", userPrompt));

        JsonObject body = new JsonObject();
        body.addProperty("model", settings.model());
        body.addProperty("temperature", settings.temperature());
        body.addProperty("max_tokens", settings.maxTokens());
        body.addProperty("stream", false);
        body.add("messages", messages);
        return body.toString();
    }

    static Completion reply(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root.has("error")) {
            throw new IllegalStateException("DeepSeek returned an error: " + errorMessage(root));
        }

        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("DeepSeek returned no choices.");
        }
        String content = choices.get(0).getAsJsonObject()
            .getAsJsonObject("message")
            .get("content")
            .getAsString();
        return new Completion(content, usage(root));
    }

    private static TokenUsage usage(JsonObject root) {
        JsonObject usage = root.getAsJsonObject("usage");
        if (usage == null) {
            return TokenUsage.NONE;
        }
        return new TokenUsage(
            number(usage, "prompt_tokens"),
            number(usage, "completion_tokens"),
            number(usage, "prompt_cache_hit_tokens")
        );
    }

    private static long number(JsonObject object, String member) {
        return object.has(member) && !object.get(member).isJsonNull() ? object.get(member).getAsLong() : 0L;
    }

    private static String errorMessage(JsonObject root) {
        JsonObject error = root.getAsJsonObject("error");
        return error.has("message") ? error.get("message").getAsString() : error.toString();
    }

    private static JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }
}
