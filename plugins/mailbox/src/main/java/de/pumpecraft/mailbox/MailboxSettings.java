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
    private final Set<Material> letters;

    public MailboxSettings(Plugin plugin, FileConfiguration config) {
        this.doorOpenTicks = Math.max(0, config.getInt("animation.door-open-ticks", 6));
        this.doorCloseTicks = Math.max(0, config.getInt("animation.door-close-ticks", 7));
        this.doorHoldTicks = Math.max(1, config.getInt("animation.door-hold-ticks", 25));
        this.flagTicks = Math.max(0, config.getInt("animation.flag-ticks", 10));
        this.capacity = capacity(config.getInt("mail.capacity", 27));
        this.notifyOwner = config.getBoolean("mail.notify-owner", true);
        this.letters = letters(plugin, config);
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

    public boolean isLetter(Material material) {
        return letters.contains(material);
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
