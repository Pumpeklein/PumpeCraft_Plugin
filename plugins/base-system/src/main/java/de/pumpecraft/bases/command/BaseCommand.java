package de.pumpecraft.bases.command;

import de.pumpecraft.bases.BaseText;
import de.pumpecraft.bases.PumpeBaseSystemPlugin;
import de.pumpecraft.bases.base.BaseService;
import de.pumpecraft.bases.base.BaseSort;
import de.pumpecraft.bases.base.PlayerBase;
import de.pumpecraft.bases.gui.BaseMenus;
import de.pumpecraft.utils.Players;
import de.pumpecraft.utils.Teleports;
import de.pumpecraft.utils.Texts;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class BaseCommand implements CommandExecutor, TabCompleter {
    private final PumpeBaseSystemPlugin plugin;
    private final BaseService service;
    private final BaseMenus menus;

    public BaseCommand(PumpeBaseSystemPlugin plugin, BaseService service, BaseMenus menus) {
        this.plugin = plugin;
        this.service = service;
        this.menus = menus;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return console(sender, label, args);
        }
        if (!player.hasPermission(plugin.permission("base-use"))) {
            player.sendMessage(BaseText.error("Dafür fehlt dir die Berechtigung."));
            return true;
        }
        if (args.length == 0) {
            menus.openMain(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "menu", "menü", "gui" -> menus.openMain(player);
            case "liste", "list", "browse", "top" -> menus.openBrowse(player, BaseSort.LIKES, 0);
            case "setzen", "set" -> setBase(player, label, args);
            case "besuchen", "visit" -> visit(player, label, args);
            case "liken", "like" -> like(player, label, args);
            case "info" -> info(player, args);
            case "öffentlich", "oeffentlich", "public" -> visibility(player, true);
            case "privat", "private" -> visibility(player, false);
            case "löschen", "loeschen", "delete" -> delete(player, args);
            case "help", "hilfe" -> sendHelp(player, label);
            default -> {
                player.sendMessage(BaseText.error("Unbekannter Base-Unterbefehl: " + args[0]));
                sendHelp(player, label);
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
        if (!sender.hasPermission(plugin.permission("base-use"))) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("info", "help"));
            if (sender instanceof Player) {
                options.addAll(List.of("menu", "liste"));
            } else {
                options.add("as");
            }
            if (sender.hasPermission(plugin.permission("base-set"))) {
                options.addAll(List.of("set", "public", "private", "delete"));
            }
            if (sender.hasPermission(plugin.permission("base-visit"))) {
                options.add("visit");
            }
            if (sender.hasPermission(plugin.permission("base-like"))) {
                options.add("like");
            }
            return Players.filterPrefix(options, args[0]);
        }
        if (args.length == 2) {
            String subcommand = args[0].toLowerCase(Locale.ROOT);
            if (!(sender instanceof Player) && matches(subcommand, "as")) {
                return Players.completeOnlineNames(args[1], 40);
            }
            if (matches(subcommand, "setzen", "set")) {
                return Players.filterPrefix(List.of("public", "private"), args[1]);
            }
            if (matches(subcommand, "besuchen", "visit", "liken", "like", "info")) {
                return Players.filterPrefix(service.directory().ownerNames(), args[1]);
            }
            if (matches(subcommand, "löschen", "loeschen", "delete")) {
                return Players.filterPrefix(List.of("confirm"), args[1]);
            }
        }
        return List.of();
    }

    private boolean console(CommandSender sender, String label, String[] args) {
        if (args.length == 0 || matches(args[0], "help", "hilfe")) {
            sendHelp(sender, label);
            return true;
        }
        if (matches(args[0], "as")) {
            if (args.length < 3) {
                sender.sendMessage(BaseText.error(
                    "Nutzung: /" + label + " as <Spieler> <Unterbefehl> ..."));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(BaseText.error("Dieser Spieler ist nicht online."));
                return true;
            }
            onCommand(
                target,
                plugin.getCommand("base"),
                label,
                Arrays.copyOfRange(args, 2, args.length)
            );
            sender.sendMessage(BaseText.success(
                "Base-Befehl als " + target.getName() + " ausgeführt."));
            return true;
        }
        if (matches(args[0], "info")) {
            if (args.length != 2) {
                sender.sendMessage(BaseText.error("Nutzung: /" + label + " info <Spieler>"));
                return true;
            }
            info(sender, args);
            return true;
        }
        sender.sendMessage(BaseText.error("Konsolennutzung: /" + label + " <as|info> ..."));
        return true;
    }

    private void setBase(Player player, String label, String[] args) {
        if (!requirePermission(player, "base-set")) {
            return;
        }
        if (args.length > 2) {
            player.sendMessage(BaseText.error("Nutzung: /" + label + " set [public|private]"));
            return;
        }
        Boolean publicBase = args.length == 2 ? visibilityValue(args[1]) : null;
        if (args.length == 2 && publicBase == null) {
            player.sendMessage(BaseText.error("Wähle public oder private."));
            return;
        }
        service.setBase(player, publicBase, null);
    }

    private void visibility(Player player, boolean publicBase) {
        if (!requirePermission(player, "base-set")) {
            return;
        }
        service.setVisibility(player, publicBase, null);
    }

    private void visit(Player player, String label, String[] args) {
        if (!requirePermission(player, "base-visit")) {
            return;
        }
        if (args.length > 2) {
            player.sendMessage(BaseText.error("Nutzung: /" + label + " visit [Spieler]"));
            return;
        }
        service.visit(player, args.length == 2 ? args[1] : player.getName(), null);
    }

    private void like(Player player, String label, String[] args) {
        if (!requirePermission(player, "base-like")) {
            return;
        }
        if (args.length != 2) {
            player.sendMessage(BaseText.error("Nutzung: /" + label + " like <Spieler>"));
            return;
        }
        service.toggleLike(player, args[1], null);
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length > 2) {
            sender.sendMessage(BaseText.error("Nutzung: /base info [Spieler]"));
            return;
        }
        String targetName = args.length == 2 ? args[1] : sender.getName();
        service.lookup(sender, targetName, base -> {
            if (base.isEmpty()) {
                sender.sendMessage(BaseText.error("Dieser Spieler hat keine Base gesetzt."));
                return;
            }
            showInfo(sender, base.get());
        });
    }

    private void delete(Player player, String[] args) {
        if (!requirePermission(player, "base-set")) {
            return;
        }
        if (args.length == 2 && matches(args[1], "bestätigen", "bestaetigen", "confirm")) {
            service.delete(player, null);
            return;
        }
        menus.openDeleteConfirm(player);
    }

    private void showInfo(CommandSender viewer, PlayerBase base) {
        boolean ownBase = viewer instanceof Player player
            && player.getUniqueId().equals(base.ownerId());
        boolean showCoordinates =
            base.publicBase() || ownBase || service.mayInspectPrivate(viewer);
        viewer.sendMessage(BaseText.DIVIDER);
        viewer.sendMessage(BaseText.title("Base von " + base.ownerName()));
        viewer.sendMessage(BaseText.label("Status: ",
            base.publicBase() ? "Öffentlich" : "Privat",
            base.publicBase() ? NamedTextColor.GREEN : NamedTextColor.RED));
        viewer.sendMessage(BaseText.label("Besuche: ",
                Texts.number(base.visitCount()), NamedTextColor.WHITE)
            .append(BaseText.hint(" · " + Texts.number(base.uniqueVisitors())
                + " unterschiedliche Spieler")));
        viewer.sendMessage(BaseText.label("Likes: ",
            Texts.number(base.likeCount()), NamedTextColor.YELLOW));
        viewer.sendMessage(showCoordinates
            ? BaseText.label("Position: ", position(base))
            : BaseText.hint("Position: verborgen (private Base)"));
        viewer.sendMessage(BaseText.DIVIDER);
    }

    /**
     * Für das Team ist die Position ein Klickziel; ohne geladene Welt bleibt nur der Klartext,
     * weil ein Teleport dorthin ohnehin scheitern würde.
     */
    private Component position(PlayerBase base) {
        Location location = base.bukkitLocation();
        if (location == null) {
            return BaseText.plain(base.location().worldName() + " · "
                + Math.round(base.location().x()) + " / "
                + Math.round(base.location().y()) + " / "
                + Math.round(base.location().z()), NamedTextColor.WHITE);
        }
        return BaseText.plain(base.location().worldName() + " ", NamedTextColor.WHITE)
            .append(Teleports.locationLink(
                location, NamedTextColor.AQUA, Teleports.DEFAULT_LOCATION_COMMAND));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(BaseText.DIVIDER);
        sender.sendMessage(BaseText.title("Base-System"));
        if (sender instanceof Player) {
            sender.sendMessage(BaseText.label("/" + label, " — öffnet das Menü",
                NamedTextColor.DARK_GRAY));
            sender.sendMessage(BaseText.plain("/" + label + " liste", NamedTextColor.GRAY));
        }
        sender.sendMessage(BaseText.plain("/" + label + " info [Spieler]", NamedTextColor.GRAY));
        if (sender.hasPermission(plugin.permission("base-set"))) {
            sender.sendMessage(BaseText.plain(
                "/" + label + " set [public|private]", NamedTextColor.GRAY));
            sender.sendMessage(BaseText.plain(
                "/" + label + " public|private", NamedTextColor.GRAY));
            sender.sendMessage(BaseText.plain(
                "/" + label + " delete confirm", NamedTextColor.GRAY));
        }
        if (sender.hasPermission(plugin.permission("base-visit"))) {
            sender.sendMessage(BaseText.plain(
                "/" + label + " visit [Spieler]", NamedTextColor.GRAY));
        }
        if (sender.hasPermission(plugin.permission("base-like"))) {
            sender.sendMessage(BaseText.plain(
                "/" + label + " like <Spieler>", NamedTextColor.GRAY));
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(BaseText.plain(
                "/" + label + " as <Spieler> <Unterbefehl>", NamedTextColor.GRAY));
        }
        sender.sendMessage(BaseText.DIVIDER);
    }

    private Boolean visibilityValue(String input) {
        if (matches(input, "öffentlich", "oeffentlich", "public")) {
            return true;
        }
        if (matches(input, "privat", "private")) {
            return false;
        }
        return null;
    }

    private boolean requirePermission(Player player, String key) {
        if (player.hasPermission(plugin.permission(key))) {
            return true;
        }
        player.sendMessage(BaseText.error("Dafür fehlt dir die Berechtigung."));
        return false;
    }

    private boolean matches(String value, String... options) {
        for (String option : options) {
            if (value.equalsIgnoreCase(option)) {
                return true;
            }
        }
        return false;
    }
}
