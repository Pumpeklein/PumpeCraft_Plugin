package de.pumpecraft.mailbox.mail;

import de.pumpecraft.utils.objects.DisplayObject;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MailboxHolder implements InventoryHolder {
    private final DisplayObject mailbox;
    private Inventory inventory;

    public MailboxHolder(DisplayObject mailbox) {
        this.mailbox = mailbox;
    }

    public DisplayObject mailbox() {
        return mailbox;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
