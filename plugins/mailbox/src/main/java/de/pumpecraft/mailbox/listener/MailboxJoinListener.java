package de.pumpecraft.mailbox.listener;

import de.pumpecraft.mailbox.MailboxService;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public final class MailboxJoinListener implements Listener {
    private static final long REPORT_DELAY_TICKS = 40L;

    private final Plugin plugin;
    private final MailboxService service;

    public MailboxJoinListener(Plugin plugin, MailboxService service) {
        this.plugin = plugin;
        this.service = service;
    }

    // Delayed on purpose: at join time the chat is still full of login messages, and resolving the
    // mailbox may have to load its chunk.
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                service.reportOnJoin(event.getPlayer());
            }
        }, REPORT_DELAY_TICKS);
    }
}
