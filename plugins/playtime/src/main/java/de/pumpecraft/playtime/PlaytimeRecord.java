package de.pumpecraft.playtime;

record PlaytimeRecord(long totalSeconds, long afkSeconds, long activeSeconds) {
    /**
     * Online-Zeit ohne AFK. {@link #activeSeconds()} zaehlt nur Sekunden mit echter
     * Aktion (Bewegung, Interaktion) und ist deshalb immer kleiner als dieser Wert.
     */
    long nonAfkSeconds() {
        return Math.max(0L, totalSeconds - afkSeconds);
    }

    PlaytimeRecord addTotal(long seconds) {
        return new PlaytimeRecord(totalSeconds + seconds, afkSeconds, activeSeconds);
    }

    PlaytimeRecord addAfk(long seconds) {
        return new PlaytimeRecord(totalSeconds, afkSeconds + seconds, activeSeconds);
    }

    PlaytimeRecord addActive(long seconds) {
        return new PlaytimeRecord(totalSeconds, afkSeconds, activeSeconds + seconds);
    }
}
