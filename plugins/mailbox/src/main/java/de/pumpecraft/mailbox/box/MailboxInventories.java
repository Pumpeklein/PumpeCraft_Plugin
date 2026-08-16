package de.pumpecraft.mailbox.box;

import de.pumpecraft.mailbox.MailboxItems;
import de.pumpecraft.mailbox.MailboxSettings;
import de.pumpecraft.utils.Items;
import de.pumpecraft.utils.objects.DisplayObject;
import de.pumpecraft.utils.objects.ObjectStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Holds the one live inventory a mailbox has while it is open.
 *
 * <p>This is the anti dupe rule: every viewer of a mailbox works on the same {@link Inventory}
 * instance, and that instance is the truth while it exists. Handing every viewer its own copy of
 * the stored items - or reading the stored items while somebody has them open - is what duplicates
 * them, because two copies get written back independently.
 */
public final class MailboxInventories {
    private final MailboxSettings settings;
    private final Map<UUID, Inventory> live = new HashMap<>();

    public MailboxInventories(MailboxSettings settings) {
        this.settings = settings;
    }

    public void open(Player viewer, DisplayObject mailbox, List<ItemStack> reservations) {
        ItemDisplay body = mailbox.body();
        if (body == null) {
            return;
        }

        Inventory inventory = live.get(body.getUniqueId());
        if (inventory == null) {
            MailboxHolder holder = new MailboxHolder(mailbox);
            inventory = Bukkit.createInventory(holder, settings.capacity(), title(mailbox));
            holder.inventory(inventory);
            for (ItemStack item : ObjectStorage.contents(mailbox)) {
                // Only reachable when the capacity was lowered in the config; dropping is still
                // better than silently swallowing the stack.
                inventory.addItem(item).values().forEach(leftover -> drop(mailbox, leftover));
            }
            placeReservations(inventory, reservations);
            live.put(body.getUniqueId(), inventory);
        }

        viewer.openInventory(inventory);
    }

    /**
     * @return true when the last viewer left and the contents were written back
     */
    public boolean closed(Inventory inventory, HumanEntity viewer) {
        if (!(inventory.getHolder() instanceof MailboxHolder holder)) {
            return false;
        }

        List<HumanEntity> viewers = new ArrayList<>(inventory.getViewers());
        viewers.remove(viewer);
        if (!viewers.isEmpty()) {
            return false;
        }

        DisplayObject mailbox = holder.mailbox();
        persist(mailbox, inventory);
        ItemDisplay body = mailbox.body();
        if (body != null) {
            live.remove(body.getUniqueId());
        }
        return true;
    }

    /**
     * @return false when the item did not fit; it is dropped at the mailbox then instead of being
     *         swallowed
     */
    public boolean deposit(DisplayObject mailbox, ItemStack item) {
        Inventory inventory = live(mailbox);
        if (inventory != null) {
            Map<Integer, ItemStack> leftovers = inventory.addItem(item);
            persist(mailbox, inventory);
            leftovers.values().forEach(leftover -> drop(mailbox, leftover));
            return leftovers.isEmpty();
        }

        List<ItemStack> contents = ObjectStorage.contents(mailbox);
        if (contents.size() >= settings.capacity()) {
            drop(mailbox, item);
            return false;
        }
        contents.add(item);
        ObjectStorage.setContents(mailbox, Items.merge(contents));
        return true;
    }

    public List<ItemStack> contents(DisplayObject mailbox) {
        Inventory inventory = live(mailbox);
        return inventory == null ? ObjectStorage.contents(mailbox) : contents(mailbox, inventory);
    }

    public int itemCount(DisplayObject mailbox) {
        return contents(mailbox).stream().mapToInt(ItemStack::getAmount).sum();
    }

    public int freeSlots(DisplayObject mailbox, int reservedStacks) {
        return Math.max(0, settings.capacity() - contents(mailbox).size() - reservedStacks);
    }

    /**
     * Empties a mailbox before it is removed. Open views are closed first, so the items exist
     * exactly once: in the returned list.
     */
    public List<ItemStack> drain(DisplayObject mailbox) {
        closeViewers(mailbox);
        List<ItemStack> contents = ObjectStorage.contents(mailbox);
        ObjectStorage.setContents(mailbox, List.of());
        return contents;
    }

    public void closeViewers(DisplayObject mailbox) {
        Inventory inventory = live(mailbox);
        if (inventory == null) {
            return;
        }
        new ArrayList<>(inventory.getViewers()).forEach(HumanEntity::closeInventory);
    }

    public boolean isOpen(DisplayObject mailbox) {
        return live(mailbox) != null;
    }

    /**
     * Rewrites the blocked slots of an open mailbox, for example after a delivery arrived or a new
     * one was announced. Reservations are never persisted, so they only exist here.
     */
    public void refreshReservations(DisplayObject mailbox, List<ItemStack> reservations) {
        Inventory inventory = live(mailbox);
        if (inventory == null) {
            return;
        }

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (MailboxItems.isReservation(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
        placeReservations(inventory, reservations);
    }

    /**
     * One marker per slot. addItem would merge markers of the same delivery into a single stack and
     * block one slot instead of the number of stacks that are actually coming.
     */
    private void placeReservations(Inventory inventory, List<ItemStack> reservations) {
        for (ItemStack marker : reservations) {
            int slot = inventory.firstEmpty();
            if (slot < 0) {
                return;
            }
            inventory.setItem(slot, marker);
        }
    }

    private void drop(DisplayObject mailbox, ItemStack item) {
        Location location = mailbox.location();
        if (location != null) {
            location.getWorld().dropItemNaturally(location, item);
        }
    }

    public void persistAll() {
        new ArrayList<>(live.values()).forEach(inventory -> {
            InventoryHolder holder = inventory.getHolder();
            if (holder instanceof MailboxHolder mailboxHolder) {
                persist(mailboxHolder.mailbox(), inventory);
            }
            new ArrayList<>(inventory.getViewers()).forEach(HumanEntity::closeInventory);
        });
        live.clear();
    }

    private void persist(DisplayObject mailbox, Inventory inventory) {
        ObjectStorage.setContents(mailbox, Items.merge(contents(mailbox, inventory)));
    }

    private List<ItemStack> contents(DisplayObject mailbox, Inventory inventory) {
        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir() && !MailboxItems.isReservation(item)) {
                contents.add(item);
            }
        }
        return contents;
    }

    private Inventory live(DisplayObject mailbox) {
        ItemDisplay body = mailbox.body();
        return body == null ? null : live.get(body.getUniqueId());
    }

    private Component title(DisplayObject mailbox) {
        return ObjectStorage.owner(mailbox).isEmpty()
            ? Component.text("Briefkasten")
            : Component.text("Briefkasten von " + ObjectStorage.ownerName(mailbox, "unbekannt"));
    }
}
