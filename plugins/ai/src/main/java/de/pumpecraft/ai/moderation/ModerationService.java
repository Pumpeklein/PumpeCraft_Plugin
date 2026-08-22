package de.pumpecraft.ai.moderation;

import de.pumpecraft.ai.support.DaemonThreads;
import de.pumpecraft.ai.support.FailureCooldown;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Prüft Texte über den Moderations-Endpunkt von OpenAI. Der Endpunkt kostet nichts, braucht aber
 * einen eigenen Schlüssel - der Schlüssel für die Textgenerierung passt dort nicht.
 *
 * <p>Jede Prüfung läuft auf einem eigenen Thread-Pool, getrennt von der Textgenerierung: eine
 * Anfrage, auf die der Chat wartet, darf nicht hinter minutenlangen Nachschub-Anfragen hängen.
 */
public final class ModerationService {
    private static final int REQUEST_THREADS = 4;

    private final ModerationSettings settings;
    private final ModerationClient client;
    private final ExecutorService executor;
    private final FailureCooldown cooldown;
    private final Logger logger;

    private ModerationService(ModerationSettings settings, Logger logger) {
        this.settings = settings;
        this.client = new ModerationClient(settings);
        this.executor = Executors.newFixedThreadPool(REQUEST_THREADS, new DaemonThreads("PumpeAI-Moderation"));
        this.cooldown = new FailureCooldown(settings.failureCooldown());
        this.logger = logger;
    }

    public static ModerationService create(ConfigurationSection section, Logger logger) {
        return new ModerationService(ModerationSettings.from(section), logger);
    }

    /** @return {@code false}, solange kein Schlüssel gesetzt ist oder ein Fehlschlag nachwirkt */
    public boolean available() {
        return settings.usable() && !cooldown.active();
    }

    public boolean configured() {
        return !settings.apiKey().isBlank();
    }

    public boolean enabled() {
        return settings.enabled();
    }

    public String model() {
        return settings.model();
    }

    /**
     * @return das Urteil, im Fehlerfall {@link ModerationVerdict#CLEAN} - eine ausgefallene Prüfung
     *     darf niemals dazu führen, dass Nachrichten verschwinden
     */
    public CompletableFuture<ModerationVerdict> inspect(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty() || !available()) {
            return CompletableFuture.completedFuture(ModerationVerdict.CLEAN);
        }

        String input = trimmed.length() > settings.maxCharacters()
            ? trimmed.substring(0, settings.maxCharacters())
            : trimmed;
        return CompletableFuture.supplyAsync(() -> request(input), executor);
    }

    public void shutdown() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(2L, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private ModerationVerdict request(String text) {
        try {
            return ModerationRules.judge(client.inspect(text), settings);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ModerationVerdict.CLEAN;
        } catch (IOException | RuntimeException exception) {
            cooldown.trip();
            logger.log(Level.WARNING, "Moderation request failed; skipping checks for "
                + cooldown.duration().toSeconds() + "s.", exception);
            return ModerationVerdict.CLEAN;
        }
    }
}
