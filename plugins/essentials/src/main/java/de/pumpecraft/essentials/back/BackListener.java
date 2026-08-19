package de.pumpecraft.essentials.back;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class BackListener implements Listener {
    private final BackHistoryService history;

    public BackListener(BackHistoryService history) {
        this.history = history;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (history.settings().records(event.getCause())) {
            history.recordTeleport(event.getPlayer(), event.getFrom(), event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        history.recordDeath(event.getEntity());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        history.preload(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        history.forget(event.getPlayer().getUniqueId());
    }
}
