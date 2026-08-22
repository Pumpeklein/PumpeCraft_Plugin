package de.pumpecraft.enchants.listener;

import de.pumpecraft.enchants.EnchantService;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;

public final class EnchantBookMigrationListener implements Listener {
    private final EnchantService enchants;

    public EnchantBookMigrationListener(EnchantService enchants) {
        this.enchants = enchants;
    }

    public int migrateLoadedWorlds(Iterable<World> worlds) {
        int changed = 0;
        for (World world : worlds) {
            for (Chunk chunk : world.getLoadedChunks()) {
                changed += migrate(chunk);
            }
        }
        return changed;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        migrate(event.getPlayer().getInventory());
        migrate(event.getPlayer().getEnderChest());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        migrate(event.getInventory());
        migrate(event.getPlayer().getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        migrate(event.getChunk());
    }

    private int migrate(Chunk chunk) {
        int changed = 0;
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof InventoryHolder holder) {
                changed += migrate(holder.getInventory());
            }
        }
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Item dropped) {
                ItemStack stack = dropped.getItemStack();
                if (migrate(stack)) {
                    dropped.setItemStack(stack);
                    changed++;
                }
            } else if (entity instanceof ItemFrame frame) {
                ItemStack stack = frame.getItem();
                if (migrate(stack)) {
                    frame.setItem(stack);
                    changed++;
                }
            } else if (entity instanceof InventoryHolder holder && !(entity instanceof Player)) {
                changed += migrate(holder.getInventory());
            }
        }
        return changed;
    }

    private int migrate(Inventory inventory) {
        int changed = 0;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (migrate(stack)) {
                inventory.setItem(slot, stack);
                changed++;
            }
        }
        return changed;
    }

    private boolean migrate(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        boolean changed = enchants.upgradeBook(stack);
        if (stack.getItemMeta() instanceof BlockStateMeta blockMeta
            && blockMeta.getBlockState() instanceof Container container) {
            if (migrate(container.getInventory()) > 0) {
                blockMeta.setBlockState(container);
                stack.setItemMeta(blockMeta);
                changed = true;
            }
        } else if (stack.getItemMeta() instanceof BundleMeta bundleMeta) {
            List<ItemStack> contents = new ArrayList<>(bundleMeta.getItems());
            boolean bundleChanged = false;
            for (ItemStack content : contents) {
                bundleChanged |= migrate(content);
            }
            if (bundleChanged) {
                bundleMeta.setItems(contents);
                stack.setItemMeta(bundleMeta);
                changed = true;
            }
        }
        return changed;
    }
}
