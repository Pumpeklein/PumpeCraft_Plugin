package de.pumpecraft.enchants;

final class RomanNumerals {
    private RomanNumerals() {
    }

    static String format(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(value);
        };
    }
}
