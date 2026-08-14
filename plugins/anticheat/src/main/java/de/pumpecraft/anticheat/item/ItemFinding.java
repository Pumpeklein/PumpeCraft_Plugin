package de.pumpecraft.anticheat.item;

public record ItemFinding(String code, String description, boolean repairable) {
    public static ItemFinding repairable(String code, String description) {
        return new ItemFinding(code, description, true);
    }

    public static ItemFinding fatal(String code, String description) {
        return new ItemFinding(code, description, false);
    }
}
