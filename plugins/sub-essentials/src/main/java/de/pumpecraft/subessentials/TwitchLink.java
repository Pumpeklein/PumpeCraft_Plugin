package de.pumpecraft.subessentials;

import java.util.UUID;

record TwitchLink(
    UUID playerId,
    String playerName,
    String twitchUserId,
    String twitchLogin,
    String twitchDisplayName,
    boolean subscriber,
    long linkedAt,
    long checkedAt,
    Long gameNotifiedAt,
    Boolean subscriptionNotifiedState
) {
}
