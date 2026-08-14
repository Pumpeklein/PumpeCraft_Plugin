package de.pumpecraft.transactions.core;

import java.util.UUID;

public record Transaction(
    long id,
    UUID playerId,
    UUID counterpartyId,
    String counterpartyName,
    long amount,
    long balanceAfter,
    TransactionType type,
    String actorName,
    String reason,
    long createdAt
) {
}
