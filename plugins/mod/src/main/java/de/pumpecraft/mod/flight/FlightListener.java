package de.pumpecraft.mod.flight;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

public final class FlightListener implements Listener {
    private final Plugin plugin;
    private final FlightService flight;

    public FlightListener(Plugin plugin, FlightService flight) {
        this.plugin = plugin;
        this.flight = flight;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        flight.disable(event.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        refreshLater(event.getPlayer());
    }

    @EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        refreshLater(event.getPlayer());
    }

    private void refreshLater(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) flight.refresh(player);
        });
    }
}
