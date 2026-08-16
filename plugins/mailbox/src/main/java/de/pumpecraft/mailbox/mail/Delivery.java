package de.pumpecraft.mailbox.mail;

import java.util.List;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public record Delivery(
    long id,
    UUID recipient,
    UUID sender,
    String senderName,
    List<ItemStack> items,
    int stacks,
    int itemCount,
    long cost,
    long sentAt,
    long arrivesAt
) {
    public boolean isDue(long now) {
        return now >= arrivesAt;
    }

    public long remainingSeconds(long now) {
        return Math.max(0L, (arrivesAt - now + 999L) / 1000L);
    }

    public Delivery withId(long newId) {
        return new Delivery(newId, recipient, sender, senderName, items, stacks, itemCount, cost, sentAt, arrivesAt);
    }
}
