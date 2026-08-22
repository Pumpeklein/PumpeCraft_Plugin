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
import de.pumpecraft.enchants.command.GenerateLootCommand;
import de.pumpecraft.enchants.item.ItemMagnet;
import de.pumpecraft.enchants.listener.AnvilEnchantListener;
import de.pumpecraft.enchants.listener.BlockEnchantListener;
import de.pumpecraft.enchants.listener.CombatEnchantListener;
import de.pumpecraft.enchants.listener.CourierListener;
import de.pumpecraft.enchants.listener.DamageEnchantListener;
import de.pumpecraft.enchants.listener.EnchantBookMigrationListener;
import de.pumpecraft.enchants.listener.SoulboundListener;
import de.pumpecraft.enchants.listener.LootEnchantListener;
import de.pumpecraft.enchants.listener.RareBookDiscoveryListener;
import de.pumpecraft.enchants.loot.CustomEnchantLoot;
import de.pumpecraft.enchants.loot.RareBookDiscovery;
import de.pumpecraft.enchants.mining.BlockMiningRules;
import de.pumpecraft.enchants.integration.Courier;
import de.pumpecraft.enchants.integration.LuckyDrops;
import de.pumpecraft.enchants.soulbound.SoulboundRepository;
import de.pumpecraft.enchants.soulbound.SoulboundRules;
import de.pumpecraft.enchants.tick.EnchantTicker;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeEnchantsPlugin extends JavaPlugin {
    private static final int CONFIG_VERSION = 4;

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
        RareBookDiscovery rareBooks = new RareBookDiscovery(this, service);
        CustomEnchantLoot customLoot = new CustomEnchantLoot(
            registry, service, settings, rareBooks);

        listen(new BlockEnchantListener(
            new BlockMiningRules(this, service, settings, chains), lucky, chains));
        listen(new CombatEnchantListener(combat, lucky));
        listen(new DamageEnchantListener(
            new FallProtection(service, settings), new Endurance(service, settings)));
        listen(new AnvilEnchantListener(
            this, new AnvilCombiner(service), settings.anvilLevelCost()));
        listen(new SoulboundListener(this, soulbound));
        listen(new CourierListener(new Courier(this, service)));
        listen(new LootEnchantListener(customLoot));
        listen(new RareBookDiscoveryListener(this, rareBooks));
        getServer().getScheduler().runTask(this, () ->
            getServer().getOnlinePlayers().forEach(rareBooks::discover));
        EnchantBookMigrationListener bookMigration = new EnchantBookMigrationListener(service);
        listen(bookMigration);
        getServer().getScheduler().runTask(this, () -> {
            int migrated = bookMigration.migrateLoadedWorlds(getServer().getWorlds());
            getLogger().info("Updated " + migrated + " existing custom enchantment books.");
        });

        ticker = new EnchantTicker(
            this,
            new ItemMagnet(service, settings),
            new JumpSpring(service),
            getConfig().getInt("tick-interval-ticks", 10));
        ticker.start();

        EnchantCommand enchantCommand = new EnchantCommand(registry, service);
        EnchantBooksCommand booksCommand = new EnchantBooksCommand(registry, service);
        GenerateLootCommand generateLootCommand = new GenerateLootCommand(customLoot);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(
                "customenchant",
                "Applies a custom enchantment to the held item.",
                List.of("cenchant", "verzaubern"),
                enchantCommand);
            event.registrar().register(
                "enchantbooks",
                "Hands out one book of every custom enchantment level.",
                List.of("testenchants"),
                booksCommand);
            event.registrar().register(
                "gen",
                "Generates chest loot in the container being looked at.",
                generateLootCommand);
        });
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
        int version = getConfig().getInt("config-version", 2);
        if (version < 3) {
            applyRaisedLootChances();
        }
        if (version < 4) {
            applyCooldownRange();
        }
        getConfig().options().copyDefaults(true);
        getConfig().set("config-version", CONFIG_VERSION);
        saveConfig();
    }

    private void applyCooldownRange() {
        for (String enchantment : List.of("vein_mining", "lumberjack")) {
            String path = "enchants." + enchantment;
            if (getConfig().getDouble(path + ".cooldown-seconds", 100.0) == 100.0) {
                getConfig().set(path + ".cooldown-seconds", 60.0);
            }
            if (getConfig().getDouble(path + ".minimum-cooldown-seconds", 60.0) == 60.0) {
                getConfig().set(path + ".minimum-cooldown-seconds", 30.0);
            }
        }
    }

    private void applyRaisedLootChances() {
        getConfig().set("enchants.telekinesis.loot-chance-percent", 2.0);
        getConfig().set("enchants.furnace.loot-chance-percent", 1.2);
        getConfig().set("enchants.vein_mining.loot-chance-percent", 0.8);
        getConfig().set("enchants.lumberjack.loot-chance-percent", 0.8);
        getConfig().set("enchants.magnet.loot-chance-percent", 1.8);
        getConfig().set("enchants.lifesteal.loot-chance-percent", 0.4);
        getConfig().set("enchants.execution.loot-chance-percent", 0.35);
        getConfig().set("enchants.thunder.loot-chance-percent", 0.18);
        getConfig().set("enchants.barb.loot-chance-percent", 0.65);
        getConfig().set("enchants.soulbound.loot-chance-percent", 0.05);
        getConfig().set("enchants.endurance.loot-chance-percent", 0.25);
        getConfig().set("enchants.featherweight.loot-chance-percent", 1.4);
        getConfig().set("enchants.jump_spring.loot-chance-percent", 1.1);
        getConfig().set("enchants.scholar.loot-chance-percent", 0.2);
        getConfig().set("enchants.lucky.loot-chance-percent", 0.1);
        getConfig().set("enchants.clan_bond.loot-chance-percent", 0.3);
        getConfig().set("enchants.courier.loot-chance-percent", 0.45);
    }
}
