package de.pumpecraft.mailbox.mail;

import de.pumpecraft.mailbox.MailboxAnimations;
import de.pumpecraft.mailbox.MailboxSettings;
import de.pumpecraft.utils.Teleports;
import de.pumpecraft.utils.objects.DisplayObject;
import de.pumpecraft.utils.objects.ObjectStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class MailService {
    private static final String MANAGE_PERMISSION = "pumpecraft.mailbox.manage";
    private static final String UNKNOWN_OWNER = "unbekannt";

    private final MailboxSettings settings;
    private final MailboxAnimations animations;
    private final Map<UUID, Inventory> openInventories = new HashMap<>();

    public MailService(MailboxSettings settings, MailboxAnimations animations) {
        this.settings = settings;
        this.animations = animations;
    }

    public boolean insert(Player sender, DisplayObject mailbox, ItemStack letter) {
        ItemStack single = letter.clone();
        single.setAmount(1);

        if (!deposit(mailbox, single)) {
            sender.sendMessage(Component.text("Der Briefkasten ist voll.", NamedTextColor.RED));
            return false;
        }

        animations.flapDoor(mailbox);
        animations.setFlag(mailbox, true);
        play(mailbox, Sound.ITEM_BOOK_PUT);
        sender.sendMessage(Component.text("Eingeworfen.", NamedTextColor.GRAY));
        notifyOwner(sender, mailbox);
        return true;
    }

    public void open(Player viewer, DisplayObject mailbox) {
        MailboxHolder holder = new MailboxHolder(mailbox);
        Inventory inventory = Bukkit.createInventory(holder, settings.capacity(), title(mailbox));
        holder.inventory(inventory);
        ObjectStorage.contents(mailbox).forEach(inventory::addItem);

        ItemDisplay body = mailbox.body();
        if (body != null) {
            openInventories.put(body.getUniqueId(), inventory);
        }

        viewer.openInventory(inventory);
        animations.openDoor(mailbox);
    }

    public void save(MailboxHolder holder, Inventory inventory) {
        DisplayObject mailbox = holder.mailbox();
        ItemDisplay body = mailbox.body();
        if (body != null) {
            openInventories.remove(body.getUniqueId());
        }

        List<ItemStack> mail = new ArrayList<>();
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) {
                mail.add(item);
            }
        }

        ObjectStorage.setContents(mailbox, mail);
        animations.closeDoor(mailbox);
        animations.setFlag(mailbox, !mail.isEmpty());
    }

    public void dropAll(DisplayObject mailbox) {
        Location location = mailbox.location();
        if (location != null) {
            ObjectStorage.contents(mailbox)
                .forEach(item -> location.getWorld().dropItemNaturally(location, item));
        }
        ObjectStorage.setContents(mailbox, List.of());
    }

    public boolean canOpen(Player player, DisplayObject mailbox) {
        Optional<UUID> owner = ObjectStorage.owner(mailbox);
        return owner.isEmpty()
            || owner.get().equals(player.getUniqueId())
            || player.hasPermission(MANAGE_PERMISSION);
    }

    public String ownerName(DisplayObject mailbox) {
        return ObjectStorage.ownerName(mailbox, UNKNOWN_OWNER);
    }

    /**
     * While someone has the mailbox open, that inventory is the newer state: saving it on close
     * would otherwise overwrite everything thrown in meanwhile.
     */
    private boolean deposit(DisplayObject mailbox, ItemStack letter) {
        ItemDisplay body = mailbox.body();
        Inventory open = body == null ? null : openInventories.get(body.getUniqueId());
        if (open != null) {
            if (open.firstEmpty() < 0) {
                return false;
            }
            open.addItem(letter);
            return true;
        }

        List<ItemStack> mail = ObjectStorage.contents(mailbox);
        if (mail.size() >= settings.capacity()) {
            return false;
        }
        mail.add(letter);
        ObjectStorage.setContents(mailbox, mail);
        return true;
    }

    private Component title(DisplayObject mailbox) {
        return ObjectStorage.owner(mailbox).isEmpty()
            ? Component.text("Briefkasten")
            : Component.text("Briefkasten von " + ownerName(mailbox));
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

    private void play(DisplayObject mailbox, Sound sound) {
        Location location = mailbox.location();
        if (location != null) {
            location.getWorld().playSound(location, sound, 0.8F, 1.0F);
        }
    }
}
