package de.pumpecraft.ai;

import de.pumpecraft.ai.support.JsonHttp;
import java.io.IOException;

/** Blockierender HTTP-Aufruf der Chat-Completions-API; wird nur vom Executor genutzt. */
final class DeepSeekClient {
    private final AiSettings settings;
    private final JsonHttp http;

    DeepSeekClient(AiSettings settings) {
        this.settings = settings;
        this.http = new JsonHttp("DeepSeek", settings.requestTimeout());
    }

    Completion complete(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        String response = http.post(
            settings.baseUrl() + "/chat/completions",
            settings.apiKey(),
            ChatPayload.request(settings, systemPrompt, userPrompt)
        );
        return ChatPayload.reply(response);
    }
}
