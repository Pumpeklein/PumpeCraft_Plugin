package de.pumpecraft.bases;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

public final class BaseText {
    public static final Component DIVIDER =
        Component.text("─".repeat(36), NamedTextColor.DARK_GRAY);

    private BaseText() {
    }

    public static Component plain(String message, TextColor color) {
        return Component.text(message, color).decoration(TextDecoration.ITALIC, false);
    }

    public static Component success(String message) {
        return plain(message, NamedTextColor.GREEN);
    }

    public static Component error(String message) {
        return plain(message, NamedTextColor.RED);
    }

    public static Component hint(String message) {
        return plain(message, NamedTextColor.DARK_GRAY);
    }

    public static Component title(String message) {
        return plain(message, NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true);
    }

    public static Component label(String label, String value, TextColor valueColor) {
        return plain(label, NamedTextColor.GRAY).append(plain(value, valueColor));
    }

    public static Component label(String label, Component value) {
        return plain(label, NamedTextColor.GRAY).append(value);
    }
}
