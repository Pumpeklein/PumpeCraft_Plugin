package de.pumpecraft.utils.messages;

import java.util.List;

/**
 * Eine Sorte Meldung. {@code key} benennt sie gegenüber der Textquelle, {@code task} beschreibt
 * ihr die Aufgabe, {@code fallbacks} sind die eigenen Vorlagen.
 *
 * <p>Die Vorlagen sind Rückfallebene und Stilvorgabe zugleich: Aus ihnen liest eine Quelle ab,
 * wie eine Meldung dieser Sorte klingt und welche Platzhalter darin vorkommen dürfen. Deshalb
 * gehört zu jedem Thema mindestens eine Vorlage mit jedem Platzhalter, den es kennt.
 */
public record MessageTopic(String key, String task, List<String> fallbacks) {
    public MessageTopic {
        fallbacks = List.copyOf(fallbacks);
    }

    public static MessageTopic of(String key, String task, String... fallbacks) {
        return new MessageTopic(key, task, List.of(fallbacks));
    }
}
