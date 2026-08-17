package de.pumpecraft.clans;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

final class PermissionRegistry {
    private final PumpeClanSystemPlugin plugin;
    private final Map<String, String> nodes = new HashMap<>();

    PermissionRegistry(PumpeClanSystemPlugin plugin) {
        this.plugin = plugin;
    }

    void load() {
        plugin.saveResource("permissions.yml", false);
        File permissionsFile = new File(plugin.getDataFolder(), "permissions.yml");
        YamlConfiguration file = YamlConfiguration.loadConfiguration(permissionsFile);
        try (InputStreamReader reader = new InputStreamReader(
            Objects.requireNonNull(plugin.getResource("permissions.yml")),
            StandardCharsets.UTF_8
        )) {
            file.setDefaults(YamlConfiguration.loadConfiguration(reader));
            file.options().copyDefaults(true);
            file.save(permissionsFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not update permissions.yml", exception);
        }
        ConfigurationSection section = Objects.requireNonNull(
            file.getConfigurationSection("permissions"),
            "permissions.yml must contain a permissions section"
        );
        for (String key : section.getKeys(false)) {
            String path = "permissions." + key;
            String node = Objects.requireNonNull(
                file.getString(path + ".node"),
                "Missing permission node for " + key
            );
            String description = file.getString(path + ".description", "");
            PermissionDefault permissionDefault = PermissionDefault.getByName(
                file.getString(path + ".default", "false")
            );
            if (permissionDefault == null) {
                permissionDefault = PermissionDefault.FALSE;
            }
            nodes.put(key, node);
            if (plugin.getServer().getPluginManager().getPermission(node) == null) {
                plugin.getServer().getPluginManager().addPermission(
                    new Permission(node, description, permissionDefault)
                );
            }
        }
    }

    String node(String key) {
        String node = nodes.get(key);
        if (node == null) {
            throw new IllegalStateException("Missing permission definition: " + key);
        }
        return node;
    }
}
