package de.pumpecraft.mailbox;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public final class MailboxSettings {
    private static final int MAX_CAPACITY = 54;

    private final int doorOpenTicks;
    private final int doorCloseTicks;
    private final int doorHoldTicks;
    private final int flagTicks;
    private final int capacity;
    private final boolean notifyOwner;
    private final int parcelThreshold;
    private final Set<Material> letters;

    private final long costBase;
    private final long costPerItem;
    private final long costPerHundredBlocks;
    private final int timeBaseSeconds;
    private final int timePerHundredBlocks;
    private final int timePerStack;
    private final int timeMaxSeconds;
    private final double crossWorldBlocks;
    private final int maxPending;

    public MailboxSettings(Plugin plugin, FileConfiguration config) {
        this.doorOpenTicks = Math.max(0, config.getInt("animation.door-open-ticks", 6));
        this.doorCloseTicks = Math.max(0, config.getInt("animation.door-close-ticks", 7));
        this.doorHoldTicks = Math.max(1, config.getInt("animation.door-hold-ticks", 25));
        this.flagTicks = Math.max(0, config.getInt("animation.flag-ticks", 10));
        this.capacity = capacity(config.getInt("mail.capacity", 27));
        this.notifyOwner = config.getBoolean("mail.notify-owner", true);
        this.parcelThreshold = Math.max(1, config.getInt("mail.parcel-threshold", 32));
        this.letters = letters(plugin, config);

        this.costBase = Math.max(0L, config.getLong("delivery.cost.base", 50L));
        this.costPerItem = Math.max(0L, config.getLong("delivery.cost.per-item", 1L));
        this.costPerHundredBlocks = Math.max(0L, config.getLong("delivery.cost.per-100-blocks", 10L));
        this.timeBaseSeconds = Math.max(1, config.getInt("delivery.time.base-seconds", 60));
        this.timePerHundredBlocks = Math.max(0, config.getInt("delivery.time.seconds-per-100-blocks", 30));
        this.timePerStack = Math.max(0, config.getInt("delivery.time.seconds-per-stack", 15));
        this.timeMaxSeconds = Math.max(timeBaseSeconds, config.getInt("delivery.time.max-seconds", 600));
        this.crossWorldBlocks = Math.max(0.0D, config.getDouble("delivery.cross-world-blocks", 5000.0D));
        this.maxPending = Math.max(1, config.getInt("delivery.max-pending", 5));
    }

    public int doorOpenTicks() {
        return doorOpenTicks;
    }

    public int doorCloseTicks() {
        return doorCloseTicks;
    }

    public int doorHoldTicks() {
        return doorHoldTicks;
    }

    public int flagTicks() {
        return flagTicks;
    }

    public int capacity() {
        return capacity;
    }

    public boolean notifyOwner() {
        return notifyOwner;
    }

    public int parcelThreshold() {
        return parcelThreshold;
    }

    public boolean isLetter(Material material) {
        return letters.contains(material);
    }

    public double crossWorldBlocks() {
        return crossWorldBlocks;
    }

    public int maxPending() {
        return maxPending;
    }

    public long cost(int itemCount, double distance) {
        return costBase
            + costPerItem * itemCount
            + Math.round(costPerHundredBlocks * distance / 100.0D);
    }

    public int deliverySeconds(int stacks, double distance) {
        long seconds = timeBaseSeconds
            + Math.round(timePerHundredBlocks * distance / 100.0D)
            + (long) timePerStack * stacks;
        return (int) Math.min(timeMaxSeconds, Math.max(timeBaseSeconds, seconds));
    }

    private int capacity(int configured) {
        int rounded = Math.round(configured / 9.0F) * 9;
        return Math.min(MAX_CAPACITY, Math.max(9, rounded));
    }

    private Set<Material> letters(Plugin plugin, FileConfiguration config) {
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String name : config.getStringList("mail.letters")) {
            Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
            if (material == null) {
                plugin.getLogger().warning("Unknown material in mail.letters: " + name);
                continue;
            }
            materials.add(material);
        }
        if (materials.isEmpty()) {
            materials.add(Material.PAPER);
        }
        return materials;
    }
}
