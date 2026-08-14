package de.pumpecraft.transactions.payout;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PayoutListener implements Listener {
    private final PayoutService payouts;

    public PayoutListener(PayoutService payouts) {
        this.payouts = payouts;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        payouts.load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        payouts.unload(event.getPlayer().getUniqueId());
    }
}
