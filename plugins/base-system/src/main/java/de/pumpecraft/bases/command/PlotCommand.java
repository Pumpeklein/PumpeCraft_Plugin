package de.pumpecraft.bases.command;

import de.pumpecraft.bases.BaseText;
import de.pumpecraft.bases.PumpeBaseSystemPlugin;
import de.pumpecraft.bases.base.PlayerIdentity;
import de.pumpecraft.bases.gui.PlotMenus;
import de.pumpecraft.bases.plot.Plot;
import de.pumpecraft.bases.plot.PlotArea;
import de.pumpecraft.bases.plot.PlotFlag;
import de.pumpecraft.bases.plot.PlotGuard;
import de.pumpecraft.bases.plot.PlotPricing;
import de.pumpecraft.bases.plot.PlotRole;
import de.pumpecraft.bases.plot.PlotSelections;
import de.pumpecraft.bases.plot.PlotService;
import de.pumpecraft.bases.plot.PlotTool;
import de.pumpecraft.bases.plot.PlotVisualizer;
import de.pumpecraft.transactions.core.Currency;
import de.pumpecraft.utils.Players;
import de.pumpecraft.utils.Texts;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class PlotCommand implements CommandExecutor, TabCompleter {
    private static final int PREVIEW_SECONDS = 60;

    private final PumpeBaseSystemPlugin plugin;
    private final PlotService plots;
    private final PlotGuard guard;
    private final PlotMenus menus;
    private final PlotTool tool;
    private final PlotVisualizer visualizer;

    public PlotCommand(
        PumpeBaseSystemPlugin plugin,
        PlotService plots,
        PlotGuard guard,
        PlotMenus menus,
        PlotTool tool,
        PlotVisualizer visualizer
    ) {
        this.plugin = plugin;
        this.plots = plots;
        this.guard = guard;
        this.menus = menus;
        this.tool = tool;
        this.visualizer = visualizer;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(BaseText.error("Dieser Befehl kann nur im Spiel benutzt werden."));
            return true;
        }
        if (!player.hasPermission(plugin.permission("plot-use"))) {
            player.sendMessage(BaseText.error("Dafür fehlt dir die Berechtigung."));
            return true;
        }
        if (args.length == 0) {
            openHereOrList(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "menu" -> openHereOrList(player);
            case "list" -> menus.openList(player);
            case "tool" -> giveTool(player);
            case "pos1" -> setCorner(player, true);
            case "pos2" -> setCorner(player, false);
            case "cost" -> showCost(player);
            case "claim" -> claim(player, args, false);
            case "info" -> info(player, args);
            case "show" -> toggleOutline(player);
            case "trust" -> trust(player, args);
            case "untrust" -> untrust(player, args);
            case "flag" -> flag(player, args);
            case "sell" -> sell(player, args);
            case "admin" -> admin(player, args);
            case "help" -> help(player, label);
            default -> {
                player.sendMessage(BaseText.error("Unbekannter Unterbefehl: " + args[0]));
                help(player, label);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
        CommandSender sender,
        Command command,
        String alias,
        String[] args
    ) {
        if (!(sender instanceof Player player)
            || !player.hasPermission(plugin.permission("plot-use"))) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of(
                "list", "tool", "pos1", "pos2", "cost", "info", "show", "help"));
            if (player.hasPermission(plugin.permission("plot-claim"))) {
                options.addAll(List.of("claim", "sell", "trust", "untrust", "flag"));
            }
            if (guard.isAdmin(player)) {
                options.add("admin");
            }
            return Players.filterPrefix(options, args[0]);
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (subcommand) {
                case "trust" -> Players.completeKnownNames(args[1], 40);
                case "untrust" -> memberNames(player, args[1]);
                case "flag" -> Players.filterPrefix(flagNames(player), args[1]);
                case "sell" -> Players.filterPrefix(List.of("confirm"), args[1]);
                case "info" -> Players.filterPrefix(plots.index().names(), args[1]);
                case "admin" -> Players.filterPrefix(
                    List.of("claim", "delete", "height", "reload"), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (subcommand) {
                case "trust" -> Players.filterPrefix(List.of("member", "manager"), args[2]);
                case "flag" -> Players.filterPrefix(List.of("on", "off", "default"), args[2]);
                case "admin" -> args[1].equalsIgnoreCase("delete")
                    ? Players.filterPrefix(plots.index().names(), args[2])
                    : args[1].equalsIgnoreCase("height")
                        ? Players.filterPrefix(List.of("full"), args[2])
                        : List.of();
                default -> List.of();
            };
        }
        return List.of();
    }

    /**
     * Steht der Spieler auf einem Grundstück, das er verwalten darf, führt {@code /plot} direkt
     * dorthin - für das Team auch auf fremdem Boden. Sonst öffnet es die eigene Liste.
     */
    private void openHereOrList(Player player) {
        Plot here = guard.column(player.getLocation());
        if (here != null && guard.canManage(player, here)) {
            menus.openDetail(player, here);
            return;
        }
        menus.openList(player);
    }

    private void giveTool(Player player) {
        if (!require(player, "plot-claim")) {
            return;
        }
        player.getInventory().addItem(tool.create());
        player.sendMessage(BaseText.success(
            "Grundstücksmesser erhalten. Linksklick setzt die erste Ecke, Rechtsklick die zweite."));
    }

    private void setCorner(Player player, boolean first) {
        if (!require(player, "plot-claim")) {
            return;
        }
        if (first) {
            plots.selections().first(player, player.getLocation());
        } else {
            plots.selections().second(player, player.getLocation());
        }
        player.sendMessage(BaseText.label(first ? "Erste Ecke: " : "Zweite Ecke: ",
            player.getLocation().getBlockX() + " / " + player.getLocation().getBlockZ(),
            NamedTextColor.WHITE));
        showCost(player);
    }

    private void showCost(Player player) {
        PlotSelections.Selection selection = plots.selections().of(player);
        PlotArea area = selection == null ? null : selection.area();
        String rejection = plots.rejectionFor(player, area);
        if (area != null) {
            visualizer.showSelection(player, area, rejection == null, PREVIEW_SECONDS);
        }
        if (rejection != null) {
            player.sendMessage(BaseText.error(rejection));
            return;
        }
        PlotPricing.Quote quote = plots.pricing().quote(area);
        player.sendMessage(BaseText.DIVIDER);
        player.sendMessage(BaseText.title("Grundstück"));
        player.sendMessage(BaseText.label("Fläche: ",
            area.width() + " × " + area.depth() + " = " + Texts.number(quote.blocks()) + " Blöcke",
            NamedTextColor.WHITE));
        player.sendMessage(BaseText.label("Abstand zu 0/0: ",
            Texts.number(Math.round(quote.distance())) + " Blöcke", NamedTextColor.WHITE));
        player.sendMessage(BaseText.label("Lagefaktor: ",
            Math.round(quote.factor() * 100.0D) + " %", NamedTextColor.AQUA));
        player.sendMessage(BaseText.label("Preis: ", Currency.format(quote.price()), Currency.COLOR));
        player.sendMessage(BaseText.hint("/plot claim <Name>"));
        player.sendMessage(BaseText.DIVIDER);
    }

    private void toggleOutline(Player player) {
        Plot plot = guard.column(player.getLocation());
        if (plot == null) {
            player.sendMessage(BaseText.error("Hier ist freies Land."));
            return;
        }
        visualizer.toggle(player, plot);
        player.sendMessage(BaseText.success(visualizer.isShowing(player, plot.id())
            ? "Grenze von " + plot.name() + " wird angezeigt."
            : "Grenze ausgeblendet."));
    }

    private void claim(Player player, String[] args, boolean admin) {
        if (!require(player, admin ? "plot-admin" : "plot-claim")) {
            return;
        }
        if (args.length < 2) {
            player.sendMessage(BaseText.error("Nutzung: /plot " + (admin ? "admin " : "")
                + "claim <Name>"));
            return;
        }
        String name = plots.normalizeName(args[args.length - 1]);
        if (admin) {
            plots.claimForServer(player, name, plot -> visualizer.hide(player));
        } else {
            plots.claim(player, name, plot -> visualizer.hide(player));
        }
    }

    private void info(Player player, String[] args) {
        Plot plot = args.length >= 2
            ? plots.index().byName(args[1])
            : guard.column(player.getLocation());
        if (plot == null) {
            player.sendMessage(BaseText.error(args.length >= 2
                ? "Dieses Grundstück gibt es nicht."
                : "Hier ist freies Land."));
            return;
        }
        PlotRole role = plot.roleOf(player.getUniqueId());
        player.sendMessage(BaseText.DIVIDER);
        player.sendMessage(BaseText.title(plot.name()
            + (plot.adminPlot() ? " (Admingebiet)" : "")));
        player.sendMessage(BaseText.label("Besitzer: ", plot.ownerName(), NamedTextColor.WHITE));
        player.sendMessage(plots.describe(plot));
        player.sendMessage(BaseText.label("Höhe: ", plot.area().heightLabel(), NamedTextColor.WHITE));
        player.sendMessage(BaseText.label("Deine Rolle: ",
            role == null ? "keine" : role.displayName(), NamedTextColor.AQUA));
        player.sendMessage(BaseText.label("Mitglieder: ",
            plot.members().isEmpty() ? "keine" : Texts.joinLimited(
                plot.members().values().stream().map(member -> member.playerName()).toList(),
                8, "(+{count})"), NamedTextColor.WHITE));
        player.sendMessage(BaseText.DIVIDER);
    }

    private void trust(Player player, String[] args) {
        Plot plot = manageable(player);
        if (plot == null) {
            return;
        }
        if (args.length < 2) {
            player.sendMessage(BaseText.error("Nutzung: /plot trust <Spieler> [manager]"));
            return;
        }
        OfflinePlayer target = Players.known(args[1]).orElse(null);
        if (target == null || target.getName() == null) {
            player.sendMessage(BaseText.error("Dieser Spieler ist dem Server nicht bekannt."));
            return;
        }
        PlotRole role = args.length >= 3 && args[2].toLowerCase(Locale.ROOT).startsWith("man")
            ? PlotRole.MANAGER
            : PlotRole.MEMBER;
        plots.setMember(
            player,
            plot,
            new PlayerIdentity(target.getUniqueId(), target.getName()),
            role,
            ignored -> {
            });
    }

    private void untrust(Player player, String[] args) {
        Plot plot = manageable(player);
        if (plot == null) {
            return;
        }
        if (args.length < 2) {
            player.sendMessage(BaseText.error("Nutzung: /plot untrust <Spieler>"));
            return;
        }
        var member = plot.members().values().stream()
            .filter(entry -> entry.playerName().equalsIgnoreCase(args[1]))
            .findFirst()
            .orElse(null);
        if (member == null) {
            player.sendMessage(BaseText.error(args[1] + " gehört nicht zu diesem Grundstück."));
            return;
        }
        plots.removeMember(
            player,
            plot,
            new PlayerIdentity(member.playerId(), member.playerName()),
            ignored -> {
            });
    }

    private void flag(Player player, String[] args) {
        Plot plot = manageable(player);
        if (plot == null) {
            return;
        }
        if (args.length < 2) {
            player.sendMessage(BaseText.error("Nutzung: /plot flag <Flagge> <on|off|default>"));
            player.sendMessage(BaseText.hint(String.join(", ", flagNames(player))));
            return;
        }
        PlotFlag flag = PlotFlag.byId(args[1]);
        if (flag == null) {
            player.sendMessage(BaseText.error("Diese Flagge gibt es nicht."));
            player.sendMessage(BaseText.hint(String.join(", ", flagNames(player))));
            return;
        }
        if (!guard.canChangeFlag(player, flag)) {
            player.sendMessage(BaseText.error(
                flag.displayName() + " kann nur das Team setzen."));
            return;
        }
        if (args.length < 3) {
            player.sendMessage(BaseText.label(flag.displayName() + ": ",
                plot.flag(flag) ? "an" : "aus",
                plot.flag(flag) ? NamedTextColor.GREEN : NamedTextColor.RED));
            player.sendMessage(BaseText.hint(flag.description()));
            return;
        }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "on", "an", "true" -> plots.setFlag(player, plot, flag, Boolean.TRUE, null);
            case "off", "aus", "false" -> plots.setFlag(player, plot, flag, Boolean.FALSE, null);
            case "default", "standard" -> plots.setFlag(player, plot, flag, null, null);
            default -> player.sendMessage(BaseText.error("Wähle on, off oder default."));
        }
    }

    private void sell(Player player, String[] args) {
        Plot plot = guard.column(player.getLocation());
        if (plot == null) {
            player.sendMessage(BaseText.error(
                "Stell dich auf das Grundstück, das du verkaufen willst."));
            return;
        }
        if (!guard.canSell(player, plot)) {
            player.sendMessage(BaseText.error("Nur der Besitzer kann verkaufen."));
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            player.sendMessage(BaseText.error("Bestätige mit /plot sell confirm."));
            player.sendMessage(BaseText.label("Erstattung: ",
                Currency.format(plots.pricing().refundFor(plot)), Currency.COLOR));
            return;
        }
        plots.sell(player, plot, ignored -> {
        });
    }

    private void admin(Player player, String[] args) {
        if (!require(player, "plot-admin")) {
            return;
        }
        if (args.length < 2) {
            player.sendMessage(BaseText.error(
                "Nutzung: /plot admin <claim <Name>|delete <Name>|height <min> <max>|reload>"));
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "claim" -> claim(player, Arrays.copyOfRange(args, 1, args.length), true);
            case "delete" -> {
                if (args.length < 3) {
                    player.sendMessage(BaseText.error("Nutzung: /plot admin delete <Name>"));
                    return;
                }
                Plot plot = plots.index().byName(args[2]);
                if (plot == null) {
                    player.sendMessage(BaseText.error("Dieses Grundstück gibt es nicht."));
                    return;
                }
                plots.deleteAsAdmin(player, plot, ignored -> {
                });
            }
            case "height" -> height(player, args);
            case "reload" -> {
                plots.reload();
                player.sendMessage(BaseText.success("Grundstücke werden neu geladen."));
            }
            default -> player.sendMessage(BaseText.error("Unbekannter Adminbefehl: " + args[1]));
        }
    }

    /** {@code /plot admin height full} nimmt die Begrenzung wieder heraus. */
    private void height(Player player, String[] args) {
        Plot plot = guard.column(player.getLocation());
        if (plot == null) {
            player.sendMessage(BaseText.error("Stell dich auf das Grundstück, das du meinst."));
            return;
        }
        if (args.length == 3 && args[2].equalsIgnoreCase("full")) {
            plots.setHeight(player, plot, null, null, null);
            return;
        }
        if (args.length < 4) {
            player.sendMessage(BaseText.error(
                "Nutzung: /plot admin height <min> <max>  oder  /plot admin height full"));
            player.sendMessage(BaseText.label("Aktuell: ",
                plot.area().heightLabel(), NamedTextColor.WHITE));
            return;
        }
        try {
            int minY = Integer.parseInt(args[2]);
            int maxY = Integer.parseInt(args[3]);
            if (minY > maxY) {
                int swap = minY;
                minY = maxY;
                maxY = swap;
            }
            plots.setHeight(player, plot, minY, maxY, null);
        } catch (NumberFormatException exception) {
            player.sendMessage(BaseText.error("Höhen müssen Zahlen sein."));
        }
    }

    private void help(Player player, String label) {
        player.sendMessage(BaseText.DIVIDER);
        player.sendMessage(BaseText.title("Grundstücke"));
        player.sendMessage(BaseText.plain("/" + label + " — öffnet das Menü", NamedTextColor.GRAY));
        player.sendMessage(BaseText.plain("/" + label + " tool", NamedTextColor.GRAY));
        player.sendMessage(BaseText.plain("/" + label + " cost", NamedTextColor.GRAY));
        player.sendMessage(BaseText.plain("/" + label + " claim <Name>", NamedTextColor.GRAY));
        player.sendMessage(BaseText.plain("/" + label + " info [Name]", NamedTextColor.GRAY));
        player.sendMessage(BaseText.plain("/" + label + " show", NamedTextColor.GRAY));
        player.sendMessage(BaseText.plain(
            "/" + label + " trust <Spieler> [manager]", NamedTextColor.GRAY));
        player.sendMessage(BaseText.plain("/" + label + " untrust <Spieler>", NamedTextColor.GRAY));
        player.sendMessage(BaseText.plain(
            "/" + label + " flag <Flagge> <on|off|default>", NamedTextColor.GRAY));
        player.sendMessage(BaseText.plain("/" + label + " sell confirm", NamedTextColor.GRAY));
        if (guard.isAdmin(player)) {
            player.sendMessage(BaseText.plain(
                "/" + label + " admin <claim <Name>|delete <Name>|height <min> <max>|reload>",
                NamedTextColor.GRAY));
        }
        player.sendMessage(BaseText.DIVIDER);
    }

    /** Das Grundstück unter den Füßen, sofern der Spieler es verwalten darf. */
    private Plot manageable(Player player) {
        Plot plot = guard.column(player.getLocation());
        if (plot == null) {
            player.sendMessage(BaseText.error("Stell dich auf das Grundstück, das du meinst."));
            return null;
        }
        if (!guard.canManage(player, plot)) {
            player.sendMessage(BaseText.error("Dafür fehlt dir die Berechtigung."));
            return null;
        }
        return plot;
    }

    private List<String> memberNames(Player player, String input) {
        Plot plot = guard.column(player.getLocation());
        if (plot == null) {
            return List.of();
        }
        return Players.filterPrefix(
            plot.members().values().stream().map(member -> member.playerName()).toList(), input);
    }

    private List<String> flagNames(Player player) {
        return Arrays.stream(PlotFlag.values())
            .filter(flag -> guard.canChangeFlag(player, flag))
            .map(PlotFlag::id)
            .toList();
    }

    private boolean require(Player player, String permissionKey) {
        if (player.hasPermission(plugin.permission(permissionKey))) {
            return true;
        }
        player.sendMessage(BaseText.error("Dafür fehlt dir die Berechtigung."));
        return false;
    }
}
