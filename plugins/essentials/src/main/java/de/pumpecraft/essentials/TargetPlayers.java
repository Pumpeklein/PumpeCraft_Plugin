package de.pumpecraft.essentials;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

final class TargetPlayers {
    private TargetPlayers() {
    }

    static Player findOnlinePlayer(String input) {
        String targetName = input.startsWith("@") ? input.substring(1) : input;
        return Bukkit.getPlayerExact(targetName);
    }

    static OfflinePlayer findKnownPlayer(String input) {
        String targetName = input.startsWith("@") ? input.substring(1) : input;
        Player onlinePlayer = Bukkit.getPlayerExact(targetName);
        if (onlinePlayer != null) {
            return onlinePlayer;
        }
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            if (offlinePlayer.getName() != null
                && offlinePlayer.getName().equalsIgnoreCase(targetName)) {
                return offlinePlayer;
            }
            if (offlinePlayer.getUniqueId().toString().equalsIgnoreCase(targetName)) {
                return offlinePlayer;
            }
        }
        return null;
    }

    static List<String> completeKnownPlayers(String input) {
        boolean withAtPrefix = input.startsWith("@");
        String lookup = withAtPrefix ? input.substring(1) : input;
        String lowerLookup = lookup.toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();

        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            String playerName = player.getName();
            if (playerName != null && playerName.toLowerCase(Locale.ROOT).startsWith(lowerLookup)) {
                completions.add(withAtPrefix ? "@" + playerName : playerName);
            }
        }
        completions.sort(String.CASE_INSENSITIVE_ORDER);
        return completions;
    }
}
