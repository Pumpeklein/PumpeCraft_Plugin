package de.pumpecraft.ai;

/**
 * Was eine Anfrage tatsächlich gekostet hat, aus dem {@code usage}-Block der Antwort. Der
 * Cache-Anteil zählt bei DeepSeek separat, weil er nur einen Bruchteil kostet.
 */
record TokenUsage(long promptTokens, long completionTokens, long cacheHitTokens) {
    static final TokenUsage NONE = new TokenUsage(0L, 0L, 0L);

    long total() {
        return promptTokens + completionTokens;
    }
}
