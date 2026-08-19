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
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;

/**
 * Krabbeln besteht aus zwei Hälften. Die feste {@link Pose#SWIMMING} regelt, was der Server und
 * alle anderen Spieler sehen. Damit der Spieler selbst krabbelt, bekommt nur sein Client einen
 * {@link CrawlCover} über den Kopf gesetzt: Erst dann verweigert ihm die eigene Spiellogik das
 * Aufstehen und lässt ihn durch einen Block hohe Lücken.
 */
public final class CrawlService {
    private final PoseSettings settings;
    private final Set<UUID> crawlers = new HashSet<>();
    private final Map<UUID, CrawlCover> covers = new HashMap<>();

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
        showCover(player, CrawlCover.at(player, player.getLocation(), settings.crawlCover()));
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
        CrawlCover target = CrawlCover.at(player, destination, settings.crawlCover());
        if (Objects.equals(covers.get(player.getUniqueId()), target)) {
            return;
        }
        uncover(player);
        showCover(player, target);
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

    private void showCover(Player player, CrawlCover target) {
        if (target == null) {
            return;
        }
        player.sendBlockChange(target.block().getLocation(), target.blockData());
        covers.put(player.getUniqueId(), target);
    }

    private void uncover(Player player) {
        CrawlCover covered = covers.remove(player.getUniqueId());
        // Nach einem Weltwechsel lädt der Client die alten Chunks ohnehin neu; ein Paket
        // mit fremden Koordinaten würde dort einen echten Block überschreiben.
        if (covered != null && covered.block().getWorld().equals(player.getWorld())) {
            player.sendBlockChange(covered.block().getLocation(), covered.block().getBlockData());
        }
    }

    private void forget(UUID playerId) {
        crawlers.remove(playerId);
        covers.remove(playerId);
    }
}
