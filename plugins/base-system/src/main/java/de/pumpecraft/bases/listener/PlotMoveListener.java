package de.pumpecraft.bases.listener;

import de.pumpecraft.bases.BaseText;
import de.pumpecraft.bases.plot.Plot;
import de.pumpecraft.bases.plot.PlotEviction;
import de.pumpecraft.bases.plot.PlotGuard;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Zeigt beim Betreten und Verlassen an, wessen Boden man gerade betritt, und hält Fremde von
 * Grundstücken fern, deren Betreten-Flagge aus ist.
 *
 * <p>Wer schon drinsteht, wird hinausgesetzt statt festgehalten: Ein einfaches Abbrechen jedes
 * Schritts würde jemanden, dem gerade die Rechte entzogen wurden, auf dem Grundstück einsperren.
 */
public final class PlotMoveListener implements Listener {
    private final PlotGuard guard;
    private final Map<UUID, Long> currentPlot = new HashMap<>();

    public PlotMoveListener(PlotGuard guard) {
        this.guard = guard;
    }

    /** Teleports kommen hier mit an: {@code PlayerTeleportEvent} ist ein {@code PlayerMoveEvent}. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        // Nur wenn sich der Block ändert; sonst liefe das hier bei jeder Kopfdrehung.
        if (from.getBlockX() == to.getBlockX()
            && from.getBlockZ() == to.getBlockZ()
            && from.getWorld().equals(to.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        // Höhenbegrenzte Gebiete gelten nur in ihren Schichten; wer darüber hinweggeht, betritt
        // sie nicht.
        Plot target = guard.at(to);
        if (target != null && !guard.canEnter(player, target)) {
            Plot origin = guard.at(from);
            if (origin == target) {
                evict(player, target);
            } else {
                event.setCancelled(true);
                player.sendActionBar(BaseText.error(
                    "Das Grundstück " + target.name() + " ist für dich gesperrt."));
            }
            return;
        }
        announce(player, target);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        currentPlot.remove(event.getPlayer().getUniqueId());
    }

    /** Setzt jemanden vor die Grenze, dem die Rechte entzogen wurden, während er darauf stand. */
    public void evict(Player player, Plot plot) {
        Location outside = PlotEviction.outside(player.getLocation(), plot.area());
        if (outside == null) {
            return;
        }
        player.teleport(outside, PlayerTeleportEvent.TeleportCause.PLUGIN);
        player.sendMessage(BaseText.error(
            "Du wurdest vom Grundstück " + plot.name() + " gesetzt."));
    }

    private void announce(Player player, Plot plot) {
        Long previous = currentPlot.get(player.getUniqueId());
        long id = plot == null ? 0L : plot.id();
        if (previous != null && previous == id) {
            return;
        }
        currentPlot.put(player.getUniqueId(), id);
        if (plot != null) {
            player.sendActionBar(Component.text("Grundstück ", NamedTextColor.GRAY)
                .append(Component.text(plot.name(), NamedTextColor.GOLD))
                .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(Component.text(plot.ownerName(), NamedTextColor.WHITE)));
        } else if (previous != null && previous != 0L) {
            player.sendActionBar(Component.text("Freies Land", NamedTextColor.DARK_GRAY));
        }
    }
}
