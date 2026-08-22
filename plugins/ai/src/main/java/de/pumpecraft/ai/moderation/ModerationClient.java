package de.pumpecraft.ai.moderation;

import de.pumpecraft.ai.support.JsonHttp;
import java.io.IOException;

/** Blockierender HTTP-Aufruf des Moderations-Endpunkts; wird nur vom Executor genutzt. */
final class ModerationClient {
    private final ModerationSettings settings;
    private final JsonHttp http;

    ModerationClient(ModerationSettings settings) {
        this.settings = settings;
        this.http = new JsonHttp("OpenAI", settings.requestTimeout());
    }

    ModerationScores inspect(String text) throws IOException, InterruptedException {
        String response = http.post(
            settings.baseUrl() + "/moderations",
            settings.apiKey(),
            ModerationPayload.request(settings.model(), text)
        );
        return ModerationPayload.parse(response);
    }
}
