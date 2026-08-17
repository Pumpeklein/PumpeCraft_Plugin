package de.pumpecraft.utils.messages;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Rendert Meldungen aus einem {@link MessageTopic}. Ist eine {@link MessageSource} hinterlegt,
 * kommt der Text von dort; liefert sie nichts, gelten die Vorlagen des Themas. Die Rotation gilt
 * über alle Themen hinweg, damit sich zwei Meldungen hintereinander nie gleichen.
 */
public final class Messages {
    private static final MessageRotation ROTATION = new MessageRotation();
    private static final Map<String, MessageTopic> REGISTERED = new ConcurrentHashMap<>();
    private static volatile MessageSource source;

    private Messages() {
    }

    /** Hinterlegt eine Quelle für Vorlagen; {@code null} schaltet zurück auf die eigenen Texte. */
    public static void use(MessageSource newSource) {
        source = newSource;
        if (newSource != null) {
            REGISTERED.values().forEach(newSource::warmUp);
        }
    }

    /**
     * Meldet Themen an, sobald das Plugin startet. Jedes Thema, das der Server im Chat zeigt,
     * gehört hierher: Eine Quelle, die ihre Texte erst besorgen muss, kann sonst erst ab der
     * zweiten Meldung eines Themas liefern.
     */
    public static void register(MessageTopic... topics) {
        MessageSource current = source;
        for (MessageTopic topic : topics) {
            if (REGISTERED.putIfAbsent(topic.key(), topic) == null && current != null) {
                current.warmUp(topic);
            }
        }
    }

    public static Collection<MessageTopic> registered() {
        return List.copyOf(REGISTERED.values());
    }

    public static String template(MessageTopic topic) {
        REGISTERED.putIfAbsent(topic.key(), topic);

        MessageSource current = source;
        String provided = current == null ? null : current.next(topic);
        return provided == null ? ROTATION.next(topic.fallbacks()) : provided;
    }

    public static Component render(MessageTopic topic, NamedTextColor color, String playerName) {
        return render(topic, color, Map.of("player", playerName));
    }

    public static Component render(MessageTopic topic, NamedTextColor color, Map<String, String> values) {
        String text = template(topic);
        for (Map.Entry<String, String> value : values.entrySet()) {
            text = text.replace("{" + value.getKey() + "}", value.getValue());
        }
        return Component.text(text, color);
    }
}
