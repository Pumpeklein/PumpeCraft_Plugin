package de.pumpecraft.mailbox;

import de.pumpecraft.mailbox.box.MailboxEntry;
import de.pumpecraft.mailbox.box.MailboxIndex;
import de.pumpecraft.mailbox.box.MailboxHolder;
import de.pumpecraft.mailbox.box.MailboxInventories;
import de.pumpecraft.mailbox.mail.Delivery;
import de.pumpecraft.mailbox.mail.DeliveryEstimate;
import de.pumpecraft.mailbox.mail.DeliveryRepository;
import de.pumpecraft.mailbox.mail.DeliveryService;
import de.pumpecraft.mailbox.mail.SendHolder;
import de.pumpecraft.mailbox.mail.SendMenu;
import de.pumpecraft.transactions.core.PointsService;
import de.pumpecraft.utils.Items;
import de.pumpecraft.utils.Teleports;
import de.pumpecraft.utils.objects.DisplayObject;
import de.pumpecraft.utils.objects.DisplayObjects;
import de.pumpecraft.utils.objects.ObjectStorage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Ties the parts together: the placed object, its inventory, the deliveries on their way and what
 * the player sees on it. Everything that changes a mailbox goes through here so the label, the flag
 * and the parcel next to it never drift apart from the contents.
 */
public final class MailboxService {
    private static final String MANAGE_PERMISSION = "pumpecraft.mailbox.manage";
    private static final String UNKNOWN_OWNER = "unbekannt";

    private final Plugin plugin;
    private final MailboxSettings settings;
    private final MailboxAnimations animations;
    private final MailboxIndex index;
    private final MailboxInventories inventories;
    private final DeliveryService deliveries;

    public MailboxService(
        Plugin plugin,
        MailboxSettings settings,
        MailboxAnimations animations,
        MailboxIndex index,
        MailboxInventories inventories,
        DeliveryRepository repository,
        PointsService points
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.animations = animations;
        this.index = index;
        this.inventories = inventories;
        this.deliveries = new DeliveryService(
            plugin, settings, index, inventories, repository, points, this::refresh);
    }

    public MailboxIndex index() {
        return index;
    }

    public DeliveryService deliveries() {
        return deliveries;
    }

    public MailboxInventories inventories() {
        return inventories;
    }

    public void start() {
        index.load();
        deliveries.start();
    }

    public void shutdown() {
        deliveries.stop();
        inventories.persistAll();
    }

    public boolean place(Player player, Location base) {
        Optional<MailboxEntry> existing = index.of(player.getUniqueId());
        if (existing.isPresent()) {
            player.sendMessage(Component.text("Du hast schon einen Briefkasten bei ", NamedTextColor.RED)
                .append(Component.text(existing.get().coordinates(), NamedTextColor.AQUA))
                .append(Component.text(". Bau ihn erst ab.", NamedTextColor.RED)));
            return false;
        }

        DisplayObject mailbox = DisplayObjects.spawn(
            MailboxObject.TYPE, base, DisplayObjects.facingYaw(player), player);
        index.add(new MailboxEntry(
            player.getUniqueId(),
            player.getName(),
            mailbox.body().getUniqueId(),
            base.getWorld().getName(),
            base.getBlockX(),
            base.getBlockY(),
            base.getBlockZ()
        ));
        refresh(mailbox);
        return true;
    }

    public boolean remove(Player actor, DisplayObject mailbox, boolean returnItem) {
        UUID owner = ObjectStorage.owner(mailbox).orElse(null);
        if (owner != null && !deliveries.pendingFor(owner).isEmpty()) {
            actor.sendMessage(Component.text(
                "Zu diesem Briefkasten ist noch etwas unterwegs - erst abwarten.", NamedTextColor.RED));
            return false;
        }

        Location location = mailbox.location();
        List<ItemStack> contents = inventories.drain(mailbox);
        DisplayObjects.remove(mailbox);
        if (owner != null) {
            index.remove(owner);
        }

        if (location != null) {
            contents.forEach(item -> location.getWorld().dropItemNaturally(location, item));
            location.getWorld().playSound(location, Sound.BLOCK_WOOD_BREAK, 1.0F, 1.0F);
            if (returnItem && actor.getGameMode() != GameMode.CREATIVE) {
                location.getWorld().dropItemNaturally(location, MailboxItems.create());
            }
        }
        return true;
    }

    public void open(Player viewer, DisplayObject mailbox) {
        UUID owner = ObjectStorage.owner(mailbox).orElse(null);
        List<ItemStack> reservations = owner == null ? List.of() : deliveries.reservations(owner);
        inventories.open(viewer, mailbox, reservations);
        animations.openDoor(mailbox);
    }

    public void closed(Inventory inventory, HumanEntity viewer) {
        if (!(inventory.getHolder() instanceof MailboxHolder holder)) {
            return;
        }
        if (inventories.closed(inventory, viewer)) {
            animations.closeDoor(holder.mailbox());
            refresh(holder.mailbox());
        }
    }

    public boolean insert(Player sender, DisplayObject mailbox, ItemStack letter) {
        ItemStack single = letter.clone();
        single.setAmount(1);

        UUID owner = ObjectStorage.owner(mailbox).orElse(null);
        int reserved = owner == null ? 0 : deliveries.reservedStacks(owner);
        boolean fitsInStack = inventories.contents(mailbox).stream()
            .anyMatch(item -> item.isSimilar(single) && item.getAmount() < item.getMaxStackSize());
        if (!fitsInStack && inventories.freeSlots(mailbox, reserved) < 1) {
            sender.sendMessage(Component.text(
                "Der Briefkasten ist voll oder alle freien Plätze sind reserviert.", NamedTextColor.RED));
            return false;
        }

        if (!inventories.deposit(mailbox, single)) {
            sender.sendMessage(Component.text("Der Briefkasten ist voll.", NamedTextColor.RED));
            return false;
        }

        animations.flapDoor(mailbox);
        Location location = mailbox.location();
        if (location != null) {
            location.getWorld().playSound(location, Sound.ITEM_BOOK_PUT, 0.8F, 1.0F);
        }
        sender.sendMessage(Component.text("Eingeworfen.", NamedTextColor.GRAY));
        refresh(mailbox);
        notifyOwner(sender, mailbox);
        return true;
    }

    /**
     * Drops stacks straight into the own mailbox, loading its chunk when needed. Everything that
     * did not fit comes back through the callback - the caller still holds those items and has to
     * decide what happens with them.
     */
    public void ship(Player owner, List<ItemStack> stacks, Consumer<List<ItemStack>> rejected) {
        index.resolve(owner.getUniqueId(), mailbox -> {
            if (mailbox == null) {
                rejected.accept(stacks);
                return;
            }
            int free = inventories.freeSlots(mailbox, deliveries.reservedStacks(owner.getUniqueId()));
            List<ItemStack> left = new ArrayList<>();
            int shipped = 0;
            for (ItemStack stack : stacks) {
                if (shipped >= free || !inventories.deposit(mailbox, stack)) {
                    left.add(stack);
                    continue;
                }
                shipped++;
            }
            if (shipped > 0) {
                animations.flapDoor(mailbox);
                refresh(mailbox);
            }
            rejected.accept(left);
        });
    }

    public void openSendMenu(Player sender, MailboxEntry target) {
        SendMenu.open(sender, target, deliveries);
    }

    public void refreshSendMenu(Player sender, Inventory inventory, SendHolder holder) {
        SendMenu.refresh(sender, inventory, holder.target(), deliveries);
    }

    public void confirmSend(Player sender, Inventory inventory, SendHolder holder) {
        // Loose items first into as few stacks as possible: that is what decides slots and price,
        // and 27 single diamonds must not block 27 slots in the target mailbox.
        List<ItemStack> items = Items.merge(SendMenu.items(inventory));
        if (items.isEmpty()) {
            sender.sendMessage(Component.text("Leg zuerst etwas in die oberen Felder.", NamedTextColor.RED));
            return;
        }

        MailboxEntry target = holder.target();
        if (deliveries.pendingFor(target.owner()).size() >= settings.maxPending()) {
            sender.sendMessage(Component.text(
                "Zu " + target.ownerName() + " sind schon zu viele Sendungen unterwegs.", NamedTextColor.RED));
            return;
        }

        int free = deliveries.freeSlots(target);
        if (items.size() > free) {
            sender.sendMessage(Component.text("Der Briefkasten von " + target.ownerName() + " hat nur noch "
                + free + " freie Plätze, deine Sendung braucht " + items.size() + ".", NamedTextColor.RED));
            return;
        }

        DeliveryEstimate estimate = deliveries.estimate(sender, target, items);
        // Items first out of the menu, then close, then charge: while the payment runs the stacks
        // exist only in this list, so they can not be taken out a second time.
        holder.processing(true);
        SendMenu.clearItems(inventory);
        sender.closeInventory();
        deliveries.send(sender, target, items, estimate);
    }

    public void returnSendItems(Player player, Inventory inventory) {
        SendMenu.items(inventory).forEach(item -> player.getInventory().addItem(item).values()
            .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover)));
        SendMenu.clearItems(inventory);
    }

    public boolean isOwner(Player player, DisplayObject mailbox) {
        return ObjectStorage.owner(mailbox).map(owner -> owner.equals(player.getUniqueId())).orElse(true);
    }

    public boolean canOpen(Player player, DisplayObject mailbox) {
        Optional<UUID> owner = ObjectStorage.owner(mailbox);
        return owner.isEmpty()
            || owner.get().equals(player.getUniqueId())
            || player.hasPermission(MANAGE_PERMISSION);
    }

    public int contentCount(DisplayObject mailbox) {
        return inventories.itemCount(mailbox);
    }

    public String ownerName(DisplayObject mailbox) {
        return ObjectStorage.ownerName(mailbox, UNKNOWN_OWNER);
    }

    /**
     * Brings label, flag and parcel in line with what is inside and what is on its way.
     */
    public void refresh(DisplayObject stale) {
        // Always work on freshly resolved parts: the parcel comes and goes, and a DisplayObject
        // that was resolved before that still carries the old part list.
        DisplayObject mailbox = stale.body() == null ? stale
            : DisplayObjects.byBody(MailboxObject.TYPE, stale.body().getUniqueId()).orElse(stale);

        UUID owner = ObjectStorage.owner(mailbox).orElse(null);
        List<ItemStack> contents = inventories.contents(mailbox);
        int items = contents.stream().mapToInt(ItemStack::getAmount).sum();
        int waiting = owner == null ? 0 : deliveries.pendingFor(owner).size();

        Component label = Component.text(ownerName(mailbox), NamedTextColor.GOLD);
        if (items > 0) {
            label = label.append(Component.newline())
                .append(Component.text("Post: " + items, NamedTextColor.YELLOW));
        }
        if (waiting > 0) {
            label = label.append(Component.newline())
                .append(Component.text(waiting + " unterwegs", NamedTextColor.GRAY));
        }
        DisplayObjects.setLabel(mailbox, label);

        animations.setFlag(mailbox, items > 0 || waiting > 0);
        updateParcel(mailbox, showParcel(contents, items));
        inventories.refreshReservations(mailbox, owner == null ? List.of() : deliveries.reservations(owner));
    }

    public void reportOnJoin(Player player) {
        Optional<MailboxEntry> entry = index.of(player.getUniqueId());
        if (entry.isEmpty()) {
            return;
        }

        List<Delivery> waiting = deliveries.pendingFor(player.getUniqueId());
        if (!waiting.isEmpty()) {
            long now = System.currentTimeMillis();
            long next = waiting.stream().mapToLong(delivery -> delivery.remainingSeconds(now)).min().orElse(0L);
            player.sendMessage(Component.text(
                waiting.size() + " Sendung(en) unterwegs, die nächste in " + DeliveryEstimate.format(next) + ".",
                NamedTextColor.GRAY));
        }

        // Deliberately no chunk loading here: a login should not pull a chunk out of the disk just
        // for a status line. Out of view the delivery task reports on arrival anyway.
        Location location = entry.get().location();
        DisplayObjects.byBody(MailboxObject.TYPE, entry.get().bodyId()).ifPresent(mailbox -> {
            int items = inventories.itemCount(mailbox);
            if (items > 0 && location != null) {
                player.sendMessage(Component.text("In deinem Briefkasten bei ", NamedTextColor.GRAY)
                    .append(Teleports.locationLink(
                        location, NamedTextColor.AQUA, Teleports.DEFAULT_LOCATION_COMMAND))
                    .append(Component.text(" liegen " + items + " Items.", NamedTextColor.GRAY)));
            }
        });
    }

    /**
     * The parcel stands next to the mailbox as soon as it holds more than letters - anything that is
     * not a letter counts as goods, and a large pile of letters counts too.
     */
    private boolean showParcel(List<ItemStack> contents, int items) {
        boolean goods = contents.stream().anyMatch(item -> !settings.isLetter(item.getType()));
        return goods || items >= settings.parcelThreshold();
    }

    private void updateParcel(DisplayObject mailbox, boolean parcel) {
        Display existing = mailbox.part(MailboxObject.PARCEL_PART);
        boolean present = existing != null && existing.isValid();
        if (parcel == present) {
            return;
        }

        if (parcel) {
            DisplayObjects.attach(
                mailbox,
                MailboxObject.PARCEL_PART,
                MailboxObject.PARCEL_MODEL,
                MailboxObject.PARCEL_OFFSET_SIDE,
                0.0D,
                MailboxObject.PARCEL_OFFSET_FRONT
            );
        } else {
            DisplayObjects.detach(mailbox, MailboxObject.PARCEL_PART);
        }
    }

    private void notifyOwner(Player sender, DisplayObject mailbox) {
        if (!settings.notifyOwner()) {
            return;
        }

        Optional<UUID> owner = ObjectStorage.owner(mailbox);
        if (owner.isEmpty() || owner.get().equals(sender.getUniqueId())) {
            return;
        }

        Player online = Bukkit.getPlayer(owner.get());
        Location location = mailbox.location();
        if (online == null || location == null) {
            return;
        }

        online.sendMessage(Component.text("Neue Post von " + sender.getName() + " im Briefkasten bei ", NamedTextColor.GRAY)
            .append(Teleports.locationLink(location, NamedTextColor.AQUA, Teleports.DEFAULT_LOCATION_COMMAND))
            .append(Component.text(".", NamedTextColor.GRAY)));
    }
}
