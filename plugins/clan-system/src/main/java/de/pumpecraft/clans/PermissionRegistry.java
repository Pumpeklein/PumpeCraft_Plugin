package de.pumpecraft.clans;

import java.io.File;
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
        YamlConfiguration file = YamlConfiguration.loadConfiguration(
            new File(plugin.getDataFolder(), "permissions.yml")
        );
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
