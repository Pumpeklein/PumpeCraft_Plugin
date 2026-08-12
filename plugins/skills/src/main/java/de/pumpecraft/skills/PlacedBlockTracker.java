package de.pumpecraft.skills;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.block.Block;

/**
 * Merkt sich von Spielern platzierte Blöcke, damit die Schleife
 * "Block setzen, wieder abbauen" keine Miner- oder Farmer-Punkte bringt.
 *
 * <p>Nur im Speicher und pro Welt auf {@link #MAX_ENTRIES_PER_WORLD} begrenzt;
 * die ältesten Einträge fallen zuerst raus. Nach einem Neustart ist die Liste
 * leer - das ist bewusst so, weil ein dauerhafter Blockindex teuer wäre und der
 * Schutz nur die schnelle Wiederholungsschleife verhindern soll.
 *
 * <p>Alle Zugriffe passieren in Block-Events und damit im Server-Thread.
 */
final class PlacedBlockTracker {
    private static final int MAX_ENTRIES_PER_WORLD = 250_000;
    private static final long Y_OFFSET = 2048L;

    private final Map<UUID, LinkedHashMap<Long, UUID>> worlds = new HashMap<>();

    void mark(Block block, UUID placer) {
        worlds
            .computeIfAbsent(block.getWorld().getUID(), key -> newWorldMap())
            .put(pack(block), placer);
    }

    /** Entfernt den Block aus dem Index und liefert den Spieler, der ihn gesetzt hat. */
    UUID release(Block block) {
        LinkedHashMap<Long, UUID> placed = worlds.get(block.getWorld().getUID());
        return placed == null ? null : placed.remove(pack(block));
    }

    void clear() {
        worlds.clear();
    }

    private LinkedHashMap<Long, UUID> newWorldMap() {
        return new LinkedHashMap<>(1024, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, UUID> eldest) {
                return size() > MAX_ENTRIES_PER_WORLD;
            }
        };
    }

    /** Packt die Blockkoordinaten in einen long: 26 Bit X, 26 Bit Z, 12 Bit Y. */
    private static long pack(Block block) {
        long x = block.getX() & 0x3FFFFFFL;
        long z = block.getZ() & 0x3FFFFFFL;
        long y = (block.getY() + Y_OFFSET) & 0xFFFL;
        return (x << 38) | (z << 12) | y;
    }
}
