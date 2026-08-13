package de.pumpecraft.anticheat;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

final class PlayerStateStore {
    private final Map<UUID, PlayerState> states = new HashMap<>();

    PlayerState get(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerState());
    }

    PlayerState find(UUID playerId) {
        return states.get(playerId);
    }

    Collection<PlayerState> all() {
        return states.values();
    }

    void remove(UUID playerId) {
        states.remove(playerId);
    }

    void reset(UUID playerId) {
        states.remove(playerId);
    }
}
