package de.pumpecraft.ai;

import de.pumpecraft.utils.Texts;
import java.util.concurrent.atomic.AtomicLong;

/** Zählt mit, was seit dem Serverstart an DeepSeek gegangen ist. */
final class AiUsage {
    private final AtomicLong requests = new AtomicLong();
    private final AtomicLong promptTokens = new AtomicLong();
    private final AtomicLong completionTokens = new AtomicLong();
    private final AtomicLong cacheHitTokens = new AtomicLong();

    void record(TokenUsage usage) {
        requests.incrementAndGet();
        promptTokens.addAndGet(usage.promptTokens());
        completionTokens.addAndGet(usage.completionTokens());
        cacheHitTokens.addAndGet(usage.cacheHitTokens());
    }

    String summary() {
        long count = requests.get();
        long total = promptTokens.get() + completionTokens.get();
        if (count == 0L) {
            return "keine Anfrage seit dem Start";
        }
        return Texts.number(count) + " Anfragen, " + Texts.number(total) + " Token"
            + " (" + Texts.number(cacheHitTokens.get()) + " davon aus dem Cache, "
            + Texts.number(total / count) + " je Anfrage)";
    }
}
