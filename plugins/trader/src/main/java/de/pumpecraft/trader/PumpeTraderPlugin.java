package de.pumpecraft.trader;

import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeTraderPlugin extends JavaPlugin {
    private TraderCommand traderCommand;

    @Override
    public void onEnable() {
        traderCommand = new TraderCommand(this);
        PluginCommand command = Objects.requireNonNull(getCommand("trader"), "Missing command: trader");
        command.setExecutor(traderCommand);
        command.setTabCompleter(traderCommand);
        getServer().getPluginManager().registerEvents(traderCommand, this);

        getLogger().info("PumpeTrader enabled.");
    }

    @Override
    public void onDisable() {
        if (traderCommand != null) {
            traderCommand.removeAllTraders();
        }
        getLogger().info("PumpeTrader disabled.");
    }
}
