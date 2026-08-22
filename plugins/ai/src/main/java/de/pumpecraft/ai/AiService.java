package de.pumpecraft.ai;

import de.pumpecraft.ai.support.FailureCooldown;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Erzeugt Textzeilen über DeepSeek. Jeder Aufruf läuft auf einem eigenen Thread; der aufrufende
 * Plugin-Code darf niemals auf das Ergebnis warten, sondern arbeitet mit dem, was da ist.
 */
public final class AiService {
    private final AiSettings settings;
    private final DeepSeekClient client;
    private final ExecutorService executor;
    private final Logger logger;
    private final FailureCooldown cooldown;

    AiService(AiSettings settings, DeepSeekClient client, ExecutorService executor, Logger logger) {
        this.settings = settings;
        this.client = client;
        this.executor = executor;
        this.logger = logger;
        this.cooldown = new FailureCooldown(settings.failureCooldown());
    }

    /** @return {@code false}, solange kein Schlüssel gesetzt ist oder ein Fehlschlag nachwirkt */
    public boolean available() {
        return settings.usable() && !cooldown.active();
    }

    public String model() {
        return settings.model();
    }

    /**
     * @return die erzeugten Zeilen, im Fehlerfall eine leere Liste - der Aufrufer bleibt bei
     *     seinen eigenen Texten
     */
    public CompletableFuture<List<String>> lines(String instruction, int count) {
        if (!available()) {
            return CompletableFuture.completedFuture(List.of());
        }

        String prompt = instruction + "\n\nGib genau " + count
            + " verschiedene Meldungen aus, eine pro Zeile, ohne Nummerierung und ohne Anführungszeichen.";
        return CompletableFuture.supplyAsync(() -> request(prompt), executor);
    }

    private List<String> request(String prompt) {
        try {
            return TextLines.parse(client.complete(settings.systemPrompt(), prompt));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (IOException | RuntimeException exception) {
            cooldown.trip();
            logger.log(Level.WARNING, "DeepSeek request failed; falling back for "
                + cooldown.duration().toSeconds() + "s.", exception);
            return List.of();
        }
    }
}
