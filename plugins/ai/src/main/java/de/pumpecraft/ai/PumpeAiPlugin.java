package de.pumpecraft.ai;

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

    private ExecutorService executor;
    private AiMessagePool pool;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        AiSettings settings = AiSettings.from(getConfig());
        // Beim Start werden alle angemeldeten Themen auf einmal vorgewärmt; auf einem Thread
        // stünden die letzten davon minutenlang in der Schlange.
        executor = Executors.newFixedThreadPool(REQUEST_THREADS, new AiThreads());

        AiService service = new AiService(settings, new DeepSeekClient(settings), executor, getLogger());
        getServer().getServicesManager().register(AiService.class, service, this, ServicePriority.Normal);

        pool = new AiMessagePool(service);
        Messages.use(pool);

        AiCommand command = new AiCommand(this, service, settings, pool);
        PluginCommand pluginCommand = Objects.requireNonNull(getCommand("pumpeai"), "Missing command: pumpeai");
        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);

        getLogger().info(settings.usable()
            ? "DeepSeek ready; model " + settings.model() + "."
            : "DeepSeek is not configured; dependent plugins keep their own texts.");
    }

    @Override
    public void onDisable() {
        Messages.use(null);
        getServer().getServicesManager().unregisterAll(this);
        if (executor != null) {
            executor.shutdownNow();
            awaitShutdown();
        }
        getLogger().info("PumpeAI disabled.");
    }

    private void awaitShutdown() {
        try {
            executor.awaitTermination(2L, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
