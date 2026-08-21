package de.pumpecraft.transactions.core;

import java.util.OptionalLong;

import de.pumpecraft.utils.Texts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** PumpePoints: ganzzahlige Serverwährung mit dem Kürzel PP. */
public final class Currency {
    public static final String SYMBOL = "PP";
    public static final String NAME = "PumpePoints";
    public static final NamedTextColor COLOR = NamedTextColor.AQUA;
    public static final NamedTextColor TITLE = NamedTextColor.DARK_GRAY;
    public static final long MAX_AMOUNT = 1_000_000_000_000L;

    private Currency() {
    }

    public static String format(long amount) {
        return Texts.number(amount) + " " + SYMBOL;
    }

    public static Component component(long amount) {
        return Component.text(format(amount), COLOR);
    }

    public static Component signed(long amount) {
        String sign = amount < 0 ? "-" : "+";
        return Component.text(
                sign + Texts.number(Math.abs(amount)) + " " + SYMBOL,
                amount < 0 ? NamedTextColor.RED : NamedTextColor.GREEN);
    }

    /**
     * Akzeptiert {@code 1500}, {@code 1.500} und {@code 1_500}; alles andere ist
     * ungültig.
     */
    public static OptionalLong parseAmount(String input) {
        String digits = input.trim().replace(".", "").replace(",", "").replace("_", "");
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
            return OptionalLong.empty();
        }
        try {
            long amount = Long.parseLong(digits);
            return amount > 0L && amount <= MAX_AMOUNT
                    ? OptionalLong.of(amount)
                    : OptionalLong.empty();
        } catch (NumberFormatException exception) {
            return OptionalLong.empty();
        }
    }
}
