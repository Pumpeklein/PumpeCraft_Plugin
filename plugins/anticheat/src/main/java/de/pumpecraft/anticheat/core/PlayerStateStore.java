package de.pumpecraft.anticheat.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

public final class PlayerStateStore {
    private final Map<UUID, PlayerState> states = new HashMap<>();

    public PlayerState get(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerState());
    }

    public PlayerState find(UUID playerId) {
        return states.get(playerId);
    }

    public Collection<PlayerState> all() {
        return states.values();
    }

    public void remove(UUID playerId) {
        states.remove(playerId);
    }
}
