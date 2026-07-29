package de.pumpecraft.essentials;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

final class TargetPlayers {
    private TargetPlayers() {
    }

    static Player findOnlinePlayer(String input) {
        String targetName = input.startsWith("@") ? input.substring(1) : input;
        return Bukkit.getPlayerExact(targetName);
    }

    static List<String> completeOnlinePlayers(String input) {
        boolean withAtPrefix = input.startsWith("@");
        String lookup = withAtPrefix ? input.substring(1) : input;
        String lowerLookup = lookup.toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(lowerLookup)) {
                completions.add(withAtPrefix ? "@" + player.getName() : player.getName());
            }
        }

        return completions;
    }
}
