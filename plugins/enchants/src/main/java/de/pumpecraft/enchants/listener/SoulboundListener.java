package de.pumpecraft.enchants.listener;

import de.pumpecraft.enchants.soulbound.SoulboundRules;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;

public final class SoulboundListener implements Listener {
    private final Plugin plugin;
    private final SoulboundRules rules;

    public SoulboundListener(Plugin plugin, SoulboundRules rules) {
        this.plugin = plugin;
        this.rules = rules;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        rules.keep(event);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // The respawn inventory only exists after the event, so handing items over now loses them.
        plugin.getServer().getScheduler().runTask(plugin, () -> rules.restore(event.getPlayer()));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        rules.recover(event.getPlayer());
    }
}
