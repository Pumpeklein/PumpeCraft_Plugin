package de.pumpecraft.bases.gui;

import de.pumpecraft.utils.Menus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.Plugin;

/**
 * Texteingabe im Menü über einen Amboss.
 *
 * <p>Eine Truhe kennt keine Tastatur. Der Amboss ist die einzige Oberfläche, in der ein Spieler
 * tippen kann, ohne das Menü zu verlassen und in den Chat zu wechseln - deshalb läuft hier alles
 * über ihn: der Name eines neuen Grundstücks ebenso wie die Suche nach einem Spieler.
 */
public final class TextInput implements Listener {
    private static final int RESULT_SLOT = 2;

    private final Plugin plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public TextInput(Plugin plugin) {
        this.plugin = plugin;
    }

    public void open(
        Player player,
        Component title,
        String initial,
        String hint,
        Consumer<String> onConfirm
    ) {
        AnvilView view = MenuType.ANVIL.create(player, title);
        view.getTopInventory().setItem(0, label(initial, hint));
        pending.put(player.getUniqueId(), new Pending(view, onConfirm));
        // Der Aufruf kommt aus einem Klick im Menü; ein Fensterwechsel im selben Tick lässt den
        // Client mit dem alten Fenster zurück.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.openInventory(view);
            }
        });
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent event) {
        Pending waiting = pending.get(event.getView().getPlayer().getUniqueId());
        if (waiting == null || waiting.view != event.getView()) {
            return;
        }
        String typed = event.getView().getRenameText();
        event.getView().setRepairCost(0);
        event.setResult(typed == null || typed.isBlank()
            ? null
            : label(typed, "Klicken zum Bestätigen"));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Pending waiting = pending.get(player.getUniqueId());
        if (waiting == null || waiting.view != event.getView()) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() != RESULT_SLOT || event.getCurrentItem() == null) {
            return;
        }
        String typed = waiting.view.getRenameText();
        if (typed == null || typed.isBlank()) {
            return;
        }
        pending.remove(player.getUniqueId());
        empty(waiting.view);
        player.closeInventory();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                waiting.onConfirm.accept(typed.trim());
            }
        });
    }

    /**
     * Nur das eigene Fenster zählt. Der Amboss wird aus einem offenen Menü heraus geöffnet, und
     * dessen Schließen meldet sich zuerst - ein bedingungsloses Austragen löschte den Eintrag,
     * bevor der Amboss überhaupt aufgeht.
     */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Pending waiting = pending.get(event.getPlayer().getUniqueId());
        if (waiting == null || waiting.view != event.getView()) {
            return;
        }
        pending.remove(event.getPlayer().getUniqueId());
        empty(waiting.view);
    }

    /**
     * Ein Amboss händigt seine Eingabeslots beim Abräumen dem Spieler aus - sonst verlöre man dort
     * echte Gegenstände. Das Namensschild ist aber nur Trägerin des Textes und darf nicht im
     * Inventar landen; es wird deshalb geleert, bevor der Server den Behälter abräumt.
     */
    private void empty(AnvilView view) {
        for (int slot = 0; slot < 3; slot++) {
            view.getTopInventory().setItem(slot, null);
        }
    }

    private ItemStack label(String text, String hint) {
        return Menus.item(Material.NAME_TAG,
            Menus.text(text == null || text.isBlank() ? " " : text, NamedTextColor.GOLD),
            List.of(Menus.text(hint, NamedTextColor.GRAY)));
    }

    private record Pending(AnvilView view, Consumer<String> onConfirm) {
    }
}
