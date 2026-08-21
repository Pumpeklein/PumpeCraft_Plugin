package de.pumpecraft.enchants;

import de.pumpecraft.enchants.anvil.AnvilCombiner;
import de.pumpecraft.enchants.fall.FallProtection;
import de.pumpecraft.enchants.listener.AnvilEnchantListener;
import de.pumpecraft.enchants.listener.BlockEnchantListener;
import de.pumpecraft.enchants.listener.FallEnchantListener;
import de.pumpecraft.enchants.mining.BlockMiningRules;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeEnchantsPlugin extends JavaPlugin {
    private static final int CONFIG_VERSION = 1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        getConfig().set("config-version", CONFIG_VERSION);
        saveConfig();

        EnchantSettings settings = EnchantSettings.from(getConfig());
        EnchantRegistry registry = new EnchantRegistry(settings);
        EnchantService service = new EnchantService(registry, new ItemEnchants(registry));
        getServer().getServicesManager().register(
            EnchantService.class, service, this, ServicePriority.Normal);

        EnchantTopics.register();
        getServer().getPluginManager().registerEvents(
            new BlockEnchantListener(new BlockMiningRules(service)), this);
        getServer().getPluginManager().registerEvents(
            new FallEnchantListener(new FallProtection(service, settings)), this);
        getServer().getPluginManager().registerEvents(
            new AnvilEnchantListener(new AnvilCombiner(service), settings.anvilLevelCost()), this);

        EnchantCommand executor = new EnchantCommand(this, registry, service);
        PluginCommand command = Objects.requireNonNull(getCommand("enchant"), "Missing command: enchant");
        command.setExecutor(executor);
        command.setTabCompleter(executor);
        getLogger().info("PumpeEnchants enabled.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        getLogger().info("PumpeEnchants disabled.");
    }
}
