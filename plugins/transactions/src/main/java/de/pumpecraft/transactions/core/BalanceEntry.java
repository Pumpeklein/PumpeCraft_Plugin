package de.pumpecraft.transactions.core;

import java.util.UUID;

public record BalanceEntry(UUID playerId, String playerName, long balance) {
}
