package de.pumpecraft.mod.spectate;

import de.pumpecraft.mod.spectate.SpectateMenuHolder.Entry;
import de.pumpecraft.utils.Menus;
import de.pumpecraft.utils.Texts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

/**
 * Auswahl der Spieler als Truhenmenü. Der Spectator-Modus bringt zwar ein eigenes Menü mit, das
 * sich aber nur über die Zifferntasten bedienen lässt; hier wählt ein Linksklick aus.
 */
public final class SpectateMenu {
    private static final int SIZE = 54;
    private static final int FIRST_ENTRY_SLOT = 9;
    private static final int LAST_ENTRY_SLOT = 44;
    private static final int PAGE_SIZE = LAST_ENTRY_SLOT - FIRST_ENTRY_SLOT + 1;

    private final Plugin plugin;
    private final SpectateService spectate;

    public SpectateMenu(Plugin plugin, SpectateService spectate) {
        this.plugin = plugin;
        this.spectate = spectate;
    }

    public void open(Player viewer) {
        SpectateMenuHolder holder = new SpectateMenuHolder(
            Component.text("Spectate", NamedTextColor.GOLD, TextDecoration.BOLD), SIZE);
        holder.candidates(candidates(viewer));
        render(viewer, holder);
        // Der Aufruf kommt aus einem Klick oder einem Tastendruck; ein Fensterwechsel im selben
        // Tick lässt den Client mit dem alten Fenster zurück.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline()) {
                viewer.openInventory(holder.getInventory());
                viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.4F, 1.4F);
            }
        });
    }

    void click(Player viewer, SpectateMenuHolder holder, int slot, boolean rightClick) {
        Entry entry = holder.entry(slot);
        switch (entry.action()) {
            case NONE -> {
            }
            case CLOSE -> viewer.closeInventory();
            case REFRESH -> {
                holder.candidates(candidates(viewer));
                holder.page(clampPage(holder.page(), holder.candidates().size()));
                render(viewer, holder);
                viewer.updateInventory();
                viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.4F, 1.6F);
            }
            case WATCH -> {
                Player target = Bukkit.getPlayer(entry.playerId());
                if (unavailable(viewer, target)) {
                    return;
                }
                if (rightClick) {
                    viewer.closeInventory();
                    TargetInventories.openInventory(viewer, target);
                    return;
                }
                viewer.closeInventory();
                startWatching(viewer, target);
            }
            case OPEN_INVENTORY -> withTarget(viewer, target -> {
                viewer.closeInventory();
                TargetInventories.openInventory(viewer, target);
            });
            case OPEN_ENDER_CHEST -> withTarget(viewer, target -> {
                viewer.closeInventory();
                TargetInventories.openEnderChest(viewer, target);
            });
            case RESET_ZOOM -> {
                spectate.resetZoom(viewer);
                render(viewer, holder);
                viewer.updateInventory();
            }
            case STOP -> {
                viewer.closeInventory();
                if (spectate.stop(viewer)) {
                    viewer.sendMessage(Component.text("Beobachtung beendet.", NamedTextColor.GREEN));
                }
            }
            case PAGE_PREVIOUS -> turnPage(viewer, holder, -1);
            case PAGE_NEXT -> turnPage(viewer, holder, 1);
        }
    }

    private void startWatching(Player viewer, Player target) {
        spectate.start(viewer, target);
        viewer.sendMessage(Component.text("Du beobachtest jetzt ", NamedTextColor.GREEN)
            .append(Component.text(target.getName(), NamedTextColor.AQUA))
            .append(Component.text(".", NamedTextColor.GREEN)));
        viewer.sendMessage(controlsHint());
    }

    static Component controlsHint() {
        return Component.text("Mausrad", NamedTextColor.AQUA)
            .append(Component.text(" Zoom · ", NamedTextColor.GRAY))
            .append(Component.text("Linksklick", NamedTextColor.AQUA))
            .append(Component.text(" Menü · ", NamedTextColor.GRAY))
            .append(Component.text("F", NamedTextColor.AQUA))
            .append(Component.text(" Inventar · ", NamedTextColor.GRAY))
            .append(Component.text("Rechtsklick", NamedTextColor.AQUA))
            .append(Component.text(" Enderchest · ", NamedTextColor.GRAY))
            .append(Component.text("Schleichen", NamedTextColor.AQUA))
            .append(Component.text(" oder ", NamedTextColor.GRAY))
            .append(Component.text("Q", NamedTextColor.AQUA))
            .append(Component.text(" beenden", NamedTextColor.GRAY));
    }

    private void turnPage(Player viewer, SpectateMenuHolder holder, int direction) {
        int page = clampPage(holder.page() + direction, holder.candidates().size());
        if (page == holder.page()) {
            return;
        }
        holder.page(page);
        render(viewer, holder);
        viewer.updateInventory();
    }

    private void render(Player viewer, SpectateMenuHolder holder) {
        Inventory inventory = holder.getInventory();
        holder.clearEntries();
        Menus.frame(inventory, FIRST_ENTRY_SLOT, LAST_ENTRY_SLOT);
        Menus.clear(inventory, FIRST_ENTRY_SLOT, LAST_ENTRY_SLOT);

        Player watched = spectate.targetOf(viewer);
        List<UUID> candidates = holder.candidates();
        put(holder, 0, Menus.item(Material.CLOCK,
            Menus.text("Aktualisieren", NamedTextColor.YELLOW),
            List.of(
                Menus.text("Liste neu laden", NamedTextColor.GRAY),
                Menus.action("Klicken"))),
            Entry.of(SpectateAction.REFRESH));
        put(holder, 4, Menus.item(Material.SPYGLASS,
            Menus.text("Spectate", NamedTextColor.GOLD),
            List.of(
                Menus.label("Online: ", Texts.number(candidates.size()), NamedTextColor.WHITE),
                Menus.label("Seite: ", pageLabel(holder), NamedTextColor.WHITE),
                Component.empty(),
                Menus.text("Linksklick: beobachten", NamedTextColor.GRAY),
                Menus.text("Rechtsklick: Inventar", NamedTextColor.GRAY))),
            Entry.NONE);
        put(holder, 8, Menus.item(Material.BARRIER,
            Menus.text("Schließen", NamedTextColor.RED), List.of()),
            Entry.of(SpectateAction.CLOSE));

        int offset = holder.page() * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && offset + index < candidates.size(); index++) {
            Player candidate = Bukkit.getPlayer(candidates.get(offset + index));
            if (candidate == null) {
                continue;
            }
            boolean current = candidate.equals(watched);
            put(holder, FIRST_ENTRY_SLOT + index,
                Menus.head(candidate,
                    Menus.text(candidate.getName(),
                        current ? NamedTextColor.GREEN : NamedTextColor.GOLD),
                    candidateLore(viewer, candidate, current)),
                Entry.of(SpectateAction.WATCH, candidate.getUniqueId(), candidate.getName()));
        }

        putPaging(holder);
        if (watched != null) {
            put(holder, 47, Menus.item(Material.CHEST,
                Menus.text("Inventar von " + watched.getName(), NamedTextColor.AQUA),
                List.of(
                    Menus.text("Spiegelansicht aus PumpeEssentials.", NamedTextColor.GRAY),
                    Menus.action("Klicken zum Öffnen"))),
                Entry.of(SpectateAction.OPEN_INVENTORY));
            put(holder, 48, Menus.item(Material.ENDER_CHEST,
                Menus.text("Enderchest von " + watched.getName(), NamedTextColor.LIGHT_PURPLE),
                List.of(Menus.action("Klicken zum Öffnen"))),
                Entry.of(SpectateAction.OPEN_ENDER_CHEST));
            put(holder, 49, Menus.item(Material.SPYGLASS,
                Menus.text("Zoom zurücksetzen", NamedTextColor.YELLOW),
                List.of(
                    Menus.text("Zurück in die Ego-Perspektive.", NamedTextColor.GRAY),
                    Menus.action("Klicken zum Zurücksetzen"))),
                Entry.of(SpectateAction.RESET_ZOOM));
            put(holder, 50, Menus.item(Material.REDSTONE_BLOCK,
                Menus.text("Beobachtung beenden", NamedTextColor.RED),
                List.of(
                    Menus.text("Zurück an deine Ausgangsposition.", NamedTextColor.GRAY),
                    Menus.action("Klicken zum Beenden"))),
                Entry.of(SpectateAction.STOP));
        }
    }

    private List<Component> candidateLore(Player viewer, Player candidate, boolean current) {
        List<Component> lore = new ArrayList<>();
        lore.add(Menus.label("Welt: ", candidate.getWorld().getName(), NamedTextColor.WHITE));
        lore.add(Menus.label("Position: ",
            Math.round(candidate.getLocation().getX()) + " / "
                + Math.round(candidate.getLocation().getY()) + " / "
                + Math.round(candidate.getLocation().getZ()), NamedTextColor.WHITE));
        lore.add(Menus.label("Leben: ",
            Texts.decimal(candidate.getHealth(), 1) + " ♥", NamedTextColor.RED));
        lore.add(Menus.label("Modus: ", gameModeName(candidate.getGameMode()), NamedTextColor.WHITE));
        lore.add(Menus.label("Ping: ", candidate.getPing() + " ms", NamedTextColor.DARK_GRAY));
        if (viewer.getWorld().equals(candidate.getWorld())) {
            lore.add(Menus.label("Entfernung: ",
                Texts.decimal(viewer.getLocation().distance(candidate.getLocation()), 1) + " Blöcke",
                NamedTextColor.DARK_GRAY));
        }
        lore.add(Component.empty());
        lore.add(current
            ? Menus.text("Du beobachtest diesen Spieler.", NamedTextColor.GREEN)
            : Menus.action("Linksklick: beobachten"));
        lore.add(Menus.text("Rechtsklick: Inventar öffnen", NamedTextColor.GREEN));
        return lore;
    }

    private void putPaging(SpectateMenuHolder holder) {
        int pages = pageCount(holder.candidates().size());
        if (holder.page() > 0) {
            put(holder, 45, Menus.item(Material.ARROW,
                Menus.text("Vorherige Seite", NamedTextColor.YELLOW),
                List.of(Menus.text("Seite " + holder.page() + " von " + pages, NamedTextColor.GRAY))),
                Entry.of(SpectateAction.PAGE_PREVIOUS));
        }
        if (holder.page() + 1 < pages) {
            put(holder, 53, Menus.item(Material.ARROW,
                Menus.text("Nächste Seite", NamedTextColor.YELLOW),
                List.of(Menus.text("Seite " + (holder.page() + 2) + " von " + pages,
                    NamedTextColor.GRAY))),
                Entry.of(SpectateAction.PAGE_NEXT));
        }
    }

    private void withTarget(Player viewer, java.util.function.Consumer<Player> action) {
        Player target = spectate.targetOf(viewer);
        if (unavailable(viewer, target)) {
            return;
        }
        action.accept(target);
    }

    private boolean unavailable(Player viewer, Player target) {
        if (target != null && target.isOnline() && viewer.canSee(target)) {
            return false;
        }
        viewer.sendMessage(Component.text(
            "Dieser Spieler ist nicht mehr erreichbar.", NamedTextColor.RED));
        return true;
    }

    private List<UUID> candidates(Player viewer) {
        return Bukkit.getOnlinePlayers().stream()
            .filter(player -> !player.equals(viewer))
            .filter(viewer::canSee)
            .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
            .map(Player::getUniqueId)
            .toList();
    }

    private void put(SpectateMenuHolder holder, int slot, org.bukkit.inventory.ItemStack item, Entry entry) {
        holder.getInventory().setItem(slot, item);
        holder.bind(slot, entry);
    }

    private String gameModeName(GameMode gameMode) {
        return switch (gameMode) {
            case SURVIVAL -> "Überleben";
            case CREATIVE -> "Kreativ";
            case ADVENTURE -> "Abenteuer";
            case SPECTATOR -> "Zuschauer";
        };
    }

    private String pageLabel(SpectateMenuHolder holder) {
        return (holder.page() + 1) + " / " + pageCount(holder.candidates().size());
    }

    private int pageCount(int size) {
        return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private int clampPage(int page, int size) {
        return Math.max(0, Math.min(page, pageCount(size) - 1));
    }
}
