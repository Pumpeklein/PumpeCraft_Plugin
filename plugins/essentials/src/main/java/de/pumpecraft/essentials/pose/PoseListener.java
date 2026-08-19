package de.pumpecraft.essentials.pose;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PoseListener implements Listener {
    private final SeatService seats;
    private final CrawlService crawl;

    public PoseListener(SeatService seats, CrawlService crawl) {
        this.seats = seats;
        this.crawl = crawl;
    }

    /**
     * Teleports sind ebenfalls Move-Events; der Deckblock zieht deshalb auch dort mit. Der
     * Blockwechsel taugt nicht als Filter: Der Schritt von einer Stufe auf den vollen Block
     * daneben verschiebt die Deckposition, ohne den Block unter den Füßen zu wechseln.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.hasChangedPosition()) {
            crawl.follow(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) {
            seats.dismounted(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        seats.discard(event.getEntity());
        crawl.stop(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SPECTATOR) {
            seats.discard(event.getPlayer());
            crawl.stop(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        seats.discard(event.getPlayer());
        crawl.forget(event.getPlayer());
    }
}
