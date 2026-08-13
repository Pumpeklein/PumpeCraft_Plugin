package de.pumpecraft.playtime;

record PlaytimeRecord(long activeSeconds, long afkSeconds) {
    long totalSeconds() {
        return activeSeconds + afkSeconds;
    }

    PlaytimeRecord addActive(long seconds) {
        return new PlaytimeRecord(activeSeconds + seconds, afkSeconds);
    }

    PlaytimeRecord addAfk(long seconds) {
        return new PlaytimeRecord(activeSeconds, afkSeconds + seconds);
    }
}
