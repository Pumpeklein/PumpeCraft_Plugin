package de.pumpecraft.bases.base;

public record VisitOutcome(Status status, PlayerBase base, long remainingMillis) {
    public enum Status {
        VISITED,
        NO_BASE,
        PRIVATE,
        WORLD_MISSING,
        COOLDOWN,
        TELEPORT_FAILED
    }
}
