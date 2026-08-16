package de.pumpecraft.mailbox.listener;

import de.pumpecraft.mailbox.mail.MailService;
import de.pumpecraft.mailbox.mail.MailboxHolder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

public final class MailboxInventoryListener implements Listener {
    private final MailService mail;

    public MailboxInventoryListener(MailService mail) {
        this.mail = mail;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MailboxHolder holder) {
            mail.save(holder, event.getInventory());
        }
    }
}
