package de.pumpecraft.skills;

import java.util.logging.Level;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Lädt die Zähler eines Spielers, bevor er die Welt betritt, und schreibt sie
 * beim Verlassen weg.
 */
final class SkillSessionListener implements Listener {
    private final PumpeSkillsPlugin plugin;
    private final SkillService service;
    private final SkillRepository repository;
    private final SkillRewardService rewards;

    SkillSessionListener(
        PumpeSkillsPlugin plugin,
        SkillService service,
        SkillRepository repository,
        SkillRewardService rewards
    ) {
        this.plugin = plugin;
        this.service = service;
        this.repository = repository;
        this.rewards = rewards;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        rewards.deliverPending(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            service.load(event.getUniqueId());
            repository.touchPlayer(event.getUniqueId(), event.getName());
        } catch (RuntimeException exception) {
            // Ohne geladene Daten wird für diesen Spieler nichts gezählt; der
            // Login soll deswegen aber nicht scheitern.
            plugin.getLogger().log(
                Level.SEVERE,
                "Could not load skill stats for " + event.getName() + "; tracking stays off this session.",
                exception
            );
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        service.unload(event.getPlayer().getUniqueId());
    }
}
