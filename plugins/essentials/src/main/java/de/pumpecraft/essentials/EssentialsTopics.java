package de.pumpecraft.essentials;

import de.pumpecraft.utils.messages.MessageTopic;
import de.pumpecraft.utils.messages.Messages;

final class EssentialsTopics {
    static final MessageTopic BROADCAST = MessageTopic.of(
        "essentials-broadcast",
        "Gib ausschließlich den Platzhalter {message} unverändert zurück. Füge keinen Namen,"
            + " kein Präfix und keinen weiteren Text hinzu.",
        "{message}"
    );

    static void register() {
        Messages.register(BROADCAST);
    }

    private EssentialsTopics() {
    }
}
