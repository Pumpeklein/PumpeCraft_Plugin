package de.pumpecraft.enchants.mining;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Breaks the blocks of a chain one by one. Every block goes through a real {@link BlockBreakEvent}:
 * only then can plot protection refuse it and the skill plugin count it - silently setting the
 * block to air would walk through both.
 */
final class ChainBreaker {
    record Result(List<ItemStack> drops, int broken) {
    }

    Result breakAll(Player player, List<Block> blocks) {
        List<ItemStack> drops = new ArrayList<>();
        int broken = 0;
        for (Block block : blocks) {
            ItemStack tool = player.getInventory().getItemInMainHand();
            if (tool.getType().isAir()) {
                break;
            }
            BlockBreakEvent event = new BlockBreakEvent(block, player);
            event.setDropItems(false);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                continue;
            }
            drops.addAll(block.getDrops(tool, player));
            block.setType(Material.AIR);
            player.damageItemStack(EquipmentSlot.HAND, 1);
            broken++;
        }
        return new Result(drops, broken);
    }
}
