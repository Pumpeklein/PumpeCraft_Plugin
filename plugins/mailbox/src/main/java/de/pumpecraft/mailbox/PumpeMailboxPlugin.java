package de.pumpecraft.mailbox;

import de.pumpecraft.mailbox.command.MailboxCommand;
import de.pumpecraft.mailbox.listener.MailboxInteractListener;
import de.pumpecraft.mailbox.listener.MailboxInventoryListener;
import de.pumpecraft.mailbox.listener.MailboxPlaceListener;
import de.pumpecraft.mailbox.mail.MailService;
import de.pumpecraft.utils.objects.HingeAnimator;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeMailboxPlugin extends JavaPlugin {
    private static final int CONFIG_VERSION = 1;

    private HingeAnimator animator;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();

        MailboxSettings settings = new MailboxSettings(this, getConfig());
        animator = new HingeAnimator(this);
        MailService mail = new MailService(settings, new MailboxAnimations(settings, animator));

        MailboxCommand mailboxCommand = new MailboxCommand(mail);
        PluginCommand command = Objects.requireNonNull(getCommand("mailbox"), "Missing command: mailbox");
        command.setExecutor(mailboxCommand);
        command.setTabCompleter(mailboxCommand);

        getServer().getPluginManager().registerEvents(new MailboxPlaceListener(), this);
        getServer().getPluginManager().registerEvents(new MailboxInteractListener(settings, mail), this);
        getServer().getPluginManager().registerEvents(new MailboxInventoryListener(mail), this);

        getLogger().info("PumpeMailbox enabled.");
    }

    @Override
    public void onDisable() {
        if (animator != null) {
            animator.cancelAll();
        }
        getLogger().info("PumpeMailbox disabled.");
    }

    private void migrateConfig() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        getConfig().set("config-version", CONFIG_VERSION);
        saveConfig();
    }
}
