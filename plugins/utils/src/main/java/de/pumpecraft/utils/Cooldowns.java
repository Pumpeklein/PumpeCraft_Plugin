package de.pumpecraft.utils;

import java.util.HashMap;
import java.util.Map;

public final class Cooldowns<K> {
    private final Map<K, Long> expiry = new HashMap<>();

    public boolean tryAcquire(K key, long durationMillis) {
        long now = System.currentTimeMillis();
        Long until = expiry.get(key);
        if (until != null && until > now) {
            return false;
        }
        expiry.put(key, now + durationMillis);
        return true;
    }

    public boolean active(K key) {
        Long until = expiry.get(key);
        return until != null && until > System.currentTimeMillis();
    }

    public long remainingMillis(K key) {
        Long until = expiry.get(key);
        return until == null ? 0L : Math.max(0L, until - System.currentTimeMillis());
    }

    public void clear(K key) {
        expiry.remove(key);
    }

    public void clearAll() {
        expiry.clear();
    }

    public void purgeExpired() {
        long now = System.currentTimeMillis();
        expiry.values().removeIf(until -> until <= now);
    }
}
