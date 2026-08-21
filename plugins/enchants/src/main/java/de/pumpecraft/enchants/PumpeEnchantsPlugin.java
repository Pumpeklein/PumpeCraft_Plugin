package de.pumpecraft.enchants;

import de.pumpecraft.enchants.anvil.AnvilCombiner;
import de.pumpecraft.enchants.command.EnchantBooksCommand;
import de.pumpecraft.enchants.command.EnchantCommand;
import de.pumpecraft.enchants.fall.FallProtection;
import de.pumpecraft.enchants.listener.AnvilEnchantListener;
import de.pumpecraft.enchants.listener.BlockEnchantListener;
import de.pumpecraft.enchants.listener.FallEnchantListener;
import de.pumpecraft.enchants.mining.BlockMiningRules;
import java.util.Objects;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeEnchantsPlugin extends JavaPlugin {
    private static final int CONFIG_VERSION = 1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();

        EnchantSettings settings = new EnchantSettings(getConfig());
        EnchantRegistry registry = new EnchantRegistry(settings);
        EnchantService service = new EnchantService(
            registry, new ItemEnchants(registry), settings.maxEnchantsPerItem());
        getServer().getServicesManager().register(
            EnchantService.class, service, this, ServicePriority.Normal);

        getServer().getPluginManager().registerEvents(
            new BlockEnchantListener(new BlockMiningRules(service)), this);
        getServer().getPluginManager().registerEvents(
            new FallEnchantListener(new FallProtection(service, settings)), this);
        getServer().getPluginManager().registerEvents(
            new AnvilEnchantListener(this, new AnvilCombiner(service), settings.anvilLevelCost()),
            this);

        register("customenchant", new EnchantCommand(registry, service));
        register("enchantbooks", new EnchantBooksCommand(registry, service));
        getLogger().info("PumpeEnchants enabled.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        getLogger().info("PumpeEnchants disabled.");
    }

    private <T extends CommandExecutor & TabCompleter> void register(String name, T executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "Missing command: " + name);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void migrateConfig() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        getConfig().set("config-version", CONFIG_VERSION);
        saveConfig();
    }
}
