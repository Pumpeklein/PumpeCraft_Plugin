package de.pumpecraft.mailbox.listener;

import de.pumpecraft.mailbox.MailboxItems;
import de.pumpecraft.mailbox.MailboxObject;
import de.pumpecraft.mailbox.MailboxSettings;
import de.pumpecraft.mailbox.mail.MailService;
import de.pumpecraft.utils.objects.DisplayObject;
import de.pumpecraft.utils.objects.DisplayObjects;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class MailboxInteractListener implements Listener {
    private static final String USE_PERMISSION = "pumpecraft.mailbox.use";
    private static final String MANAGE_PERMISSION = "pumpecraft.mailbox.manage";

    private final MailboxSettings settings;
    private final MailService mail;

    public MailboxInteractListener(MailboxSettings settings, MailService mail) {
        this.settings = settings;
        this.mail = mail;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
            || !DisplayObjects.isPart(MailboxObject.TYPE, event.getRightClicked())) {
            return;
        }

        event.setCancelled(true);
        Optional<DisplayObject> found = DisplayObjects.resolve(MailboxObject.TYPE, event.getRightClicked());
        if (found.isEmpty()) {
            return;
        }

        DisplayObject mailbox = found.get();
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();

        if (settings.isLetter(held.getType()) && !MailboxItems.isMailbox(held)) {
            postLetter(player, mailbox, held);
            return;
        }

        if (!mail.canOpen(player, mailbox)) {
            player.sendMessage(Component.text(
                "Dieser Briefkasten gehört " + mail.ownerName(mailbox) + ".", NamedTextColor.RED));
            return;
        }

        mail.open(player, mailbox);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (!DisplayObjects.isPart(MailboxObject.TYPE, event.getAttacked())) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.isSneaking() || !player.hasPermission(MANAGE_PERMISSION)) {
            return;
        }

        Optional<DisplayObject> found = DisplayObjects.resolve(MailboxObject.TYPE, event.getAttacked());
        if (found.isEmpty()) {
            return;
        }

        DisplayObject mailbox = found.get();
        Location location = mailbox.location();
        mail.dropAll(mailbox);
        DisplayObjects.remove(mailbox);
        if (location != null) {
            location.getWorld().playSound(location, Sound.BLOCK_WOOD_BREAK, 1.0F, 1.0F);
            if (player.getGameMode() != GameMode.CREATIVE) {
                location.getWorld().dropItemNaturally(location, MailboxItems.create(1));
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (DisplayObjects.isPart(MailboxObject.TYPE, event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private void postLetter(Player player, DisplayObject mailbox, ItemStack held) {
        if (!player.hasPermission(USE_PERMISSION)) {
            player.sendMessage(Component.text("Dir fehlt die Berechtigung zum Einwerfen.", NamedTextColor.RED));
            return;
        }

        if (mail.insert(player, mailbox, held) && player.getGameMode() != GameMode.CREATIVE) {
            player.getInventory().setItem(EquipmentSlot.HAND, held.subtract());
        }
    }
}
