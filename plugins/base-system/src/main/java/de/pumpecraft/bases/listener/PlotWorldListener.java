package de.pumpecraft.bases.listener;

import de.pumpecraft.bases.BaseText;
import de.pumpecraft.bases.plot.PlotFlag;
import de.pumpecraft.bases.plot.PlotGuard;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;

/**
 * Flaggen, die keine Erlaubnis beschreiben, sondern eine Eigenschaft des Ortes: Was zerfällt hier,
 * was bildet sich, was bleibt liegen. Sie gelten für jeden, auch für den Besitzer.
 */
public final class PlotWorldListener implements Listener {
    private final PlotGuard guard;

    public PlotWorldListener(PlotGuard guard) {
        this.guard = guard;
    }

    /** Schmelzendes Eis und absterbende Korallen sind dasselbe Ereignis mit anderem Block. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        Material material = event.getBlock().getType();
        PlotFlag flag = coral(material) ? PlotFlag.CORAL_DECAY
            : meltable(material) ? PlotFlag.ICE_MELT
            : null;
        if (flag != null && !guard.allowsHere(event.getBlock().getLocation(), flag)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        Material result = event.getNewState().getType();
        if ((result == Material.SNOW || meltable(result))
            && !guard.allowsHere(event.getBlock().getLocation(), PlotFlag.SNOW_FORM)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (!guard.allowsHere(event.getBlock().getLocation(), PlotFlag.LEAF_DECAY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (!guard.canSleep(event.getPlayer(), event.getBed().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(BaseText.error("Hier darfst du nicht schlafen."));
        }
    }

    private boolean meltable(Material material) {
        return material == Material.ICE
            || material == Material.FROSTED_ICE
            || material == Material.SNOW
            || material == Material.SNOW_BLOCK;
    }

    private boolean coral(Material material) {
        return Tag.CORALS.isTagged(material)
            || Tag.CORAL_BLOCKS.isTagged(material)
            || Tag.WALL_CORALS.isTagged(material);
    }
}
