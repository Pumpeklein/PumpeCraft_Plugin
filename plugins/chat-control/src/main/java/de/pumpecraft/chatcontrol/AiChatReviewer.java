package de.pumpecraft.chatcontrol;

import de.pumpecraft.ai.Ai;
import de.pumpecraft.ai.moderation.ModerationService;
import de.pumpecraft.ai.moderation.ModerationVerdict;
import de.pumpecraft.utils.Texts;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

final class AiChatReviewer implements ChatReviewer {
    private static final int REASON_LIMIT = 255;

    private final ModerationService service;
    private final boolean blockInstead;
    private final long maxWaitMillis;

    private AiChatReviewer(ModerationService service, boolean blockInstead, long maxWaitMillis) {
        this.service = service;
        this.blockInstead = blockInstead;
        this.maxWaitMillis = maxWaitMillis;
    }

    static ChatReviewer create(Plugin plugin, ConfigurationSection config) {
        ModerationService service = Ai.moderation(plugin);
        if (service == null) {
            return ChatReviewer.none();
        }
        String action = config == null ? "review" : config.getString("action", "review");
        long maxWait = config == null ? 1200L : config.getLong("max-wait-millis", 1200L);
        return new AiChatReviewer(
            service,
            "block".equals(action.toLowerCase(Locale.ROOT).trim()),
            Math.max(100L, maxWait)
        );
    }

    @Override
    public boolean active() {
        return service.available();
    }

    @Override
    public FilterResult inspect(String message) {
        if (!active()) {
            return FilterResult.allow();
        }
        try {
            return review(message).get(maxWaitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return FilterResult.allow();
        } catch (ExecutionException | TimeoutException exception) {
            return FilterResult.allow();
        }
    }

    @Override
    public CompletableFuture<FilterResult> review(String message) {
        if (!active()) {
            return CompletableFuture.completedFuture(FilterResult.allow());
        }
        return service.inspect(message).thenApply(this::result);
    }

    private FilterResult result(ModerationVerdict verdict) {
        return switch (verdict.severity()) {
            case NONE -> FilterResult.allow();
            case LOW -> FilterResult.mark(reason(verdict));
            case HIGH -> blockInstead
                ? FilterResult.block(Texts.truncate(
                    "Die automatische Prüfung hat " + verdict.label() + " erkannt.", REASON_LIMIT))
                : FilterResult.hold(reason(verdict));
        };
    }

    private String reason(ModerationVerdict verdict) {
        return Texts.truncate(
            "Automatische Prüfung: " + verdict.label() + " (" + Texts.percent(verdict.score()) + ")",
            REASON_LIMIT);
    }
}
