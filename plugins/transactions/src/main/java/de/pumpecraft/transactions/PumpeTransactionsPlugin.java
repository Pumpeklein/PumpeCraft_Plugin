package de.pumpecraft.transactions;

import de.pumpecraft.database.DatabaseService;
import de.pumpecraft.database.Databases;
import de.pumpecraft.transactions.command.PointsAdmin;
import de.pumpecraft.transactions.command.PointsCommand;
import de.pumpecraft.transactions.core.PointsService;
import de.pumpecraft.transactions.core.TransactionsSettings;
import de.pumpecraft.transactions.payout.PayoutListener;
import de.pumpecraft.transactions.payout.PayoutService;
import de.pumpecraft.transactions.storage.AccountRepository;
import de.pumpecraft.transactions.storage.PayoutRepository;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeTransactionsPlugin extends JavaPlugin {
    private static final int CONFIG_VERSION = 1;

    private PayoutService payouts;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        if (!databaseAvailable()) {
            return;
        }

        TransactionsSettings settings = TransactionsSettings.from(getConfig());
        DatabaseService database = Databases.require(this);
        AccountRepository accounts = new AccountRepository(database);
        PointsService points = new PointsService(this, accounts, settings);
        payouts = new PayoutService(this, new PayoutRepository(database, accounts), settings);

        getServer().getServicesManager()
            .register(PointsService.class, points, this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(new PayoutListener(payouts), this);

        PointsCommand pointsCommand = new PointsCommand(points, payouts, new PointsAdmin(points));
        PluginCommand command = Objects.requireNonNull(getCommand("pp"), "Missing command: pp");
        command.setExecutor(pointsCommand);
        command.setTabCompleter(pointsCommand);

        payouts.start();

        // Beim Reload sind Spieler schon online und lösen kein Join-Event mehr aus.
        getServer().getOnlinePlayers().forEach(player -> payouts.load(player.getUniqueId()));

        getLogger().info("PumpeTransactions enabled.");
    }

    @Override
    public void onDisable() {
        if (payouts != null) {
            payouts.shutdown();
        }
        getServer().getServicesManager().unregisterAll(this);
        getLogger().info("PumpeTransactions disabled.");
    }

    private void migrateConfig() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        getConfig().set("config-version", CONFIG_VERSION);
        saveConfig();
    }

    private boolean databaseAvailable() {
        Plugin databasePlugin = getServer().getPluginManager().getPlugin("PumpeDatabase");
        if (databasePlugin != null && databasePlugin.isEnabled()) {
            return true;
        }
        getLogger().severe("PumpeDatabase is not available; PumpeTransactions will remain disabled.");
        getServer().getPluginManager().disablePlugin(this);
        return false;
    }
}
