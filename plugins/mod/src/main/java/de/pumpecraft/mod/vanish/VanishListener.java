package de.pumpecraft.mod.vanish;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

public final class VanishListener implements Listener {
    private final Plugin plugin;
    private final VanishService vanish;

    public VanishListener(Plugin plugin, VanishService vanish) {
        this.plugin = plugin;
        this.vanish = vanish;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        vanish.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (vanish.handleQuit(event.getPlayer())) {
            event.quitMessage(null);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        refreshLater(event.getPlayer());
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        refreshLater(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        refreshLater(event.getPlayer());
    }

    /** Der Kopf eines Vanish gehört nur Zuschauern gezeigt, die nicht selbst Spectator sind. */
    @EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        Player viewer = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline()) {
                vanish.refreshViewer(viewer);
            }
        });
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player player && vanish.isVanished(player)) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && vanish.isVanished(player)) {
            event.setCancelled(true);
        }
    }

    private void refreshLater(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                vanish.refresh(player);
            }
        });
    }
}
