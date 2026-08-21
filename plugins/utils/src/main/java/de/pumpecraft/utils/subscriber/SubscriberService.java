package de.pumpecraft.utils.subscriber;

import java.util.UUID;

/** Read-only bridge for plugins that include a player's name in their own tab formatting. */
public interface SubscriberService {
    boolean isSubscriber(UUID playerId);
}
