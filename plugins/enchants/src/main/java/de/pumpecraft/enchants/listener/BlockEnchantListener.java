package de.pumpecraft.enchants.listener;

import de.pumpecraft.enchants.mining.BlockMiningRules;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public final class BlockEnchantListener implements Listener {
    private final BlockMiningRules rules;

    public BlockEnchantListener(BlockMiningRules rules) {
        this.rules = rules;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        rules.apply(event);
    }
}
