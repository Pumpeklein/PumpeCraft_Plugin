package de.pumpecraft.ai;

import de.pumpecraft.utils.messages.MessageSource;
import de.pumpecraft.utils.messages.MessageTopic;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vorrat an erzeugten Meldungen, getrennt nach Thema. Eine Meldung wird nie erst im Ereignis
 * angefordert - das würde den Server-Thread an eine HTTP-Antwort hängen. Stattdessen wird der
 * Vorrat im Hintergrund nachgefüllt und geliefert, was gerade da ist; sonst {@code null}, dann
 * greifen die Vorlagen des Themas.
 */
final class AiMessagePool implements MessageSource {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[a-zA-Z]+}");
    private static final int MAX_LENGTH = 140;

    private final AiService ai;
    private final MessageSettings settings;
    private final AiMessageStore store;
    private final Map<String, Deque<String>> pools = new ConcurrentHashMap<>();
    private final Set<String> refilling = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> pausedUntil = new ConcurrentHashMap<>();

    AiMessagePool(AiService ai, MessageSettings settings, AiMessageStore store) {
        this.ai = ai;
        this.settings = settings;
        this.store = store;
        store.load().forEach((key, lines) -> pools.put(key, new ConcurrentLinkedDeque<>(lines)));
    }

    @Override
    public String next(MessageTopic topic) {
        if (!usable(topic)) {
            return null;
        }

        Deque<String> pool = pool(topic);
        if (pool.size() < settings.refillBelow()) {
            refill(topic, pool);
        }
        return pool.poll();
    }

    @Override
    public void warmUp(MessageTopic topic) {
        if (!settings.warmUp() || !usable(topic)) {
            return;
        }
        Deque<String> pool = pool(topic);
        if (pool.size() >= settings.refillBelow()) {
            return;
        }
        refill(topic, pool);
    }

    void save() {
        store.save(pools);
    }

    int stored(MessageTopic topic) {
        Deque<String> pool = pools.get(topic.key());
        return pool == null ? 0 : pool.size();
    }

    /** @return Zeilen im Vorrat und Themen, für die schon einmal nachgefüllt wurde */
    String summary() {
        int lines = pools.values().stream().mapToInt(Deque::size).sum();
        return lines + " Zeilen in " + pools.size() + " Themen";
    }

    private boolean usable(MessageTopic topic) {
        return settings.enabled() && ai.available() && !settings.excluded(topic);
    }

    private Deque<String> pool(MessageTopic topic) {
        return pools.computeIfAbsent(topic.key(), key -> new ConcurrentLinkedDeque<>());
    }

    private void refill(MessageTopic topic, Deque<String> pool) {
        Long paused = pausedUntil.get(topic.key());
        if (paused != null && System.currentTimeMillis() < paused) {
            return;
        }
        if (!refilling.add(topic.key())) {
            return;
        }

        Set<String> allowed = TopicPrompt.placeholders(topic);
        Set<String> required = TopicPrompt.required(topic);
        ai.lines(TopicPrompt.instruction(topic), settings.batchSize())
            .thenAccept(lines -> accept(topic, pool, lines, allowed, required))
            .whenComplete((ignored, error) -> refilling.remove(topic.key()));
    }

    // Eine Antwort ohne brauchbare Zeile pausiert das Thema. Ohne das kostet ein Thema, dessen
    // Zeilen immer durchfallen, bei jeder einzelnen Meldung eine weitere Anfrage.
    private void accept(
        MessageTopic topic,
        Deque<String> pool,
        List<String> lines,
        Set<String> allowed,
        Set<String> required
    ) {
        List<String> accepted = lines.stream()
            .filter(line -> usable(line, allowed, required))
            .toList();
        pool.addAll(accepted);
        if (accepted.isEmpty()) {
            pausedUntil.put(topic.key(), System.currentTimeMillis() + settings.retryCooldown().toMillis());
        }
    }

    private boolean usable(String line, Set<String> allowed, Set<String> required) {
        if (line.length() > MAX_LENGTH || !line.contains(" ")) {
            return false;
        }

        for (String placeholder : required) {
            if (!line.contains(placeholder)) {
                return false;
            }
        }

        Matcher matcher = PLACEHOLDER.matcher(line);
        while (matcher.find()) {
            if (!allowed.contains(matcher.group())) {
                return false;
            }
        }
        return true;
    }
}
