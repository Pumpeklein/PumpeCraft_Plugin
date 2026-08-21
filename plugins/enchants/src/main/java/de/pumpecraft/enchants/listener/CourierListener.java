package de.pumpecraft.enchants.listener;

import de.pumpecraft.enchants.integration.Courier;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class CourierListener implements Listener {
    private final Courier courier;

    public CourierListener(Courier courier) {
        this.courier = courier;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
            || !event.getPlayer().isSneaking()
            || (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        if (!courier.handles(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        courier.ship(event.getPlayer());
    }
}
