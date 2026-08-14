package de.pumpecraft.skills;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Zähler eines eingeloggten Spielers im Speicher. Events laufen im Server-Thread,
 * das Speichern asynchron - deshalb ist der Zugriff durchgehend synchronisiert.
 */
final class PlayerSkillData {
    private final Map<StatKey, Long> values = new HashMap<>();
    private final Set<StatKey> dirty = new HashSet<>();

    PlayerSkillData(Map<StatKey, Long> loaded) {
        values.putAll(loaded);
    }

    synchronized void add(StatKey key, long delta) {
        addAndGet(key, delta);
    }

    synchronized long addAndGet(StatKey key, long delta) {
        if (delta == 0L) {
            return values.getOrDefault(key, 0L);
        }
        values.merge(key, delta, Long::sum);
        dirty.add(key);
        return values.get(key);
    }

    /** Setzt den Wert nur, wenn er kleiner als der bisherige ist (z. B. günstigster Trade). */
    synchronized void keepMinimum(StatKey key, long value) {
        Long current = values.get(key);
        if (current != null && current <= value) {
            return;
        }
        values.put(key, value);
        dirty.add(key);
    }

    synchronized long get(StatKey key) {
        return values.getOrDefault(key, 0L);
    }

    synchronized Map<StatKey, Long> allValues() {
        return new HashMap<>(values);
    }

    synchronized Map<String, Long> statsOf(Skill skill) {
        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<StatKey, Long> entry : values.entrySet()) {
            if (entry.getKey().skill() == skill) {
                result.put(entry.getKey().key(), entry.getValue());
            }
        }
        return result;
    }

    /** Nimmt die geänderten Werte heraus und leert die Dirty-Markierung. */
    synchronized Map<StatKey, Long> takeDirty() {
        if (dirty.isEmpty()) {
            return Map.of();
        }
        Map<StatKey, Long> snapshot = new HashMap<>();
        for (StatKey key : dirty) {
            snapshot.put(key, values.getOrDefault(key, 0L));
        }
        dirty.clear();
        return snapshot;
    }

    /** Markiert Werte erneut als ungespeichert, wenn das Schreiben fehlgeschlagen ist. */
    synchronized void markDirtyAgain(Map<StatKey, Long> failed) {
        dirty.addAll(failed.keySet());
    }
}
