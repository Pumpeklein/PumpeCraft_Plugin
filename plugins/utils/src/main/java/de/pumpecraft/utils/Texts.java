package de.pumpecraft.utils;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class Texts {
    private Texts() {
    }

    public static String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public static String decimal(double value, int digits) {
        return String.format(Locale.ROOT, "%." + Math.max(0, digits) + "f", value);
    }

    /** Tausendertrennung für Spielerausgaben, z. B. {@code 120.050}. */
    public static String number(long value) {
        return String.format(Locale.GERMANY, "%,d", value);
    }

    public static String percent(double ratio) {
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100.0);
    }

    public static String truncate(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    public static String joinLimited(Collection<String> values, int limit, String overflowSuffix) {
        List<String> ordered = List.copyOf(values);
        if (ordered.size() <= limit) {
            return String.join(", ", ordered);
        }
        return String.join(", ", ordered.subList(0, limit))
            + " " + overflowSuffix.replace("{count}", String.valueOf(ordered.size() - limit));
    }

    public static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
