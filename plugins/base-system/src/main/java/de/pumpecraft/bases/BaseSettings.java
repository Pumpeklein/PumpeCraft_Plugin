package de.pumpecraft.bases;

import org.bukkit.configuration.file.FileConfiguration;

public final class BaseSettings {
    private final boolean defaultPublic;
    private final long visitCooldownMillis;
    private final int browseLimit;
    private final long directoryRefreshTicks;

    BaseSettings(FileConfiguration config) {
        defaultPublic = config.getBoolean("bases.default-public", false);
        visitCooldownMillis = Math.max(0L, config.getLong("bases.visit-cooldown-seconds", 10L)) * 1000L;
        browseLimit = Math.max(1, config.getInt("bases.browse-limit", 500));
        directoryRefreshTicks =
            Math.max(5L, config.getLong("bases.directory-refresh-seconds", 60L)) * 20L;
    }

    public boolean defaultPublic() {
        return defaultPublic;
    }

    public long visitCooldownMillis() {
        return visitCooldownMillis;
    }

    public int browseLimit() {
        return browseLimit;
    }

    public long directoryRefreshTicks() {
        return directoryRefreshTicks;
    }
}
