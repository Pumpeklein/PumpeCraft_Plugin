package de.pumpecraft.clans;

import de.pumpecraft.clans.ClanData.PlayerBase;
import de.pumpecraft.clans.ClanData.PlayerIdentity;
import de.pumpecraft.clans.ClanRepository.BaseLocation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

final class BaseCommand implements CommandExecutor, TabCompleter {
    private static final Component DIVIDER = Component.text("─".repeat(36), NamedTextColor.DARK_GRAY);

    private final PumpeClanSystemPlugin plugin;
    private final ClanRepository repository;
    private final boolean defaultPublic;

    BaseCommand(PumpeClanSystemPlugin plugin, ClanRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        defaultPublic = plugin.getConfig().getBoolean("bases.default-public", false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                "Dieser Befehl kann nur von Spielern genutzt werden.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission(plugin.permission("base-use"))) {
            player.sendMessage(error("Dafür fehlt dir die Berechtigung."));
            return true;
        }
        if (args.length == 0 || matches(args[0], "hilfe", "help")) {
            sendHelp(player, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "setzen", "set" -> setBase(player, label, args);
            case "besuchen", "visit" -> visit(player, label, args);
            case "liken", "like" -> like(player, label, args);
            case "info" -> info(player, args);
            case "öffentlich", "oeffentlich", "public" -> visibility(player, true);
            case "privat", "private" -> visibility(player, false);
            case "löschen", "loeschen", "delete" -> delete(player, label, args);
            default -> {
                player.sendMessage(error("Unbekannter Base-Unterbefehl: " + args[0]));
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
            List<String> options = new ArrayList<>(List.of("info", "hilfe"));
            if (sender.hasPermission(plugin.permission("base-set"))) {
                options.addAll(List.of("setzen", "öffentlich", "privat", "löschen"));
            }
            if (sender.hasPermission(plugin.permission("base-visit"))) {
                options.add("besuchen");
            }
            if (sender.hasPermission(plugin.permission("base-like"))) {
                options.add("liken");
            }
            return filter(options, args[0]);
        }
        if (args.length == 2) {
            String subcommand = args[0].toLowerCase(Locale.ROOT);
            if (matches(subcommand, "setzen", "set")) {
                return filter(List.of("öffentlich", "privat"), args[1]);
            }
            if (matches(subcommand, "besuchen", "visit", "liken", "like", "info")) {
                return filter(plugin.directory().baseOwnerNames(), args[1]);
            }
            if (matches(subcommand, "löschen", "loeschen", "delete")) {
                return filter(List.of("bestätigen"), args[1]);
            }
        }
        return List.of();
    }

    private void setBase(Player player, String label, String[] args) {
        if (!requirePermission(player, "base-set")) {
            return;
        }
        if (args.length > 2) {
            player.sendMessage(error(
                "Nutzung: /" + label + " setzen [öffentlich|privat]"));
            return;
        }
        Boolean publicBase = args.length == 2 ? visibilityValue(args[1]) : defaultPublic;
        if (publicBase == null) {
            player.sendMessage(error("Wähle öffentlich oder privat."));
            return;
        }

        Location location = player.getLocation();
        BaseLocation baseLocation = new BaseLocation(
            location.getWorld().getUID(),
            location.getWorld().getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch()
        );
        PlayerIdentity owner = identity(player);
        boolean finalPublicBase = publicBase;
        plugin.runAsync(player, () -> {
            repository.setBase(owner, baseLocation, finalPublicBase, System.currentTimeMillis());
            return true;
        }, ignored -> {
            player.sendMessage(success(
                "Deine Base wurde gesetzt und ist "
                    + (finalPublicBase ? "öffentlich." : "privat.")));
            plugin.refreshDirectory();
        });
    }

    private void visibility(Player player, boolean publicBase) {
        if (!requirePermission(player, "base-set")) {
            return;
        }
        plugin.runAsync(
            player,
            () -> repository.setBaseVisibility(player.getUniqueId(), publicBase),
            updated -> player.sendMessage(updated
                ? success("Deine Base ist jetzt " + (publicBase ? "öffentlich." : "privat."))
                : error("Du hast noch keine Base gesetzt."))
        );
    }

    private void visit(Player player, String label, String[] args) {
        if (!requirePermission(player, "base-visit")) {
            return;
        }
        if (args.length > 2) {
            player.sendMessage(error("Nutzung: /" + label + " besuchen [Spieler]"));
            return;
        }
        String targetName = args.length == 2 ? args[1] : player.getName();
        UUID visitorId = player.getUniqueId();
        boolean bypassPrivate = player.hasPermission(plugin.permission("base-admin"));
        plugin.runAsync(player, () -> repository.baseForName(targetName), base -> {
            if (base.isEmpty()) {
                player.sendMessage(error("Dieser Spieler hat keine Base gesetzt."));
                return;
            }
            PlayerBase target = base.get();
            boolean ownBase = target.ownerId().equals(visitorId);
            if (!target.publicBase() && !ownBase && !bypassPrivate) {
                player.sendMessage(error("Diese Base ist privat."));
                return;
            }
            World world = Bukkit.getWorld(target.worldId());
            if (world == null) {
                world = Bukkit.getWorld(target.worldName());
            }
            if (world == null) {
                player.sendMessage(error("Die Welt dieser Base ist momentan nicht verfügbar."));
                return;
            }
            Location destination = new Location(
                world, target.x(), target.y(), target.z(), target.yaw(), target.pitch());
            if (!player.teleport(destination)) {
                player.sendMessage(error("Die Teleportation zur Base ist fehlgeschlagen."));
                return;
            }
            player.sendMessage(success("Du besuchst die Base von " + target.ownerName() + "."));
            if (!ownBase) {
                PlayerIdentity visitor = identity(player);
                plugin.runAsync(() -> repository.recordVisit(
                    target.ownerId(), visitor, System.currentTimeMillis()));
            }
        });
    }

    private void like(Player player, String label, String[] args) {
        if (!requirePermission(player, "base-like")) {
            return;
        }
        if (args.length != 2) {
            player.sendMessage(error("Nutzung: /" + label + " liken <Spieler>"));
            return;
        }
        PlayerIdentity liker = identity(player);
        boolean bypassPrivate = player.hasPermission(plugin.permission("base-admin"));
        plugin.runAsync(player, () -> {
            Optional<PlayerBase> base = repository.baseForName(args[1]);
            if (base.isEmpty()) {
                return new LikeOutcome(LikeStatus.NO_BASE, null);
            }
            if (base.get().ownerId().equals(liker.playerId())) {
                return new LikeOutcome(LikeStatus.OWN_BASE, base.get());
            }
            if (!base.get().publicBase() && !bypassPrivate) {
                return new LikeOutcome(LikeStatus.PRIVATE, base.get());
            }
            boolean added = repository.likeBase(
                base.get().ownerId(), liker, System.currentTimeMillis());
            return new LikeOutcome(added ? LikeStatus.LIKED : LikeStatus.ALREADY_LIKED, base.get());
        }, outcome -> {
            switch (outcome.status()) {
                case LIKED -> player.sendMessage(success(
                    "Du hast die Base von " + outcome.base().ownerName() + " geliked."));
                case ALREADY_LIKED -> player.sendMessage(error(
                    "Du hast diese Base bereits geliked."));
                case NO_BASE -> player.sendMessage(error("Dieser Spieler hat keine Base gesetzt."));
                case OWN_BASE -> player.sendMessage(error("Du kannst deine eigene Base nicht liken."));
                case PRIVATE -> player.sendMessage(error("Diese Base ist privat."));
            }
        });
    }

    private void info(Player player, String[] args) {
        if (args.length > 2) {
            player.sendMessage(error("Nutzung: /base info [Spieler]"));
            return;
        }
        String targetName = args.length == 2 ? args[1] : player.getName();
        boolean admin = player.hasPermission(plugin.permission("base-admin"));
        plugin.runAsync(player, () -> repository.baseForName(targetName), base -> {
            if (base.isEmpty()) {
                player.sendMessage(error("Dieser Spieler hat keine Base gesetzt."));
                return;
            }
            showBaseInfo(player, base.get(), admin);
        });
    }

    private void delete(Player player, String label, String[] args) {
        if (!requirePermission(player, "base-set")) {
            return;
        }
        if (args.length != 2 || !matches(args[1], "bestätigen", "bestaetigen", "confirm")) {
            player.sendMessage(error("Nutzung: /" + label + " löschen bestätigen"));
            return;
        }
        plugin.runAsync(
            player,
            () -> repository.deleteBase(player.getUniqueId()),
            deleted -> {
                player.sendMessage(deleted
                    ? success("Deine Base und ihre Statistiken wurden gelöscht.")
                    : error("Du hast noch keine Base gesetzt."));
                if (deleted) {
                    plugin.refreshDirectory();
                }
            }
        );
    }

    private void showBaseInfo(Player viewer, PlayerBase base, boolean admin) {
        boolean owner = viewer.getUniqueId().equals(base.ownerId());
        boolean showCoordinates = base.publicBase() || owner || admin;
        playerMessage(viewer, DIVIDER);
        playerMessage(viewer, Component.text(
            "Base von " + base.ownerName(), NamedTextColor.GOLD, TextDecoration.BOLD));
        playerMessage(viewer, Component.text("Status: ", NamedTextColor.GRAY)
            .append(Component.text(
                base.publicBase() ? "Öffentlich" : "Privat",
                base.publicBase() ? NamedTextColor.GREEN : NamedTextColor.RED
            )));
        playerMessage(viewer, Component.text("Besuche: ", NamedTextColor.GRAY)
            .append(Component.text(formatNumber(base.visitCount()), NamedTextColor.WHITE))
            .append(Component.text(
                " · " + formatNumber(base.uniqueVisitors()) + " unterschiedliche Spieler",
                NamedTextColor.DARK_GRAY
            )));
        playerMessage(viewer, Component.text("Likes: ", NamedTextColor.GRAY)
            .append(Component.text(formatNumber(base.likeCount()), NamedTextColor.YELLOW)));
        if (showCoordinates) {
            playerMessage(viewer, Component.text("Position: ", NamedTextColor.GRAY)
                .append(Component.text(
                    base.worldName() + " · "
                        + Math.round(base.x()) + " / "
                        + Math.round(base.y()) + " / "
                        + Math.round(base.z()),
                    NamedTextColor.WHITE
                )));
        } else {
            playerMessage(viewer, Component.text(
                "Position: verborgen (private Base)", NamedTextColor.DARK_GRAY));
        }
        playerMessage(viewer, DIVIDER);
    }

    private void sendHelp(Player player, String label) {
        player.sendMessage(DIVIDER);
        player.sendMessage(Component.text("Base-System", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("/" + label + " info [Spieler]", NamedTextColor.GRAY));
        if (player.hasPermission(plugin.permission("base-set"))) {
            player.sendMessage(Component.text(
                "/" + label + " setzen [öffentlich|privat]", NamedTextColor.GRAY));
            player.sendMessage(Component.text(
                "/" + label + " öffentlich|privat", NamedTextColor.GRAY));
            player.sendMessage(Component.text(
                "/" + label + " löschen bestätigen", NamedTextColor.GRAY));
        }
        if (player.hasPermission(plugin.permission("base-visit"))) {
            player.sendMessage(Component.text(
                "/" + label + " besuchen [Spieler]", NamedTextColor.GRAY));
        }
        if (player.hasPermission(plugin.permission("base-like"))) {
            player.sendMessage(Component.text(
                "/" + label + " liken <Spieler>", NamedTextColor.GRAY));
        }
        player.sendMessage(DIVIDER);
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
        player.sendMessage(error("Dafür fehlt dir die Berechtigung."));
        return false;
    }

    private PlayerIdentity identity(Player player) {
        return new PlayerIdentity(player.getUniqueId(), player.getName());
    }

    private boolean matches(String value, String... options) {
        for (String option : options) {
            if (value.equalsIgnoreCase(option)) {
                return true;
            }
        }
        return false;
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        Set<String> unique = new LinkedHashSet<>();
        for (String option : options) {
            if (option != null && option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                unique.add(option);
            }
            if (unique.size() >= 40) {
                break;
            }
        }
        return List.copyOf(unique);
    }

    private String formatNumber(long value) {
        return String.format(Locale.GERMANY, "%,d", value);
    }

    private void playerMessage(Player player, Component message) {
        player.sendMessage(message);
    }

    private Component success(String message) {
        return Component.text(message, NamedTextColor.GREEN);
    }

    private Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }

    private enum LikeStatus {
        LIKED,
        ALREADY_LIKED,
        NO_BASE,
        OWN_BASE,
        PRIVATE
    }

    private record LikeOutcome(LikeStatus status, PlayerBase base) {
    }
}
