package de.pumpecraft.mailbox.mail;

import de.pumpecraft.mailbox.box.MailboxEntry;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class SendHolder implements InventoryHolder {
    private final MailboxEntry target;
    private Inventory inventory;
    private boolean processing;

    public SendHolder(MailboxEntry target) {
        this.target = target;
    }

    public MailboxEntry target() {
        return target;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    /**
     * Set once the items left the menu and are on their way. The close handler must not hand them
     * back then - that would be the same stack twice.
     */
    public boolean processing() {
        return processing;
    }

    public void processing(boolean processing) {
        this.processing = processing;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
