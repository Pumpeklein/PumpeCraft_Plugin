package de.pumpecraft.enchants.listener;

import de.pumpecraft.enchants.anvil.AnvilCombination;
import de.pumpecraft.enchants.anvil.AnvilCombiner;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.Plugin;

public final class AnvilEnchantListener implements Listener {
    private final Plugin plugin;
    private final AnvilCombiner combiner;
    private final int levelCost;

    public AnvilEnchantListener(Plugin plugin, AnvilCombiner combiner, int levelCost) {
        this.plugin = plugin;
        this.combiner = combiner;
        this.levelCost = levelCost;
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent event) {
        AnvilView view = event.getView();
        AnvilCombination combination = combiner.combine(view.getItem(0), view.getItem(1));
        if (!combination.handled()) {
            return;
        }

        HumanEntity viewer = view.getPlayer();
        if (combination.result() == null) {
            viewer.sendActionBar(Component.text(combination.rejection(), NamedTextColor.RED));
            return;
        }

        ItemStack result = combination.result();
        rename(result, view.getRenameText());
        event.setResult(result);
        view.setMaximumRepairCost(Math.max(view.getMaximumRepairCost(), levelCost));
        view.setRepairCost(levelCost);
        // The client keeps the result slot and the level cost of the vanilla calculation until the
        // window is sent again, so without this the anvil looks empty and refuses the take.
        if (viewer instanceof Player player) {
            plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);
        }
    }

    private void rename(ItemStack item, String renameText) {
        if (renameText == null || renameText.isBlank()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(renameText).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
    }
}
