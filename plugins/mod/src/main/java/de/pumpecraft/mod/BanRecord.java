package de.pumpecraft.mod;

import java.time.Duration;

/** Ein aktiver Ban aus {@code pc_punishments}. {@code expiresAt == null} bedeutet permanent. */
record BanRecord(
    String punishmentId,
    String reason,
    String staffName,
    long createdAt,
    Long expiresAt
) {
    boolean permanent() {
        return expiresAt == null;
    }

    Duration total() {
        return permanent() ? Duration.ZERO : Duration.ofMillis(Math.max(0L, expiresAt - createdAt));
    }

    Duration remaining() {
        return permanent()
            ? Duration.ZERO
            : Duration.ofMillis(Math.max(0L, expiresAt - System.currentTimeMillis()));
    }
}
