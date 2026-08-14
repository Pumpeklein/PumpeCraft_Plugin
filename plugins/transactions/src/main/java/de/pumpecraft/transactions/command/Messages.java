package de.pumpecraft.transactions.command;

import de.pumpecraft.transactions.core.Currency;
import de.pumpecraft.transactions.core.TransferOutcome;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

final class Messages {
    static final Component DIVIDER = Component.text("─".repeat(34), NamedTextColor.DARK_GRAY);

    private Messages() {
    }

    static Component header(String title) {
        return Component.text(title, Currency.COLOR, TextDecoration.BOLD);
    }

    static Component error(String message) {
        return Component.text(message, NamedTextColor.RED);
    }

    static Component hint(String message) {
        return Component.text(message, NamedTextColor.GRAY);
    }

    static Component success(String message) {
        return Component.text(message, NamedTextColor.GREEN);
    }

    static Component transferError(TransferOutcome outcome, long minimum, long maximum) {
        return switch (outcome) {
            case DISABLED -> error("Überweisungen sind derzeit deaktiviert.");
            case INVALID_AMOUNT -> error("Der Betrag muss eine positive Zahl sein.");
            case SELF -> error("Du kannst dir selbst nichts überweisen.");
            case BELOW_MINIMUM -> error("Mindestbetrag: " + Currency.format(minimum) + ".");
            case ABOVE_MAXIMUM -> error("Höchstbetrag pro Überweisung: " + Currency.format(maximum) + ".");
            case INSUFFICIENT_FUNDS -> error("Dafür reicht dein Kontostand nicht.");
            case OK -> Component.empty();
        };
    }

    /** Restzeit als {@code 12 Min} oder {@code 42 Sek}, je nachdem was aussagekräftiger ist. */
    static String duration(int seconds) {
        if (seconds >= 60) {
            return (seconds / 60) + " Min";
        }
        return Math.max(0, seconds) + " Sek";
    }
}
