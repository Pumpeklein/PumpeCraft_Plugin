package de.pumpecraft.mod;

import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeModPlugin extends JavaPlugin {
    private ModerationRepository repository;
    private ModerationCommand moderationCommand;

    @Override
    public void onEnable() {
        repository = new ModerationRepository(this);
        repository.load();

        moderationCommand = new ModerationCommand(this, repository);
        registerCommand("report", moderationCommand);
        registerCommand("reports", moderationCommand);
        registerCommand("warn", moderationCommand);
        registerCommand("mute", moderationCommand);
        registerCommand("ban", moderationCommand);
        registerCommand("vanish", moderationCommand);
        getServer().getPluginManager().registerEvents(moderationCommand, this);

        getLogger().info("PumpeMod enabled.");
    }

    @Override
    public void onDisable() {
        if (moderationCommand != null) {
            moderationCommand.revealAllVanishedPlayers();
        }
        if (repository != null) {
            repository.save();
        }
        getLogger().info("PumpeMod disabled.");
    }

    private void registerCommand(String commandName, ModerationCommand executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(commandName), "Missing command: " + commandName);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
