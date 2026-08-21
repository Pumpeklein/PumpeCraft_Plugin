package de.pumpecraft.enchants;

import de.pumpecraft.enchants.anvil.AnvilCombiner;
import de.pumpecraft.enchants.armor.Endurance;
import de.pumpecraft.enchants.armor.FallProtection;
import de.pumpecraft.enchants.armor.JumpSpring;
import de.pumpecraft.enchants.combat.Barb;
import de.pumpecraft.enchants.combat.ClanBond;
import de.pumpecraft.enchants.combat.CombatRules;
import de.pumpecraft.enchants.combat.Execution;
import de.pumpecraft.enchants.combat.LifeSteal;
import de.pumpecraft.enchants.combat.ThunderStrike;
import de.pumpecraft.enchants.command.EnchantBooksCommand;
import de.pumpecraft.enchants.command.EnchantCommand;
import de.pumpecraft.enchants.item.ItemMagnet;
import de.pumpecraft.enchants.listener.AnvilEnchantListener;
import de.pumpecraft.enchants.listener.BlockEnchantListener;
import de.pumpecraft.enchants.listener.CombatEnchantListener;
import de.pumpecraft.enchants.listener.CourierListener;
import de.pumpecraft.enchants.listener.DamageEnchantListener;
import de.pumpecraft.enchants.listener.SoulboundListener;
import de.pumpecraft.enchants.mining.BlockMiningRules;
import de.pumpecraft.enchants.integration.Courier;
import de.pumpecraft.enchants.integration.LuckyDrops;
import de.pumpecraft.enchants.soulbound.SoulboundRepository;
import de.pumpecraft.enchants.soulbound.SoulboundRules;
import de.pumpecraft.enchants.tick.EnchantTicker;
import java.util.Objects;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeEnchantsPlugin extends JavaPlugin {
    private static final int CONFIG_VERSION = 2;

    private EnchantTicker ticker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();
        if (!databaseAvailable()) {
            return;
        }

        EnchantSettings settings = new EnchantSettings(getConfig());
        EnchantRegistry registry = new EnchantRegistry(settings);
        EnchantChains chains = new EnchantChains();
        EnchantService service = new EnchantService(
            registry, new ItemEnchants(registry), settings, chains);
        getServer().getServicesManager().register(
            EnchantService.class, service, this, ServicePriority.Normal);

        LuckyDrops lucky = new LuckyDrops(this, service, settings);
        CombatRules combat = new CombatRules(
            new Execution(service, settings),
            new ClanBond(this, service, settings),
            new ThunderStrike(service, settings),
            new LifeSteal(service, settings),
            new Barb(service, settings));
        SoulboundRules soulbound = new SoulboundRules(
            this, service, new SoulboundRepository(this));

        listen(new BlockEnchantListener(new BlockMiningRules(service, settings, chains), lucky, chains));
        listen(new CombatEnchantListener(combat, lucky));
        listen(new DamageEnchantListener(
            new FallProtection(service, settings), new Endurance(service, settings)));
        listen(new AnvilEnchantListener(
            this, new AnvilCombiner(service), settings.anvilLevelCost()));
        listen(new SoulboundListener(this, soulbound));
        listen(new CourierListener(new Courier(this, service)));

        ticker = new EnchantTicker(
            this,
            new ItemMagnet(service, settings),
            new JumpSpring(service),
            getConfig().getInt("tick-interval-ticks", 10));
        ticker.start();

        register("customenchant", new EnchantCommand(registry, service));
        register("enchantbooks", new EnchantBooksCommand(registry, service));
        getLogger().info("PumpeEnchants enabled.");
    }

    @Override
    public void onDisable() {
        if (ticker != null) {
            ticker.stop();
        }
        getServer().getServicesManager().unregisterAll(this);
        getLogger().info("PumpeEnchants disabled.");
    }

    private void listen(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private <T extends CommandExecutor & TabCompleter> void register(String name, T executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "Missing command: " + name);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private boolean databaseAvailable() {
        Plugin database = getServer().getPluginManager().getPlugin("PumpeDatabase");
        if (database != null && database.isEnabled()) {
            return true;
        }
        getLogger().severe("PumpeDatabase is not available; PumpeEnchants will remain disabled.");
        getServer().getPluginManager().disablePlugin(this);
        return false;
    }

    private void migrateConfig() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        getConfig().set("config-version", CONFIG_VERSION);
        saveConfig();
    }
}
