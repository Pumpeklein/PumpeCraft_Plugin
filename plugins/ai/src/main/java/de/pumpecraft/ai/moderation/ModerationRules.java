package de.pumpecraft.ai.moderation;

import java.util.Map;

/**
 * Macht aus Bewertungen ein Urteil. OpenAI flaggt erst, wenn es sich sehr sicher ist; die
 * Schwellen in der Config liegen darunter, damit auch das durchkommt, was knapp darunter liegt.
 */
final class ModerationRules {
    private ModerationRules() {
    }

    static ModerationVerdict judge(ModerationScores scores, ModerationSettings settings) {
        String strongest = "";
        double strongestScore = -1.0D;
        String allowedStrongest = "";
        double allowedStrongestScore = -1.0D;
        String low = "";
        double lowScore = -1.0D;
        String high = "";
        double highScore = -1.0D;

        for (Map.Entry<String, Double> entry : scores.values().entrySet()) {
            String category = entry.getKey();
            double value = entry.getValue();
            if (value > strongestScore) {
                strongest = category;
                strongestScore = value;
            }
            if (settings.ignored(category)) {
                continue;
            }
            if (value > allowedStrongestScore) {
                allowedStrongest = category;
                allowedStrongestScore = value;
            }
            if (value >= settings.holdThresholdFor(category) && value > highScore) {
                high = category;
                highScore = value;
            } else if (value >= settings.thresholdFor(category) && value > lowScore) {
                low = category;
                lowScore = value;
            }
        }

        if (!high.isEmpty()) {
            return new ModerationVerdict(ModerationSeverity.HIGH, high, highScore, scores.values());
        }
        // Ein Flag von OpenAI zählt weiter, aber nur wenn es nicht aus einer abgeschalteten
        // Kategorie kommt - sonst wäre "ignored-categories" wirkungslos. Es setzt sein Flag erst
        // sehr spät, darum wiegt es schwer.
        if (scores.flagged() && !settings.ignored(strongest)) {
            return new ModerationVerdict(ModerationSeverity.HIGH, strongest, strongestScore, scores.values());
        }
        if (!low.isEmpty()) {
            return new ModerationVerdict(ModerationSeverity.LOW, low, lowScore, scores.values());
        }
        return new ModerationVerdict(
            ModerationSeverity.NONE, allowedStrongest, Math.max(allowedStrongestScore, 0.0D), scores.values());
    }
}
