package de.pumpecraft.transactions.core;

public enum TransactionType {
    PAYOUT("Zeitgutschrift"),
    TRANSFER_IN("Erhalten"),
    TRANSFER_OUT("Gesendet"),
    ADMIN_GRANT("Team-Gutschrift"),
    ADMIN_TAKE("Team-Abbuchung"),
    ADMIN_SET("Team-Korrektur"),
    ESSENTIALS_SERVICE("Essentials-Dienst"),
    TRADER_PURCHASE("Trader-Kauf"),
    PLOT_PURCHASE("Grundstückskauf"),
    PLOT_REFUND("Grundstücksverkauf");

    private final String label;

    TransactionType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static TransactionType byId(String id) {
        for (TransactionType type : values()) {
            if (type.name().equalsIgnoreCase(id)) {
                return type;
            }
        }
        return null;
    }
}
