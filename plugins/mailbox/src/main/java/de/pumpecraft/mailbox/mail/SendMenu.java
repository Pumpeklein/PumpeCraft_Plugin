package de.pumpecraft.mailbox.mail;

import de.pumpecraft.mailbox.MailboxItems;
import de.pumpecraft.mailbox.box.MailboxEntry;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * The menu a parcel is packed in: items on top, price and travel time on the button below. Both
 * are recalculated whenever the content changes.
 */
public final class SendMenu {
    public static final int ITEM_SLOTS = 36;
    public static final int SIZE = 45;
    public static final int INFO_SLOT = 38;
    public static final int CONFIRM_SLOT = 40;
    public static final int CANCEL_SLOT = 42;

    private SendMenu() {
    }

    public static void open(Player sender, MailboxEntry target, DeliveryService deliveries) {
        SendHolder holder = new SendHolder(target);
        Inventory inventory = Bukkit.createInventory(
            holder, SIZE, Component.text("Sendung an " + target.ownerName()));
        holder.inventory(inventory);
        refresh(sender, inventory, target, deliveries);
        sender.openInventory(inventory);
    }

    public static void refresh(Player sender, Inventory inventory, MailboxEntry target, DeliveryService deliveries) {
        DeliveryEstimate estimate = deliveries.estimate(sender, target, items(inventory));
        inventory.setItem(INFO_SLOT, MailboxItems.infoButton(target.ownerName(), estimate.distance()));
        inventory.setItem(CONFIRM_SLOT,
            MailboxItems.confirmButton(estimate.cost(), estimate.duration(), estimate.stacks()));
        inventory.setItem(CANCEL_SLOT, MailboxItems.cancelButton());
    }

    public static List<ItemStack> items(Inventory inventory) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < ITEM_SLOTS; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                items.add(item.clone());
            }
        }
        return items;
    }

    public static void clearItems(Inventory inventory) {
        for (int slot = 0; slot < ITEM_SLOTS; slot++) {
            inventory.setItem(slot, null);
        }
    }

    public static boolean isControlSlot(int slot) {
        return slot >= ITEM_SLOTS && slot < SIZE;
    }
}
