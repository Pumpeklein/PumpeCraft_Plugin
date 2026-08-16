package de.pumpecraft.mailbox.listener;

import de.pumpecraft.mailbox.MailboxItems;
import de.pumpecraft.mailbox.MailboxObject;
import de.pumpecraft.mailbox.MailboxService;
import de.pumpecraft.mailbox.MailboxSettings;
import de.pumpecraft.utils.objects.DisplayObject;
import de.pumpecraft.utils.objects.DisplayObjects;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
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
    private final MailboxService service;

    public MailboxInteractListener(MailboxSettings settings, MailboxService service) {
        this.settings = settings;
        this.service = service;
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

        if (!service.canOpen(player, mailbox)) {
            player.sendMessage(Component.text(
                "Dieser Briefkasten gehört " + service.ownerName(mailbox)
                    + ". Nutze /mailbox send " + service.ownerName(mailbox) + ".", NamedTextColor.RED));
            return;
        }

        service.open(player, mailbox);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (!DisplayObjects.isPart(MailboxObject.TYPE, event.getAttacked())) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        Optional<DisplayObject> found = DisplayObjects.resolve(MailboxObject.TYPE, event.getAttacked());
        if (found.isEmpty()) {
            return;
        }

        DisplayObject mailbox = found.get();
        if (!service.isOwner(player, mailbox) && !player.hasPermission(MANAGE_PERMISSION)) {
            player.sendMessage(Component.text("Das ist nicht dein Briefkasten.", NamedTextColor.RED));
            return;
        }

        service.remove(player, mailbox, true);
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

        if (service.insert(player, mailbox, held) && player.getGameMode() != GameMode.CREATIVE) {
            player.getInventory().setItem(EquipmentSlot.HAND, held.subtract());
        }
    }
}
