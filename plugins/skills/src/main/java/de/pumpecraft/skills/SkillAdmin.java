package de.pumpecraft.skills;

import de.pumpecraft.utils.Players;
import de.pumpecraft.utils.Texts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongUnaryOperator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Punkte sind die gespeicherte Größe, das Level wird daraus abgeleitet. Ein gesetztes Level
 * bedeutet deshalb "Punkte auf den Anfang dieses Levels".
 */
final class SkillAdmin {
    static final String PERMISSION = "pumpecraft.skills.admin";
    static final List<String> ACTIONS = List.of("set", "add", "setlevel", "reset");

    private final SkillService service;
    private final SkillRepository repository;
    private final SkillRewardService rewards;

    SkillAdmin(SkillService service, SkillRepository repository, SkillRewardService rewards) {
        this.service = service;
        this.repository = repository;
        this.rewards = rewards;
    }

    static boolean handles(String action) {
        return ACTIONS.contains(action.toLowerCase(Locale.ROOT));
    }

    boolean handle(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text("Dazu fehlt dir die Berechtigung.", NamedTextColor.RED));
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        boolean resetting = action.equals("reset");
        int required = resetting ? 2 : 4;
        if (args.length < required) {
            usage(sender, label);
            return true;
        }

        String targetName = args[1];
        List<Skill> targets = new ArrayList<>();
        if (resetting && args.length == 2) {
            targets.addAll(Skill.LEVELED);
        } else {
            Skill skill = Skill.byId(args[2]);
            if (skill == null || !skill.leveled()) {
                sender.sendMessage(Component.text(
                    "Unbekannter Skill: " + args[2], NamedTextColor.RED));
                sender.sendMessage(Component.text(
                    "Skills: " + String.join(", ", skillIds()), NamedTextColor.GRAY));
                return true;
            }
            targets.add(skill);
        }

        LongUnaryOperator update = update(sender, action, args);
        if (update == null) {
            return true;
        }
        resolve(sender, targetName, targetId -> apply(sender, targetId, targetName, targets, update));
        return true;
    }

    List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        if (args.length == 2) {
            return Players.completeOnlineNames(args[1], 40);
        }
        if (args.length == 3) {
            return Players.filterPrefix(skillIds(), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("setlevel")) {
            return Players.filterPrefix(List.of("1", "10", "25", "50", "100"), args[3]);
        }
        if (args.length == 4) {
            return Players.filterPrefix(List.of("0", "100", "1000", "10000"), args[3]);
        }
        return List.of();
    }

    private LongUnaryOperator update(CommandSender sender, String action, String[] args) {
        if (action.equals("reset")) {
            return previous -> 0L;
        }

        long value;
        try {
            value = Long.parseLong(args[3]);
        } catch (NumberFormatException exception) {
            sender.sendMessage(Component.text("Keine Zahl: " + args[3], NamedTextColor.RED));
            return null;
        }

        return switch (action) {
            case "set" -> previous -> value;
            case "add" -> previous -> previous + value;
            case "setlevel" -> {
                if (value < 1 || value > SkillLevel.MAX_LEVEL) {
                    sender.sendMessage(Component.text(
                        "Level muss zwischen 1 und " + SkillLevel.MAX_LEVEL + " liegen.",
                        NamedTextColor.RED
                    ));
                    yield null;
                }
                yield previous -> SkillLevel.scoreForLevel((int) value);
            }
            default -> null;
        };
    }

    /** Online-Spieler laufen über den Cache, sonst überschreibt ihn der nächste Speicherlauf. */
    private void apply(
        CommandSender sender,
        UUID targetId,
        String targetName,
        List<Skill> skills,
        LongUnaryOperator update
    ) {
        PlayerSkillData cached = service.data(targetId);
        if (cached != null) {
            Map<Skill, Change> changes = new LinkedHashMap<>();
            for (Skill skill : skills) {
                StatKey key = StatKey.score(skill);
                long previous = cached.get(key);
                long value = Math.max(0L, update.applyAsLong(previous));
                cached.set(key, value);
                changes.put(skill, new Change(previous, value));
            }
            service.persistNow(targetId);
            announce(sender, targetId, targetName, changes);
            return;
        }

        service.runAsync(() -> {
            Map<StatKey, Long> stored = repository.loadPlayer(targetId);
            Map<Skill, Change> changes = new LinkedHashMap<>();
            Map<StatKey, Long> writes = new LinkedHashMap<>();
            for (Skill skill : skills) {
                StatKey key = StatKey.score(skill);
                long previous = stored.getOrDefault(key, 0L);
                long value = Math.max(0L, update.applyAsLong(previous));
                writes.put(key, value);
                changes.put(skill, new Change(previous, value));
            }
            repository.save(targetId, writes);
            service.runSync(() -> {
                // Zwischen Laden und Schreiben kann der Spieler eingeloggt haben; sein Cache
                // stammt dann noch vom alten Stand und wuerde die Aenderung ueberschreiben.
                PlayerSkillData joined = service.data(targetId);
                if (joined != null) {
                    writes.forEach(joined::set);
                }
                announce(sender, targetId, targetName, changes);
            });
        });
    }

    /**
     * Belohnungen werden auch beim Setzen vergeben. Bereits ausgeschüttete Meilensteine
     * bleiben dank {@code INSERT IGNORE} in {@code pc_skill_rewards} einmalig.
     */
    private void announce(
        CommandSender sender,
        UUID targetId,
        String targetName,
        Map<Skill, Change> changes
    ) {
        Player target = Bukkit.getPlayer(targetId);
        for (Map.Entry<Skill, Change> entry : changes.entrySet()) {
            Skill skill = entry.getKey();
            Change change = entry.getValue();
            rewards.scoreChanged(targetId, skill, change.previous(), change.value());

            sender.sendMessage(Component.text(targetName + " · ", NamedTextColor.GRAY)
                .append(Component.text(skill.displayName(), skill.color()))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(Component.text(
                    describe(change.previous()) + " → " + describe(change.value()),
                    NamedTextColor.WHITE
                )));

            if (target != null) {
                target.sendMessage(Component.text("Dein Skill ", NamedTextColor.GRAY)
                    .append(Component.text(skill.displayName(), skill.color()))
                    .append(Component.text(" steht jetzt auf ", NamedTextColor.GRAY))
                    .append(Component.text(describe(change.value()), NamedTextColor.WHITE)));
            }
        }
    }

    private void resolve(CommandSender sender, String targetName, Consumer<UUID> action) {
        Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            action.accept(online.getUniqueId());
            return;
        }
        service.runAsync(() -> {
            UUID targetId = repository.findPlayerByName(targetName);
            service.runSync(() -> {
                if (targetId == null) {
                    sender.sendMessage(Component.text(
                        "Der Spieler " + targetName + " ist nicht bekannt.", NamedTextColor.RED));
                    return;
                }
                action.accept(targetId);
            });
        });
    }

    private void usage(CommandSender sender, String label) {
        sender.sendMessage(Component.text("Skill-Verwaltung", NamedTextColor.GOLD));
        sender.sendMessage(hint("/" + label + " set <Spieler> <Skill> <Punkte>"));
        sender.sendMessage(hint("/" + label + " add <Spieler> <Skill> <Punkte>  (negativ zum Abziehen)"));
        sender.sendMessage(hint("/" + label + " setlevel <Spieler> <Skill> <Level 1-"
            + SkillLevel.MAX_LEVEL + ">"));
        sender.sendMessage(hint("/" + label + " reset <Spieler> [Skill]  (ohne Skill: alle)"));
        sender.sendMessage(hint("Skills: " + String.join(", ", skillIds())));
    }

    private String describe(long score) {
        return "Lv " + SkillLevel.levelOf(score) + " (" + Texts.number(score) + " Punkte)";
    }

    private List<String> skillIds() {
        return Skill.LEVELED.stream().map(Skill::id).toList();
    }

    private Component hint(String message) {
        return Component.text(message, NamedTextColor.GRAY);
    }

    private record Change(long previous, long value) {
    }
}
