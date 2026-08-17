package de.pumpecraft.utils.messages;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Meldungen für Betreten und Verlassen des Servers. Sie liegen hier und nicht im Plugin, das die
 * Events verdrahtet, weil der Vanish aus {@code PumpeMod} dasselbe Verlassen vortäuscht - eine
 * eigene Formulierung dort würde einen versteckten Teamler sofort verraten.
 */
public final class ConnectionMessages {
    public static final MessageTopic JOIN = MessageTopic.of(
        "connection-join",
        "Schreibe Meldungen für einen Spieler, der den Server betritt.",
        "{player} ist da. Die Ruhe war schön, solange sie hielt.",
        "{player} hat sich eingeloggt und bringt vermutlich Chaos mit.",
        "{player} betritt den Server. Bitte Wertsachen sichern.",
        "{player} ist online. Der Serverchat atmet tief durch.",
        "{player} hat den Weg zurückgefunden. Erstaunlich.",
        "{player} ist wieder da und tut so, als wäre nichts gewesen.",
        "{player} loggt ein. Plan für heute: vermutlich keiner.",
        "{player} erscheint. Applaus bleibt optional.",
        "{player} ist eingetroffen. Der Countdown bis zum ersten Tod läuft.",
        "{player} hat sich zurückgemeldet. Die Baustelle wartet schon.",
        "{player} ist online und hat garantiert wieder Ideen.",
        "{player} betritt die Welt. Die Welt bleibt gelassen."
    );

    public static final MessageTopic FIRST_JOIN = MessageTopic.of(
        "connection-first-join",
        "Schreibe Willkommensmeldungen für einen Spieler, der zum allerersten Mal auf den Server"
            + " kommt. Etwas freundlicher als sonst, aber nicht kitschig.",
        "{player} ist zum ersten Mal da. Kurz nett sein, danach wie immer.",
        "{player} hat den Server frisch entdeckt. Willkommen im Chaos.",
        "{player} ist neu hier und weiß noch nichts vom Glück.",
        "{player} spawnt zum ersten Mal. Die Lernkurve beginnt jetzt.",
        "{player} ist neu. Bitte einmal freundlich winken.",
        "{player} hat gerade angefangen. Der erste Tod kommt bestimmt."
    );

    public static final MessageTopic LEAVE = MessageTopic.of(
        "connection-leave",
        "Schreibe Meldungen für einen Spieler, der den Server verlässt.",
        "{player} ist weg. Wahrscheinlich Ausreden holen.",
        "{player} hat sich ausgeloggt und den Rest uns überlassen.",
        "{player} verlässt den Server. Die Baustelle bleibt.",
        "{player} ist raus. Vermutlich Essen, vermutlich Ausrede.",
        "{player} hat aufgegeben. Für heute jedenfalls.",
        "{player} hat den Stecker gezogen.",
        "{player} ist offline. Der Server wirkt sofort aufgeräumter.",
        "{player} macht Pause. Angeblich nur kurz.",
        "{player} hat sich verabschiedet, ohne tschüss zu sagen.",
        "{player} ist gegangen. Die Kisten bleiben hier.",
        "{player} loggt aus. Den Rest des Chaos übernehmen wir.",
        "{player} ist weg vom Fenster. Freiwillig."
    );

    /** Meldet die Themen an; jedes Plugin, das Join oder Leave meldet, ruft das beim Start auf. */
    public static void register() {
        Messages.register(JOIN, FIRST_JOIN, LEAVE);
    }

    private ConnectionMessages() {
    }

    public static Component join(String playerName) {
        return Messages.render(JOIN, NamedTextColor.GRAY, playerName);
    }

    public static Component firstJoin(String playerName) {
        return Messages.render(FIRST_JOIN, NamedTextColor.GREEN, playerName);
    }

    public static Component leave(String playerName) {
        return Messages.render(LEAVE, NamedTextColor.GRAY, playerName);
    }
}
