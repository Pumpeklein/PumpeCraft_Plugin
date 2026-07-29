package de.pumpecraft.deathmessages;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.configuration.file.YamlConfiguration;

final class DeathCounterRepository {
    private final PumpeDeathMessagesPlugin plugin;
    private final File dataFile;
    private YamlConfiguration data;

    DeathCounterRepository(PumpeDeathMessagesPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "death-message-data.yml");
    }

    void load() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        if (data.getConfigurationSection("death-counts") == null) {
            data.createSection("death-counts");
        }
        save();
    }

    synchronized int incrementDeaths(UUID playerId) {
        String path = "death-counts." + playerId;
        int deaths = data.getInt(path, 0) + 1;
        data.set(path, deaths);
        save();
        return deaths;
    }

    synchronized void save() {
        try {
            data.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save death message data.", exception);
        }
    }
}
