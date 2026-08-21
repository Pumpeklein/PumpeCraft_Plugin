package de.pumpecraft.mod.spectate;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class SpectateListener implements Listener {
    private final SpectateService spectate;

    public SpectateListener(SpectateService spectate) {
        this.spectate = spectate;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        spectate.handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        Player viewer = event.getPlayer();
        if (!event.isSneaking() || !spectate.isSpectating(viewer)) {
            return;
        }

        event.setCancelled(true);
        if (spectate.stop(viewer)) {
            viewer.sendMessage("Spectate beendet.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player viewer = event.getPlayer();
        if (!spectate.isSpectating(viewer)) {
            return;
        }

        event.setCancelled(true);
        int slotDistance = Math.floorMod(event.getNewSlot() - event.getPreviousSlot(), 9);
        int direction = slotDistance > 0 && slotDistance <= 4 ? 1 : -1;
        SpectateService.ZoomResult zoom = spectate.adjustZoom(viewer, direction);
        if (zoom != null) {
            Component value = zoom.firstPerson()
                    ? Component.text("First Person", NamedTextColor.AQUA)
                    : Component.text(
                            String.format(java.util.Locale.ROOT, "%.2f Blöcke", zoom.distance()),
                            NamedTextColor.AQUA);
            viewer.sendActionBar(Component.text("Kamera: ", NamedTextColor.GRAY).append(value));
        }
    }
}
