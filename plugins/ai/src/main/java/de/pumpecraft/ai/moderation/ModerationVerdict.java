package de.pumpecraft.ai.moderation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Das Urteil zu einem Text. Wie streng ein Plugin darauf reagiert, entscheidet
 * {@link ModerationSeverity}; {@code category} und {@code score} sind für die Begründung, die das
 * Team zu sehen bekommt.
 *
 * @param category die stärkste Kategorie - bei {@link ModerationSeverity#NONE} die stärkste
 *     überhaupt, damit sich Schwellen anhand echter Nachrichten einstellen lassen
 */
public record ModerationVerdict(
    ModerationSeverity severity,
    String category,
    double score,
    Map<String, Double> scores
) {
    public static final ModerationVerdict CLEAN =
        new ModerationVerdict(ModerationSeverity.NONE, "", 0.0D, Map.of());

    public boolean flagged() {
        return severity != ModerationSeverity.NONE;
    }

    /** Der Kategoriename auf Deutsch, wie er in einer Meldung an das Team steht. */
    public String label() {
        return ModerationCategories.label(category);
    }

    public List<Map.Entry<String, Double>> highest(int count) {
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
            .limit(count)
            .toList();
    }
}
