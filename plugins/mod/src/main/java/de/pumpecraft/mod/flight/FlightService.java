package de.pumpecraft.mod.flight;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public final class FlightService {
    private final Map<UUID, FlightState> states = new HashMap<>();

    public boolean isEnabled(Player player) {
        return states.containsKey(player.getUniqueId());
    }

    public boolean toggle(Player player) {
        if (isEnabled(player)) {
            disable(player);
            return false;
        }
        states.put(player.getUniqueId(), new FlightState(player.getAllowFlight(), player.isFlying()));
        player.setAllowFlight(true);
        return true;
    }

    public void disable(Player player) {
        FlightState state = states.remove(player.getUniqueId());
        if (state == null || hasNativeFlight(player.getGameMode())) return;

        if (!state.allowFlight()) player.setFlying(false);
        player.setAllowFlight(state.allowFlight());
        player.setFlying(state.flying() && player.getAllowFlight());
    }

    public void refresh(Player player) {
        if (isEnabled(player) && !hasNativeFlight(player.getGameMode())) {
            player.setAllowFlight(true);
        }
    }

    public void shutdown() {
        for (UUID playerId : states.keySet().toArray(UUID[]::new)) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) disable(player);
        }
        states.clear();
    }

    private boolean hasNativeFlight(GameMode gameMode) {
        return gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR;
    }

    private record FlightState(boolean allowFlight, boolean flying) {
    }
}
