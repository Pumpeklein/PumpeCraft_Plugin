package de.pumpecraft.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;

/**
 * Click targets use {@link ClickEvent#suggestCommand} rather than running the command: the
 * teleport lands in the chat input so the recipient still confirms it.
 */
public final class Teleports {
    public static final String DEFAULT_PLAYER_COMMAND = "/tp %player%";
    public static final String DEFAULT_LOCATION_COMMAND = "/tp %x% %y% %z%";

    private Teleports() {
    }

    public static String coordinates(Location location) {
        return Math.round(location.getX()) + ", "
            + Math.round(location.getY()) + ", "
            + Math.round(location.getZ());
    }

    public static String playerCommand(String template, String playerName) {
        return template.replace("%player%", playerName);
    }

    public static String locationCommand(String template, Location location) {
        return template
            .replace("%x%", String.valueOf(Math.round(location.getX())))
            .replace("%y%", String.valueOf(Math.round(location.getY())))
            .replace("%z%", String.valueOf(Math.round(location.getZ())))
            .replace("%world%", location.getWorld() == null ? "" : location.getWorld().getName());
    }

    public static Component clickable(Component label, String command, Component hover) {
        return label
            .clickEvent(ClickEvent.suggestCommand(command))
            .hoverEvent(HoverEvent.showText(hover));
    }

    public static Component playerLink(String playerName, TextColor color, String template) {
        return clickable(
            Component.text(playerName, color),
            playerCommand(template, playerName),
            Component.text("Klicken für Teleport zu " + playerName, NamedTextColor.GRAY)
        );
    }

    public static Component locationLink(Location location, TextColor color, String template) {
        String world = location.getWorld() == null ? "?" : location.getWorld().getName();
        return clickable(
            Component.text("[" + coordinates(location) + "]", color),
            locationCommand(template, location),
            Component.text("Klicken für Teleport nach " + world + " "
                + coordinates(location), NamedTextColor.GRAY)
        );
    }
}
