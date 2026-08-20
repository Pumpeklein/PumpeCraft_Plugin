package de.pumpecraft.playtime;

import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import de.pumpecraft.utils.Players;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

final class PlaytimeCommand implements CommandExecutor, TabCompleter {
    private final PlaytimeTracker tracker;

    PlaytimeCommand(PlaytimeTracker tracker) {
        this.tracker = tracker;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player player;
        if (sender instanceof Player self) {
            if (args.length != 0) return false;
            player = self;
        } else {
            if (args.length != 1) {
                sender.sendMessage(Component.text("Nutzung: /" + label + " <Spieler>", NamedTextColor.RED));
                return true;
            }
            player = Players.online(args[0]).orElse(null);
            if (player == null) {
                sender.sendMessage(Component.text("Dieser Spieler ist nicht online.", NamedTextColor.RED));
                return true;
            }
        }

        PlaytimeRecord record = tracker.getRecord(player);
        sender.sendMessage(Component.text(
            sender.equals(player) ? "Deine Playtime" : "Playtime von " + player.getName(),
            NamedTextColor.GOLD
        ));
        sender.sendMessage(line("Gesamt", record.totalSeconds(), NamedTextColor.AQUA));
        sender.sendMessage(Component.empty());
        sender.sendMessage(line("Aktiv", record.activeSeconds(), NamedTextColor.GREEN));
        sender.sendMessage(line("AFK", record.afkSeconds(), NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return !(sender instanceof Player) && args.length == 1
            ? Players.completeOnlineNames(args[0], 50)
            : List.of();
    }

    private Component line(String label, long seconds, NamedTextColor valueColor) {
        return Component.text(" - " + label + ": ", NamedTextColor.GRAY)
                .append(Component.text(formatDuration(seconds), valueColor));
    }

    private String formatDuration(long seconds) {
        long days = seconds / 86_400L;
        long remaining = seconds % 86_400L;
        long hours = remaining / 3_600L;
        remaining %= 3_600L;
        long minutes = remaining / 60L;
        long secs = remaining % 60L;

        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        if (minutes > 0) {
            return minutes + "m " + secs + "s";
        }
        return secs + "s";
    }
}
