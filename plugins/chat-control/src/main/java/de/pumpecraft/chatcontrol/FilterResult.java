package de.pumpecraft.chatcontrol;

/**
 * Was mit einer Nachricht geschehen soll. Zwischen Durchlassen und Aufhalten steht die
 * Markierung: Die Nachricht geht sofort in den Chat, das Team sieht sie aber als erkannt und
 * kann sie nachträglich löschen. So bleibt eine unsichere Erkennung sichtbar, ohne den Chat
 * jedes Mal anzuhalten.
 */
record FilterResult(Level level, String reason) {
    enum Level {
        ALLOWED,
        MARKED,
        HELD,
        BLOCKED
    }

    static FilterResult allow() {
        return new FilterResult(Level.ALLOWED, "");
    }

    static FilterResult mark(String reason) {
        return new FilterResult(Level.MARKED, reason);
    }

    static FilterResult hold(String reason) {
        return new FilterResult(Level.HELD, reason);
    }

    static FilterResult block(String reason) {
        return new FilterResult(Level.BLOCKED, reason);
    }

    /** @return {@code true}, solange die Nachricht zugestellt wird - markiert oder nicht */
    boolean allowed() {
        return level == Level.ALLOWED || level == Level.MARKED;
    }

    boolean marked() {
        return level == Level.MARKED;
    }

    boolean held() {
        return level == Level.HELD;
    }

    boolean blocked() {
        return level == Level.BLOCKED;
    }
}
