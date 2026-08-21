package de.pumpecraft.bases.listener;

import de.pumpecraft.bases.BaseText;
import de.pumpecraft.bases.PumpeBaseSystemPlugin;
import de.pumpecraft.bases.plot.PlotArea;
import de.pumpecraft.bases.plot.PlotPricing;
import de.pumpecraft.bases.plot.PlotSelections;
import de.pumpecraft.bases.plot.PlotService;
import de.pumpecraft.bases.plot.PlotTool;
import de.pumpecraft.bases.plot.PlotVisualizer;
import de.pumpecraft.transactions.core.Currency;
import de.pumpecraft.utils.Texts;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class PlotToolListener implements Listener {
    private static final int PREVIEW_SECONDS = 60;

    private final PumpeBaseSystemPlugin plugin;
    private final PlotService plots;
    private final PlotTool tool;
    private final PlotVisualizer visualizer;

    public PlotToolListener(
        PumpeBaseSystemPlugin plugin,
        PlotService plots,
        PlotTool tool,
        PlotVisualizer visualizer
    ) {
        this.plugin = plugin;
        this.plots = plots;
        this.tool = tool;
        this.visualizer = visualizer;
    }

    /**
     * LOWEST und abgebrochen: Danach überspringen der Grundstücksschutz und der Blockabbau dieses
     * Ereignis, das Messer setzt also auch auf fremdem Boden eine Ecke, ohne dort etwas anzurühren.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null
            || event.getHand() != EquipmentSlot.HAND
            || !tool.isTool(event.getItem())) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission(plugin.permission("plot-claim"))) {
            return;
        }
        boolean first = event.getAction() == Action.LEFT_CLICK_BLOCK;
        if (!first && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true);

        Location corner = block.getLocation();
        if (first) {
            plots.selections().first(player, corner);
        } else {
            plots.selections().second(player, corner);
        }
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6F, first ? 1.2F : 1.6F);
        player.sendMessage(BaseText.label(
            first ? "Erste Ecke: " : "Zweite Ecke: ",
            corner.getBlockX() + " / " + corner.getBlockZ(),
            NamedTextColor.WHITE));
        describeSelection(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plots.selections().clear(event.getPlayer());
        visualizer.hide(event.getPlayer());
    }

    private void describeSelection(Player player) {
        PlotSelections.Selection selection = plots.selections().of(player);
        if (selection == null || !selection.complete()) {
            return;
        }
        PlotArea area = selection.area();
        String rejection = plots.rejectionFor(player, area);
        // Die Vorschau steht sofort, auch wenn die Auswahl noch nicht kaufbar ist - gerade dann
        // zeigt sie, woran es liegt.
        visualizer.showSelection(player, area, rejection == null, PREVIEW_SECONDS);
        if (rejection != null) {
            player.sendMessage(BaseText.error(rejection));
            return;
        }
        PlotPricing.Quote quote = plots.pricing().quote(area);
        player.sendMessage(BaseText.label("Fläche: ",
                area.width() + " × " + area.depth() + " = "
                    + Texts.number(quote.blocks()) + " Blöcke", NamedTextColor.WHITE)
            .append(BaseText.hint("  ·  Lage " + Math.round(quote.factor() * 100.0D) + " %")));
        player.sendMessage(BaseText.label("Preis: ",
            Currency.format(quote.price()), Currency.COLOR)
            .append(BaseText.hint("  ·  /plot kaufen <Name>")));
    }
}
