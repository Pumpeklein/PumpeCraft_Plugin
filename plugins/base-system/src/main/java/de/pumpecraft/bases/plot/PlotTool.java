package de.pumpecraft.bases.plot;

import de.pumpecraft.bases.PlotSettings;
import de.pumpecraft.utils.Menus;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Der Grundstücksmesser. Erkannt wird er an einer Markierung im Item, nicht am Material - sonst
 * würde jede Goldschaufel im Spiel zum Werkzeug und niemand könnte damit noch graben.
 */
public final class PlotTool {
    private final NamespacedKey key;
    private final PlotSettings settings;

    public PlotTool(Plugin plugin, PlotSettings settings) {
        this.key = new NamespacedKey(plugin, "plot_tool");
        this.settings = settings;
    }

    public ItemStack create() {
        ItemStack item = Menus.item(
            settings.selectionTool(),
            Menus.text("Grundstücksmesser", NamedTextColor.GOLD),
            List.of(
                Menus.text("Linksklick: erste Ecke", NamedTextColor.GRAY),
                Menus.text("Rechtsklick: zweite Ecke", NamedTextColor.GRAY),
                Component.empty(),
                Menus.text("/plot kosten zeigt den Preis", NamedTextColor.DARK_GRAY))
        );
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Nach dem Kauf hat das Messer seinen Zweck erfüllt und verschwindet aus dem Inventar. */
    public void takeFrom(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isTool(contents[slot])) {
                player.getInventory().setItem(slot, null);
            }
        }
        player.updateInventory();
    }

    public boolean isTool(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
            .has(key, PersistentDataType.BYTE);
    }
}
