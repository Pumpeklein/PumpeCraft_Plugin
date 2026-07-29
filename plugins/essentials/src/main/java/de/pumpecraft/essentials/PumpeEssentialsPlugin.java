package de.pumpecraft.essentials;

import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;

public final class PumpeEssentialsPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        OpenInventoryCommand openInventoryCommand = new OpenInventoryCommand();
        registerCommand("openinv", openInventoryCommand);
        registerCommand("opendender", new OpenEnderCommand());
        getServer().getPluginManager().registerEvents(openInventoryCommand, this);

        getLogger().info("PumpeEssentials enabled.");
    }

    @Override
    public void onDisable() {
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
