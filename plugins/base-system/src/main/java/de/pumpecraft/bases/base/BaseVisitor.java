package de.pumpecraft.bases.base;

import java.util.UUID;

public record BaseVisitor(UUID playerId, String playerName, long visitCount, long lastVisitedAt) {
}
