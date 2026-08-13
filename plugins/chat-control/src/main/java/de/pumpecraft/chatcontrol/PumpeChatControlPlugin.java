package de.pumpecraft.chatcontrol;

import de.pumpecraft.database.DatabaseService;
import de.pumpecraft.database.Databases;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeChatControlPlugin extends JavaPlugin {
    private static final long MESSAGE_RETENTION_MILLIS = 15L * 60L * 1000L;

    private final Map<String, String> permissions = new HashMap<>();
    private final ConcurrentHashMap<String, TrackedChatMessage> trackedMessages = new ConcurrentHashMap<>();
    private String blockedPrefix;
    private String deletedText;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPermissions();
        blockedPrefix = getConfig().getString("messages.blocked-prefix", "Deine Nachricht wurde blockiert: ");
        deletedText = getConfig().getString("messages.deleted-placeholder", "Diese Nachricht gibt's nimmer.");

        DatabaseService database = Databases.require(this);
        ChatMessageRepository repository = new ChatMessageRepository(this, database);
        ChatFilter filter = new ChatFilter(getConfig());

        getServer().getPluginManager().registerEvents(
            new ChatControlListener(this, filter, repository, trackedMessages),
            this
        );
        PrivateMessageCommand privateMessages = new PrivateMessageCommand(this, filter, repository);
        Objects.requireNonNull(getCommand("msg")).setExecutor(privateMessages);
        Objects.requireNonNull(getCommand("msg")).setTabCompleter(privateMessages);
        ChatControlCommand moderation = new ChatControlCommand(this, repository, trackedMessages);
        Objects.requireNonNull(getCommand("chatcontrol")).setExecutor(moderation);
        Objects.requireNonNull(getCommand("chatcontrol")).setTabCompleter(moderation);

        getServer().getScheduler().runTaskTimer(this, this::removeExpiredMessages, 20L * 60L, 20L * 60L);
        getLogger().info("Chat tracking, filtering, private messages and DEL moderation are active.");
    }

    String permission(String key) {
        String permission = permissions.get(key);
        if (permission == null) throw new IllegalStateException("Missing permission definition: " + key);
        return permission;
    }

    Component blockedMessage(String reason) {
        return Component.text(blockedPrefix, NamedTextColor.RED)
            .append(Component.text(reason, NamedTextColor.GRAY));
    }

    Component deletedPlaceholder() {
        return Component.text(deletedText, NamedTextColor.DARK_GRAY);
    }

    private void loadPermissions() {
        saveResource("permissions.yml", false);
        YamlConfiguration file = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "permissions.yml"));
        ConfigurationSection section = Objects.requireNonNull(
            file.getConfigurationSection("permissions"),
            "permissions.yml must contain a permissions section"
        );
        for (String key : section.getKeys(false)) {
            String path = "permissions." + key;
            String node = Objects.requireNonNull(file.getString(path + ".node"), "Missing permission node for " + key);
            String description = file.getString(path + ".description", "");
            PermissionDefault permissionDefault = PermissionDefault.getByName(file.getString(path + ".default", "false"));
            if (permissionDefault == null) permissionDefault = PermissionDefault.FALSE;
            permissions.put(key, node);
            if (getServer().getPluginManager().getPermission(node) == null) {
                getServer().getPluginManager().addPermission(new Permission(node, description, permissionDefault));
            }
        }
    }

    private void removeExpiredMessages() {
        long cutoff = System.currentTimeMillis() - MESSAGE_RETENTION_MILLIS;
        trackedMessages.entrySet().removeIf(entry -> entry.getValue().createdAt() < cutoff);
    }
}
