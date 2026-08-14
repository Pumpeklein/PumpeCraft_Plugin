package de.pumpecraft.anticheat.check;

import de.pumpecraft.anticheat.core.CheckType;
import de.pumpecraft.anticheat.core.PlayerStateStore;
import de.pumpecraft.anticheat.core.ViolationService;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

/**
 * Guards the state an illegal item leaves behind. {@link ItemChecks} can only see a stack while
 * it exists; once an exploit potion has been drunk the effect itself is the remaining evidence.
 */
public final class EffectChecks extends AbstractCheck {
    private BukkitTask scanTask;

    public EffectChecks(Plugin plugin, PlayerStateStore states, ViolationService violations) {
        super(plugin, states, violations);
    }

    public void start() {
        long interval = Math.max(
            100L,
            settings.duration(CheckType.EFFECT, "scan-interval-ticks", 200L)
        );
        scanTask = plugin.getServer().getScheduler()
            .runTaskTimer(plugin, this::scanOnlinePlayers, interval, interval);
    }

    public void shutdown() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)
            || !violations.enabled(CheckType.EFFECT)
            || exempt(player)) {
            return;
        }

        PotionEffect applied = event.getNewEffect();
        Set<String> exemptTypes = state(player).effects.exemptTypes;
        if (applied == null) {
            if (event.getOldEffect() != null) {
                exemptTypes.remove(event.getOldEffect().getType().getKey().getKey());
            }
            return;
        }

        String key = applied.getType().getKey().getKey();
        if (ignoredCauses().contains(event.getCause().name())) {
            exemptTypes.add(key);
            return;
        }
        exemptTypes.remove(key);
        evaluate(player, applied, event.getCause().name());
    }

    private void scanOnlinePlayers() {
        if (!violations.enabled(CheckType.EFFECT)) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (exempt(player)) {
                continue;
            }
            Set<String> exemptTypes = state(player).effects.exemptTypes;
            for (PotionEffect effect : player.getActivePotionEffects()) {
                if (!exemptTypes.contains(effect.getType().getKey().getKey())) {
                    evaluate(player, effect, "AKTIV");
                }
            }
        }
    }

    private void evaluate(Player player, PotionEffect effect, String cause) {
        String name = effect.getType().getKey().getKey();
        int maximumAmplifier = settings.integer(CheckType.EFFECT, "max-amplifier", 4);
        int maximumSeconds = settings.integer(CheckType.EFFECT, "max-duration-seconds", 3_600);
        boolean infinite = effect.getDuration() < 0;
        boolean allowInfinite = settings.bool(CheckType.EFFECT, "allow-infinite", false);

        String reason = null;
        if (effect.getAmplifier() > maximumAmplifier) {
            reason = name + " Stufe " + (effect.getAmplifier() + 1)
                + " > " + (maximumAmplifier + 1);
        } else if (infinite && !allowInfinite) {
            reason = name + " ohne Zeitlimit";
        } else if (!infinite && effect.getDuration() / 20 > maximumSeconds) {
            reason = name + " über " + (effect.getDuration() / 20) + "s > " + maximumSeconds + "s";
        }
        if (reason == null) {
            return;
        }

        violations.flag(
            player,
            CheckType.EFFECT,
            settings.decimal(CheckType.EFFECT, "violation-amount", 2.0),
            reason + " (" + cause + ")"
        );

        if (settings.bool(CheckType.EFFECT, "remove-illegal", true)) {
            plugin.getServer().getScheduler()
                .runTask(plugin, () -> player.removePotionEffect(effect.getType()));
        }
    }

    private Set<String> ignoredCauses() {
        Set<String> causes = new HashSet<>();
        for (String value : settings.strings(CheckType.EFFECT, "ignored-causes")) {
            causes.add(value.trim().toUpperCase(Locale.ROOT));
        }
        return causes;
    }
}
