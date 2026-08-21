package de.pumpecraft.enchants.mining;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.bukkit.block.Block;

/** Collects the blocks connected to an origin, breadth first, so the nearest ones come first. */
final class VeinMiner {
    List<Block> collect(Block origin, Predicate<Block> matches, int limit) {
        List<Block> found = new ArrayList<>();
        if (limit <= 0) {
            return found;
        }

        Set<Block> seen = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);
        while (!queue.isEmpty() && found.size() < limit) {
            Block current = queue.poll();
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        Block neighbour = current.getRelative(x, y, z);
                        if (!seen.add(neighbour) || !matches.test(neighbour)) {
                            continue;
                        }
                        found.add(neighbour);
                        queue.add(neighbour);
                        if (found.size() >= limit) {
                            return found;
                        }
                    }
                }
            }
        }
        return found;
    }
}
