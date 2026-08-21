package de.pumpecraft.bases.base;

import java.util.UUID;

public record BaseLike(UUID playerId, String playerName, long createdAt) {
}
