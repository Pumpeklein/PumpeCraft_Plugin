package de.pumpecraft.ai.moderation;

import java.util.Map;

/**
 * Die rohe Antwort des Endpunkts: was OpenAI selbst flaggt und wie sicher es sich je Kategorie ist.
 * Was davon für uns ein Treffer ist, entscheidet {@link ModerationRules}.
 */
record ModerationScores(boolean flagged, Map<String, Double> values) {
    static ModerationScores empty() {
        return new ModerationScores(false, Map.of());
    }
}
