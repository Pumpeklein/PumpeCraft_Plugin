package de.pumpecraft.mailbox.mail;

import de.pumpecraft.mailbox.MailboxItems;
import de.pumpecraft.mailbox.MailboxObject;
import de.pumpecraft.mailbox.MailboxSettings;
import de.pumpecraft.mailbox.box.MailboxEntry;
import de.pumpecraft.mailbox.box.MailboxIndex;
import de.pumpecraft.mailbox.box.MailboxInventories;
import de.pumpecraft.transactions.core.PointsService;
import de.pumpecraft.transactions.core.TransactionType;
import de.pumpecraft.utils.objects.DisplayObject;
import de.pumpecraft.utils.objects.DisplayObjects;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Everything that happens between "abgeschickt" and "im Briefkasten": price, travel time, the
 * blocked slots while a delivery is under way and the hand over once it arrives.
 */
public final class DeliveryService {
    private static final long TICK_INTERVAL = 20L;
    private static final long FULL_WARNING_INTERVAL = 300_000L;

    private final Plugin plugin;
    private final MailboxSettings settings;
    private final MailboxIndex index;
    private final MailboxInventories inventories;
    private final DeliveryRepository repository;
    private final PointsService points;
    private final Consumer<DisplayObject> onChanged;
    private final List<Delivery> pending = new ArrayList<>();
    private final Set<Long> inFlight = new HashSet<>();
    private final Map<Long, Long> lastWarning = new HashMap<>();
    private BukkitTask task;

    public DeliveryService(
        Plugin plugin,
        MailboxSettings settings,
        MailboxIndex index,
        MailboxInventories inventories,
        DeliveryRepository repository,
        PointsService points,
        Consumer<DisplayObject> onChanged
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.index = index;
        this.inventories = inventories;
        this.repository = repository;
        this.points = points;
        this.onChanged = onChanged;
    }

    public void start() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Delivery> loaded = repository.loadPending();
            Bukkit.getScheduler().runTask(plugin, () -> {
                pending.clear();
                pending.addAll(loaded);
                plugin.getLogger().info("Loaded " + pending.size() + " pending deliveries.");
            });
        });

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL, TICK_INTERVAL);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public List<Delivery> pendingFor(UUID recipient) {
        return pending.stream().filter(delivery -> delivery.recipient().equals(recipient)).toList();
    }

    public int reservedStacks(UUID recipient) {
        return pendingFor(recipient).stream().mapToInt(Delivery::stacks).sum();
    }

    public List<ItemStack> reservations(UUID recipient) {
        long now = System.currentTimeMillis();
        List<ItemStack> markers = new ArrayList<>();
        for (Delivery delivery : pendingFor(recipient)) {
            String remaining = DeliveryEstimate.format(delivery.remainingSeconds(now));
            for (int stack = 0; stack < delivery.stacks(); stack++) {
                markers.add(MailboxItems.reservation(delivery.senderName(), remaining));
            }
        }
        return markers;
    }

    public DeliveryEstimate estimate(Player sender, MailboxEntry target, List<ItemStack> items) {
        int itemCount = items.stream().mapToInt(ItemStack::getAmount).sum();
        int stacks = items.size();
        double distance = distance(sender.getLocation(), target);
        return new DeliveryEstimate(
            distance,
            settings.cost(itemCount, distance),
            settings.deliverySeconds(stacks, distance),
            stacks,
            itemCount
        );
    }

    /**
     * Charges the sender and books the delivery. The items are already out of the world at this
     * point - the caller took them out of the send menu, so nothing can be taken out twice.
     */
    public void send(Player sender, MailboxEntry target, List<ItemStack> items, DeliveryEstimate estimate) {
        long now = System.currentTimeMillis();
        Delivery delivery = new Delivery(
            0L,
            target.owner(),
            sender.getUniqueId(),
            sender.getName(),
            items,
            estimate.stacks(),
            estimate.itemCount(),
            estimate.cost(),
            now,
            now + estimate.seconds() * 1000L
        );

        UUID senderId = sender.getUniqueId();
        String senderName = sender.getName();
        points.runAsync(() -> {
            boolean paid = estimate.cost() <= 0L || points.withdraw(
                senderId,
                senderName,
                estimate.cost(),
                TransactionType.TRANSFER_OUT,
                senderName,
                "Briefkasten-Versand an " + target.ownerName()
            );

            if (!paid) {
                points.runSync(() -> refund(sender, items,
                    "Du hast nicht genug PumpePoints (" + estimate.cost() + " PP)."));
                return;
            }

            long id = repository.insert(delivery);
            points.runSync(() -> {
                pending.add(delivery.withId(id));
                announceSent(sender, target, estimate);
                // Blocks the slots in the target mailbox right away, so it can not be filled up
                // while the delivery is on its way.
                index.resolve(target.owner(), mailbox -> {
                    if (mailbox != null) {
                        onChanged.accept(mailbox);
                    }
                });
            });
        });
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Delivery delivery : List.copyOf(pending)) {
            if (!delivery.isDue(now) || !inFlight.add(delivery.id())) {
                continue;
            }
            index.resolve(delivery.recipient(), mailbox -> {
                inFlight.remove(delivery.id());
                if (mailbox != null) {
                    deliver(delivery, mailbox);
                }
            });
        }
    }

    private void deliver(Delivery delivery, DisplayObject mailbox) {
        if (!pending.contains(delivery)) {
            return;
        }

        // Only the real contents may block a delivery. Counting the reservations of the other
        // pending deliveries here deadlocks them: every one of them would wait for the slots that
        // the others keep blocked, and nobody could ever make room.
        if (inventories.freeSlots(mailbox, 0) < delivery.stacks()) {
            warnFull(delivery);
            return;
        }

        pending.remove(delivery);
        lastWarning.remove(delivery.id());
        // Its own blocked slots go first, otherwise the items find no room.
        inventories.refreshReservations(mailbox, reservations(delivery.recipient()));
        delivery.items().forEach(item -> inventories.deposit(mailbox, item));
        onChanged.accept(mailbox);
        Bukkit.getScheduler().runTaskAsynchronously(plugin,
            () -> repository.markDelivered(delivery.id(), System.currentTimeMillis()));

        notifyRecipient(delivery.recipient(), Component.text(
            "Sendung von " + delivery.senderName() + " ist im Briefkasten angekommen ("
                + delivery.itemCount() + " Items).", NamedTextColor.GREEN));
    }

    /**
     * Free slots in a mailbox including everything that is already booked for it. Used before a
     * delivery is accepted, so the sum of contents and reservations can never exceed the capacity.
     */
    public int freeSlots(MailboxEntry target) {
        int used = DisplayObjects.byBody(MailboxObject.TYPE, target.bodyId())
            .map(mailbox -> inventories.contents(mailbox).size())
            .orElse(0);
        return Math.max(0, settings.capacity() - used - reservedStacks(target.owner()));
    }

    private void warnFull(Delivery delivery) {
        long now = System.currentTimeMillis();
        Long last = lastWarning.get(delivery.id());
        if (last != null && now - last < FULL_WARNING_INTERVAL) {
            return;
        }
        lastWarning.put(delivery.id(), now);
        notifyRecipient(delivery.recipient(), Component.text(
            "Dein Briefkasten ist voll - eine Sendung von " + delivery.senderName() + " wartet.",
            NamedTextColor.RED));
    }

    private void refund(Player sender, List<ItemStack> items, String reason) {
        sender.sendMessage(Component.text(reason, NamedTextColor.RED));
        items.forEach(item -> sender.getInventory().addItem(item).values()
            .forEach(leftover -> sender.getWorld().dropItemNaturally(sender.getLocation(), leftover)));
    }

    private void announceSent(Player sender, MailboxEntry target, DeliveryEstimate estimate) {
        sender.sendMessage(Component.text(
            "Sendung an " + target.ownerName() + " unterwegs - Ankunft in " + estimate.duration()
                + ", Kosten " + estimate.cost() + " PP.", NamedTextColor.GRAY));

        notifyRecipient(target.owner(), Component.text(
            sender.getName() + " schickt dir etwas - Ankunft in " + estimate.duration() + ".",
            NamedTextColor.GRAY));
    }

    private void notifyRecipient(UUID recipient, Component message) {
        Player online = Bukkit.getPlayer(recipient);
        if (online != null) {
            online.sendMessage(message);
        }
    }

    private double distance(Location from, MailboxEntry target) {
        Location to = target.location();
        if (to == null || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return settings.crossWorldBlocks();
        }
        return from.distance(to);
    }
}
