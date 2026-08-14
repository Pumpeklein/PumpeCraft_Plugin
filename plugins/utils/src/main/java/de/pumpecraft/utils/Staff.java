package de.pumpecraft.utils;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class Staff {
    private Staff() {
    }

    public static List<Player> withPermission(String permission) {
        List<Player> recipients = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(permission)) {
                recipients.add(player);
            }
        }
        return recipients;
    }

    public static void broadcast(String permission, Component message) {
        for (Player player : withPermission(permission)) {
            player.sendMessage(message);
        }
    }
}
