package de.pumpecraft.enchants;

import de.pumpecraft.utils.messages.MessageTopic;
import de.pumpecraft.utils.messages.Messages;

final class EnchantTopics {
    static final MessageTopic GRANTED = MessageTopic.of(
        "enchant-granted",
        "Schreibe eine knappe Meldung, dass {player} die Verzauberung {enchant} erhalten hat.",
        "{player} hat jetzt {enchant}.",
        "Für {player} wurde {enchant} auf ein Item gelegt.",
        "{player} erhält {enchant}."
    );

    static void register() {
        Messages.register(GRANTED);
    }

    private EnchantTopics() {
    }
}
