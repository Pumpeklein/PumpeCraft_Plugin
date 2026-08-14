package de.pumpecraft.transactions.core;

public enum TransferOutcome {
    OK,
    DISABLED,
    INVALID_AMOUNT,
    BELOW_MINIMUM,
    ABOVE_MAXIMUM,
    SELF,
    INSUFFICIENT_FUNDS
}
