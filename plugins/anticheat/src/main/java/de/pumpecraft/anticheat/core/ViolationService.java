package de.pumpecraft.anticheat.core;

import de.pumpecraft.anticheat.storage.AntiCheatEventRepository;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class ViolationService {
    private final Plugin plugin;
    private final PlayerStateStore states;
    private final CheckSettings settings;
    private final AlertDispatcher alerts;
    private final AntiCheatEventRepository eventRepository;

    public ViolationService(
        Plugin plugin,
        PlayerStateStore states,
        CheckSettings settings,
        AlertDispatcher alerts,
        AntiCheatEventRepository eventRepository
    ) {
        this.plugin = plugin;
        this.states = states;
        this.settings = settings;
        this.alerts = alerts;
        this.eventRepository = eventRepository;
    }

    public double flag(Player player, CheckType check, double amount, String detail) {
        if (!settings.enabled(check)) {
            return 0.0;
        }

        PlayerState state = states.get(player);
        double level = Math.min(maximumLevel(), state.violation(check) + amount);
        state.violations.put(check, level);

        if (level < settings.alertLevel(check)) {
            return level;
        }

        String platform = settings.platform(player);
        eventRepository.record(player, check, level, detail, platform);
        alerts.submit(player, check, level, detail, platform);
        return level;
    }

    public void reward(Player player, CheckType check, double amount) {
        states.get(player).violations
            .computeIfPresent(check, (ignored, level) -> Math.max(0.0, level - amount));
    }

    public boolean shouldCancel(Player player, CheckType check, double level) {
        return settings.enabled(check)
            && settings.hasCancelLevel(check)
            && level >= settings.cancelLevel(check);
    }

    public boolean enabled(CheckType check) {
        return settings.enabled(check);
    }

    public CheckSettings settings() {
        return settings;
    }

    public void decay() {
        double decay = Math.max(0.0, plugin.getConfig().getDouble("violations.decay-per-second", 0.08));
        if (decay <= 0.0) {
            return;
        }
        for (PlayerState state : states.all()) {
            state.violations.replaceAll((ignored, level) -> Math.max(0.0, level - decay));
        }
    }

    public void reset(UUID playerId) {
        PlayerState state = states.find(playerId);
        if (state != null) {
            state.violations.clear();
        }
        alerts.forget(playerId);
    }

    private double maximumLevel() {
        return Math.max(1.0, plugin.getConfig().getDouble("violations.maximum-level", 40.0));
    }
}
