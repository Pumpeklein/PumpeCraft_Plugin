package de.pumpecraft.ai;

import de.pumpecraft.utils.messages.MessageSource;
import de.pumpecraft.utils.messages.MessageTopic;
import java.util.Deque;
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
    private static final int BATCH_SIZE = 10;
    private static final int REFILL_BELOW = 3;
    private static final int MAX_LENGTH = 140;

    private final AiService ai;
    private final Map<String, Deque<String>> pools = new ConcurrentHashMap<>();
    private final Set<String> refilling = ConcurrentHashMap.newKeySet();

    AiMessagePool(AiService ai) {
        this.ai = ai;
    }

    @Override
    public String next(MessageTopic topic) {
        if (!ai.available()) {
            return null;
        }

        Deque<String> pool = pools.computeIfAbsent(topic.key(), key -> new ConcurrentLinkedDeque<>());
        if (pool.size() < REFILL_BELOW) {
            refill(topic, pool);
        }
        return pool.poll();
    }

    @Override
    public void warmUp(MessageTopic topic) {
        if (!ai.available()) {
            return;
        }
        refill(topic, pools.computeIfAbsent(topic.key(), key -> new ConcurrentLinkedDeque<>()));
    }

    /** @return Zeilen im Vorrat und Themen, für die schon einmal nachgefüllt wurde */
    String summary() {
        int lines = pools.values().stream().mapToInt(Deque::size).sum();
        return lines + " Zeilen in " + pools.size() + " Themen";
    }

    private void refill(MessageTopic topic, Deque<String> pool) {
        if (!refilling.add(topic.key())) {
            return;
        }

        Set<String> allowed = TopicPrompt.placeholders(topic);
        Set<String> required = TopicPrompt.required(topic);
        ai.lines(TopicPrompt.instruction(topic), BATCH_SIZE)
            .thenAccept(lines -> lines.stream()
                .filter(line -> usable(line, allowed, required))
                .forEach(pool::add))
            .whenComplete((ignored, error) -> refilling.remove(topic.key()));
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
