package de.pumpecraft.essentials;

import de.pumpecraft.transactions.core.Points;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;

public final class PumpeEssentialsPlugin extends JavaPlugin {
    private OpenInventoryCommand openInventoryCommand;
    private OfflinePlayerDataService offlinePlayerDataService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        offlinePlayerDataService = new OfflinePlayerDataService(this);
        openInventoryCommand = new OpenInventoryCommand(this, offlinePlayerDataService);
        registerCommand("openinv", openInventoryCommand);
        registerCommand("opendender", new OpenEnderCommand(offlinePlayerDataService));
        ItemCustomizationService itemCustomization = new ItemCustomizationService(
            this,
            Points.require(this),
            new ItemServicePricing(getConfig())
        );
        registerCommand("rename", new RenameCommand(itemCustomization));
        registerCommand("sign", new SignCommand(itemCustomization));
        getServer().getPluginManager().registerEvents(offlinePlayerDataService, this);
        getServer().getPluginManager().registerEvents(openInventoryCommand, this);

        getLogger().info("PumpeEssentials enabled.");
    }

    @Override
    public void onDisable() {
        if (openInventoryCommand != null) {
            openInventoryCommand.shutdown();
        }
        if (offlinePlayerDataService != null) {
            offlinePlayerDataService.shutdown();
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
