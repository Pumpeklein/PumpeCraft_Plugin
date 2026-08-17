package de.pumpecraft.utils.messages;

/**
 * Liefert Vorlagen von außerhalb, etwa erzeugte Texte. Wird über {@link Messages#use} einmal
 * hinterlegt und gilt dann für jedes Plugin, das seine Meldungen über {@link Messages} rendert.
 */
public interface MessageSource {
    /** @return eine Vorlage oder {@code null} für die eigenen Texte des Themas */
    String next(MessageTopic topic);

    /**
     * Meldet ein Thema an, bevor es zum ersten Mal gebraucht wird. Eine Quelle, die ihre Texte
     * erst besorgen muss, hat damit Zeit dafür - sonst fiele die erste Meldung jedes Themas
     * zwangsläufig auf die eigenen Vorlagen zurück.
     */
    default void warmUp(MessageTopic topic) {
    }
}
