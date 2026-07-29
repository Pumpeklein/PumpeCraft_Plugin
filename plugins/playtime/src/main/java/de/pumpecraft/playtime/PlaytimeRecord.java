package de.pumpecraft.playtime;

record PlaytimeRecord(long totalSeconds, long afkSeconds, long activeSeconds) {
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
