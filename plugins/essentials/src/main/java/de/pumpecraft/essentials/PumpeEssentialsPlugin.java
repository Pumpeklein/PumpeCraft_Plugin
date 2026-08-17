package de.pumpecraft.essentials;

import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;

public final class PumpeEssentialsPlugin extends JavaPlugin {
    private OpenInventoryCommand openInventoryCommand;

    @Override
    public void onEnable() {
        OfflineInventoryBridge offlineInventoryBridge;
        try {
            offlineInventoryBridge = new OfflineInventoryBridge(this);
        } catch (IllegalStateException exception) {
            getLogger().severe(exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        openInventoryCommand = new OpenInventoryCommand(this, offlineInventoryBridge);
        registerCommand("openinv", openInventoryCommand);
        registerCommand("opendender", new OpenEnderCommand(offlineInventoryBridge));
        getServer().getPluginManager().registerEvents(openInventoryCommand, this);

        getLogger().info("PumpeEssentials enabled.");
    }

    @Override
    public void onDisable() {
        if (openInventoryCommand != null) {
            openInventoryCommand.shutdown();
        }
        getLogger().info("PumpeEssentials disabled.");
    }

    private void registerCommand(String commandName, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(commandName), "Missing command: " + commandName);
        command.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }
}
