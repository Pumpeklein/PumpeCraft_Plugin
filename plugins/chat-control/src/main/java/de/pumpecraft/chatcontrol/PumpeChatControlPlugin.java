package de.pumpecraft.chatcontrol;

import de.pumpecraft.database.DatabaseService;
import de.pumpecraft.database.Databases;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
    private static final int CONFIG_VERSION = 3;
    private static final List<String> REQUIRED_BLOCKED_TERMS = List.of(
        "hurensohn", "hurentochter", "hure", "nutte", "nutten", "schlampe", "fotze",
        "wichser", "wixer", "ficker", "bastard", "missgeburt", "arschloch", "arschgeige",
        "drecksau", "drecksschwein", "scheisskerl", "pissnelke", "spast", "spasti",
        "behindertenkind", "mongo", "idiot", "vollidiot", "depp", "dummkopf", "opfer",
        "loser", "lappen", "schwuchtel", "faggot", "retard", "nigger", "nigga", "neger",
        "kanake", "kanacke", "kameltreiber", "judensau", "bitch", "slut", "whore",
        "motherfucker", "son of a bitch", "sohn einer hure", "halt die fresse", "halt dein maul",
        "fick dich", "verpiss dich", "leck mich am arsch"
    );
    private static final long MESSAGE_RETENTION_MILLIS = 15L * 60L * 1000L;

    private final Map<String, String> permissions = new HashMap<>();
    private final ConcurrentHashMap<String, TrackedChatMessage> trackedMessages = new ConcurrentHashMap<>();
    private String blockedPrefix;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        loadPermissions();
        blockedPrefix = getConfig().getString("messages.blocked-prefix", "Deine Nachricht wurde blockiert: ");

        DatabaseService database = Databases.require(this);
        ChatMessageRepository repository = new ChatMessageRepository(this, database);
        ChatFilter filter = new ChatFilter(getConfig());
        ChatReviewer reviewer = ChatReviewer.create(this, getConfig().getConfigurationSection("moderation"));
        ChatIdentityRenderer identityRenderer = new ChatIdentityRenderer();

        getServer().getPluginManager().registerEvents(
            new ChatControlListener(this, filter, reviewer, repository, trackedMessages, identityRenderer),
            this
        );
        ChatReviewer privateReviewer = getConfig().getBoolean("moderation.private-messages", true)
            ? reviewer
            : ChatReviewer.none();
        PrivateMessageCommand privateMessages =
            new PrivateMessageCommand(this, filter, privateReviewer, repository);
        Objects.requireNonNull(getCommand("msg")).setExecutor(privateMessages);
        Objects.requireNonNull(getCommand("msg")).setTabCompleter(privateMessages);
        ChatControlCommand moderation = new ChatControlCommand(this, repository, trackedMessages);
        Objects.requireNonNull(getCommand("chatcontrol")).setExecutor(moderation);
        Objects.requireNonNull(getCommand("chatcontrol")).setTabCompleter(moderation);

        getServer().getScheduler().runTaskTimer(this, this::removeExpiredMessages, 20L * 60L, 20L * 60L);
        getLogger().info("Chat tracking, filtering, private messages and DEL moderation are active.");
        getLogger().info(reviewer.active()
            ? "Automatic message review through PumpeAI is active."
            : "Automatic message review is inactive; only the term filter applies.");
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

    private void migrateConfig() {
        int version = getConfig().getInt("config-version", 1);
        if (version >= CONFIG_VERSION) return;

        if (version < 2) {
            LinkedHashSet<String> terms = new LinkedHashSet<>(getConfig().getStringList("filter.blocked-terms"));
            terms.addAll(REQUIRED_BLOCKED_TERMS);
            getConfig().set("filter.blocked-terms", new ArrayList<>(terms));
        }
        getConfig().options().copyDefaults(true);
        getConfig().set("config-version", CONFIG_VERSION);
        saveConfig();
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
