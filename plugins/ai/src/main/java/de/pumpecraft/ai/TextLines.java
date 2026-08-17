package de.pumpecraft.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Zerlegt eine Modellantwort in einzelne Meldungen und räumt die üblichen Verzierungen weg. */
final class TextLines {
    private TextLines() {
    }

    static List<String> parse(String reply) {
        Set<String> lines = new LinkedHashSet<>();
        for (String raw : reply.split("\\R")) {
            String line = clean(raw);
            if (!line.isEmpty()) {
                lines.add(line);
            }
        }
        return List.copyOf(new ArrayList<>(lines));
    }

    private static String clean(String raw) {
        String line = raw.strip();
        line = line.replaceFirst("^[-*•]\\s+", "");
        line = line.replaceFirst("^\\d+[.)]\\s+", "");
        line = line.strip();

        if (line.length() >= 2 && line.startsWith("\"") && line.endsWith("\"")) {
            line = line.substring(1, line.length() - 1).strip();
        }
        return line;
    }
}
