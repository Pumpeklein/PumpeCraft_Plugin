package de.pumpecraft.anticheat.platform;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.plugin.Plugin;

public final class BedrockDetector {
    private final Plugin plugin;
    private Object platformApi;
    private Method isBedrockPlayer;
    private String providerName;

    public BedrockDetector(Plugin plugin) {
        this.plugin = plugin;
        initialize();
    }

    public boolean isBedrock(UUID playerId) {
        if (platformApi == null || isBedrockPlayer == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(isBedrockPlayer.invoke(platformApi, playerId));
        } catch (IllegalAccessException | InvocationTargetException exception) {
            plugin.getLogger().log(
                Level.WARNING,
                "Could not query " + providerName + " player status.",
                exception
            );
            platformApi = null;
            isBedrockPlayer = null;
            return false;
        }
    }

    public boolean isAvailable() {
        return platformApi != null;
    }

    public String providerName() {
        return providerName == null ? "keiner" : providerName;
    }

    private void initialize() {
        if (plugin.getConfig().getBoolean("bedrock.use-floodgate", true)
            && plugin.getServer().getPluginManager().getPlugin("floodgate") != null
            && initializeProvider(
                "org.geysermc.floodgate.api.FloodgateApi",
                "getInstance",
                "isFloodgatePlayer",
                "Floodgate"
            )) {
            return;
        }

        if (plugin.getConfig().getBoolean("bedrock.use-geyser", true)
            && plugin.getServer().getPluginManager().getPlugin("Geyser-Spigot") != null) {
            initializeProvider(
                "org.geysermc.geyser.api.GeyserApi",
                "api",
                "isBedrockPlayer",
                "Geyser"
            );
        }
    }

    private boolean initializeProvider(
        String className,
        String instanceMethod,
        String checkMethod,
        String name
    ) {
        try {
            Class<?> apiClass = Class.forName(className);
            Object api = apiClass.getMethod(instanceMethod).invoke(null);
            if (api == null) {
                return false;
            }
            platformApi = api;
            isBedrockPlayer = apiClass.getMethod(checkMethod, UUID.class);
            providerName = name;
            plugin.getLogger().info(name + " detected; Bedrock-safe thresholds are active.");
            return true;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().log(
                Level.WARNING,
                name + " is installed but its API could not be loaded.",
                exception
            );
            return false;
        }
    }
}
