package de.pumpecraft.anticheat;

import de.pumpecraft.anticheat.check.BlockChecks;
import de.pumpecraft.anticheat.check.CombatChecks;
import de.pumpecraft.anticheat.check.EffectChecks;
import de.pumpecraft.anticheat.check.ItemChecks;
import de.pumpecraft.anticheat.check.MovementChecks;
import de.pumpecraft.anticheat.check.XrayChecks;
import de.pumpecraft.anticheat.client.ClientDetectionService;
import de.pumpecraft.anticheat.core.AlertDispatcher;
import de.pumpecraft.anticheat.core.CheckSettings;
import de.pumpecraft.anticheat.core.CheckType;
import de.pumpecraft.anticheat.core.PlayerStateStore;
import de.pumpecraft.anticheat.core.ViolationService;
import de.pumpecraft.anticheat.platform.BedrockDetector;
import de.pumpecraft.anticheat.storage.AntiCheatEventRepository;
import de.pumpecraft.anticheat.storage.PlayerPlatformRepository;
import de.pumpecraft.database.Databases;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeAntiCheatPlugin extends JavaPlugin implements Listener {
    private static final int CONFIG_VERSION = 7;

    private PlayerStateStore states;
    private AlertDispatcher alerts;
    private ClientDetectionService clientDetection;
    private ItemChecks itemChecks;
    private EffectChecks effectChecks;
    private XrayChecks xrayChecks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();

        states = new PlayerStateStore();
        BedrockDetector bedrockDetector = new BedrockDetector(this);
        CheckSettings settings = new CheckSettings(this, bedrockDetector);
        alerts = new AlertDispatcher(this);
        AntiCheatEventRepository events = new AntiCheatEventRepository(this, Databases.require(this));
        PlayerPlatformRepository platforms = new PlayerPlatformRepository(this, Databases.require(this));
        ViolationService violations = new ViolationService(this, states, settings, alerts, events);

        MovementChecks movementChecks = new MovementChecks(this, states, violations);
        itemChecks = new ItemChecks(this, states, violations);
        effectChecks = new EffectChecks(this, states, violations);
        xrayChecks = new XrayChecks(this, states, violations);
        clientDetection = new ClientDetectionService(this, bedrockDetector, platforms);

        register(
            this,
            movementChecks,
            new BlockChecks(this, states, violations),
            new CombatChecks(this, states, violations),
            xrayChecks,
            itemChecks,
            effectChecks,
            clientDetection
        );

        alerts.start();
        itemChecks.start();
        effectChecks.start();
        clientDetection.start();

        AntiCheatCommand commandHandler = new AntiCheatCommand(
            this,
            states,
            violations,
            settings,
            alerts,
            clientDetection,
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
        getLogger().info("PumpeAntiCheat enabled with " + CheckType.values().length + " checks.");
    }

    @Override
    public void onDisable() {
        if (clientDetection != null) {
            clientDetection.shutdown();
            clientDetection = null;
        }
        if (itemChecks != null) {
            itemChecks.shutdown();
            itemChecks = null;
        }
        if (effectChecks != null) {
            effectChecks.shutdown();
            effectChecks = null;
        }
        if (alerts != null) {
            alerts.shutdown();
            alerts = null;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        states.remove(event.getPlayer().getUniqueId());
        if (alerts != null) {
            alerts.forget(event.getPlayer().getUniqueId());
        }
    }

    public void reloadSettings() {
        migrateConfig();
        if (clientDetection != null) {
            clientDetection.reload();
        }
        if (xrayChecks != null) {
            xrayChecks.reload();
        }
    }

    private void register(Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    private void migrateConfig() {
        reloadConfig();
        int version = getConfig().contains("config-version", true)
            ? getConfig().getInt("config-version")
            : 1;
        if (version < 2) {
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
        if (version < 3) {
            replaceIntDefault("checks.autoclicker.maximum-cps-java", 16, 15);
            replaceIntDefault("checks.autoclicker.maximum-cps-bedrock", 20, 18);
            replaceIntDefault("checks.autoclicker.minimum-samples", 12, 10);
            replaceDoubleDefault("checks.autoclicker.minimum-consistent-cps-java", 10.0, 9.0);
            replaceDoubleDefault("checks.autoclicker.minimum-consistent-cps-bedrock", 13.0, 11.0);
            replaceDoubleDefault("checks.autoclicker.maximum-interval-variation-java", 0.075, 0.22);
            replaceDoubleDefault("checks.autoclicker.maximum-interval-variation-bedrock", 0.05, 0.30);
        }
        if (version < 4) {
            dropLegacyClientSignatures();
        }
        if (version < 5) {
            getConfig().set("violations.alert-cooldown-millis", null);
        }
        if (version < 6) {
            replaceDoubleDefault("checks.nofall.alert-level", 3.0, 1.0);
        }
        if (version < 7) {
            replaceDoubleDefault("checks.nuker.alert-level", 3.0, 1.0);
            replaceDoubleDefault("checks.killaura.alert-level", 3.0, 1.0);
            replaceDoubleDefault("checks.xray.alert-level", 3.0, 1.0);
            replaceDoubleDefault("checks.blockreach.alert-level", 2.0, 1.0);
        }
        getConfig().options().copyDefaults(true);
        getConfig().set("config-version", CONFIG_VERSION);
        saveConfig();
    }

    /**
     * Version 4 replaced one flat list per client with separate brand and channel lists. Leaving
     * the old section in place would keep matching loaders as if they were clients.
     */
    private void dropLegacyClientSignatures() {
        getConfig().set("client-detection.join-message-delay-ticks", null);
        ConfigurationSection signatures = getConfig()
            .getConfigurationSection("client-detection.known-signatures");
        if (signatures == null) {
            return;
        }
        boolean legacyFormat = signatures.getKeys(false).stream()
            .anyMatch(key -> signatures.getConfigurationSection(key) == null);
        if (legacyFormat) {
            getConfig().set("client-detection.known-signatures", null);
        }
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
