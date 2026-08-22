package de.pumpecraft.chatcontrol;

import java.util.Map;
import java.util.Set;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;

record TrackedChatMessage(
    SignedMessage signedMessage,
    Set<Audience> viewers,
    long createdAt,
    boolean held,
    Map<Audience, Component> pendingDeliveries
) {
}
