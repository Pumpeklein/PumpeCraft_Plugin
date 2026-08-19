package de.pumpecraft.essentials.pose;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;

/**
 * Krabbeln besteht aus zwei Hälften. Die feste {@link Pose#SWIMMING} regelt, was der Server und
 * alle anderen Spieler sehen. Damit der Spieler selbst krabbelt, bekommt nur sein Client einen
 * Block über den Kopf gesetzt: Erst dann verweigert ihm die eigene Spiellogik das Aufstehen und
 * lässt ihn durch einen Block hohe Lücken.
 */
public final class CrawlService {
    private final PoseSettings settings;
    private final Set<UUID> crawlers = new HashSet<>();
    private final Map<UUID, Block> covers = new HashMap<>();

    public CrawlService(PoseSettings settings) {
        this.settings = settings;
    }

    public boolean isCrawling(Player player) {
        return crawlers.contains(player.getUniqueId());
    }

    public boolean start(Player player) {
        if (!crawlers.add(player.getUniqueId())) {
            return false;
        }
        player.setPose(Pose.SWIMMING, true);
        cover(player, player.getLocation());
        return true;
    }

    public void stop(Player player) {
        if (!crawlers.remove(player.getUniqueId())) {
            return;
        }
        uncover(player);
        player.setPose(Pose.STANDING, false);
    }

    /** Der Spieler verschwindet, ohne dass sein Client noch etwas empfangen müsste. */
    public void forget(Player player) {
        crawlers.remove(player.getUniqueId());
        covers.remove(player.getUniqueId());
    }

    public void follow(Player player, Location destination) {
        if (!crawlers.contains(player.getUniqueId())) {
            return;
        }
        if (headBlock(destination).equals(covers.get(player.getUniqueId()))) {
            return;
        }
        uncover(player);
        cover(player, destination);
    }

    public void clear() {
        for (UUID crawler : List.copyOf(crawlers)) {
            Player player = Bukkit.getPlayer(crawler);
            if (player == null) {
                forget(crawler);
            } else {
                stop(player);
            }
        }
    }

    private void cover(Player player, Location location) {
        Block head = headBlock(location);
        if (!head.isPassable() || head.isLiquid()) {
            return;
        }
        player.sendBlockChange(head.getLocation(), settings.crawlCover());
        covers.put(player.getUniqueId(), head);
    }

    private void uncover(Player player) {
        Block covered = covers.remove(player.getUniqueId());
        // Nach einem Weltwechsel lädt der Client die alten Chunks ohnehin neu; ein Paket
        // mit fremden Koordinaten würde dort einen echten Block überschreiben.
        if (covered != null && covered.getWorld().equals(player.getWorld())) {
            player.sendBlockChange(covered.getLocation(), covered.getBlockData());
        }
    }

    private void forget(UUID playerId) {
        crawlers.remove(playerId);
        covers.remove(playerId);
    }

    private static Block headBlock(Location location) {
        return location.getBlock().getRelative(BlockFace.UP);
    }
}
