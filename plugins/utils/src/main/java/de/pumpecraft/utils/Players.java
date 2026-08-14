package de.pumpecraft.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Players {
    private Players() {
    }

    public static String stripSelector(String input) {
        return input.startsWith("@") ? input.substring(1) : input;
    }

    public static Optional<Player> online(String input) {
        return Optional.ofNullable(Bukkit.getPlayerExact(stripSelector(input)));
    }

    public static Optional<OfflinePlayer> known(String input) {
        String name = stripSelector(input);
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return Optional.of(online);
        }
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (name.equalsIgnoreCase(player.getName())) {
                return Optional.of(player);
            }
        }
        return Optional.empty();
    }

    public static Optional<Player> self(CommandSender sender) {
        return sender instanceof Player player ? Optional.of(player) : Optional.empty();
    }

    public static List<String> completeKnownNames(String input, int limit) {
        boolean selector = input.startsWith("@");
        String prefix = stripSelector(input).toLowerCase(Locale.ROOT);
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
            .limit(limit)
            .map(name -> selector ? "@" + name : name)
            .toList();
    }

    public static List<String> completeOnlineNames(String input, int limit) {
        String prefix = stripSelector(input).toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(player.getName());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names.size() <= limit ? names : names.subList(0, limit);
    }

    public static List<String> filterPrefix(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                matches.add(value);
            }
        }
        return matches;
    }

    public static String displayName(OfflinePlayer player) {
        String name = player.getName();
        return name == null ? player.getUniqueId().toString().substring(0, 8) : name;
    }
}
