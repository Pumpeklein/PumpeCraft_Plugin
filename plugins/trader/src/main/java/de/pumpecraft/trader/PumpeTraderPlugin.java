package de.pumpecraft.trader;

import de.pumpecraft.transactions.core.Points;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeTraderPlugin extends JavaPlugin {
    private TraderCommand traderCommand;

    @Override
    public void onEnable() {
        TraderTopics.register();

        TraderItems items = new TraderItems(this);
        TraderShop shop = new TraderShop(this, Points.require(this), items);
        traderCommand = new TraderCommand(this, items, shop);
        PluginCommand command = Objects.requireNonNull(getCommand("trader"), "Missing command: trader");
        command.setExecutor(traderCommand);
        command.setTabCompleter(traderCommand);
        getServer().getPluginManager().registerEvents(traderCommand, this);
        getServer().getPluginManager().registerEvents(shop, this);

        getLogger().info("PumpeTrader enabled.");
    }

    @Override
    public void onDisable() {
        if (traderCommand != null) {
            traderCommand.removeAllTraders(false);
        }
        getLogger().info("PumpeTrader disabled.");
    }
}
