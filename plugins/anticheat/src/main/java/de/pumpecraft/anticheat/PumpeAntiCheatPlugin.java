package de.pumpecraft.anticheat;

import de.pumpecraft.database.Databases;
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
        AntiCheatEventRepository eventRepository = new AntiCheatEventRepository(
            this,
            Databases.require(this)
        );
        ViolationService violations = new ViolationService(
            this,
            states,
            bedrockDetector,
            eventRepository
        );

        MovementChecks movementChecks = new MovementChecks(this, states, violations, bedrockDetector);
        getServer().getPluginManager().registerEvents(movementChecks, this);
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
        getServer().getScheduler().runTaskTimer(this, movementChecks::tickFlyChecks, 1L, 1L);
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
        int configVersion = getConfig().contains("config-version", true)
            ? getConfig().getInt("config-version")
            : 1;
        if (configVersion < 2) {
            migrateVersionTwoDefaults();
        }
        if (configVersion < 3) {
            migrateVersionThreeDefaults();
        }
        getConfig().options().copyDefaults(true);
        getConfig().set("config-version", 3);
        saveConfig();
    }

    private void migrateVersionTwoDefaults() {
        replaceIntDefault("checks.fastplace.max-places-per-second-java", 12, 8);
        replaceIntDefault("checks.fastplace.max-places-per-second-bedrock", 16, 11);
        replaceDoubleDefault("checks.fastplace.cancel-level", 5.0, 4.0);
        replaceIntDefault("checks.autoclicker.maximum-cps-java", 20, 16);
        replaceIntDefault("checks.autoclicker.maximum-cps-bedrock", 25, 20);
        replaceDoubleDefault("checks.autoclicker.alert-level", 4.0, 2.0);
        replaceIntDefault("checks.scaffold.suspicious-placements-java", 5, 6);
        replaceIntDefault("checks.scaffold.suspicious-placements-bedrock", 8, 10);
        replaceDoubleDefault("checks.scaffold.alert-level", 4.0, 2.0);
    }

    private void migrateVersionThreeDefaults() {
        replaceIntDefault("checks.autoclicker.maximum-cps-java", 16, 15);
        replaceIntDefault("checks.autoclicker.maximum-cps-bedrock", 20, 18);
        replaceIntDefault("checks.autoclicker.minimum-samples", 12, 10);
        replaceDoubleDefault("checks.autoclicker.minimum-consistent-cps-java", 10.0, 9.0);
        replaceDoubleDefault("checks.autoclicker.minimum-consistent-cps-bedrock", 13.0, 11.0);
        replaceDoubleDefault("checks.autoclicker.maximum-interval-variation-java", 0.075, 0.22);
        replaceDoubleDefault("checks.autoclicker.maximum-interval-variation-bedrock", 0.05, 0.30);
    }

    private void replaceIntDefault(String path, int previousDefault, int newDefault) {
        if (getConfig().getInt(path) == previousDefault) {
            getConfig().set(path, newDefault);
        }
    }

    private void replaceDoubleDefault(String path, double previousDefault, double newDefault) {
        if (Double.compare(getConfig().getDouble(path), previousDefault) == 0) {
            getConfig().set(path, newDefault);
        }
    }
}
