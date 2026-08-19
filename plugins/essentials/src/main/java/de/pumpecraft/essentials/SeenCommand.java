package de.pumpecraft.essentials;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

final class SeenCommand implements CommandExecutor, TabCompleter {
    private static final DateTimeFormatter LAST_SEEN_FORMAT = DateTimeFormatter
        .ofPattern("dd.MM.yyyy 'um' HH:mm:ss 'Uhr'")
        .withZone(ZoneId.systemDefault());

    @Override
    public boolean onCommand(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String label,
        @NotNull String[] args
    ) {
        if (args.length != 1) return false;
        OfflinePlayer target = TargetPlayers.findKnownPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Dieser Spieler ist nicht bekannt.", NamedTextColor.RED));
            return true;
        }

        Player online = target.getPlayer();
        if (online != null && isVisibleTo(sender, online)) {
            sender.sendMessage(Component.text(online.getName(), NamedTextColor.AQUA)
                .append(Component.text(" ist gerade online.", NamedTextColor.GREEN)));
            return true;
        }
        if (!target.hasPlayedBefore()) {
            sender.sendMessage(Component.text("Dieser Spieler ist nicht bekannt.", NamedTextColor.RED));
            return true;
        }

        long lastSeen = target.getLastSeen();
        if (lastSeen <= 0L) {
            sender.sendMessage(Component.text("Für diesen Spieler ist kein letzter Zeitpunkt bekannt.", NamedTextColor.RED));
            return true;
        }

        String name = target.getName() == null ? target.getUniqueId().toString().substring(0, 8) : target.getName();
        Instant timestamp = Instant.ofEpochMilli(lastSeen);
        sender.sendMessage(Component.text(name, NamedTextColor.AQUA)
            .append(Component.text(" war zuletzt am ", NamedTextColor.GRAY))
            .append(Component.text(LAST_SEEN_FORMAT.format(timestamp), NamedTextColor.WHITE))
            .append(Component.text(" online (" + elapsed(timestamp) + ").", NamedTextColor.GRAY)));
        return true;
    }

    @Override
    public List<String> onTabComplete(
        @NotNull CommandSender sender,
        @NotNull Command command,
        @NotNull String alias,
        @NotNull String[] args
    ) {
        return args.length == 1 ? TargetPlayers.completeKnownPlayers(args[0]) : List.of();
    }

    private boolean isVisibleTo(CommandSender sender, Player target) {
        return !(sender instanceof Player viewer) || viewer.equals(target) || viewer.canSee(target);
    }

    private String elapsed(Instant timestamp) {
        long seconds = Math.max(0L, Duration.between(timestamp, Instant.now()).toSeconds());
        if (seconds < 60L) return "vor wenigen Sekunden";
        long minutes = seconds / 60L;
        if (minutes < 60L) return "vor " + amount(minutes, "Minute", "Minuten");
        long hours = minutes / 60L;
        if (hours < 24L) return "vor " + amount(hours, "Stunde", "Stunden");
        long days = hours / 24L;
        return "vor " + amount(days, "Tag", "Tagen");
    }

    private String amount(long value, String singular, String plural) {
        return value + " " + (value == 1L ? singular : plural);
    }
}
