package de.pumpecraft.enchants.listener;

import de.pumpecraft.enchants.anvil.AnvilCombination;
import de.pumpecraft.enchants.anvil.AnvilCombiner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;

public final class AnvilEnchantListener implements Listener {
    private final AnvilCombiner combiner;
    private final int levelCost;

    public AnvilEnchantListener(AnvilCombiner combiner, int levelCost) {
        this.combiner = combiner;
        this.levelCost = levelCost;
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent event) {
        ItemStack target = event.getInventory().getItem(0);
        ItemStack addition = event.getInventory().getItem(1);
        AnvilCombination combination = combiner.combine(target, addition);
        if (!combination.handled()) {
            return;
        }
        event.setResult(combination.result());
        if (combination.result() != null) {
            event.getView().setRepairCost(levelCost);
        } else {
            event.getView().getPlayer().sendActionBar(
                Component.text(combination.rejection(), NamedTextColor.RED));
        }
    }
}
