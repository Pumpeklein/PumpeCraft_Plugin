package de.pumpecraft.ai;

import de.pumpecraft.ai.moderation.ModerationService;
import de.pumpecraft.ai.support.DaemonThreads;
import de.pumpecraft.utils.messages.Messages;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeAiPlugin extends JavaPlugin {
    private static final int REQUEST_THREADS = 3;
    private static final int CONFIG_VERSION = 5;
    private static final long SAVE_INTERVAL_TICKS = 20L * 300L;

    private ExecutorService executor;
    private AiMessagePool pool;
    private ModerationService moderation;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        migrateConfig();

        AiSettings settings = AiSettings.from(getConfig());
        // Beim Start werden alle angemeldeten Themen auf einmal vorgewärmt; auf einem Thread
        // stünden die letzten davon minutenlang in der Schlange.
        executor = Executors.newFixedThreadPool(REQUEST_THREADS, new DaemonThreads("PumpeAI"));

        AiService service = new AiService(settings, new DeepSeekClient(settings), executor, getLogger());
        getServer().getServicesManager().register(AiService.class, service, this, ServicePriority.Normal);

        moderation = ModerationService.create(getConfig().getConfigurationSection("moderation"), getLogger());
        getServer().getServicesManager().register(ModerationService.class, moderation, this, ServicePriority.Normal);

        MessageSettings messageSettings = MessageSettings.from(getConfig().getConfigurationSection("messages"));
        pool = new AiMessagePool(service, messageSettings, new AiMessageStore(getDataFolder(), getLogger()));
        Messages.use(pool);

        AiCommand command = new AiCommand(this, service, settings, messageSettings, pool, moderation);
        PluginCommand pluginCommand = Objects.requireNonNull(getCommand("pumpeai"), "Missing command: pumpeai");
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        // Ein abgewuergter Server kaeme sonst mit leeren Vorraeten zurueck, und das kostet
        // wieder eine Anfrage pro Thema.
        getServer().getScheduler().runTaskTimerAsynchronously(
            this, pool::save, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);

        getLogger().info(settings.usable()
            ? "DeepSeek ready; model " + settings.model() + "."
            : "DeepSeek is not configured; dependent plugins keep their own texts.");
        getLogger().info(moderation.available()
            ? "Moderation ready; model " + moderation.model() + "."
            : "Moderation is not configured; dependent plugins keep their own filters.");
    }

    @Override
    public void onDisable() {
        Messages.use(null);
        if (pool != null) {
            pool.save();
        }
        getServer().getServicesManager().unregisterAll(this);
        if (moderation != null) {
            moderation.shutdown();
        }
        if (executor != null) {
            executor.shutdownNow();
            awaitShutdown();
        }
        getLogger().info("PumpeAI disabled.");
    }

    private void migrateConfig() {
        int version = getConfig().getInt("config-version", 1);
        if (version >= CONFIG_VERSION) {
            return;
        }
        if (version == 2) {
            // Die ersten Schwellen waren fuer kurze deutsche Saetze zu hoch. Sie werden geloescht
            // statt ueberschrieben, weil copyDefaults nur fehlende Schluessel ergaenzt und einen
            // vorhandenen Wert nie anfasst.
            getConfig().set("moderation.thresholds", null);
            getConfig().set("moderation.default-threshold", null);
        }
        getConfig().options().copyDefaults(true);
        getConfig().set("config-version", CONFIG_VERSION);
        saveConfig();
    }

    private void awaitShutdown() {
        try {
            executor.awaitTermination(2L, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
