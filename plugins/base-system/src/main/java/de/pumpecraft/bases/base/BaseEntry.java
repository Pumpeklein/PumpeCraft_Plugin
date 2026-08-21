package de.pumpecraft.bases.base;

import java.util.UUID;

public record BaseEntry(
    UUID ownerId,
    String ownerName,
    String worldName,
    boolean publicBase,
    long visitCount,
    long likeCount,
    long updatedAt
) {
}
