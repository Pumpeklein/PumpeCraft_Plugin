package de.pumpecraft.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.ConfigurationSection;

public final class Configs {
    private Configs() {
    }

    public static List<String> lowerStringList(ConfigurationSection section, String path) {
        List<String> values = new ArrayList<>();
        if (section == null) {
            return values;
        }
        for (String value : section.getStringList(path)) {
            if (!value.isBlank()) {
                values.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return values;
    }

    public static List<String> lowerStringList(List<String> values) {
        List<String> normalized = new ArrayList<>();
        for (String value : values) {
            if (!value.isBlank()) {
                normalized.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    /** Exact token match; a trailing {@code *} turns the pattern into a prefix match. */
    public static boolean matchesAny(List<String> patterns, java.util.Set<String> tokens) {
        for (String pattern : patterns) {
            if (!pattern.endsWith("*")) {
                if (tokens.contains(pattern)) {
                    return true;
                }
                continue;
            }
            String prefix = pattern.substring(0, pattern.length() - 1);
            if (prefix.isEmpty()) {
                continue;
            }
            for (String token : tokens) {
                if (token.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }
}
