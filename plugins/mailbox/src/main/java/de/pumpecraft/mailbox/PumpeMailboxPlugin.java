package de.pumpecraft.mailbox;

import de.pumpecraft.mailbox.box.MailboxIndex;
import de.pumpecraft.mailbox.box.MailboxInventories;
import de.pumpecraft.mailbox.box.MailboxRepository;
import de.pumpecraft.mailbox.command.MailboxCommand;
import de.pumpecraft.mailbox.craft.MailboxRecipe;
import de.pumpecraft.mailbox.listener.MailboxInteractListener;
import de.pumpecraft.mailbox.listener.MailboxInventoryListener;
import de.pumpecraft.mailbox.listener.MailboxJoinListener;
import de.pumpecraft.mailbox.listener.MailboxPlaceListener;
import de.pumpecraft.mailbox.mail.DeliveryRepository;
import de.pumpecraft.transactions.core.Points;
import de.pumpecraft.transactions.core.PointsService;
import de.pumpecraft.utils.objects.HingeAnimator;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeMailboxPlugin extends JavaPlugin {
    private static final int CONFIG_VERSION = 3;

    private HingeAnimator animator;
    private MailboxService service;
    private MailboxRecipe recipe;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();

        if (!dependenciesAvailable()) {
            return;
        }

        MailboxSettings settings = new MailboxSettings(this, getConfig());
        animator = new HingeAnimator(this);
        MailboxAnimations animations = new MailboxAnimations(settings, animator);
        MailboxIndex index = new MailboxIndex(this, new MailboxRepository(this));
        MailboxInventories inventories = new MailboxInventories(settings);
        PointsService points = Points.require(this);

        service = new MailboxService(
            this, settings, animations, index, inventories, new DeliveryRepository(this), points);
        service.start();
        getServer().getServicesManager().register(
            MailboxService.class, service, this, ServicePriority.Normal);

        recipe = new MailboxRecipe();
        if (settings.craftingEnabled()) {
            recipe.register();
        }

        MailboxCommand mailboxCommand = new MailboxCommand(service);
        PluginCommand command = Objects.requireNonNull(getCommand("mailbox"), "Missing command: mailbox");
        command.setExecutor(mailboxCommand);
        command.setTabCompleter(mailboxCommand);

        getServer().getPluginManager().registerEvents(new MailboxPlaceListener(service), this);
        getServer().getPluginManager().registerEvents(new MailboxInteractListener(settings, service), this);
        getServer().getPluginManager().registerEvents(new MailboxInventoryListener(this, service), this);
        getServer().getPluginManager().registerEvents(new MailboxJoinListener(this, service, recipe), this);

        getLogger().info("PumpeMailbox enabled.");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (recipe != null) {
            recipe.unregister();
        }
        if (service != null) {
            service.shutdown();
        }
        if (animator != null) {
            animator.cancelAll();
        }
        getLogger().info("PumpeMailbox disabled.");
    }

    private boolean dependenciesAvailable() {
        for (String name : new String[] {"PumpeDatabase", "PumpeTransactions"}) {
            Plugin dependency = getServer().getPluginManager().getPlugin(name);
            if (dependency == null || !dependency.isEnabled()) {
                getLogger().severe(name + " is not available; PumpeMailbox will remain disabled.");
                getServer().getPluginManager().disablePlugin(this);
                return false;
            }
        }
        return true;
    }

    private void migrateConfig() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        getConfig().set("config-version", CONFIG_VERSION);
        saveConfig();
    }
}
