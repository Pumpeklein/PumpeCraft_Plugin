package de.pumpecraft.essentials.back;

import de.pumpecraft.utils.Players;
import de.pumpecraft.utils.Texts;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class BackCommand implements CommandExecutor, TabCompleter {
    static final String DEATH_PERMISSION = "pumpecraft.essentials.back.death";
    static final String OTHERS_PERMISSION = "pumpecraft.essentials.back.others";

    private static final Set<BackCause> EVERY_CAUSE = EnumSet.allOf(BackCause.class);
    private static final Set<BackCause> ONLY_TELEPORTS = EnumSet.of(BackCause.TELEPORT);
    private static final String HISTORY = "history";
    private static final String SELECT = "select";
    private static final String USER = "user";

    private final BackHistoryService history;

    public BackCommand(BackHistoryService history) {
        this.history = history;
    }

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (args.length == 0) {
            return teleportSelf(sender, label, 1);
        }
        switch (Texts.lower(args[0])) {
            case HISTORY -> {
                return showSelf(sender, label);
            }
            case SELECT -> {
                Integer index = index(args.length > 1 ? args[1] : null);
                if (index == null) {
                    sender.sendMessage(BackView.error("Nutzung: /" + label + " select <Index>"));
                    return true;
                }
                return teleportSelf(sender, label, index);
            }
            case USER -> {
                return forUser(sender, label, args);
            }
            default -> {
                Integer index = index(args[0]);
                if (index == null) {
                    return false;
                }
                return teleportSelf(sender, label, index);
            }
        }
    }

    @Override
    public List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        if (!command.testPermissionSilent(sender)) {
            return List.of();
        }
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of(HISTORY, SELECT));
            if (sender.hasPermission(OTHERS_PERMISSION)) {
                options.add(USER);
            }
            return Players.filterPrefix(options, args[0]);
        }

        String subcommand = Texts.lower(args[0]);
        if (SELECT.equals(subcommand) && args.length == 2) {
            return Players.filterPrefix(indices(ownEntries(sender).size()), args[1]);
        }
        if (!USER.equals(subcommand) || !sender.hasPermission(OTHERS_PERMISSION)) {
            return List.of();
        }
        if (args.length == 2) {
            return Players.completeKnownNames(args[1], 20);
        }

        int size = Players.known(args[1])
            .map(target -> history.cached(target.getUniqueId(), EVERY_CAUSE).size())
            .orElse(0);
        if (args.length == 3) {
            List<String> options = new ArrayList<>(List.of(HISTORY, SELECT));
            options.addAll(indices(size));
            return Players.filterPrefix(options, args[2]);
        }
        if (args.length == 4 && SELECT.equals(Texts.lower(args[2]))) {
            return Players.filterPrefix(indices(size), args[3]);
        }
        return List.of();
    }

    private boolean showSelf(CommandSender sender, String label) {
        Optional<Player> self = Players.self(sender);
        if (self.isEmpty()) {
            sender.sendMessage(BackView.error("Diesen Verlauf hat nur ein Spieler; nutze /" + label + " user <Spieler>."));
            return true;
        }
        Player player = self.get();
        history.history(player.getUniqueId(), causesFor(player), entries ->
            show(player, "Deine Rücksprungpunkte", entries, "/" + label + " " + SELECT + " "));
        return true;
    }

    private boolean teleportSelf(CommandSender sender, String label, int index) {
        Optional<Player> self = Players.self(sender);
        if (self.isEmpty()) {
            sender.sendMessage(BackView.error(
                "Konsolennutzung: /" + label + " user <Spieler> [history|<Index>]"));
            return true;
        }
        Player player = self.get();
        history.history(player.getUniqueId(), causesFor(player), entries ->
            teleport(sender, player, entries, index, "/" + label + " " + HISTORY));
        return true;
    }

    private boolean forUser(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(OTHERS_PERMISSION)) {
            sender.sendMessage(BackView.error("Dir fehlt die Berechtigung für fremde Rücksprungpunkte."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(BackView.error("Nutzung: /" + label + " user <Spieler> [history|<Index>]"));
            return true;
        }

        Optional<OfflinePlayer> known = Players.known(args[1]);
        if (known.isEmpty()) {
            sender.sendMessage(BackView.error("Spieler " + Players.stripSelector(args[1]) + " ist unbekannt."));
            return true;
        }
        OfflinePlayer target = known.get();
        String name = Players.displayName(target);
        String selectPrefix = "/" + label + " " + USER + " " + name + " ";

        String action = args.length > 2 ? Texts.lower(args[2]) : null;
        if (HISTORY.equals(action)) {
            history.history(target.getUniqueId(), EVERY_CAUSE, entries ->
                show(sender, "Rücksprungpunkte von " + name, entries, selectPrefix));
            return true;
        }

        Integer index = action == null ? Integer.valueOf(1) : index(SELECT.equals(action) && args.length > 3 ? args[3] : args[2]);
        if (index == null) {
            sender.sendMessage(BackView.error("Nutzung: /" + label + " user " + name + " [history|<Index>]"));
            return true;
        }

        Optional<Player> online = Players.online(name);
        if (online.isEmpty()) {
            sender.sendMessage(BackView.error(name + " ist nicht online; nutze " + selectPrefix.trim() + " history."));
            return true;
        }
        history.history(target.getUniqueId(), EVERY_CAUSE, entries ->
            teleport(sender, online.get(), entries, index, selectPrefix + HISTORY));
        return true;
    }

    private void show(CommandSender receiver, String title, List<BackLocation> entries, String selectPrefix) {
        BackView.history(title, entries, selectPrefix, history.settings().teleportCommand())
            .forEach(receiver::sendMessage);
    }

    private void teleport(
        CommandSender sender,
        Player target,
        List<BackLocation> entries,
        int index,
        String historyHint
    ) {
        if (entries.isEmpty()) {
            sender.sendMessage(BackView.error(sender.equals(target)
                ? "Für dich ist noch kein Rücksprungpunkt gespeichert."
                : "Für " + target.getName() + " ist noch kein Rücksprungpunkt gespeichert."));
            return;
        }
        if (index < 1 || index > entries.size()) {
            sender.sendMessage(BackView.error("Es gibt nur " + entries.size()
                + " Rücksprungpunkte. Übersicht: " + historyHint));
            return;
        }

        BackLocation entry = entries.get(index - 1);
        Optional<Location> location = entry.resolve();
        if (location.isEmpty()) {
            sender.sendMessage(BackView.error("Die Welt " + entry.world() + " ist nicht geladen."));
            return;
        }
        target.teleportAsync(location.get()).thenAccept(success -> {
            if (success) {
                announce(sender, target, entry);
            } else {
                sender.sendMessage(BackView.error("Der Teleport ist fehlgeschlagen."));
            }
        });
    }

    private void announce(CommandSender sender, Player target, BackLocation entry) {
        Component position = BackView.position(entry, history.settings().teleportCommand());
        Component suffix = Component.text(" " + entry.world() + " (" + entry.cause().label() + ", "
            + Texts.since(System.currentTimeMillis() - entry.createdAt()) + ").", NamedTextColor.GRAY);
        if (sender.equals(target)) {
            target.sendMessage(BackView.info("Zurück zu ").append(position).append(suffix));
            return;
        }
        sender.sendMessage(Component.text(target.getName() + " zurückgesetzt auf ", NamedTextColor.GREEN)
            .append(position)
            .append(suffix));
        target.sendMessage(BackView.info("Ein Teammitglied hat dich zurückgesetzt auf ")
            .append(position)
            .append(suffix));
    }

    private Set<BackCause> causesFor(Player player) {
        return player.hasPermission(DEATH_PERMISSION) ? EVERY_CAUSE : ONLY_TELEPORTS;
    }

    private List<BackLocation> ownEntries(CommandSender sender) {
        return Players.self(sender)
            .map(player -> history.cached(player.getUniqueId(), causesFor(player)))
            .orElseGet(List::of);
    }

    private static List<String> indices(int size) {
        List<String> values = new ArrayList<>(size);
        for (int index = 1; index <= size; index++) {
            values.add(String.valueOf(index));
        }
        return values;
    }

    private static Integer index(String argument) {
        if (argument == null) {
            return null;
        }
        try {
            return Integer.valueOf(argument.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
