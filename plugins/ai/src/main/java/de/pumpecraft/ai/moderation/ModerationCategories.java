package de.pumpecraft.ai.moderation;

import java.util.Map;

/** Die Kategorien der OpenAI-Moderation mit dem Namen, den das Team im Spiel liest. */
public final class ModerationCategories {
    private static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry("harassment", "Beleidigung"),
        Map.entry("harassment/threatening", "Bedrohung"),
        Map.entry("hate", "Hassrede"),
        Map.entry("hate/threatening", "Hassrede mit Drohung"),
        Map.entry("sexual", "Sexueller Inhalt"),
        Map.entry("sexual/minors", "Sexueller Inhalt mit Minderjährigen"),
        Map.entry("self-harm", "Selbstverletzung"),
        Map.entry("self-harm/intent", "Ankündigung von Selbstverletzung"),
        Map.entry("self-harm/instructions", "Anleitung zur Selbstverletzung"),
        Map.entry("illicit", "Anleitung zu Straftaten"),
        Map.entry("illicit/violent", "Anleitung zu Gewalttaten"),
        Map.entry("violence", "Gewalt"),
        Map.entry("violence/graphic", "Drastische Gewalt")
    );

    private ModerationCategories() {
    }

    public static String label(String category) {
        return LABELS.getOrDefault(category, category);
    }
}
