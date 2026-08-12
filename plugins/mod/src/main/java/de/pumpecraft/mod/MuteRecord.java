package de.pumpecraft.mod;

import java.time.Duration;

record MuteRecord(String reason, String staffName, long mutedAt, long expiresAt) {
    boolean isActive() {
        return expiresAt > System.currentTimeMillis();
    }

    Duration total() {
        return Duration.ofMillis(Math.max(0L, expiresAt - mutedAt));
    }

    Duration remaining() {
        return Duration.ofMillis(Math.max(0L, expiresAt - System.currentTimeMillis()));
    }
}
