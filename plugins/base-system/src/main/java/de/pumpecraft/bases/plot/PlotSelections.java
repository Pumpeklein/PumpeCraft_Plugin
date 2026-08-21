package de.pumpecraft.bases.plot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Die beiden gesetzten Ecken je Spieler, bis er kauft oder den Server verlässt. */
public final class PlotSelections {
    private final Map<UUID, Selection> selections = new HashMap<>();

    public void first(Player player, Location corner) {
        selections.compute(player.getUniqueId(), (ignored, current) ->
            with(current, corner, true));
    }

    public void second(Player player, Location corner) {
        selections.compute(player.getUniqueId(), (ignored, current) ->
            with(current, corner, false));
    }

    public Selection of(Player player) {
        return selections.get(player.getUniqueId());
    }

    public void clear(Player player) {
        selections.remove(player.getUniqueId());
    }

    /**
     * Die Auswahl ist erst mit zwei Ecken in derselben Welt vollständig; eine Ecke in einer
     * anderen Welt ersetzt deshalb die ganze Auswahl statt sie zu ergänzen.
     */
    private Selection with(Selection current, Location corner, boolean first) {
        Location kept = current == null ? null : first ? current.second() : current.first();
        if (kept != null && !kept.getWorld().equals(corner.getWorld())) {
            kept = null;
        }
        return first
            ? new Selection(corner.clone(), kept)
            : new Selection(kept, corner.clone());
    }

    public record Selection(Location first, Location second) {
        public boolean complete() {
            return first != null && second != null;
        }

        public PlotArea area() {
            return complete() ? PlotArea.between(first, second) : null;
        }
    }
}
