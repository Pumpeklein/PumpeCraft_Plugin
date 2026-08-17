package de.pumpecraft.ai;

import de.pumpecraft.utils.messages.MessageTopic;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Baut aus einem Thema die Aufgabe für das Modell. Die erlaubten Platzhalter werden aus den
 * Vorlagen des Themas abgeleitet: Was das Plugin selbst benutzt, darf auch das Modell benutzen -
 * so muss niemand eine zweite Tabelle pflegen.
 */
final class TopicPrompt {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[a-zA-Z]+}");
    private static final int EXAMPLE_COUNT = 3;

    private TopicPrompt() {
    }

    static String instruction(MessageTopic topic) {
        return topic.task()
            + "\nJede Meldung muss diese Platzhalter enthalten: " + String.join(", ", required(topic))
            + ". Zusätzlich erlaubt sind: " + String.join(", ", placeholders(topic))
            + ". Andere Platzhalter darfst du nicht erfinden."
            + "\nSo klingen unsere bisherigen Meldungen dieser Sorte:\n- "
            + String.join("\n- ", examples(topic))
            + "\nSchreibe neue Meldungen im selben Ton, aber mit anderen Bildern und Pointen.";
    }

    /** Alle Platzhalter, die in irgendeiner Vorlage des Themas vorkommen. */
    static Set<String> placeholders(MessageTopic topic) {
        Set<String> placeholders = new LinkedHashSet<>();
        for (String fallback : topic.fallbacks()) {
            placeholders.addAll(placeholdersIn(fallback));
        }
        return Set.copyOf(placeholders);
    }

    /**
     * Die Platzhalter, die in jeder Vorlage stehen. Sie tragen die Information der Meldung - eine
     * Trader-Meldung ohne Ort oder eine Todesmeldung ohne Namen wäre wertlos.
     */
    static Set<String> required(MessageTopic topic) {
        Set<String> required = null;
        for (String fallback : topic.fallbacks()) {
            Set<String> current = placeholdersIn(fallback);
            if (required == null) {
                required = current;
            } else {
                required.retainAll(current);
            }
        }
        return required == null ? Set.of() : Set.copyOf(required);
    }

    private static Set<String> placeholdersIn(String template) {
        Set<String> placeholders = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            placeholders.add(matcher.group());
        }
        return placeholders;
    }

    private static List<String> examples(MessageTopic topic) {
        return topic.fallbacks().stream().limit(EXAMPLE_COUNT).toList();
    }
}
