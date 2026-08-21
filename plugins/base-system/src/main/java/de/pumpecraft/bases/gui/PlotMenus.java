package de.pumpecraft.bases.gui;

import de.pumpecraft.bases.BaseText;
import de.pumpecraft.bases.PumpeBaseSystemPlugin;
import de.pumpecraft.bases.base.PlayerIdentity;
import de.pumpecraft.bases.gui.PlotHolder.Target;
import de.pumpecraft.bases.plot.Plot;
import de.pumpecraft.bases.plot.PlotFlag;
import de.pumpecraft.bases.plot.PlotGuard;
import de.pumpecraft.bases.plot.PlotMember;
import de.pumpecraft.bases.plot.PlotPricing;
import de.pumpecraft.bases.plot.PlotRole;
import de.pumpecraft.bases.plot.PlotArea;
import de.pumpecraft.bases.plot.PlotSelections;
import de.pumpecraft.bases.plot.PlotService;
import de.pumpecraft.bases.plot.PlotTool;
import de.pumpecraft.bases.plot.PlotVisualizer;
import de.pumpecraft.transactions.core.Currency;
import de.pumpecraft.utils.Menus;
import de.pumpecraft.utils.Texts;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class PlotMenus {
    private static final int LIST_SIZE = 54;
    private static final int FIRST_ENTRY_SLOT = 9;
    private static final int LAST_ENTRY_SLOT = 44;
    private static final int PAGE_SIZE = LAST_ENTRY_SLOT - FIRST_ENTRY_SLOT + 1;
    private static final int PREVIEW_SECONDS = 60;

    private final PumpeBaseSystemPlugin plugin;
    private final PlotService plots;
    private final PlotGuard guard;
    private final PlotTool tool;
    private final PlotVisualizer visualizer;
    private final TextInput textInput;

    public PlotMenus(
        PumpeBaseSystemPlugin plugin,
        PlotService plots,
        PlotGuard guard,
        PlotTool tool,
        PlotVisualizer visualizer,
        TextInput textInput
    ) {
        this.plugin = plugin;
        this.plots = plots;
        this.guard = guard;
        this.tool = tool;
        this.visualizer = visualizer;
        this.textInput = textInput;
    }

    public void openList(Player player) {
        List<Plot> accessible = plots.index().accessibleBy(player.getUniqueId());
        PlotHolder holder = new PlotHolder(
            PlotView.LIST, title("Deine Grundstücke"), LIST_SIZE, 0L, accessible);
        renderList(player, holder);
        open(player, holder);
    }

    public void openDetail(Player player, Plot plot) {
        PlotHolder holder = new PlotHolder(
            PlotView.DETAIL, title(plot.name()), 36, plot.id(), List.of());
        renderDetail(player, holder, plot);
        open(player, holder);
    }

    public void openMembers(Player player, Plot plot) {
        PlotHolder holder = new PlotHolder(
            PlotView.MEMBERS, title("Mitglieder · " + plot.name()), LIST_SIZE, plot.id(),
            List.copyOf(plot.members().values()));
        renderMembers(player, holder, plot);
        open(player, holder);
    }

    public void openAddMember(Player player, Plot plot, String query) {
        List<UUID> online = onlineCandidates(player, plot, query);
        if (query == null || query.isBlank()) {
            showAddMember(player, plot, query, online);
            return;
        }
        // Die Suche über bekannte Offlinespieler liest das Spielerverzeichnis; das gehört nicht
        // in den Haupt-Thread, auch wenn es meist schnell geht.
        plugin.runAsync(player, () -> offlineCandidates(plot, query, online), offline -> {
            List<UUID> found = new ArrayList<>(online);
            found.addAll(offline);
            showAddMember(player, plot, query, found);
        });
    }

    private void showAddMember(Player player, Plot plot, String query, List<UUID> candidates) {
        PlotHolder holder = new PlotHolder(
            PlotView.ADD_MEMBER, title("Hinzufügen · " + plot.name()), LIST_SIZE, plot.id(),
            candidates);
        holder.query(query);
        renderAddMember(player, holder, plot);
        open(player, holder);
    }

    /**
     * Die nächsten zuerst: Wer jemanden aufnimmt, meint fast immer den, der neben ihm steht.
     */
    private List<UUID> onlineCandidates(Player player, Plot plot, String query) {
        String wanted = query == null ? null : query.toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
            .filter(online -> !online.equals(player))
            .filter(player::canSee)
            .filter(online -> plot.roleOf(online.getUniqueId()) == null)
            .filter(online -> wanted == null
                || online.getName().toLowerCase(Locale.ROOT).contains(wanted))
            .sorted(Comparator.comparingDouble(online -> distance(player, online)))
            .map(Player::getUniqueId)
            .toList();
    }

    private List<UUID> offlineCandidates(Plot plot, String query, List<UUID> alreadyFound) {
        String wanted = query.toLowerCase(Locale.ROOT);
        List<UUID> found = new ArrayList<>();
        for (OfflinePlayer known : Bukkit.getOfflinePlayers()) {
            if (found.size() + alreadyFound.size() >= PAGE_SIZE) {
                break;
            }
            String name = known.getName();
            if (name == null
                || known.isOnline()
                || !name.toLowerCase(Locale.ROOT).contains(wanted)
                || plot.roleOf(known.getUniqueId()) != null) {
                continue;
            }
            found.add(known.getUniqueId());
        }
        return found;
    }

    private double distance(Player player, Player other) {
        return other.getWorld().equals(player.getWorld())
            ? player.getLocation().distanceSquared(other.getLocation())
            : Double.MAX_VALUE;
    }

    public void openFlags(Player player, Plot plot) {
        PlotHolder holder = new PlotHolder(
            PlotView.FLAGS, title("Flaggen · " + plot.name()), LIST_SIZE, plot.id(),
            List.of(PlotFlag.values()));
        renderFlags(player, holder, plot);
        open(player, holder);
    }

    public void click(Player player, PlotHolder holder, int slot, boolean rightClick) {
        Target target = holder.target(slot);
        Plot plot = plots.index().byId(holder.plotId());
        switch (target.action()) {
            case NONE -> {
            }
            case CLOSE -> player.closeInventory();
            case OPEN_LIST -> openList(player);
            case OPEN_DETAIL -> {
                Plot selected = plots.index().byId(target.plotId());
                if (selected != null) {
                    openDetail(player, selected);
                }
            }
            case OPEN_MEMBERS -> withManage(player, plot, () -> openMembers(player, plot));
            case OPEN_ADD_MEMBER -> withManage(player, plot,
                () -> openAddMember(player, plot, holder.query()));
            case OPEN_CREATE -> openCreate(player);
            case GIVE_TOOL -> {
                player.closeInventory();
                player.getInventory().addItem(tool.create());
                player.sendMessage(BaseText.success("Grundstücksmesser erhalten."));
            }
            case NAME_AND_CLAIM -> textInput.open(
                player,
                title("Name des Grundstücks"),
                player.getName() + "s Grundstück",
                "Namen eintippen",
                name -> plots.claim(player, plots.normalizeName(name), created -> {
                    visualizer.hide(player);
                    if (created != null) {
                        openDetail(player, created);
                    } else {
                        openCreate(player);
                    }
                }));
            case SEARCH_PLAYER -> withManage(player, plot, () -> textInput.open(
                player,
                title("Spieler suchen"),
                holder.query() == null ? "" : holder.query(),
                "Namen eintippen",
                query -> openAddMember(player, plot, query)));
            case TOGGLE_OUTLINE -> {
                if (plot != null) {
                    visualizer.toggle(player, plot);
                    openDetail(player, plot);
                }
            }
            case OPEN_FLAGS -> withManage(player, plot, () -> openFlags(player, plot));
            case TELEPORT -> {
                player.closeInventory();
                if (plot != null) {
                    plots.teleport(player, plot);
                }
            }
            case ASK_SELL -> {
                if (plot != null && guard.canSell(player, plot)) {
                    openSellConfirm(player, plot);
                }
            }
            case CONFIRM_SELL -> {
                player.closeInventory();
                if (plot != null) {
                    plots.sell(player, plot, ignored -> openList(player));
                }
            }
            case ADD_MEMBER -> withManage(player, plot, () -> plots.setMember(
                player,
                plot,
                new PlayerIdentity(target.playerId(), target.playerName()),
                PlotRole.MEMBER,
                ignored -> openMembers(player, plot)));
            case CYCLE_MEMBER -> withManage(player, plot, () -> {
                PlayerIdentity member =
                    new PlayerIdentity(target.playerId(), target.playerName());
                if (rightClick) {
                    plots.removeMember(player, plot, member, ignored -> openMembers(player, plot));
                    return;
                }
                PlotRole current = plot.roleOf(target.playerId());
                PlotRole next = current == PlotRole.MANAGER ? PlotRole.MEMBER : PlotRole.MANAGER;
                plots.setMember(player, plot, member, next, ignored -> openMembers(player, plot));
            });
            case REMOVE_MEMBER -> withManage(player, plot, () -> plots.removeMember(
                player,
                plot,
                new PlayerIdentity(target.playerId(), target.playerName()),
                ignored -> openMembers(player, plot)));
            case TOGGLE_FLAG -> withManage(player, plot, () -> {
                PlotFlag flag = PlotFlag.byId(target.flagId());
                if (flag != null) {
                    Boolean value = rightClick ? null : !plot.flag(flag);
                    plots.setFlag(player, plot, flag, value, () -> openFlags(player, plot));
                }
            });
            case RESET_FLAG -> withManage(player, plot, () -> {
                PlotFlag flag = PlotFlag.byId(target.flagId());
                if (flag != null) {
                    plots.setFlag(player, plot, flag, null, () -> openFlags(player, plot));
                }
            });
            case PAGE_PREVIOUS -> turnPage(player, holder, plot, -1);
            case PAGE_NEXT -> turnPage(player, holder, plot, 1);
        }
    }

    /** Die Auswahl mit Preis und Urteil, dazu Werkzeug und Kauf - Grundstücke ohne Chat. */
    public void openCreate(Player player) {
        PlotHolder holder = new PlotHolder(
            PlotView.CREATE, title("Neues Grundstück"), 27, 0L, List.of());
        Inventory inventory = holder.getInventory();
        Menus.fill(inventory);

        PlotSelections.Selection selection = plots.selections().of(player);
        PlotArea area = selection == null ? null : selection.area();
        String rejection = plots.rejectionFor(player, area);
        boolean ready = rejection == null;
        if (area != null) {
            visualizer.showSelection(player, area, ready, PREVIEW_SECONDS);
        }

        List<Component> lore = new ArrayList<>();
        if (area == null) {
            lore.add(Menus.text("Setze beide Ecken mit dem Messer.", NamedTextColor.GRAY));
        } else {
            PlotPricing.Quote quote = plots.pricing().quote(area);
            lore.add(Menus.label("Fläche: ",
                area.width() + " × " + area.depth() + " = "
                    + Texts.number(quote.blocks()) + " Blöcke", NamedTextColor.WHITE));
            lore.add(Menus.label("Welt: ", area.worldName(), NamedTextColor.WHITE));
            lore.add(Menus.label("Abstand zu 0/0: ",
                Texts.number(Math.round(quote.distance())) + " Blöcke", NamedTextColor.WHITE));
            lore.add(Menus.label("Lagefaktor: ",
                Math.round(quote.factor() * 100.0D) + " %", NamedTextColor.AQUA));
            lore.add(Menus.label("Preis: ", Currency.format(quote.price()), Currency.COLOR));
            lore.add(Component.empty());
            lore.add(ready
                ? Menus.text("Die Vorschau steht grün um die Fläche.", NamedTextColor.GREEN)
                : Menus.text(rejection, NamedTextColor.RED));
        }
        put(holder, 13, Menus.item(ready ? Material.GRASS_BLOCK : Material.DIRT,
            Menus.text("Deine Auswahl", NamedTextColor.GOLD), lore), Target.NONE);

        put(holder, 11, Menus.item(plots.settings().selectionTool(),
            Menus.text("Grundstücksmesser", NamedTextColor.AQUA),
            List.of(
                Menus.text("Linksklick: erste Ecke", NamedTextColor.GRAY),
                Menus.text("Rechtsklick: zweite Ecke", NamedTextColor.GRAY),
                Menus.action("Klicken: Messer holen"))),
            Target.of(PlotMenuAction.GIVE_TOOL));

        put(holder, 15, Menus.item(ready ? Material.EMERALD : Material.GRAY_DYE,
            Menus.text(ready ? "Kaufen" : "Noch nicht kaufbar",
                ready ? NamedTextColor.GREEN : NamedTextColor.GRAY),
            List.of(ready
                ? Menus.action("Klicken: Namen eingeben und kaufen")
                : Menus.text("Erst muss die Auswahl stimmen.", NamedTextColor.DARK_GRAY))),
            ready ? Target.of(PlotMenuAction.NAME_AND_CLAIM) : Target.NONE);

        put(holder, 18, backItem("Zur Liste"), Target.of(PlotMenuAction.OPEN_LIST));
        put(holder, 26, closeItem(), Target.of(PlotMenuAction.CLOSE));
        open(player, holder);
    }

    private void openSellConfirm(Player player, Plot plot) {
        PlotHolder holder = new PlotHolder(
            PlotView.SELL_CONFIRM, title("Verkaufen?"), 27, plot.id(), List.of());
        Inventory inventory = holder.getInventory();
        Menus.fill(inventory);
        long refund = plots.pricing().refundFor(plot);
        put(holder, 11, Menus.item(Material.LIME_CONCRETE,
            Menus.text("Ja, verkaufen", NamedTextColor.GREEN),
            List.of(Menus.label("Erstattung: ", Currency.format(refund), Currency.COLOR))),
            Target.of(PlotMenuAction.CONFIRM_SELL));
        put(holder, 13, Menus.item(Material.GOLD_INGOT,
            Menus.text(plot.name() + " verkaufen", NamedTextColor.GOLD),
            List.of(
                Menus.text("Das Grundstück wird freigegeben.", NamedTextColor.GRAY),
                Menus.text("Gebaute Blöcke bleiben stehen.", NamedTextColor.DARK_GRAY))),
            Target.NONE);
        put(holder, 15, Menus.item(Material.RED_CONCRETE,
            Menus.text("Abbrechen", NamedTextColor.RED),
            List.of(Menus.text("Zurück zur Liste", NamedTextColor.GRAY))),
            Target.of(PlotMenuAction.OPEN_LIST));
        open(player, holder);
    }

    private void renderList(Player player, PlotHolder holder) {
        Inventory inventory = holder.getInventory();
        holder.clearTargets();
        Menus.frame(inventory, FIRST_ENTRY_SLOT, LAST_ENTRY_SLOT);
        Menus.clear(inventory, FIRST_ENTRY_SLOT, LAST_ENTRY_SLOT);

        List<?> entries = holder.entries();
        put(holder, 4, Menus.item(Material.FILLED_MAP,
            Menus.text("Deine Grundstücke", NamedTextColor.GOLD),
            List.of(
                Menus.label("Anzahl: ", Texts.number(entries.size()), NamedTextColor.WHITE),
                Menus.label("Eigene: ",
                    plots.index().countOwnedBy(player.getUniqueId())
                        + " / " + plots.settings().maxPerPlayer(), NamedTextColor.WHITE),
                Component.empty(),
                Menus.text("/plot werkzeug holt den Messer", NamedTextColor.DARK_GRAY))),
            Target.NONE);
        put(holder, 8, closeItem(), Target.of(PlotMenuAction.CLOSE));
        putStandingOn(player, holder);
        if (player.hasPermission(plugin.permission("plot-claim"))) {
            put(holder, 0, Menus.item(Material.EMERALD_BLOCK,
                Menus.text("Neues Grundstück", NamedTextColor.GREEN),
                List.of(
                    Menus.text("Auswahl, Preis und Kauf in einem Fenster.", NamedTextColor.GRAY),
                    Menus.action("Klicken zum Öffnen"))),
                Target.of(PlotMenuAction.OPEN_CREATE));
        }

        int offset = holder.page() * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && offset + index < entries.size(); index++) {
            Plot plot = (Plot) entries.get(offset + index);
            PlotRole role = plot.roleOf(player.getUniqueId());
            put(holder, FIRST_ENTRY_SLOT + index,
                Menus.item(plot.adminPlot() ? Material.BEDROCK : Material.GRASS_BLOCK,
                    Menus.text(plot.name(),
                        plot.adminPlot() ? NamedTextColor.RED : NamedTextColor.GREEN),
                    List.of(
                        Menus.label("Besitzer: ", plot.ownerName(), NamedTextColor.WHITE),
                        Menus.label("Deine Rolle: ",
                            role == null ? "-" : role.displayName(), NamedTextColor.AQUA),
                        Menus.label("Welt: ", plot.area().worldName(), NamedTextColor.WHITE),
                        Menus.label("Fläche: ",
                            Texts.number(plot.area().area()) + " Blöcke", NamedTextColor.WHITE),
                        Menus.label("Mitglieder: ",
                            Texts.number(plot.members().size()), NamedTextColor.WHITE),
                        Component.empty(),
                        Menus.action("Klicken für Details"))),
                Target.plot(PlotMenuAction.OPEN_DETAIL, plot.id()));
        }
        putPaging(holder);
    }

    /**
     * Das Grundstück, auf dem der Spieler gerade steht - sofern er es verwalten darf und es nicht
     * ohnehin in seiner Liste steht. Für das Team ist das der einzige Weg in ein fremdes
     * Grundstück, ohne dessen Namen zu kennen.
     */
    private void putStandingOn(Player player, PlotHolder holder) {
        Plot here = guard.column(player.getLocation());
        if (here == null || !guard.canManage(player, here)) {
            return;
        }
        boolean own = here.roleOf(player.getUniqueId()) != null;
        put(holder, 6, Menus.item(Material.COMPASS,
            Menus.text("Grundstück hier: " + here.name(), NamedTextColor.AQUA),
            List.of(
                Menus.label("Besitzer: ", here.ownerName(), NamedTextColor.WHITE),
                Menus.text(own ? "Du gehörst dazu." : "Zugriff über deine Teamrechte.",
                    NamedTextColor.GRAY),
                Component.empty(),
                Menus.action("Klicken für Details"))),
            Target.plot(PlotMenuAction.OPEN_DETAIL, here.id()));
    }

    private void renderDetail(Player player, PlotHolder holder, Plot plot) {
        Inventory inventory = holder.getInventory();
        holder.clearTargets();
        Menus.fill(inventory);
        boolean manage = guard.canManage(player, plot);
        PlotPricing.Quote quote = plots.pricing().quote(plot.area());

        List<Component> lore = new ArrayList<>();
        lore.add(Menus.label("Besitzer: ", plot.ownerName(), NamedTextColor.WHITE));
        lore.add(Menus.label("Welt: ", plot.area().worldName(), NamedTextColor.WHITE));
        lore.add(Menus.label("Ecken: ", plot.area().corners(), NamedTextColor.WHITE));
        lore.add(Menus.label("Höhe: ", plot.area().heightLabel(), NamedTextColor.WHITE));
        lore.add(Menus.label("Fläche: ",
            plot.area().width() + " × " + plot.area().depth() + " = "
                + Texts.number(plot.area().area()) + " Blöcke", NamedTextColor.WHITE));
        lore.add(Menus.label("Lagefaktor: ",
            Math.round(quote.factor() * 100.0D) + " %", NamedTextColor.AQUA));
        if (!plot.adminPlot()) {
            lore.add(Menus.label("Gezahlt: ",
                Currency.format(plot.pricePaid()), Currency.COLOR));
        }
        lore.add(Menus.label("Angelegt: ",
            Texts.since(System.currentTimeMillis() - plot.createdAt()), NamedTextColor.DARK_GRAY));
        put(holder, 4, Menus.item(plot.adminPlot() ? Material.BEDROCK : Material.GRASS_BLOCK,
            Menus.text(plot.name(), NamedTextColor.GOLD), lore), Target.NONE);

        put(holder, 11, Menus.item(Material.PLAYER_HEAD,
            Menus.text("Mitglieder", NamedTextColor.AQUA),
            List.of(
                Menus.label("Eingetragen: ",
                    Texts.number(plot.members().size()), NamedTextColor.WHITE),
                Menus.action(manage ? "Klicken zum Verwalten" : "Nur Verwalter dürfen das"))),
            Target.of(PlotMenuAction.OPEN_MEMBERS));
        put(holder, 13, Menus.item(Material.REDSTONE_TORCH,
            Menus.text("Flaggen", NamedTextColor.LIGHT_PURPLE),
            List.of(
                Menus.text("Was Fremde und die Welt dürfen.", NamedTextColor.GRAY),
                Menus.action(manage ? "Klicken zum Verwalten" : "Nur Verwalter dürfen das"))),
            Target.of(PlotMenuAction.OPEN_FLAGS));
        put(holder, 15, Menus.item(Material.ENDER_PEARL,
            Menus.text("Hinbringen", NamedTextColor.AQUA),
            List.of(Menus.action("Klicken zum Teleportieren"))),
            Target.of(PlotMenuAction.TELEPORT));

        if (guard.canSell(player, plot)) {
            put(holder, 29, Menus.item(Material.GOLD_INGOT,
                Menus.text("Verkaufen", NamedTextColor.YELLOW),
                List.of(
                    Menus.label("Erstattung: ",
                        Currency.format(plots.pricing().refundFor(plot)), Currency.COLOR),
                    Menus.action("Klicken - danach folgt eine Rückfrage"))),
                Target.of(PlotMenuAction.ASK_SELL));
        }
        boolean showing = visualizer.isShowing(player, plot.id());
        put(holder, 31, Menus.item(
            showing ? Material.LIME_STAINED_GLASS : Material.YELLOW_STAINED_GLASS,
            Menus.text("Grenze " + (showing ? "ausblenden" : "anzeigen"), NamedTextColor.YELLOW),
            List.of(
                Menus.text("Zeichnet die Kante aus buntem Glas.", NamedTextColor.GRAY),
                Menus.text("Nur du siehst sie.", NamedTextColor.DARK_GRAY),
                Menus.action("Klicken zum Umschalten"))),
            Target.of(PlotMenuAction.TOGGLE_OUTLINE));
        put(holder, 33, backItem("Zur Liste"), Target.of(PlotMenuAction.OPEN_LIST));
        put(holder, 35, closeItem(), Target.of(PlotMenuAction.CLOSE));
    }

    private void renderMembers(Player player, PlotHolder holder, Plot plot) {
        Inventory inventory = holder.getInventory();
        holder.clearTargets();
        Menus.frame(inventory, FIRST_ENTRY_SLOT, LAST_ENTRY_SLOT);
        Menus.clear(inventory, FIRST_ENTRY_SLOT, LAST_ENTRY_SLOT);

        put(holder, 0, backItem("Zum Grundstück"), Target.plot(PlotMenuAction.OPEN_DETAIL, plot.id()));
        put(holder, 4, Menus.item(Material.PLAYER_HEAD,
            Menus.text("Mitglieder von " + plot.name(), NamedTextColor.GOLD),
            List.of(
                Menus.label("Besitzer: ", plot.ownerName(), NamedTextColor.WHITE),
                Menus.label("Mitglieder: ",
                    Texts.number(plot.members().size()), NamedTextColor.WHITE),
                Component.empty(),
                Menus.text("Linksklick: Rolle wechseln", NamedTextColor.GRAY),
                Menus.text("Rechtsklick: entfernen", NamedTextColor.GRAY))),
            Target.NONE);
        put(holder, 8, closeItem(), Target.of(PlotMenuAction.CLOSE));

        List<?> entries = holder.entries();
        int offset = holder.page() * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && offset + index < entries.size(); index++) {
            PlotMember member = (PlotMember) entries.get(offset + index);
            put(holder, FIRST_ENTRY_SLOT + index,
                Menus.head(member.playerId(),
                    Menus.text(member.playerName(), NamedTextColor.WHITE),
                    List.of(
                        Menus.label("Rolle: ", member.role().displayName(), NamedTextColor.AQUA),
                        Menus.label("Dabei seit: ",
                            Texts.since(System.currentTimeMillis() - member.addedAt()),
                            NamedTextColor.DARK_GRAY),
                        Component.empty(),
                        Menus.action("Linksklick: Rolle wechseln"),
                        Menus.text("Rechtsklick: entfernen", NamedTextColor.RED))),
                new Target(PlotMenuAction.CYCLE_MEMBER, plot.id(),
                    member.playerId(), member.playerName(), null));
        }

        put(holder, 49, Menus.item(Material.EMERALD,
            Menus.text("Spieler hinzufügen", NamedTextColor.GREEN),
            List.of(
                Menus.text("Nächste Spieler zuerst, mit Suche.", NamedTextColor.GRAY),
                Menus.action("Klicken zum Öffnen"))),
            Target.of(PlotMenuAction.OPEN_ADD_MEMBER));
        putPaging(holder);
    }

    private void renderAddMember(Player player, PlotHolder holder, Plot plot) {
        Inventory inventory = holder.getInventory();
        holder.clearTargets();
        Menus.frame(inventory, FIRST_ENTRY_SLOT, LAST_ENTRY_SLOT);
        Menus.clear(inventory, FIRST_ENTRY_SLOT, LAST_ENTRY_SLOT);

        put(holder, 0, backItem("Zu den Mitgliedern"), Target.of(PlotMenuAction.OPEN_MEMBERS));
        put(holder, 4, Menus.item(Material.EMERALD,
            Menus.text("Spieler hinzufügen", NamedTextColor.GREEN),
            List.of(
                Menus.label("Zur Auswahl: ",
                    Texts.number(holder.entries().size()), NamedTextColor.WHITE),
                Menus.label("Suche: ",
                    holder.query() == null ? "alle in der Nähe" : holder.query(),
                    NamedTextColor.AQUA),
                Component.empty(),
                Menus.text("Klick trägt als Mitglied ein.", NamedTextColor.GRAY))),
            Target.NONE);
        put(holder, 8, closeItem(), Target.of(PlotMenuAction.CLOSE));
        put(holder, 49, Menus.item(Material.SPYGLASS,
            Menus.text("Suchen", NamedTextColor.AQUA),
            List.of(
                Menus.text("Findet auch Spieler, die offline sind.", NamedTextColor.GRAY),
                Menus.action("Klicken zum Eintippen"))),
            Target.of(PlotMenuAction.SEARCH_PLAYER));

        List<?> entries = holder.entries();
        int offset = holder.page() * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && offset + index < entries.size(); index++) {
            UUID candidateId = (UUID) entries.get(offset + index);
            Player online = Bukkit.getPlayer(candidateId);
            OfflinePlayer known = online != null ? online : Bukkit.getOfflinePlayer(candidateId);
            String name = known.getName();
            if (name == null) {
                continue;
            }
            List<Component> lore = new ArrayList<>();
            if (online != null) {
                lore.add(Menus.label("Welt: ", online.getWorld().getName(), NamedTextColor.WHITE));
                lore.add(Menus.label("Entfernung: ",
                    online.getWorld().equals(player.getWorld())
                        ? Texts.decimal(player.getLocation().distance(online.getLocation()), 1)
                            + " Blöcke"
                        : "andere Welt",
                    NamedTextColor.DARK_GRAY));
            } else {
                lore.add(Menus.text("Offline", NamedTextColor.DARK_GRAY));
            }
            lore.add(Component.empty());
            lore.add(Menus.action("Klicken: als Mitglied eintragen"));
            put(holder, FIRST_ENTRY_SLOT + index,
                Menus.head(known,
                    Menus.text(name, online != null ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                    lore),
                new Target(PlotMenuAction.ADD_MEMBER, plot.id(), candidateId, name, null));
        }
        putPaging(holder);
    }

    private void renderFlags(Player player, PlotHolder holder, Plot plot) {
        Inventory inventory = holder.getInventory();
        holder.clearTargets();
        Menus.frame(inventory, FIRST_ENTRY_SLOT, LAST_ENTRY_SLOT);
        Menus.clear(inventory, FIRST_ENTRY_SLOT, LAST_ENTRY_SLOT);

        put(holder, 0, backItem("Zum Grundstück"), Target.plot(PlotMenuAction.OPEN_DETAIL, plot.id()));
        put(holder, 4, Menus.item(Material.REDSTONE_TORCH,
            Menus.text("Flaggen von " + plot.name(), NamedTextColor.GOLD),
            List.of(
                Menus.text("Mitglieder dürfen ohnehin alles.", NamedTextColor.GRAY),
                Menus.text("Flaggen regeln Fremde und die Welt.", NamedTextColor.GRAY),
                Component.empty(),
                Menus.text("Linksklick: umschalten", NamedTextColor.GRAY),
                Menus.text("Rechtsklick: auf Standard zurück", NamedTextColor.GRAY))),
            Target.NONE);
        put(holder, 8, closeItem(), Target.of(PlotMenuAction.CLOSE));

        List<PlotFlag> flags = Arrays.stream(PlotFlag.values())
            .filter(flag -> guard.canChangeFlag(player, flag))
            .toList();
        for (int index = 0; index < flags.size() && index < PAGE_SIZE; index++) {
            PlotFlag flag = flags.get(index);
            boolean value = plot.flag(flag);
            List<Component> lore = new ArrayList<>();
            lore.add(Menus.text(flag.description(), NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(Menus.label("Zustand: ",
                value ? "an" : "aus", value ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(Menus.label("Standard: ",
                flag.defaultValue() ? "an" : "aus", NamedTextColor.DARK_GRAY));
            if (!plot.flagIsDefault(flag)) {
                lore.add(Menus.text("Vom Standard abweichend", NamedTextColor.YELLOW));
            }
            lore.add(Component.empty());
            lore.add(Menus.action("Linksklick: umschalten"));
            lore.add(Menus.text("Rechtsklick: Standard", NamedTextColor.GRAY));
            put(holder, FIRST_ENTRY_SLOT + index,
                Menus.item(flag.icon(),
                    Menus.text(flag.displayName() + (value ? " · an" : " · aus"),
                        value ? NamedTextColor.GREEN : NamedTextColor.RED),
                    lore),
                new Target(PlotMenuAction.TOGGLE_FLAG, plot.id(), null, null, flag.id()));
        }
    }

    private void turnPage(Player player, PlotHolder holder, Plot plot, int direction) {
        int pages = pageCount(holder.entries().size());
        int page = Math.max(0, Math.min(holder.page() + direction, pages - 1));
        if (page == holder.page()) {
            return;
        }
        holder.page(page);
        switch (holder.view()) {
            case LIST -> renderList(player, holder);
            case MEMBERS -> renderMembers(player, holder, plot);
            case ADD_MEMBER -> renderAddMember(player, holder, plot);
            default -> {
                return;
            }
        }
        player.updateInventory();
    }

    private void withManage(Player player, Plot plot, Runnable action) {
        if (plot == null) {
            return;
        }
        if (!guard.canManage(player, plot)) {
            player.sendMessage(BaseText.error("Dafür fehlt dir die Berechtigung."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6F, 1.0F);
            return;
        }
        action.run();
    }

    private void putPaging(PlotHolder holder) {
        int pages = pageCount(holder.entries().size());
        if (holder.page() > 0) {
            put(holder, 45, Menus.item(Material.ARROW,
                Menus.text("Vorherige Seite", NamedTextColor.YELLOW),
                List.of(Menus.text("Seite " + holder.page() + " von " + pages, NamedTextColor.GRAY))),
                Target.of(PlotMenuAction.PAGE_PREVIOUS));
        }
        if (holder.page() + 1 < pages) {
            put(holder, 53, Menus.item(Material.ARROW,
                Menus.text("Nächste Seite", NamedTextColor.YELLOW),
                List.of(Menus.text("Seite " + (holder.page() + 2) + " von " + pages,
                    NamedTextColor.GRAY))),
                Target.of(PlotMenuAction.PAGE_NEXT));
        }
    }

    private void open(Player player, PlotHolder holder) {
        // Der Aufruf kommt oft aus einem Klick; ein Fensterwechsel im selben Tick lässt den
        // Client mit dem alten Fenster zurück.
        plugin.runSync(() -> {
            if (player.isOnline()) {
                player.openInventory(holder.getInventory());
            }
        });
    }

    private void put(PlotHolder holder, int slot, ItemStack item, Target target) {
        holder.getInventory().setItem(slot, item);
        holder.bind(slot, target);
    }

    private ItemStack backItem(String description) {
        return Menus.item(Material.ARROW,
            Menus.text("Zurück", NamedTextColor.YELLOW),
            List.of(Menus.text(description, NamedTextColor.GRAY)));
    }

    private ItemStack closeItem() {
        return Menus.item(Material.BARRIER,
            Menus.text("Schließen", NamedTextColor.RED), List.of());
    }

    private Component title(String value) {
        return Component.text(value, NamedTextColor.GOLD, TextDecoration.BOLD);
    }

    private int pageCount(int size) {
        return Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE);
    }
}
