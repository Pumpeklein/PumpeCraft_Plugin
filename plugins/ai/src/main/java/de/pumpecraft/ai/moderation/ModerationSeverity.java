package de.pumpecraft.ai.moderation;

/**
 * Wie schwer ein Treffer wiegt. Die Grenze zwischen {@code LOW} und {@code HIGH} ist eine zweite
 * Schwelle je Kategorie: Was nur die erste reisst, ist ein Verdacht; was auch die zweite reisst,
 * ist deutlich genug, um eine Nachricht aufzuhalten.
 */
public enum ModerationSeverity {
    NONE,
    LOW,
    HIGH
}
