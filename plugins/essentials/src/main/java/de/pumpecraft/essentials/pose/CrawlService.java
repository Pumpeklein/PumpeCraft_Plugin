package de.pumpecraft.essentials.pose;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;

/**
 * Krabbeln besteht aus zwei Hälften. Die feste {@link Pose#SWIMMING} regelt, was der Server und
 * alle anderen Spieler sehen. Damit der Spieler selbst krabbelt, bekommt nur sein Client eine
 * {@link CrawlBox} über den Kopf gesetzt: Erst dann verweigert ihm die eigene Spiellogik das
 * Aufstehen und lässt ihn durch einen Block hohe Lücken.
 */
public final class CrawlService {
    private final Map<UUID, CrawlBox> boxes = new HashMap<>();

    public boolean isCrawling(Player player) {
        return boxes.containsKey(player.getUniqueId());
    }

    public boolean start(Player player) {
        if (boxes.containsKey(player.getUniqueId())) {
            return false;
        }
        CrawlBox box = CrawlBox.spawn(player);
        if (box == null) {
            return false;
        }
        boxes.put(player.getUniqueId(), box);
        player.setPose(Pose.SWIMMING, true);
        return true;
    }

    public void stop(Player player) {
        CrawlBox box = boxes.remove(player.getUniqueId());
        if (box == null) {
            return;
        }
        box.hide();
        player.setPose(Pose.STANDING, false);
    }

    /** Der Spieler verschwindet, ohne dass sein Client noch etwas empfangen müsste. */
    public void forget(Player player) {
        boxes.remove(player.getUniqueId());
    }

    /**
     * Die Decke hängt an der genauen Standhöhe, nicht am Blockraster - ein Wechsel von einer
     * Stufe auf den vollen Block daneben verschiebt sie, ohne dass sich der Block unter den
     * Füßen ändert. Deshalb wird bei jeder Positionsänderung neu gerechnet und nur beim
     * tatsächlichen Wechsel ein Paket geschickt.
     */
    public void follow(Player player, Location destination) {
        CrawlBox box = boxes.get(player.getUniqueId());
        if (box != null) {
            box.follow(destination);
        }
    }

    public void clear() {
        if (boxes.isEmpty()) {
            return;
        }
        for (UUID crawler : List.copyOf(boxes.keySet())) {
            Player player = Bukkit.getPlayer(crawler);
            if (player == null) {
                boxes.remove(crawler);
            } else {
                stop(player);
            }
        }
    }
}
