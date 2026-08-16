package de.pumpecraft.mailbox.listener;

import de.pumpecraft.mailbox.MailboxItems;
import de.pumpecraft.mailbox.MailboxObject;
import de.pumpecraft.mailbox.MailboxService;
import de.pumpecraft.utils.objects.DisplayObjects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class MailboxPlaceListener implements Listener {
    private static final String PLACE_PERMISSION = "pumpecraft.mailbox.place";
    private static final double OCCUPIED_RADIUS = 0.6D;

    private final MailboxService service;

    public MailboxPlaceListener(MailboxService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        ItemStack item = event.getItem();
        if (!MailboxItems.isMailbox(item)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission(PLACE_PERMISSION)) {
            player.sendMessage(Component.text("Dir fehlt die Berechtigung zum Aufstellen.", NamedTextColor.RED));
            return;
        }

        Block target = event.getClickedBlock().getRelative(event.getBlockFace());
        if (!target.isEmpty()) {
            player.sendMessage(Component.text("Hier ist kein Platz für den Briefkasten.", NamedTextColor.RED));
            return;
        }

        Location base = DisplayObjects.baseOf(target);
        if (DisplayObjects.nearest(MailboxObject.TYPE, base, OCCUPIED_RADIUS).isPresent()) {
            player.sendMessage(Component.text("Hier steht schon ein Briefkasten.", NamedTextColor.RED));
            return;
        }

        if (!service.place(player, base)) {
            return;
        }

        target.getWorld().playSound(base, Sound.BLOCK_WOOD_PLACE, 1.0F, 1.0F);
        if (player.getGameMode() != GameMode.CREATIVE) {
            player.getInventory().setItem(EquipmentSlot.HAND, item.subtract());
        }
    }
}
