package de.pumpecraft.bases.base;

public record LikeOutcome(Status status, PlayerBase base) {
    public enum Status {
        LIKED,
        UNLIKED,
        NO_BASE,
        OWN_BASE,
        PRIVATE
    }
}
