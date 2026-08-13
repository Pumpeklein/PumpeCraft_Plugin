package de.pumpecraft.chatcontrol;

import java.util.Set;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;

record TrackedChatMessage(SignedMessage signedMessage, Set<Audience> viewers, long createdAt) {
}
