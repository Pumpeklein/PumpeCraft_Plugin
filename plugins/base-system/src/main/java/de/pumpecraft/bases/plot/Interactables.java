package de.pumpecraft.bases.plot;

import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.Tag;

/**
 * Blöcke, deren Rechtsklick etwas auslöst und deshalb unter die Benutzen-Flagge fällt.
 *
 * <p>{@code Material#isInteractable()} wäre der naheliegende Weg, ist aber abgekündigt und laut
 * eigener Beschreibung unvollständig - für einen Schutz, der über fremdes Eigentum entscheidet,
 * zu wenig.
 *
 * <p>Behälter und Betten fehlen hier bewusst: Beide haben eine eigene Flagge. Stünden sie auch in
 * dieser Menge, entschiede immer die strengere von zwei Flaggen - und wer das Schlafen erlaubt,
 * bekäme trotzdem ein verschlossenes Bett, ohne zu sehen, warum.
 */
public final class Interactables {
    private static final Set<Material> SINGLES = EnumSet.of(
        Material.LEVER,
        Material.NOTE_BLOCK,
        Material.JUKEBOX,
        Material.CRAFTING_TABLE,
        Material.ENCHANTING_TABLE,
        Material.BEACON,
        Material.BELL,
        Material.CAKE,
        Material.COMPARATOR,
        Material.REPEATER,
        Material.DAYLIGHT_DETECTOR,
        Material.DRAGON_EGG,
        Material.GRINDSTONE,
        Material.LECTERN,
        Material.LOOM,
        Material.CARTOGRAPHY_TABLE,
        Material.SMITHING_TABLE,
        Material.STONECUTTER,
        Material.RESPAWN_ANCHOR,
        Material.COMPOSTER,
        Material.CHISELED_BOOKSHELF,
        Material.DECORATED_POT,
        Material.LODESTONE,
        Material.SWEET_BERRY_BUSH
    );

    private Interactables() {
    }

    public static boolean isInteractable(Material material) {
        return SINGLES.contains(material)
            || Tag.DOORS.isTagged(material)
            || Tag.TRAPDOORS.isTagged(material)
            || Tag.FENCE_GATES.isTagged(material)
            || Tag.BUTTONS.isTagged(material)
            || Tag.CANDLES.isTagged(material)
            || Tag.CANDLE_CAKES.isTagged(material)
            || Tag.CAULDRONS.isTagged(material)
            || Tag.ANVIL.isTagged(material)
            || Tag.FLOWER_POTS.isTagged(material)
            || Tag.ALL_SIGNS.isTagged(material)
            || Tag.ALL_HANGING_SIGNS.isTagged(material);
    }
}
