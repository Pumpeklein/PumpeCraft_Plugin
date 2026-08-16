package de.pumpecraft.mailbox.mail;

public record DeliveryEstimate(double distance, long cost, int seconds, int stacks, int itemCount) {
    public String duration() {
        return format(seconds);
    }

    public static String format(long seconds) {
        long minutes = seconds / 60L;
        long rest = seconds % 60L;
        return minutes > 0L ? minutes + " min " + rest + " s" : rest + " s";
    }
}
