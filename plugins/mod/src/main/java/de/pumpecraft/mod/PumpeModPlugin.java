package de.pumpecraft.mod;

import de.pumpecraft.mod.flight.FlightListener;
import de.pumpecraft.mod.flight.FlightService;
import de.pumpecraft.mod.flight.FlyCommand;
import de.pumpecraft.mod.spectate.SpectateCommand;
import de.pumpecraft.mod.spectate.SpectateListener;
import de.pumpecraft.mod.spectate.SpectateMenu;
import de.pumpecraft.mod.spectate.SpectateMenuListener;
import de.pumpecraft.mod.spectate.SpectateService;
import de.pumpecraft.mod.spectate.SpectateSettings;
import de.pumpecraft.mod.vanish.VanishListener;
import de.pumpecraft.mod.vanish.VanishService;
import de.pumpecraft.utils.messages.ConnectionMessages;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeModPlugin extends JavaPlugin {
    private ModerationRepository repository;
    private ModerationCommand moderationCommand;
    private VanishService vanishService;
    private FlightService flightService;
    private SpectateService spectateService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!databaseAvailable()) {
            return;
        }

        repository = new ModerationRepository(this);
        repository.load();

        ModerationTopics.register();
        ConnectionMessages.register();

        vanishService = new VanishService(this);
        getServer().getPluginManager().registerEvents(new VanishListener(this, vanishService), this);

        moderationCommand = new ModerationCommand(this, repository, vanishService);
        registerCommand("report", moderationCommand);
        registerCommand("reports", moderationCommand);
        registerCommand("warn", moderationCommand);
        registerCommand("mute", moderationCommand);
        registerCommand("unmute", moderationCommand);
        registerCommand("ban", moderationCommand);
        registerCommand("unban", moderationCommand);
        registerCommand("vanish", moderationCommand);
        getServer().getPluginManager().registerEvents(moderationCommand, this);

        flightService = new FlightService();
        FlyCommand flyCommand = new FlyCommand(flightService, vanishService);
        PluginCommand fly = Objects.requireNonNull(getCommand("fly"), "Missing command: fly");
        fly.setExecutor(flyCommand);
        fly.setTabCompleter(flyCommand);
        getServer().getPluginManager().registerEvents(new FlightListener(this, flightService), this);

        spectateService = new SpectateService(
            this, vanishService, new SpectateSettings(getConfig()));
        SpectateMenu spectateMenu = new SpectateMenu(this, spectateService);
        SpectateCommand spectateCommand = new SpectateCommand(spectateService, spectateMenu);
        PluginCommand spectate = Objects.requireNonNull(getCommand("spectate"), "Missing command: spectate");
        spectate.setExecutor(spectateCommand);
        spectate.setTabCompleter(spectateCommand);
        getServer().getPluginManager().registerEvents(
            new SpectateListener(spectateService, spectateMenu), this);
        getServer().getPluginManager().registerEvents(
            new SpectateMenuListener(spectateMenu), this);

        getLogger().info("PumpeMod enabled.");
    }

    @Override
    public void onDisable() {
        if (spectateService != null) {
            spectateService.shutdown();
        }
        if (vanishService != null) {
            vanishService.revealAll();
        }
        if (moderationCommand != null) {
            moderationCommand.clearCaches();
        }
        if (flightService != null) {
            flightService.shutdown();
        }
        getLogger().info("PumpeMod disabled.");
    }

    private boolean databaseAvailable() {
        Plugin databasePlugin = getServer().getPluginManager().getPlugin("PumpeDatabase");
        if (databasePlugin != null && databasePlugin.isEnabled()) {
            return true;
        }
        getLogger().severe("PumpeDatabase is not available; PumpeMod will remain disabled.");
        getServer().getPluginManager().disablePlugin(this);
        return false;
    }

    private void registerCommand(String commandName, ModerationCommand executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(commandName), "Missing command: " + commandName);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
