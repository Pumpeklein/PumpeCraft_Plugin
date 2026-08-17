package de.pumpecraft.trader;

import de.pumpecraft.utils.messages.MessageTopic;
import de.pumpecraft.utils.messages.Messages;

/** Themen der Trader-Meldungen. Der Ort ist die Information, alles andere ist Beiwerk. */
final class TraderTopics {
    static final MessageTopic SPAWNED = MessageTopic.of(
        "trader-spawned",
        "Schreibe Meldungen für einen Händler, der auf dem Server auftaucht. Die Koordinaten stehen"
            + " in {location} und gehören in jede Meldung. Kein Spielername kommt darin vor.",
        "Ein Trader ist gespawnt bei {location}.",
        "Bei {location} steht ein Trader und wartet auf Kundschaft.",
        "Ein Händler hat bei {location} seinen Stand aufgebaut.",
        "Trader gesichtet: {location}. Wer zuerst da ist, mahlt zuerst.",
        "Bei {location} gibt es kurz etwas zu holen.",
        "Ein Trader macht bei {location} auf. Öffnungszeiten: knapp."
    );

    static final MessageTopic DESPAWNED = MessageTopic.of(
        "trader-despawned",
        "Schreibe Meldungen für einen Händler, der wieder verschwindet. Die Koordinaten stehen in"
            + " {location} und gehören in jede Meldung. Kein Spielername kommt darin vor.",
        "Der Trader bei {location} ist wieder verschwunden.",
        "Bei {location} hat der Händler dichtgemacht.",
        "Der Stand bei {location} ist abgebaut.",
        "Der Trader von {location} hat Feierabend.",
        "Bei {location} ist der Laden zu. Zu langsam."
    );

    static void register() {
        Messages.register(SPAWNED, DESPAWNED);
    }

    private TraderTopics() {
    }
}
