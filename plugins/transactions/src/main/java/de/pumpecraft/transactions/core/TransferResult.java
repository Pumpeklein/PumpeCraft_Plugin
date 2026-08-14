package de.pumpecraft.transactions.core;

public record TransferResult(TransferOutcome outcome, long senderBalance, long receiverBalance) {
    public static TransferResult failed(TransferOutcome outcome) {
        return new TransferResult(outcome, 0L, 0L);
    }

    public boolean success() {
        return outcome == TransferOutcome.OK;
    }
}
