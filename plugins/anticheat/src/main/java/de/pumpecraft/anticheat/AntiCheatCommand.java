package de.pumpecraft.anticheat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

final class AntiCheatCommand implements CommandExecutor, TabCompleter {
    private static final List<String> ACTIONS = List.of("status", "violations", "reset", "reload");

    private final PumpeAntiCheatPlugin plugin;
    private final PlayerStateStore states;
    private final ViolationService violations;
    private final BedrockDetector bedrockDetector;

    AntiCheatCommand(
        PumpeAntiCheatPlugin plugin,
        PlayerStateStore states,
        ViolationService violations,
        BedrockDetector bedrockDetector
    ) {
        this.plugin = plugin;
        this.states = states;
        this.violations = violations;
        this.bedrockDetector = bedrockDetector;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            showStatus(sender, args.length >= 2 ? findKnownPlayer(args[1]) : null);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadSettings();
            sender.sendMessage(Component.text("AntiCheat-Konfiguration neu geladen.", NamedTextColor.GREEN));
            return true;
        }

        if (args[0].equalsIgnoreCase("violations")) {
            OfflinePlayer target = args.length >= 2 ? findKnownPlayer(args[1]) : ownPlayer(sender);
            if (target == null) {
                sender.sendMessage(Component.text("Spieler nicht gefunden.", NamedTextColor.RED));
                return true;
            }
            showViolations(sender, target);
            return true;
        }

        if (args[0].equalsIgnoreCase("reset")) {
            if (args.length < 2) {
                sender.sendMessage(Component.text("Verwendung: /anticheat reset <Spieler>", NamedTextColor.RED));
                return true;
            }
            OfflinePlayer target = findKnownPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Spieler nicht gefunden.", NamedTextColor.RED));
                return true;
            }
            violations.reset(target.getUniqueId());
            sender.sendMessage(Component.text(
                "Violations von " + displayName(target) + " zurückgesetzt.",
                NamedTextColor.GREEN
            ));
            return true;
        }

        sender.sendMessage(Component.text(
            "Verwendung: /anticheat <status|violations|reset|reload> [Spieler]",
            NamedTextColor.RED
        ));
        return true;
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
            return filter(ACTIONS, args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
            return completeKnownPlayers(args[1]);
        }
        return List.of();
    }

    private void showStatus(CommandSender sender, OfflinePlayer target) {
        sender.sendMessage(Component.text("PumpeAntiCheat", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(
            " - Bedrock-Erkennung: " + bedrockDetector.providerName(),
            bedrockDetector.isAvailable() ? NamedTextColor.GREEN : NamedTextColor.YELLOW
        ));
        long enabledChecks = java.util.Arrays.stream(CheckType.values()).filter(violations::enabled).count();
        sender.sendMessage(Component.text(
            " - Checks: " + enabledChecks + "/" + CheckType.values().length + " aktiv",
            NamedTextColor.AQUA
        ));
        if (target != null) {
            boolean bedrock = bedrockDetector.isBedrock(target.getUniqueId());
            sender.sendMessage(Component.text(
                " - " + displayName(target) + ": " + (bedrock ? "Bedrock" : "Java"),
                bedrock ? NamedTextColor.YELLOW : NamedTextColor.GRAY
            ));
        }
    }

    private void showViolations(CommandSender sender, OfflinePlayer target) {
        sender.sendMessage(Component.text(
            "Violations von " + displayName(target),
            NamedTextColor.GOLD
        ));
        PlayerState state = states.find(target.getUniqueId());
        if (state == null || state.violations.values().stream().noneMatch(level -> level > 0.0)) {
            sender.sendMessage(Component.text(" - Keine aktuellen Violations", NamedTextColor.GREEN));
            return;
        }
        for (CheckType check : CheckType.values()) {
            double level = state.violation(check);
            if (level <= 0.0) {
                continue;
            }
            sender.sendMessage(Component.text(
                " - " + check.displayName() + ": " + String.format(Locale.ROOT, "%.1f", level),
                NamedTextColor.YELLOW
            ));
        }
    }

    private OfflinePlayer ownPlayer(CommandSender sender) {
        return sender instanceof Player player ? player : null;
    }

    private OfflinePlayer findKnownPlayer(String input) {
        String name = input.startsWith("@") ? input.substring(1) : input;
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null && player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return null;
    }

    private List<String> completeKnownPlayers(String input) {
        boolean atPrefix = input.startsWith("@");
        String prefix = (atPrefix ? input.substring(1) : input).toLowerCase(Locale.ROOT);
        Set<String> names = new LinkedHashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null) {
                names.add(player.getName());
            }
        }
        return names.stream()
            .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .limit(40)
            .map(name -> atPrefix ? "@" + name : name)
            .toList();
    }

    private List<String> filter(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.startsWith(prefix)) {
                matches.add(value);
            }
        }
        return matches;
    }

    private String displayName(OfflinePlayer player) {
        String name = player.getName();
        UUID playerId = player.getUniqueId();
        return name == null ? playerId.toString().substring(0, 8) : name;
    }
}
