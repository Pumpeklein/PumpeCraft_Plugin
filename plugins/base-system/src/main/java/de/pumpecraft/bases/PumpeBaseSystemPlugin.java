package de.pumpecraft.bases;

import de.pumpecraft.bases.base.BaseRepository;
import de.pumpecraft.bases.base.BaseService;
import de.pumpecraft.bases.command.BaseCommand;
import de.pumpecraft.bases.command.PlotCommand;
import de.pumpecraft.bases.gui.BaseMenuListener;
import de.pumpecraft.bases.gui.BaseMenus;
import de.pumpecraft.bases.gui.PlotMenuListener;
import de.pumpecraft.bases.gui.PlotMenus;
import de.pumpecraft.bases.gui.TextInput;
import de.pumpecraft.bases.listener.BaseJoinListener;
import de.pumpecraft.bases.listener.PlotBlockListener;
import de.pumpecraft.bases.listener.PlotEntityListener;
import de.pumpecraft.bases.listener.PlotMoveListener;
import de.pumpecraft.bases.listener.PlotToolListener;
import de.pumpecraft.bases.listener.PlotWorldListener;
import de.pumpecraft.bases.plot.PlotGuard;
import de.pumpecraft.bases.plot.PlotIndex;
import de.pumpecraft.bases.plot.PlotRepository;
import de.pumpecraft.bases.plot.PlotService;
import de.pumpecraft.bases.plot.PlotTool;
import de.pumpecraft.bases.plot.PlotVisualizer;
import de.pumpecraft.transactions.core.Points;
import de.pumpecraft.transactions.core.PointsService;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class PumpeBaseSystemPlugin extends JavaPlugin {
    private BasePermissions permissions;
    private PointsService points;
    private PlotRepository plotRepository;
    private PlotVisualizer visualizer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        permissions = new BasePermissions(this);
        permissions.load();
        if (!databaseAvailable()) {
            return;
        }

        points = Points.require(this);
        PlotSettings plotSettings = new PlotSettings(getConfig());
        plotRepository = new PlotRepository(this);
        PlotIndex plotIndex = new PlotIndex();
        PlotGuard plotGuard = new PlotGuard(this, plotIndex);
        PlotTool plotTool = new PlotTool(this, plotSettings);
        PlotService plotService = new PlotService(
            this, plotRepository, plotIndex, plotSettings, plotGuard, plotTool);
        visualizer = new PlotVisualizer(this);
        TextInput textInput = new TextInput(this);
        PlotMenus plotMenus = new PlotMenus(
            this, plotService, plotGuard, plotTool, visualizer, textInput);

        BaseSettings settings = new BaseSettings(getConfig());
        BaseRepository repository = new BaseRepository(this);
        BaseService service = new BaseService(this, repository, settings, plotGuard);
        BaseMenus menus = new BaseMenus(this, service);

        registerCommand("base", new BaseCommand(this, service, menus));
        registerCommand("plot",
            new PlotCommand(this, plotService, plotGuard, plotMenus, plotTool, visualizer));
        getServer().getPluginManager().registerEvents(new BaseMenuListener(menus), this);
        getServer().getPluginManager().registerEvents(new BaseJoinListener(this, repository), this);
        getServer().getPluginManager().registerEvents(new PlotMenuListener(plotMenus), this);
        getServer().getPluginManager().registerEvents(textInput, this);
        getServer().getPluginManager().registerEvents(
            new PlotToolListener(this, plotService, plotTool, visualizer), this);
        getServer().getPluginManager().registerEvents(new PlotBlockListener(plotGuard), this);
        getServer().getPluginManager().registerEvents(new PlotEntityListener(plotGuard), this);
        getServer().getPluginManager().registerEvents(new PlotMoveListener(plotGuard), this);
        getServer().getPluginManager().registerEvents(new PlotWorldListener(plotGuard), this);
        plotService.reload();

        service.refreshDirectory();
        getServer().getScheduler().runTaskTimer(
            this,
            service::refreshDirectory,
            settings.directoryRefreshTicks(),
            settings.directoryRefreshTicks()
        );
        getLogger().info("Player base and plot systems enabled.");
    }

    @Override
    public void onDisable() {
        if (visualizer != null) {
            visualizer.clear();
        }
        getLogger().info("PumpeBaseSystem disabled.");
    }

    public String permission(String key) {
        return permissions.node(key);
    }

    public PointsService points() {
        return points;
    }

    public PlotRepository plotRepository() {
        return plotRepository;
    }

    public <T> void runAsync(CommandSender recipient, Supplier<T> work, Consumer<T> callback) {
        String recipientName = recipient.getName();
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                T result = work.get();
                getServer().getScheduler().runTask(this, () -> {
                    if (!(recipient instanceof Player player) || player.isOnline()) {
                        callback.accept(result);
                    }
                });
            } catch (RuntimeException exception) {
                getLogger().warning(
                    "Base database operation for " + recipientName + " failed: "
                        + exception.getMessage()
                );
                getServer().getScheduler().runTask(this, () -> {
                    if (!(recipient instanceof Player player) || player.isOnline()) {
                        recipient.sendMessage(Component.text(
                            "Die Base-Datenbank konnte nicht verarbeitet werden.",
                            NamedTextColor.RED
                        ));
                    }
                });
            }
        });
    }

    public void runAsync(Runnable work) {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                work.run();
            } catch (RuntimeException exception) {
                getLogger().warning("Base background operation failed: " + exception.getMessage());
            }
        });
    }

    public void runSync(Runnable work) {
        getServer().getScheduler().runTask(this, work);
    }

    private void registerCommand(String name, CommandExecutor executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name), "Missing command: " + name);
        command.setExecutor(executor);
        if (executor instanceof TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }

    private boolean databaseAvailable() {
        Plugin databasePlugin = getServer().getPluginManager().getPlugin("PumpeDatabase");
        if (databasePlugin != null && databasePlugin.isEnabled()) {
            return true;
        }
        getLogger().severe("PumpeDatabase is not available; PumpeBaseSystem will remain disabled.");
        getServer().getPluginManager().disablePlugin(this);
        return false;
    }
}
