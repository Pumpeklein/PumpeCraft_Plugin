package de.pumpecraft.enchants.mining;

import de.pumpecraft.enchants.EnchantRegistry;
import de.pumpecraft.enchants.EnchantService;
import de.pumpecraft.utils.Items;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

public final class BlockMiningRules {
    private final EnchantService enchants;
    private final DropSmelter smelter = new DropSmelter();
    private final DropDelivery delivery = new DropDelivery();

    public BlockMiningRules(EnchantService enchants) {
        this.enchants = enchants;
    }

    public void apply(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!event.isDropItems() || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        boolean telekinesis = enchants.activeLevel(tool, EnchantRegistry.TELEKINESIS) > 0;
        // Silk touch drops the block itself, and smelting an ore block yields its ingot - that
        // would quietly undo what the player picked silk touch for.
        boolean furnace = enchants.activeLevel(tool, EnchantRegistry.FURNACE) > 0
            && tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) == 0;
        if (!telekinesis && !furnace) {
            return;
        }

        Block block = event.getBlock();
        // Fortune already multiplied the raw drops here, so smelting each stack keeps the bonus.
        List<ItemStack> drops = new ArrayList<>(block.getDrops(tool, player));
        List<ItemStack> processed = furnace ? smelter.smelt(block.getType(), drops) : drops;

        event.setDropItems(false);
        delivery.deliver(player, block.getLocation().toCenterLocation(),
            Items.merge(processed), telekinesis);
        if (telekinesis && event.getExpToDrop() > 0) {
            player.giveExp(event.getExpToDrop(), true);
            event.setExpToDrop(0);
        }
    }
}
