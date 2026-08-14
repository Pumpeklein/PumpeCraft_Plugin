package de.pumpecraft.transactions.payout;

import de.pumpecraft.transactions.core.Currency;
import de.pumpecraft.transactions.core.TransactionsSettings;
import de.pumpecraft.transactions.storage.PayoutRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Schreibt jedem Online-Spieler Spielzeit gut und zahlt beim Erreichen des Intervalls
 * PumpePoints aus. Der Fortschritt liegt im Speicher und wird regelmäßig weggeschrieben,
 * damit ein Neustart die gesammelten Minuten nicht verwirft.
 */
public final class PayoutService {
    private static final int TICK_SECONDS = 10;
    private static final long FLUSH_INTERVAL_TICKS = 20L * 60L;
    private static final String REASON = "Spielzeit";

    private final Plugin plugin;
    private final PayoutRepository payouts;
    private final TransactionsSettings settings;
    private final Map<UUID, AtomicInteger> accrued = new ConcurrentHashMap<>();
    private final Set<UUID> awarding = ConcurrentHashMap.newKeySet();
    private BukkitTask tickTask;
    private BukkitTask flushTask;

    public PayoutService(Plugin plugin, PayoutRepository payouts, TransactionsSettings settings) {
        this.plugin = plugin;
        this.payouts = payouts;
        this.settings = settings;
    }

    public void start() {
        if (!settings.payoutEnabled()) {
            return;
        }
        tickTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin, this::tick, 20L * TICK_SECONDS, 20L * TICK_SECONDS);
        flushTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
            plugin, this::flush, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        flush();
        accrued.clear();
    }

    public void load(UUID playerId) {
        if (!settings.payoutEnabled()) {
            return;
        }
        runAsync(() -> {
            int seconds = Math.min(payouts.loadAccruedSeconds(playerId), settings.payoutIntervalSeconds());
            // Der Spieler kann während des Ladens schon wieder weg sein; dann darf kein
            // Eintrag zurückbleiben, den unload nie mehr abräumt.
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (plugin.getServer().getPlayer(playerId) != null) {
                    accrued.put(playerId, new AtomicInteger(seconds));
                }
            });
        }, "load payout progress for " + playerId);
    }

    public void unload(UUID playerId) {
        AtomicInteger counter = accrued.remove(playerId);
        if (counter == null) {
            return;
        }
        int seconds = counter.get();
        runAsync(
            () -> payouts.saveAccruedSeconds(Map.of(playerId, seconds)),
            "save payout progress for " + playerId
        );
    }

    public int accruedSeconds(UUID playerId) {
        AtomicInteger counter = accrued.get(playerId);
        return counter == null ? 0 : counter.get();
    }

    public int remainingSeconds(UUID playerId) {
        return Math.max(0, settings.payoutIntervalSeconds() - accruedSeconds(playerId));
    }

    private void tick() {
        int interval = settings.payoutIntervalSeconds();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            AtomicInteger counter = accrued.get(player.getUniqueId());
            if (counter == null || isIdle(player)) {
                continue;
            }
            if (counter.addAndGet(TICK_SECONDS) < interval) {
                continue;
            }
            counter.addAndGet(-interval);
            award(player);
        }
    }

    private boolean isIdle(Player player) {
        int maxIdleSeconds = settings.maxIdleSeconds();
        return maxIdleSeconds > 0 && player.getIdleDuration().toSeconds() >= maxIdleSeconds;
    }

    private void award(Player player) {
        UUID playerId = player.getUniqueId();
        if (!awarding.add(playerId)) {
            return;
        }
        String playerName = player.getName();
        long amount = settings.payoutAmount();
        int remaining = accruedSeconds(playerId);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                long balance = payouts.award(playerId, playerName, amount, remaining, REASON);
                plugin.getServer().getScheduler().runTask(
                    plugin, () -> announce(playerId, amount, balance));
            } catch (RuntimeException exception) {
                returnTime(playerId);
                plugin.getLogger().log(
                    Level.WARNING,
                    "Could not pay out " + amount + " " + Currency.SYMBOL + " to " + playerName
                        + "; the time was credited back.",
                    exception
                );
            } finally {
                awarding.remove(playerId);
            }
        });
    }

    private void returnTime(UUID playerId) {
        AtomicInteger counter = accrued.get(playerId);
        if (counter != null) {
            counter.addAndGet(settings.payoutIntervalSeconds());
        }
    }

    private void announce(UUID playerId, long amount, long balance) {
        if (!settings.announcePayout()) {
            return;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null) {
            return;
        }
        player.sendMessage(Component.text("Du hast ", NamedTextColor.GRAY)
            .append(Currency.component(amount))
            .append(Component.text(
                " für " + settings.payoutIntervalMinutes() + " Minuten Spielzeit erhalten. ",
                NamedTextColor.GRAY))
            .append(Component.text("Kontostand: ", NamedTextColor.DARK_GRAY))
            .append(Currency.component(balance)));
    }

    private void flush() {
        if (accrued.isEmpty()) {
            return;
        }
        Map<UUID, Integer> snapshot = new HashMap<>();
        accrued.forEach((playerId, counter) -> snapshot.put(playerId, counter.get()));
        try {
            payouts.saveAccruedSeconds(snapshot);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(
                Level.WARNING, "Could not persist payout progress; retrying on the next run.", exception);
        }
    }

    private void runAsync(Runnable action, String description) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                action.run();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not " + description + ".", exception);
            }
        });
    }
}
