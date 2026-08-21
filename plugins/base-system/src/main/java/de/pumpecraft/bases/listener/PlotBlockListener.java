package de.pumpecraft.bases.listener;

import de.pumpecraft.bases.BaseText;
import de.pumpecraft.bases.plot.Interactables;
import de.pumpecraft.bases.plot.Plot;
import de.pumpecraft.bases.plot.PlotFlag;
import de.pumpecraft.bases.plot.PlotGuard;
import de.pumpecraft.utils.Cooldowns;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Setzt die Flaggen eines Grundstücks für alles durch, was mit Blöcken passiert. Kolben und
 * Explosionen zählen mit dazu: Ohne sie ließe sich jede Grenze von außen umgehen.
 */
public final class PlotBlockListener implements Listener {
    private static final long NOTICE_MILLIS = 2_000L;

    private final PlotGuard guard;
    private final Cooldowns<UUID> notices = new Cooldowns<>();

    public PlotBlockListener(PlotGuard guard) {
        this.guard = guard;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!guard.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            deny(event.getPlayer(), event.getBlock().getLocation());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!guard.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            deny(event.getPlayer(), event.getBlock().getLocation());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!guard.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            deny(event.getPlayer(), event.getBlock().getLocation());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!guard.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            deny(event.getPlayer(), event.getBlock().getLocation());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Player player = event.getPlayer();
        if (event.getAction() == Action.PHYSICAL) {
            if (block.getType() == Material.FARMLAND && !flag(block.getLocation(), PlotFlag.TRAMPLE)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // Reihenfolge mit Absicht: Ein Bett entscheidet sich an der Schlafen-Flagge, ein Behälter
        // an der Behälter-Flagge, alles Übrige an Benutzen. Jeder Block hat genau einen Zuständigen.
        boolean allowed;
        if (Tag.BEDS.isTagged(block.getType())) {
            allowed = guard.canSleep(player, block.getLocation());
        } else if (block.getState(false) instanceof InventoryHolder) {
            allowed = guard.canOpenContainer(player, block.getLocation());
        } else {
            allowed = !Interactables.isInteractable(block.getType())
                || guard.canInteract(player, block.getLocation());
        }
        if (!allowed) {
            deny(player, block.getLocation());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getPlayer() != null) {
            if (!guard.canBuild(event.getPlayer(), event.getBlock().getLocation())) {
                deny(event.getPlayer(), event.getBlock().getLocation());
                event.setCancelled(true);
            }
            return;
        }
        if (!flag(event.getBlock().getLocation(), PlotFlag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (!flag(event.getBlock().getLocation(), PlotFlag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() == Material.FIRE
            && !flag(event.getBlock().getLocation(), PlotFlag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> !flag(block.getLocation(), PlotFlag.EXPLOSIONS));
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> !flag(block.getLocation(), PlotFlag.EXPLOSIONS));
    }

    /**
     * Ein Kolben darf keine Grenze überschreiten - weder hinein noch hinaus. Sonst reichte ein
     * Aufbau direkt vor dem Grundstück, um darin Blöcke zu verschieben.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (crossesBorder(event.getBlock(), event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (crossesBorder(event.getBlock(), event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    private boolean crossesBorder(Block piston, Iterable<Block> moved) {
        Plot origin = guard.at(piston.getLocation());
        for (Block block : moved) {
            if (guard.at(block.getLocation()) != origin) {
                return true;
            }
        }
        return false;
    }

    private boolean flag(Location location, PlotFlag flag) {
        Plot plot = guard.at(location);
        return plot == null || plot.flag(flag);
    }

    private void deny(Player player, Location location) {
        Plot plot = guard.at(location);
        if (plot == null || !notices.tryAcquire(player.getUniqueId(), NOTICE_MILLIS)) {
            return;
        }
        player.sendActionBar(BaseText.error("Das Grundstück " + plot.name() + " gehört "
            + plot.ownerName() + "."));
    }
}
