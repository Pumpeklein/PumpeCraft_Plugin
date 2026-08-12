package de.pumpecraft.mod;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zeitangaben für Strafen: Parsen von Eingaben wie {@code 30s}, {@code 10m},
 * {@code 2h}, {@code 7d}, {@code 1w} oder kombiniert {@code 1h30m}.
 * Eine reine Zahl ohne Einheit wird als Minuten gelesen.
 */
final class Durations {
    /** Obergrenze, damit {@code Instant.plus(...)} nicht überläuft. */
    static final Duration MAX = Duration.ofDays(3650L);

    static final String EXAMPLES = "30s, 10m, 2h, 1d, 1w oder kombiniert 1h30m";

    private static final Pattern SINGLE_PART = Pattern.compile("(\\d+)([smhdw])");
    private static final Pattern SUPPORTED = Pattern.compile("(?:\\d+[smhdw])+|\\d+");

    private Durations() {
    }

    /**
     * @return die geparste Dauer oder {@code null}, wenn die Eingabe keine
     *     gültige, positive Zeitangabe innerhalb von {@link #MAX} ist.
     */
    static Duration parse(String input) {
        if (input == null) {
            return null;
        }

        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED.matcher(normalized).matches()) {
            return null;
        }

        if (normalized.chars().allMatch(Character::isDigit)) {
            return validate(ofUnit('m', parseAmount(normalized)));
        }

        Duration total = Duration.ZERO;
        Matcher matcher = SINGLE_PART.matcher(normalized);
        while (matcher.find()) {
            Duration part = ofUnit(matcher.group(2).charAt(0), parseAmount(matcher.group(1)));
            if (part == null) {
                return null;
            }
            total = total.plus(part);
            if (total.compareTo(MAX) > 0) {
                return null;
            }
        }
        return validate(total);
    }

    /**
     * Formatiert eine Dauer kompakt, z. B. {@code 7d 3h 20m} oder {@code 45s}.
     * Sekunden erscheinen, sobald sie relevant sind.
     */
    static String format(Duration duration) {
        long remaining = duration == null ? 0L : Math.max(0L, duration.getSeconds());
        long days = remaining / 86400L;
        remaining %= 86400L;
        long hours = remaining / 3600L;
        remaining %= 3600L;
        long minutes = remaining / 60L;
        long seconds = remaining % 60L;

        StringBuilder builder = new StringBuilder();
        appendPart(builder, days, "d");
        appendPart(builder, hours, "h");
        appendPart(builder, minutes, "m");
        if (seconds > 0L || builder.isEmpty()) {
            appendPart(builder, seconds, "s");
        }
        return builder.toString();
    }

    private static void appendPart(StringBuilder builder, long amount, String unit) {
        if (amount <= 0L && !unit.equals("s")) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        builder.append(amount).append(unit);
    }

    private static Duration ofUnit(char unit, long amount) {
        if (amount <= 0L) {
            return null;
        }
        return switch (unit) {
            case 's' -> Duration.ofSeconds(amount);
            case 'm' -> Duration.ofMinutes(amount);
            case 'h' -> Duration.ofHours(amount);
            case 'd' -> Duration.ofDays(amount);
            case 'w' -> Duration.ofDays(amount * 7L);
            default -> null;
        };
    }

    private static Duration validate(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative() || duration.compareTo(MAX) > 0) {
            return null;
        }
        return duration;
    }

    private static long parseAmount(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return -1L;
        }
    }
}
