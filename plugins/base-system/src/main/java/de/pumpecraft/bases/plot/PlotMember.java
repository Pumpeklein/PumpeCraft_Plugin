package de.pumpecraft.bases.plot;

import java.util.UUID;

public record PlotMember(UUID playerId, String playerName, PlotRole role, long addedAt) {
}
