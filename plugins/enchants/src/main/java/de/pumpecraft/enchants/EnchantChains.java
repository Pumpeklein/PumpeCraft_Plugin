package de.pumpecraft.enchants;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Marks the moment a chain enchantment breaks blocks for a player. Two very different readers need
 * it: the mining rules, so their own break events do not chain again, and PumpeAntiCheat, whose
 * reach and break-rate checks would otherwise flag a legitimate vein.
 */
public final class EnchantChains {
    private final Set<UUID> active = new HashSet<>();

    public boolean active(UUID playerId) {
        return active.contains(playerId);
    }

    public void open(UUID playerId) {
        active.add(playerId);
    }

    public void close(UUID playerId) {
        active.remove(playerId);
    }
}
