package de.pumpecraft.anticheat;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

final class ViolationService {
    private final PumpeAntiCheatPlugin plugin;
    private final PlayerStateStore states;
    private final BedrockDetector bedrockDetector;

    ViolationService(
        PumpeAntiCheatPlugin plugin,
        PlayerStateStore states,
        BedrockDetector bedrockDetector
    ) {
        this.plugin = plugin;
        this.states = states;
        this.bedrockDetector = bedrockDetector;
    }

    double flag(Player player, CheckType check, double amount, String detail) {
        if (!enabled(check)) {
            return 0.0;
        }

        PlayerState state = states.get(player);
        double level = state.violation(check) + amount;
        state.violations.put(check, level);

        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("violations.alert-cooldown-millis", 1200L);
        long lastAlert = state.lastAlerts.getOrDefault(check, 0L);
        double alertLevel = plugin.getConfig().getDouble(
            "checks." + check.configKey() + ".alert-level",
            1.0
        );
        if (level >= alertLevel && now - lastAlert >= cooldown) {
            state.lastAlerts.put(check, now);
            sendAlert(player, check, level, detail);
        }
        return level;
    }

    void reward(Player player, CheckType check, double amount) {
        PlayerState state = states.get(player);
        state.violations.computeIfPresent(check, (ignored, level) -> Math.max(0.0, level - amount));
    }

    boolean shouldCancel(Player player, CheckType check, double level) {
        String path = "checks." + check.configKey() + ".cancel-level";
        return enabled(check)
            && plugin.getConfig().contains(path)
            && level >= plugin.getConfig().getDouble(path);
    }

    boolean enabled(CheckType check) {
        return plugin.getConfig().getBoolean("checks." + check.configKey() + ".enabled", true);
    }

    void decay() {
        double decay = Math.max(0.0, plugin.getConfig().getDouble("violations.decay-per-second", 0.08));
        for (PlayerState state : states.all()) {
            state.violations.replaceAll((ignored, level) -> Math.max(0.0, level - decay));
        }
    }

    void reset(java.util.UUID playerId) {
        states.reset(playerId);
    }

    private void sendAlert(Player suspect, CheckType check, double level, String detail) {
        String platform = bedrockDetector.isBedrock(suspect.getUniqueId()) ? "Bedrock" : "Java";
        Component alert = Component.text("[AntiCheat] ", NamedTextColor.RED)
            .append(Component.text(suspect.getName(), NamedTextColor.YELLOW))
            .append(Component.text(" löst " + check.displayName(), NamedTextColor.GRAY))
            .append(Component.text(
                " (VL " + String.format(Locale.ROOT, "%.1f", level) + ", " + platform + ")",
                NamedTextColor.DARK_GRAY
            ))
            .append(Component.text(" - " + detail, NamedTextColor.GRAY));

        plugin.getLogger().warning(
            suspect.getName() + " failed " + check.displayName() + " at VL "
                + String.format(Locale.ROOT, "%.1f", level) + ": " + detail
        );
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (plugin.getCommand("anticheat").testPermissionSilent(staff)) {
                staff.sendMessage(alert);
            }
        }
    }
}
