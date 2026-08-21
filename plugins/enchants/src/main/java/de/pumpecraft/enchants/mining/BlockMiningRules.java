package de.pumpecraft.enchants.mining;

import de.pumpecraft.enchants.EnchantRegistry;
import de.pumpecraft.enchants.EnchantService;
import de.pumpecraft.utils.Items;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

public final class BlockMiningRules {
    private final EnchantService enchants;
    private final DropSmelter smelter = new DropSmelter();
    private final FortuneApplicator fortune = new FortuneApplicator();
    private final DropDelivery delivery = new DropDelivery();

    public BlockMiningRules(EnchantService enchants) {
        this.enchants = enchants;
    }

    public void apply(BlockBreakEvent event) {
        if (!event.isDropItems() || event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        boolean telekinesis = enchants.activeLevel(tool, EnchantRegistry.TELEKINESIS) > 0;
        boolean furnace = enchants.activeLevel(tool, EnchantRegistry.FURNACE) > 0;
        if (!telekinesis && !furnace) {
            return;
        }

        ItemStack collectionTool = furnace ? withoutFortune(tool) : tool;
        List<ItemStack> collected = new ArrayList<>(
            event.getBlock().getDrops(collectionTool, event.getPlayer()));
        List<ItemStack> processed = furnace
            ? smelter.smelt(event.getBlock().getType(), collected)
            : collected;
        if (furnace && tool.getEnchantmentLevel(Enchantment.FORTUNE) > 0) {
            List<ItemStack> fortunate = new ArrayList<>(
                event.getBlock().getDrops(tool, event.getPlayer()));
            processed = fortune.apply(processed, collected, fortunate);
        }
        processed = Items.merge(processed);

        event.setDropItems(false);
        delivery.deliver(event.getPlayer(), event.getBlock().getLocation().add(0.5, 0.5, 0.5),
            processed, telekinesis);
        if (telekinesis && event.getExpToDrop() > 0) {
            event.getPlayer().giveExp(event.getExpToDrop());
            event.setExpToDrop(0);
        }
    }

    private ItemStack withoutFortune(ItemStack tool) {
        ItemStack copy = tool.clone();
        copy.removeEnchantment(Enchantment.FORTUNE);
        return copy;
    }
}
