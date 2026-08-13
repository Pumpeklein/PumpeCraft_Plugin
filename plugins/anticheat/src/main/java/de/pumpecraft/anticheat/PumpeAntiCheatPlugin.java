package de.pumpecraft.anticheat;

import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeAntiCheatPlugin extends JavaPlugin {
    private ClientDetectionService clientDetectionService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadSettings();

        PlayerStateStore states = new PlayerStateStore();
        BedrockDetector bedrockDetector = new BedrockDetector(this);
        ViolationService violations = new ViolationService(this, states, bedrockDetector);

        getServer().getPluginManager().registerEvents(
            new MovementChecks(this, states, violations, bedrockDetector),
            this
        );
        getServer().getPluginManager().registerEvents(
            new BlockChecks(this, states, violations, bedrockDetector),
            this
        );
        getServer().getPluginManager().registerEvents(
            new CombatChecks(this, states, violations, bedrockDetector),
            this
        );
        getServer().getPluginManager().registerEvents(
            new XrayChecks(this, states, violations, bedrockDetector),
            this
        );
        clientDetectionService = new ClientDetectionService(this, bedrockDetector);
        getServer().getPluginManager().registerEvents(clientDetectionService, this);
        clientDetectionService.start();

        AntiCheatCommand commandHandler = new AntiCheatCommand(
            this,
            states,
            violations,
            bedrockDetector
        );
        PluginCommand command = Objects.requireNonNull(
            getCommand("anticheat"),
            "Missing command: anticheat"
        );
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);

        getServer().getScheduler().runTaskTimer(this, violations::decay, 20L, 20L);
        getLogger().info("PumpeAntiCheat enabled with 9 checks.");
    }

    @Override
    public void onDisable() {
        if (clientDetectionService != null) {
            clientDetectionService.shutdown();
            clientDetectionService = null;
        }
    }

    void reloadSettings() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
    }
}
