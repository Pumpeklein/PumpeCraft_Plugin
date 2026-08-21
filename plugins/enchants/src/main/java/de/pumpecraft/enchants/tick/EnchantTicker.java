package de.pumpecraft.enchants.tick;

import de.pumpecraft.enchants.armor.JumpSpring;
import de.pumpecraft.enchants.item.ItemMagnet;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** The one repeating task for everything that has no event to hang on to. */
public final class EnchantTicker implements Runnable {
    private final Plugin plugin;
    private final ItemMagnet magnet;
    private final JumpSpring jumpSpring;
    private final int intervalTicks;
    private BukkitTask task;

    public EnchantTicker(Plugin plugin, ItemMagnet magnet, JumpSpring jumpSpring, int intervalTicks) {
        this.plugin = plugin;
        this.magnet = magnet;
        this.jumpSpring = jumpSpring;
        this.intervalTicks = Math.max(1, intervalTicks);
    }

    public void start() {
        task = plugin.getServer().getScheduler()
            .runTaskTimer(plugin, this, intervalTicks, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            magnet.pull(player);
            jumpSpring.apply(player, intervalTicks);
        }
    }
}
