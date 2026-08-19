package de.pumpecraft.essentials.pose;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;

/**
 * Krabbeln besteht aus zwei Hälften. Die feste {@link Pose#SWIMMING} regelt, was der Server und
 * alle anderen Spieler sehen. Damit der Spieler selbst krabbelt, bekommt nur sein Client einen
 * Block über den Kopf gesetzt: Erst dann verweigert ihm die eigene Spiellogik das Aufstehen und
 * lässt ihn durch einen Block hohe Lücken.
 */
public final class CrawlService {
    private static final double CRAWL_HEIGHT = 0.6D;
    private static final double STANDING_HEIGHT = 1.8D;

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
        cover(player, coverBlock(player, player.getLocation()));
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
        forget(player.getUniqueId());
    }

    /**
     * Die Deckposition hängt an der genauen Standhöhe, nicht am Blockraster - ein Wechsel von
     * einer Stufe auf einen vollen Block verschiebt sie, ohne dass sich der Block unter den
     * Füßen ändert. Deshalb wird bei jeder Positionsänderung neu gerechnet und nur beim
     * tatsächlichen Wechsel ein Paket geschickt.
     */
    public void follow(Player player, Location destination) {
        if (!crawlers.contains(player.getUniqueId())) {
            return;
        }
        Block target = coverBlock(player, destination);
        if (Objects.equals(covers.get(player.getUniqueId()), target)) {
            return;
        }
        uncover(player);
        cover(player, target);
    }

    public void clear() {
        if (crawlers.isEmpty()) {
            return;
        }
        for (UUID crawler : List.copyOf(crawlers)) {
            Player player = Bukkit.getPlayer(crawler);
            if (player == null) {
                forget(crawler);
            } else {
                stop(player);
            }
        }
    }

    /**
     * Der Deckel muss über die Krabbelhöhe und unter die Standhöhe passen, sonst steckt er im
     * Spieler und drückt ihn heraus. Beide Höhen hängen an {@link Attribute#SCALE}: Ein auf 0.4
     * verkleinerter Spieler ist stehend nur 0.72 hoch, für ihn gibt es meist gar keine gültige
     * Blockposition. Dann bleibt es bei der Pose - für seinen eigenen Client ohne Wirkung, aber
     * ohne ihn durch die Gegend zu schieben.
     */
    private Block coverBlock(Player player, Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        double scale = scale(player);
        double feet = location.getY();
        int y = (int) Math.floor(feet + CRAWL_HEIGHT * scale) + 1;
        if (y > feet + STANDING_HEIGHT * scale || y >= world.getMaxHeight()) {
            return null;
        }
        Block block = world.getBlockAt(location.getBlockX(), y, location.getBlockZ());
        return block.isPassable() && !block.isLiquid() ? block : null;
    }

    private void cover(Player player, Block target) {
        if (target == null) {
            return;
        }
        player.sendBlockChange(target.getLocation(), settings.crawlCover());
        covers.put(player.getUniqueId(), target);
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

    private static double scale(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.SCALE);
        return attribute == null ? 1.0D : attribute.getValue();
    }
}
