package de.pumpecraft.bases.listener;

import de.pumpecraft.bases.PumpeBaseSystemPlugin;
import de.pumpecraft.bases.base.BaseRepository;
import de.pumpecraft.bases.base.PlayerIdentity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class BaseJoinListener implements Listener {
    private final PumpeBaseSystemPlugin plugin;
    private final BaseRepository repository;

    public BaseJoinListener(PumpeBaseSystemPlugin plugin, BaseRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        PlayerIdentity player = new PlayerIdentity(
            event.getPlayer().getUniqueId(), event.getPlayer().getName());
        plugin.runAsync(() -> repository.syncPlayerName(player));
    }
}
