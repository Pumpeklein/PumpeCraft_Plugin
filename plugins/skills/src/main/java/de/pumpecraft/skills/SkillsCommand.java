package de.pumpecraft.skills;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

final class SkillsCommand implements CommandExecutor, TabCompleter {
    private static final int LEADERBOARD_SIZE = 10;
    private static final String OTHERS_PERMISSION = "pumpecraft.skills.others";

    private static final Component DIVIDER =
        Component.text("─".repeat(34), NamedTextColor.DARK_GRAY);

    private final SkillService service;
    private final SkillRepository repository;
    private final SkillsGui gui;

    SkillsCommand(
        SkillService service,
        SkillRepository repository,
        SkillsGui gui
    ) {
        this.service = service;
        this.repository = repository;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                "Dieser Befehl kann nur von Spielern genutzt werden.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            gui.openOverview(player, player.getUniqueId(), player.getName());
            return true;
        }

        if (args[0].equalsIgnoreCase("top")) {
            handleTop(player, label, args);
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(player, label);
            return true;
        }

        Skill skill = Skill.byId(args[0]);
        if (skill != null) {
            gui.openDetail(player, player.getUniqueId(), player.getName(), skill);
            return true;
        }

        handleOtherPlayer(player, label, args);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.testPermissionSilent(sender)) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> options = new ArrayList<>(skillIds());
            options.add("top");
            options.add("help");
            if (sender.hasPermission(OTHERS_PERMISSION)) {
                Bukkit.getOnlinePlayers().forEach(online -> options.add(online.getName()));
            }
            return filter(options, args[0]);
        }

        if (args.length == 2) {
            return filter(skillIds(), args[1]);
        }

        return List.of();
    }

    // ── Unterbefehle ──

    private void handleTop(Player player, String label, String[] args) {
        if (args.length < 2) {
            player.sendMessage(error("Nutzung: /" + label + " top <Skill>"));
            player.sendMessage(hint("Skills: " + String.join(", ", skillIds())));
            return;
        }

        Skill skill = Skill.byId(args[1]);
        if (skill == null) {
            player.sendMessage(error("Unbekannter Skill: " + args[1]));
            player.sendMessage(hint("Skills: " + String.join(", ", skillIds())));
            return;
        }
        if (!skill.leveled()) {
            player.sendMessage(error("Für " + skill.displayName() + " gibt es keine Bestenliste."));
            return;
        }

        service.runAsync(() -> {
            List<SkillRepository.LeaderboardEntry> entries =
                repository.topPlayers(skill, Skill.SCORE, LEADERBOARD_SIZE);
            service.runSync(() -> sendLeaderboard(player, skill, entries));
        });
    }

    private void handleOtherPlayer(Player player, String label, String[] args) {
        if (!player.hasPermission(OTHERS_PERMISSION)) {
            player.sendMessage(error("Unbekannter Skill: " + args[0]));
            player.sendMessage(hint("Skills: " + String.join(", ", skillIds())));
            return;
        }

        String targetName = args[0];
        Skill skill = args.length >= 2 ? Skill.byId(args[1]) : null;
        if (args.length >= 2 && skill == null) {
            player.sendMessage(error("Unbekannter Skill: " + args[1]));
            return;
        }

        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            openTargetGui(player, online.getUniqueId(), online.getName(), skill);
            return;
        }

        service.runAsync(() -> {
            UUID targetId = repository.findPlayerByName(targetName);
            if (targetId == null) {
                service.runSync(() -> player.sendMessage(
                    error("Der Spieler " + targetName + " ist nicht bekannt.")));
                return;
            }
            service.runSync(() -> openTargetGui(player, targetId, targetName, skill));
        });
    }

    private void openTargetGui(
        Player viewer,
        UUID targetId,
        String targetName,
        Skill skill
    ) {
        if (skill == null) {
            gui.openOverview(viewer, targetId, targetName);
        } else {
            gui.openDetail(viewer, targetId, targetName, skill);
        }
    }

    private void sendLeaderboard(
        Player player,
        Skill skill,
        List<SkillRepository.LeaderboardEntry> entries
    ) {
        if (!player.isOnline()) {
            return;
        }
        player.sendMessage(DIVIDER);
        player.sendMessage(Component.text("Bestenliste · ", NamedTextColor.GOLD)
            .append(Component.text(skill.displayName(), skill.color(), TextDecoration.BOLD)));
        player.sendMessage(DIVIDER);

        if (entries.isEmpty()) {
            player.sendMessage(hint("Hier hat noch niemand Punkte gesammelt."));
            return;
        }

        int position = 1;
        for (SkillRepository.LeaderboardEntry entry : entries) {
            boolean self = entry.playerId().equals(player.getUniqueId());
            player.sendMessage(Component.text(rankLabel(position), rankColor(position))
                .append(Component.text(" " + entry.playerName(),
                    self ? NamedTextColor.WHITE : NamedTextColor.GRAY))
                .append(Component.text("  Lv " + SkillLevel.levelOf(entry.amount()) + "  ",
                    NamedTextColor.DARK_GRAY))
                .append(Component.text(number(entry.amount()), skill.color())));
            position++;
        }
    }

    private void sendHelp(Player player, String label) {
        player.sendMessage(DIVIDER);
        player.sendMessage(Component.text("Skills", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(DIVIDER);
        player.sendMessage(hint("/" + label + " — öffnet deine Skill-Übersicht als GUI"));
        player.sendMessage(hint("/" + label + " <Skill> — öffnet die Skill-Details als GUI"));
        player.sendMessage(hint("/" + label + " top <Skill> — Bestenliste"));
        if (player.hasPermission(OTHERS_PERMISSION)) {
            player.sendMessage(hint("/" + label + " <Spieler> [Skill] — Werte eines Spielers"));
        }
        player.sendMessage(Component.empty());
        for (Skill skill : Skill.LEVELED) {
            player.sendMessage(Component.text("  " + pad(skill.id(), 12), skill.color())
                .append(Component.text(skill.description(), NamedTextColor.DARK_GRAY)));
        }
    }

    // ── Hilfsmethoden ──

    private String rankLabel(int position) {
        return "#" + pad(String.valueOf(position), 3);
    }

    private NamedTextColor rankColor(int position) {
        return switch (position) {
            case 1 -> NamedTextColor.GOLD;
            case 2 -> NamedTextColor.WHITE;
            case 3 -> NamedTextColor.YELLOW;
            default -> NamedTextColor.DARK_GRAY;
        };
    }

    private String number(long value) {
        return String.format(Locale.GERMANY, "%,d", value);
    }

    private String pad(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }

    /** Skills mit Level und Bestenliste. */
    private List<String> skillIds() {
        return Skill.LEVELED.stream().map(Skill::id).toList();
    }

    private List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream()
            .filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower))
            .limit(40)
            .toList();
    }

    private Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }

    private Component hint(String message) {
        return Component.text(message, NamedTextColor.GRAY);
    }

}
