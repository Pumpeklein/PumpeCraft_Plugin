package de.pumpecraft.enchants.listener;

import de.pumpecraft.enchants.EnchantChains;
import de.pumpecraft.enchants.mining.BlockMiningRules;
import de.pumpecraft.enchants.integration.LuckyDrops;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public final class BlockEnchantListener implements Listener {
    private final BlockMiningRules rules;
    private final LuckyDrops lucky;
    private final EnchantChains chains;

    public BlockEnchantListener(BlockMiningRules rules, LuckyDrops lucky, EnchantChains chains) {
        this.rules = rules;
        this.lucky = lucky;
        this.chains = chains;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // A whole vein is one action of the player: it neither chains again nor rolls again.
        if (chains.active(event.getPlayer().getUniqueId())) {
            return;
        }
        rules.apply(event);
        lucky.roll(event.getPlayer(), event.getPlayer().getInventory().getItemInMainHand());
    }
}
