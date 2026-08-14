package de.pumpecraft.anticheat;

import de.pumpecraft.anticheat.client.ClientDetectionService;
import de.pumpecraft.anticheat.client.ClientReport;
import de.pumpecraft.anticheat.core.AlertDispatcher;
import de.pumpecraft.anticheat.core.CheckSettings;
import de.pumpecraft.anticheat.core.CheckType;
import de.pumpecraft.anticheat.core.PlayerState;
import de.pumpecraft.anticheat.core.PlayerStateStore;
import de.pumpecraft.anticheat.core.ViolationService;
import de.pumpecraft.anticheat.platform.BedrockDetector;
import de.pumpecraft.utils.Players;
import de.pumpecraft.utils.Texts;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class AntiCheatCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ACTIONS = List.of(
        "status", "violations", "client", "recent", "alerts", "checks", "reset", "reload"
    );
    private static final int MAX_LISTED_CHANNELS = 20;
    private static final int MAX_RECENT_ENTRIES = 10;

    private final PumpeAntiCheatPlugin plugin;
    private final PlayerStateStore states;
    private final ViolationService violations;
    private final CheckSettings settings;
    private final AlertDispatcher alerts;
    private final ClientDetectionService clientDetection;
    private final BedrockDetector bedrockDetector;

    public AntiCheatCommand(
        PumpeAntiCheatPlugin plugin,
        PlayerStateStore states,
        ViolationService violations,
        CheckSettings settings,
        AlertDispatcher alerts,
        ClientDetectionService clientDetection,
        BedrockDetector bedrockDetector
    ) {
        this.plugin = plugin;
        this.states = states;
        this.violations = violations;
        this.settings = settings;
        this.alerts = alerts;
        this.clientDetection = clientDetection;
        this.bedrockDetector = bedrockDetector;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        String argument = args.length >= 2 ? args[1] : null;

        return switch (action) {
            case "status" -> status(sender, argument);
            case "violations" -> violations(sender, argument);
            case "client" -> client(sender, argument);
            case "recent" -> recent(sender, argument);
            case "alerts" -> alerts(sender);
            case "checks" -> checks(sender);
            case "reset" -> reset(sender, argument);
            case "reload" -> reload(sender);
            default -> usage(sender);
        };
    }

    @Override
    public List<String> onTabComplete(
        CommandSender sender,
        Command command,
        String alias,
        String[] args
    ) {
        if (!command.testPermissionSilent(sender)) {
            return List.of();
        }
        if (args.length == 1) {
            return Players.filterPrefix(ACTIONS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("client")) {
            return Players.completeOnlineNames(args[1], 40);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload")
            && !args[0].equalsIgnoreCase("alerts")
            && !args[0].equalsIgnoreCase("checks")) {
            return Players.completeKnownNames(args[1], 40);
        }
        return List.of();
    }

    private boolean status(CommandSender sender, String argument) {
        sender.sendMessage(Component.text("PumpeAntiCheat", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(
            " - Bedrock-Erkennung: " + bedrockDetector.providerName(),
            bedrockDetector.isAvailable() ? NamedTextColor.GREEN : NamedTextColor.YELLOW
        ));

        long enabled = java.util.Arrays.stream(CheckType.values()).filter(settings::enabled).count();
        sender.sendMessage(Component.text(
            " - Checks: " + enabled + "/" + CheckType.values().length + " aktiv",
            NamedTextColor.AQUA
        ));
        sender.sendMessage(Component.text(
            " - Alerts: " + (plugin.getConfig().getBoolean("alerts.aggregate", true)
                ? "gebündelt alle "
                    + plugin.getConfig().getLong("alerts.flush-interval-ticks", 100L) / 20L + "s"
                : "sofort"),
            NamedTextColor.AQUA
        ));

        if (argument == null) {
            return true;
        }
        Optional<OfflinePlayer> target = Players.known(argument);
        if (target.isEmpty()) {
            return notFound(sender);
        }
        boolean bedrock = bedrockDetector.isBedrock(target.get().getUniqueId());
        sender.sendMessage(Component.text(
            " - " + Players.displayName(target.get()) + ": " + (bedrock ? "Bedrock" : "Java"),
            bedrock ? NamedTextColor.YELLOW : NamedTextColor.GRAY
        ));
        return true;
    }

    private boolean violations(CommandSender sender, String argument) {
        Optional<OfflinePlayer> target = argument == null
            ? Players.self(sender).map(OfflinePlayer.class::cast)
            : Players.known(argument);
        if (target.isEmpty()) {
            return notFound(sender);
        }

        sender.sendMessage(Component.text(
            "Violations von " + Players.displayName(target.get()),
            NamedTextColor.GOLD
        ));
        PlayerState state = states.find(target.get().getUniqueId());
        if (state == null || state.violations.values().stream().noneMatch(level -> level > 0.0)) {
            sender.sendMessage(Component.text(" - Keine aktuellen Violations", NamedTextColor.GREEN));
            return true;
        }

        Map<CheckType.Category, List<String>> grouped = new EnumMap<>(CheckType.Category.class);
        for (CheckType check : CheckType.values()) {
            double level = state.violation(check);
            if (level > 0.0) {
                grouped.computeIfAbsent(check.category(), ignored -> new ArrayList<>())
                    .add(check.displayName() + " " + Texts.decimal(level, 1));
            }
        }
        grouped.forEach((category, entries) -> sender.sendMessage(Component.text(
            " " + category.displayName() + ": " + String.join(", ", entries),
            NamedTextColor.YELLOW
        )));
        return true;
    }

    private boolean client(CommandSender sender, String argument) {
        Optional<Player> target = argument == null
            ? Players.self(sender)
            : Players.online(argument);
        if (target.isEmpty()) {
            sender.sendMessage(Component.text("Spieler ist nicht online.", NamedTextColor.RED));
            return true;
        }

        ClientReport report = clientDetection.report(target.get());
        sender.sendMessage(Component.text("ClientCheck: ", NamedTextColor.GOLD)
            .append(alerts.playerLink(target.get().getName())));
        sender.sendMessage(Component.text(" - Position: ", NamedTextColor.GRAY)
            .append(alerts.locationLink(target.get().getLocation())));
        sender.sendMessage(Component.text(
            " - Plattform: " + (report.bedrock() ? "Bedrock" : "Java"),
            report.bedrock() ? NamedTextColor.YELLOW : NamedTextColor.GRAY
        ));
        sender.sendMessage(Component.text(
            " - Brand: " + (report.brand() == null ? "noch nicht empfangen" : report.brand()),
            report.brand() == null ? NamedTextColor.RED : NamedTextColor.AQUA
        ));
        sender.sendMessage(Component.text(" - Loader: " + report.loader(), NamedTextColor.AQUA));
        sender.sendMessage(Component.text(
            " - Client: " + (report.client() == null ? "keine Signatur" : report.client()),
            report.client() == null ? NamedTextColor.GRAY : NamedTextColor.RED
        ));
        sender.sendMessage(Component.text(
            " - Mods: " + (report.mods().isEmpty() ? "keine erkannt" : String.join(", ", report.mods())),
            report.mods().isEmpty() ? NamedTextColor.GRAY : NamedTextColor.YELLOW
        ));

        List<String> channels = report.channels();
        sender.sendMessage(Component.text(" - Kanäle (" + channels.size() + "):", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
            "   " + (channels.isEmpty()
                ? "keine"
                : Texts.joinLimited(channels, MAX_LISTED_CHANNELS, "... und {count} weitere")),
            NamedTextColor.DARK_GRAY
        ));
        return true;
    }

    private boolean recent(CommandSender sender, String argument) {
        Optional<OfflinePlayer> filter = argument == null ? Optional.empty() : Players.known(argument);
        if (argument != null && filter.isEmpty()) {
            return notFound(sender);
        }

        List<AlertDispatcher.Entry> entries = alerts.recent(
            filter.map(OfflinePlayer::getUniqueId).orElse(null),
            MAX_RECENT_ENTRIES
        );
        sender.sendMessage(Component.text(
            "Letzte Meldungen" + (argument == null ? "" : " von " + argument),
            NamedTextColor.GOLD
        ));
        if (entries.isEmpty()) {
            sender.sendMessage(Component.text(" - keine", NamedTextColor.GREEN));
            return true;
        }
        long now = System.currentTimeMillis();
        for (AlertDispatcher.Entry entry : entries) {
            Component line = Component.text(
                " " + Duration.ofMillis(now - entry.createdAt()).toSeconds() + "s her  ",
                NamedTextColor.DARK_GRAY
            ).append(alerts.playerLink(entry.playerName()))
                .append(Component.text(" » " + entry.check().displayName(), NamedTextColor.GRAY))
                .append(Component.text(
                    " (VL " + Texts.decimal(entry.level(), 1) + ") ",
                    NamedTextColor.DARK_GRAY
                ))
                .append(Component.text(entry.detail(), NamedTextColor.GRAY));
            if (entry.location() != null) {
                line = line.append(Component.space()).append(alerts.locationLink(entry.location()));
            }
            sender.sendMessage(line);
        }
        return true;
    }

    private boolean alerts(CommandSender sender) {
        Optional<Player> staff = Players.self(sender);
        if (staff.isEmpty()) {
            sender.sendMessage(Component.text("Nur für Spieler.", NamedTextColor.RED));
            return true;
        }
        boolean muted = alerts.toggleMute(staff.get());
        sender.sendMessage(Component.text(
            muted ? "AntiCheat-Meldungen stummgeschaltet." : "AntiCheat-Meldungen aktiviert.",
            muted ? NamedTextColor.YELLOW : NamedTextColor.GREEN
        ));
        return true;
    }

    private boolean checks(CommandSender sender) {
        sender.sendMessage(Component.text("Checks", NamedTextColor.GOLD));
        for (CheckType.Category category : CheckType.Category.values()) {
            Component line = Component.text(" " + category.displayName() + ": ", NamedTextColor.GRAY);
            boolean first = true;
            for (CheckType check : CheckType.values()) {
                if (check.category() != category) {
                    continue;
                }
                if (!first) {
                    line = line.append(Component.text(", ", NamedTextColor.DARK_GRAY));
                }
                line = line.append(Component.text(
                    check.displayName(),
                    settings.enabled(check) ? NamedTextColor.GREEN : NamedTextColor.RED
                ));
                first = false;
            }
            sender.sendMessage(line);
        }
        return true;
    }

    private boolean reset(CommandSender sender, String argument) {
        if (argument == null) {
            sender.sendMessage(Component.text(
                "Verwendung: /anticheat reset <Spieler>",
                NamedTextColor.RED
            ));
            return true;
        }
        Optional<OfflinePlayer> target = Players.known(argument);
        if (target.isEmpty()) {
            return notFound(sender);
        }
        violations.reset(target.get().getUniqueId());
        sender.sendMessage(Component.text(
            "Violations von " + Players.displayName(target.get()) + " zurückgesetzt.",
            NamedTextColor.GREEN
        ));
        return true;
    }

    private boolean reload(CommandSender sender) {
        plugin.reloadSettings();
        sender.sendMessage(Component.text(
            "AntiCheat-Konfiguration neu geladen.",
            NamedTextColor.GREEN
        ));
        return true;
    }

    private boolean usage(CommandSender sender) {
        sender.sendMessage(Component.text(
            "Verwendung: /anticheat <" + String.join("|", ACTIONS) + "> [Spieler]",
            NamedTextColor.RED
        ));
        return true;
    }

    private boolean notFound(CommandSender sender) {
        sender.sendMessage(Component.text("Spieler nicht gefunden.", NamedTextColor.RED));
        return true;
    }
}
