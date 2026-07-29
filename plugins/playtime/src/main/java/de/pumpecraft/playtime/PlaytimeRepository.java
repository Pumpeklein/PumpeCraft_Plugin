package de.pumpecraft.playtime;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class PlaytimeRepository {
    private final PumpePlaytimePlugin plugin;
    private final File dataFile;
    private final Map<UUID, PlaytimeRecord> records = new HashMap<>();
    private YamlConfiguration data;

    PlaytimeRepository(PumpePlaytimePlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "playtime-data.yml");
    }

    void load() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
        }

        data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = data.getConfigurationSection("players");
        if (section == null) {
            data.createSection("players");
            save();
            return;
        }

        for (String key : section.getKeys(false)) {
            UUID playerId;
            try {
                playerId = UUID.fromString(key);
            } catch (IllegalArgumentException exception) {
                continue;
            }

            records.put(playerId, new PlaytimeRecord(
                data.getLong("players." + key + ".total-seconds", 0L),
                data.getLong("players." + key + ".afk-seconds", 0L),
                data.getLong("players." + key + ".active-seconds", 0L)
            ));
        }
    }

    synchronized PlaytimeRecord get(UUID playerId) {
        return records.getOrDefault(playerId, new PlaytimeRecord(0L, 0L, 0L));
    }

    synchronized void addSecond(UUID playerId, boolean afk, boolean active) {
        PlaytimeRecord record = get(playerId).addTotal(1L);
        if (afk) {
            record = record.addAfk(1L);
        }
        if (active) {
            record = record.addActive(1L);
        }
        records.put(playerId, record);
    }

    synchronized void save() {
        if (data == null) {
            data = new YamlConfiguration();
        }

        data.set("players", null);
        for (Map.Entry<UUID, PlaytimeRecord> entry : records.entrySet()) {
            String path = "players." + entry.getKey();
            PlaytimeRecord record = entry.getValue();
            data.set(path + ".total-seconds", record.totalSeconds());
            data.set(path + ".afk-seconds", record.afkSeconds());
            data.set(path + ".active-seconds", record.activeSeconds());
        }

        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save playtime data.", exception);
        }
    }
}
