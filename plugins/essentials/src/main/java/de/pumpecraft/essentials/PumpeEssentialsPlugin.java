package de.pumpecraft.essentials;

import de.pumpecraft.essentials.back.BackCommand;
import de.pumpecraft.essentials.back.BackHistoryService;
import de.pumpecraft.essentials.back.BackListener;
import de.pumpecraft.essentials.back.BackRepository;
import de.pumpecraft.essentials.back.BackSettings;
import de.pumpecraft.essentials.pose.CrawlService;
import de.pumpecraft.essentials.pose.PoseCommand;
import de.pumpecraft.essentials.pose.PoseListener;
import de.pumpecraft.essentials.pose.PoseSettings;
import de.pumpecraft.essentials.pose.SeatService;
import de.pumpecraft.transactions.core.Points;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;

public final class PumpeEssentialsPlugin extends JavaPlugin {
    private static final int CONFIG_VERSION = 7;

    private OpenInventoryCommand openInventoryCommand;
    private OfflinePlayerDataService offlinePlayerDataService;
    private SeatService seatService;
    private CrawlService crawlService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
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
        registerCommand("seen", new SeenCommand());

        BackHistoryService backHistory = new BackHistoryService(
            this,
            new BackRepository(this),
            BackSettings.from(getConfig(), getLogger())
        );
        registerCommand("back", new BackCommand(backHistory));

        PoseSettings poseSettings = PoseSettings.from(getConfig(), getLogger());
        seatService = new SeatService(this, poseSettings);
        crawlService = new CrawlService(poseSettings);
        PoseCommand poseCommand = new PoseCommand(seatService, crawlService);
        registerCommand("sit", poseCommand);
        registerCommand("crawl", poseCommand);

        getServer().getPluginManager().registerEvents(offlinePlayerDataService, this);
        getServer().getPluginManager().registerEvents(openInventoryCommand, this);
        getServer().getPluginManager().registerEvents(new BackListener(backHistory), this);
        getServer().getPluginManager().registerEvents(new PoseListener(seatService, crawlService), this);

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
        if (seatService != null) {
            seatService.clear();
        }
        if (crawlService != null) {
            crawlService.clear();
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

    private void migrateConfig() {
        int version = getConfig().getInt("config-version", 1);
        if (version >= CONFIG_VERSION) return;

        if (version < 2) {
            long oldRenameMinimum = getConfig().getLong("item-services.rename.minimum-cost", 5L);
            int oldRenamePercent = getConfig().getInt("item-services.rename.value-percent", 5);
            long oldSignMinimum = getConfig().getLong("item-services.sign.minimum-cost", 10L);
            int oldSignPercent = getConfig().getInt("item-services.sign.value-percent", 10);

            getConfig().set("item-services.rename.minimum-cost", null);
            getConfig().set("item-services.rename.base-cost", oldRenameMinimum == 5L ? 75L : oldRenameMinimum);
            getConfig().set("item-services.rename.per-character", 15L);
            getConfig().set("item-services.rename.value-percent", oldRenamePercent == 5 ? 10 : oldRenamePercent);
            getConfig().set("item-services.sign.minimum-cost", null);
            getConfig().set("item-services.sign.base-cost", oldSignMinimum == 10L ? 125L : oldSignMinimum);
            getConfig().set("item-services.sign.per-character", 10L);
            getConfig().set("item-services.sign.value-percent", oldSignPercent == 10 ? 15 : oldSignPercent);
        }
        if (version < 3) {
            replaceDefault("item-services.rename.base-cost", 75L, 175L);
            replaceDefault("item-services.rename.value-percent", 10L, 15L);
            replaceDefault("item-services.sign.base-cost", 125L, 100L);
            replaceDefault("item-services.sign.per-character", 10L, 8L);
            replaceDefault("item-services.sign.value-percent", 15L, 10L);
        }
        if (version < 7) {
            // Beide früheren Standardwerte setzten den Sitzenden zu tief: -0.2 galt noch ab der
            // Blockgrenze, -0.15 rechnete den Maßstab des Spielermodells nicht mit.
            double seatOffset = getConfig().getDouble("sit.seat-offset");
            if (seatOffset == -0.2D || seatOffset == -0.15D) {
                getConfig().set("sit.seat-offset", PoseSettings.DEFAULT_SEAT_OFFSET);
            }
            // Neue Abschnitte fehlen in bestehenden Dateien; copyDefaults schreibt sie nach.
            getConfig().options().copyDefaults(true);
        }
        getConfig().set("config-version", CONFIG_VERSION);
        saveConfig();
    }

    private void replaceDefault(String path, long previous, long replacement) {
        if (getConfig().getLong(path) == previous) getConfig().set(path, replacement);
    }
}
