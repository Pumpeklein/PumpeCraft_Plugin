package de.pumpecraft.mailbox.listener;

import de.pumpecraft.mailbox.MailboxItems;
import de.pumpecraft.mailbox.MailboxService;
import de.pumpecraft.mailbox.box.MailboxHolder;
import de.pumpecraft.mailbox.mail.SendHolder;
import de.pumpecraft.mailbox.mail.SendMenu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class MailboxInventoryListener implements Listener {
    private final Plugin plugin;
    private final MailboxService service;

    public MailboxInventoryListener(Plugin plugin, MailboxService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof MailboxHolder) {
            if (MailboxItems.isReservation(event.getCurrentItem())
                || MailboxItems.isReservation(event.getCursor())) {
                event.setCancelled(true);
            }
            return;
        }

        if (!(holder instanceof SendHolder sendHolder) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot >= 0 && SendMenu.isControlSlot(slot)) {
            event.setCancelled(true);
            ItemStack button = event.getCurrentItem();
            if (MailboxItems.isConfirm(button)) {
                service.confirmSend(player, event.getInventory(), sendHolder);
            } else if (MailboxItems.isCancel(button)) {
                player.closeInventory();
            }
            return;
        }

        if (MailboxItems.isMenuItem(event.getCurrentItem()) || MailboxItems.isMenuItem(event.getCursor())) {
            event.setCancelled(true);
            return;
        }

        refreshLater(player, event.getInventory(), sendHolder);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof MailboxHolder) {
            boolean touchesReservation = event.getRawSlots().stream()
                .map(slot -> event.getView().getItem(slot))
                .anyMatch(MailboxItems::isReservation);
            if (touchesReservation || MailboxItems.isReservation(event.getOldCursor())) {
                event.setCancelled(true);
            }
            return;
        }

        if (!(holder instanceof SendHolder sendHolder) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getRawSlots().stream().anyMatch(SendMenu::isControlSlot)) {
            event.setCancelled(true);
            return;
        }

        refreshLater(player, event.getInventory(), sendHolder);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof MailboxHolder) {
            service.closed(event.getInventory(), event.getPlayer());
            return;
        }

        if (holder instanceof SendHolder sendHolder
            && !sendHolder.processing()
            && event.getPlayer() instanceof Player player) {
            service.returnSendItems(player, event.getInventory());
        }
    }

    private void refreshLater(Player player, Inventory inventory, SendHolder holder) {
        Bukkit.getScheduler().runTask(plugin, () -> service.refreshSendMenu(player, inventory, holder));
    }
}
