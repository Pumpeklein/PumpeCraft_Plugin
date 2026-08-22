package de.pumpecraft.mod;

import java.util.Map;
import java.util.UUID;

record ModerationSnapshot(Map<UUID, BanRecord> bans, Map<UUID, MuteRecord> mutes) {
    ModerationSnapshot {
        bans = Map.copyOf(bans);
        mutes = Map.copyOf(mutes);
    }
}
