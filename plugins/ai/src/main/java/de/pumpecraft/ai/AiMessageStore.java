package de.pumpecraft.ai;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Legt den Vorrat beim Herunterfahren ab und holt ihn beim Start zurück. Ohne das kostet jeder
 * Serverstart eine Anfrage pro Thema, obwohl die Zeilen von gestern noch gültig sind.
 */
final class AiMessageStore {
    private static final String SECTION = "pools";

    private final File file;
    private final Logger logger;

    AiMessageStore(File dataFolder, Logger logger) {
        this.file = new File(dataFolder, "pools.yml");
        this.logger = logger;
    }

    Map<String, List<String>> load() {
        if (!file.isFile()) {
            return Map.of();
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = configuration.getConfigurationSection(SECTION);
        if (section == null) {
            return Map.of();
        }
        Map<String, List<String>> pools = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            List<String> lines = section.getStringList(key);
            if (!lines.isEmpty()) {
                pools.put(key, List.copyOf(lines));
            }
        }
        return Map.copyOf(pools);
    }

    void save(Map<String, Deque<String>> pools) {
        YamlConfiguration configuration = new YamlConfiguration();
        pools.forEach((key, lines) -> {
            if (!lines.isEmpty()) {
                configuration.set(SECTION + "." + key, new ArrayList<>(lines));
            }
        });
        try {
            configuration.save(file);
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Could not save the generated message pools.", exception);
        }
    }
}
