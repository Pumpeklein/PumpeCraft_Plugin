package de.pumpecraft.skills;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

final class SkillsCommand implements CommandExecutor, TabCompleter {
    private static final int LEADERBOARD_SIZE = 10;
    private static final int TOP_DETAIL_ENTRIES = 5;
    private static final int BAR_LENGTH = 10;
    private static final String OTHERS_PERMISSION = "pumpecraft.skills.others";

    private static final Component DIVIDER =
        Component.text("─".repeat(34), NamedTextColor.DARK_GRAY);

    /** Welche Detailzähler ein Skill anzeigt und woraus die Top-Liste kommt. */
    private static final Map<Skill, SkillDetails> DETAILS = Map.of(
        Skill.FISCHER, new SkillDetails("item.", "Häufigste Fänge", List.of(
            new DetailLine("Fänge gesamt", "caught"),
            new DetailLine("Fische", "fish"),
            new DetailLine("Schätze", "treasure"),
            new DetailLine("Müll", "junk"))),
        Skill.MINER, new SkillDetails("ore.", "Meiste Erze", List.of(
            new DetailLine("Blöcke abgebaut", "blocks"),
            new DetailLine("Stein", "stone"),
            new DetailLine("Erze", "ore"))),
        Skill.MOBS, new SkillDetails("mob.", "Meiste Kills", List.of(
            new DetailLine("Kills gesamt", "kills"),
            new DetailLine("Monster", "monster"),
            new DetailLine("Tiere", "animal"),
            new DetailLine("Bosse", "boss"))),
        Skill.DORF, new SkillDetails("trade.", "Häufigste Trades", List.of(
            new DetailLine("Handel", "trades"),
            new DetailLine("Villager", "villagers"),
            new DetailLine("Smaragde gezahlt", "emeralds"),
            new DetailLine("Günstigster Handel", "best_price"))),
        Skill.FARMER, new SkillDetails("crop.", "Meiste Ernte", List.of(
            new DetailLine("Ernten", "crops"),
            new DetailLine("Abgepflückt", "harvested"),
            new DetailLine("Holz", "logs"),
            new DetailLine("Erde", "dirt"),
            new DetailLine("Ackerland", "farmland"))),
        Skill.BUILDER, new SkillDetails("block.", "Meistgenutzte Blöcke", List.of(
            new DetailLine("Blöcke platziert", "placed"))),
        Skill.TIERFREUND, new SkillDetails("pet.", "Deine Tiere", List.of(
            new DetailLine("Gezähmt", "tamed"))),
        Skill.ALLGEMEIN, new SkillDetails("used.", "Meistgenutzte Items", List.of(
            new DetailLine("Items benutzt", "used"),
            new DetailLine("Gegessen/Getrunken", "consumed"),
            new DetailLine("Kaputt gegangen", "broken")))
    );

    private final SkillService service;
    private final SkillRepository repository;

    SkillsCommand(SkillService service, SkillRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                "Dieser Befehl kann nur von Spielern genutzt werden.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            service.runAsync(() -> sendOverview(player, player.getUniqueId(), player.getName()));
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
            service.runAsync(() -> sendDetail(player, player.getUniqueId(), player.getName(), skill));
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
            List<String> options = new ArrayList<>(allSkillIds());
            options.add("top");
            options.add("help");
            if (sender.hasPermission(OTHERS_PERMISSION)) {
                Bukkit.getOnlinePlayers().forEach(online -> options.add(online.getName()));
            }
            return filter(options, args[0]);
        }

        if (args.length == 2) {
            return filter(args[0].equalsIgnoreCase("top") ? skillIds() : allSkillIds(), args[1]);
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

        service.runAsync(() -> {
            Player online = Bukkit.getPlayerExact(targetName);
            UUID targetId = online != null ? online.getUniqueId() : repository.findPlayerByName(targetName);
            if (targetId == null) {
                player.sendMessage(error("Der Spieler " + targetName + " ist nicht bekannt."));
                return;
            }
            String resolvedName = online != null ? online.getName() : targetName;
            if (skill == null) {
                sendOverview(player, targetId, resolvedName);
            } else {
                sendDetail(player, targetId, resolvedName, skill);
            }
        });
    }

    // ── Ausgabe ──

    /** Läuft asynchron: liest je nach Online-Status aus dem Cache oder der Datenbank. */
    private void sendOverview(Player viewer, UUID targetId, String targetName) {
        Map<StatKey, Long> stats = statsOf(targetId);
        boolean self = viewer.getUniqueId().equals(targetId);

        viewer.sendMessage(DIVIDER);
        viewer.sendMessage(Component.text(self ? "Deine Skills" : "Skills von " + targetName,
            NamedTextColor.GOLD, TextDecoration.BOLD));
        viewer.sendMessage(DIVIDER);

        long totalScore = 0L;
        for (Skill skill : Skill.LEVELED) {
            long score = stats.getOrDefault(StatKey.score(skill), 0L);
            totalScore += score;
            int level = SkillLevel.levelOf(score);
            viewer.sendMessage(Component.text(pad(skill.displayName(), 12), skill.color())
                .append(Component.text("Lv " + pad(String.valueOf(level), 3), NamedTextColor.WHITE))
                .append(progressBar(SkillLevel.progress(score), skill.color()))
                .append(Component.text("  " + number(score), NamedTextColor.GRAY)));
        }

        viewer.sendMessage(DIVIDER);
        viewer.sendMessage(Component.text("Gesamt: ", NamedTextColor.GRAY)
            .append(Component.text(number(totalScore) + " Punkte", NamedTextColor.GOLD)));
        if (self) {
            viewer.sendMessage(hint("/skills <Skill> für Details · /skills top <Skill> für die Bestenliste"));
        }
    }

    private void sendDetail(Player viewer, UUID targetId, String targetName, Skill skill) {
        Map<StatKey, Long> stats = statsOf(targetId);
        Map<String, Long> values = valuesOf(stats, skill);
        long score = values.getOrDefault(Skill.SCORE, 0L);
        boolean self = viewer.getUniqueId().equals(targetId);

        viewer.sendMessage(DIVIDER);
        viewer.sendMessage(Component.text(skill.displayName(), skill.color(), TextDecoration.BOLD)
            .append(Component.text(skill.leveled() ? " · Level " + SkillLevel.levelOf(score) : "",
                NamedTextColor.WHITE))
            .append(Component.text(self ? "" : " · " + targetName, NamedTextColor.GRAY)));
        viewer.sendMessage(Component.text(skill.description(), NamedTextColor.DARK_GRAY));
        viewer.sendMessage(DIVIDER);

        if (skill.leveled()) {
            viewer.sendMessage(line("Punkte", number(score), skill.color()));
            long toNext = SkillLevel.scoreToNextLevel(score);
            viewer.sendMessage(Component.text("  Fortschritt   ", NamedTextColor.GRAY)
                .append(progressBar(SkillLevel.progress(score), skill.color()))
                .append(Component.text(
                    toNext > 0 ? "  " + number(toNext) + " bis Level " + (SkillLevel.levelOf(score) + 1)
                        : "  Maximales Level",
                    NamedTextColor.DARK_GRAY)));

            int rank = repository.rankOf(targetId, skill, Skill.SCORE);
            if (score > 0 && rank > 0) {
                viewer.sendMessage(line("Platz", "#" + rank, NamedTextColor.WHITE));
            }
        }

        SkillDetails details = DETAILS.get(skill);
        if (details == null) {
            return;
        }

        viewer.sendMessage(Component.empty());
        for (DetailLine detail : details.lines()) {
            long value = values.getOrDefault(detail.statKey(), 0L);
            viewer.sendMessage(line(detail.label(), number(value), NamedTextColor.WHITE));
        }

        List<Map.Entry<String, Long>> top = values.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(details.topPrefix()))
            .filter(entry -> entry.getValue() > 0L)
            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
            .limit(TOP_DETAIL_ENTRIES)
            .toList();

        if (top.isEmpty()) {
            return;
        }

        viewer.sendMessage(Component.empty());
        viewer.sendMessage(Component.text(details.topLabel(), NamedTextColor.GRAY));
        for (Map.Entry<String, Long> entry : top) {
            viewer.sendMessage(Component.text("  • ", NamedTextColor.DARK_GRAY)
                .append(displayName(details.topPrefix(), entry.getKey()).color(skill.color()))
                .append(Component.text("  " + number(entry.getValue()), NamedTextColor.WHITE)));
        }
    }

    private void sendHelp(Player player, String label) {
        player.sendMessage(DIVIDER);
        player.sendMessage(Component.text("Skills", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(DIVIDER);
        player.sendMessage(hint("/" + label + " — deine Übersicht"));
        player.sendMessage(hint("/" + label + " <Skill> — Details zu einem Skill"));
        player.sendMessage(hint("/" + label + " top <Skill> — Bestenliste"));
        if (player.hasPermission(OTHERS_PERMISSION)) {
            player.sendMessage(hint("/" + label + " <Spieler> [Skill] — Werte eines Spielers"));
        }
        player.sendMessage(Component.empty());
        for (Skill skill : Skill.values()) {
            player.sendMessage(Component.text("  " + pad(skill.id(), 12), skill.color())
                .append(Component.text(skill.description(), NamedTextColor.DARK_GRAY)));
        }
    }

    // ── Hilfsmethoden ──

    private Map<StatKey, Long> statsOf(UUID playerId) {
        PlayerSkillData data = service.data(playerId);
        return data != null ? data.allValues() : repository.loadPlayer(playerId);
    }

    private Map<String, Long> valuesOf(Map<StatKey, Long> stats, Skill skill) {
        Map<String, Long> values = new HashMap<>();
        for (Map.Entry<StatKey, Long> entry : stats.entrySet()) {
            if (entry.getKey().skill() == skill) {
                values.put(entry.getKey().key(), entry.getValue());
            }
        }
        return values;
    }

    /** Übersetzt {@code ore.diamond_ore} in den Item- bzw. Mobnamen des Clients. */
    private Component displayName(String prefix, String statKey) {
        String suffix = statKey.substring(prefix.length());
        String constant = suffix.toUpperCase(Locale.ROOT);

        if (prefix.equals("mob.") || prefix.equals("pet.")) {
            try {
                return Component.translatable(EntityType.valueOf(constant).translationKey());
            } catch (IllegalArgumentException ignored) {
                return Component.text(suffix);
            }
        }

        Material material = Material.matchMaterial(suffix);
        return material == null
            ? Component.text(suffix)
            : Component.translatable(material.translationKey());
    }

    private Component progressBar(double progress, NamedTextColor color) {
        int filled = (int) Math.round(progress * BAR_LENGTH);
        filled = Math.max(0, Math.min(BAR_LENGTH, filled));
        return Component.text("█".repeat(filled), color)
            .append(Component.text("░".repeat(BAR_LENGTH - filled), NamedTextColor.DARK_GRAY));
    }

    private Component line(String label, String value, NamedTextColor valueColor) {
        return Component.text("  " + pad(label, 14), NamedTextColor.GRAY)
            .append(Component.text(value, valueColor));
    }

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

    /** Alle Bereiche inklusive der reinen Tracking-Ablage. */
    private List<String> allSkillIds() {
        return java.util.Arrays.stream(Skill.values()).map(Skill::id).toList();
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

    private record DetailLine(String label, String statKey) {
    }

    private record SkillDetails(String topPrefix, String topLabel, List<DetailLine> lines) {
    }
}
