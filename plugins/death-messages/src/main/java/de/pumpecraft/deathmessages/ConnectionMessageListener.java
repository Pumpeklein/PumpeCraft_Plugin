package de.pumpecraft.deathmessages;

import de.pumpecraft.utils.messages.ConnectionMessages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Meldungen für Betreten und Verlassen. Beide Handler laufen auf {@link EventPriority#LOW}, damit
 * der Vanish aus {@code PumpeMod} die Meldung eines versteckten Teamlers danach noch streichen kann.
 */
public final class ConnectionMessageListener implements Listener {
    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(player.hasPlayedBefore()
            ? ConnectionMessages.join(player.getName())
            : ConnectionMessages.firstJoin(player.getName()));
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.quitMessage(ConnectionMessages.leave(event.getPlayer().getName()));
    }
}
