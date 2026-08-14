package de.pumpecraft.clans;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.format.NamedTextColor;

final class ClanColors {
    private static final Map<String, ColorChoice> BY_INPUT = new LinkedHashMap<>();
    private static final List<String> SUGGESTIONS = List.of(
        "aqua", "blau", "dunkelblau", "dunkelgrau", "dunkelgrün", "dunkelrot",
        "gold", "grau", "grün", "lila", "pink", "rot", "weiß", "gelb"
    );

    static {
        register("AQUA", NamedTextColor.AQUA, "aqua", "türkis", "tuerkis");
        register("BLUE", NamedTextColor.BLUE, "blau", "blue");
        register("DARK_BLUE", NamedTextColor.DARK_BLUE, "dunkelblau", "dark_blue");
        register("DARK_GRAY", NamedTextColor.DARK_GRAY, "dunkelgrau", "dark_gray");
        register("DARK_GREEN", NamedTextColor.DARK_GREEN, "dunkelgrün", "dunkelgruen", "dark_green");
        register("DARK_PURPLE", NamedTextColor.DARK_PURPLE, "lila", "dunkellila", "dark_purple");
        register("DARK_RED", NamedTextColor.DARK_RED, "dunkelrot", "dark_red");
        register("GOLD", NamedTextColor.GOLD, "gold");
        register("GRAY", NamedTextColor.GRAY, "grau", "gray");
        register("GREEN", NamedTextColor.GREEN, "grün", "gruen", "green");
        register("LIGHT_PURPLE", NamedTextColor.LIGHT_PURPLE, "pink", "helllila", "light_purple");
        register("RED", NamedTextColor.RED, "rot", "red");
        register("WHITE", NamedTextColor.WHITE, "weiß", "weiss", "white");
        register("YELLOW", NamedTextColor.YELLOW, "gelb", "yellow");
    }

    private ClanColors() {
    }

    static ColorChoice byInput(String input) {
        return BY_INPUT.get(input.toLowerCase(Locale.ROOT));
    }

    static NamedTextColor color(String storedName) {
        ColorChoice choice = BY_INPUT.get(storedName.toLowerCase(Locale.ROOT));
        return choice == null ? NamedTextColor.AQUA : choice.color();
    }

    static List<String> suggestions() {
        return SUGGESTIONS;
    }

    private static void register(String storedName, NamedTextColor color, String... aliases) {
        ColorChoice choice = new ColorChoice(storedName, color);
        BY_INPUT.put(storedName.toLowerCase(Locale.ROOT), choice);
        for (String alias : aliases) {
            BY_INPUT.put(alias.toLowerCase(Locale.ROOT), choice);
        }
    }

    record ColorChoice(String storedName, NamedTextColor color) {
    }
}
