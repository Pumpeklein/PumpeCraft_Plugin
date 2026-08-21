package de.pumpecraft.bases.plot;

import java.util.Locale;
import org.bukkit.Material;

/**
 * Schalter eines Grundstücks. Rollen regeln, was <em>Mitglieder</em> dürfen; Flaggen regeln, was
 * alle anderen und was die Welt selbst darf. Beides zu vermischen wäre der schnellste Weg zu einem
 * Grundstück, dessen Besitzer nicht mehr erklären kann, warum etwas geht oder nicht.
 *
 * <p>{@link Scope} trennt die beiden Sorten: Eine Flagge mit {@link Scope#STRANGERS} lässt
 * Mitglieder unberührt, eine mit {@link Scope#EVERYONE} gilt für alle - Tierschutz schützt sonst
 * nur vor Fremden, und genau das war nie gemeint.
 */
public enum PlotFlag {
    ENTRY("Betreten", "Dürfen Fremde das Grundstück betreten?",
        true, Scope.STRANGERS, false, Material.OAK_DOOR),
    PUBLIC_BUILD("Bauen", "Dürfen Fremde Blöcke setzen und abbauen?",
        false, Scope.STRANGERS, false, Material.DIAMOND_PICKAXE),
    PUBLIC_INTERACT("Benutzen", "Dürfen Fremde Türen, Knöpfe und Hebel benutzen?",
        true, Scope.STRANGERS, false, Material.LEVER),
    CONTAINERS("Behälter", "Dürfen Fremde Kisten, Öfen und Fässer öffnen?",
        false, Scope.STRANGERS, false, Material.CHEST),
    SLEEPING("Schlafen", "Dürfen Fremde hier in einem Bett schlafen?",
        true, Scope.STRANGERS, false, Material.RED_BED),
    PVP("PvP", "Dürfen sich Spieler hier gegenseitig verletzen?",
        false, Scope.EVERYONE, true, Material.IRON_SWORD),
    // Der Name sagt, was erlaubt ist, nicht was geschützt wird: "Tierschutz an" liest sich wie
    // Schutz, hieße in diesem Modell aber erlaubter Schaden. Genau daran gehen solche Flaggen kaputt.
    ANIMAL_DAMAGE("Tiere verletzen", "Dürfen Tiere hier verletzt werden - auch von dir selbst?",
        false, Scope.EVERYONE, false, Material.BONE),
    MOB_SPAWNING("Monster", "Sollen hier Monster erscheinen?",
        true, Scope.EVERYONE, false, Material.ZOMBIE_HEAD),
    MOB_GRIEFING("Monsterschäden", "Dürfen Creeper und Endermen Blöcke verändern?",
        false, Scope.EVERYONE, false, Material.CREEPER_HEAD),
    EXPLOSIONS("Explosionen", "Dürfen Explosionen hier Blöcke zerstören?",
        false, Scope.EVERYONE, false, Material.TNT),
    FIRE_SPREAD("Feuer", "Darf sich Feuer hier ausbreiten?",
        false, Scope.EVERYONE, false, Material.FLINT_AND_STEEL),
    TRAMPLE("Ackerland", "Darf Ackerland zertreten werden?",
        false, Scope.EVERYONE, false, Material.FARMLAND),
    FALLING_BLOCKS("Fallende Blöcke", "Dürfen Sand, Kies und Ambosse hier fallen?",
        true, Scope.EVERYONE, false, Material.SAND),
    ICE_MELT("Eisschmelze", "Darf Eis hier schmelzen?",
        true, Scope.EVERYONE, false, Material.ICE),
    SNOW_FORM("Schnee und Eis", "Darf sich hier Schnee legen und Wasser gefrieren?",
        true, Scope.EVERYONE, false, Material.SNOW_BLOCK),
    CORAL_DECAY("Korallen", "Dürfen Korallen hier absterben?",
        true, Scope.EVERYONE, false, Material.BRAIN_CORAL_BLOCK),
    LEAF_DECAY("Laub", "Darf Laub hier verrotten?",
        true, Scope.EVERYONE, false, Material.OAK_LEAVES);

    private final String displayName;
    private final String description;
    private final boolean defaultValue;
    private final Scope scope;
    private final boolean staffOnly;
    private final Material icon;

    PlotFlag(
        String displayName,
        String description,
        boolean defaultValue,
        Scope scope,
        boolean staffOnly,
        Material icon
    ) {
        this.displayName = displayName;
        this.description = description;
        this.defaultValue = defaultValue;
        this.scope = scope;
        this.staffOnly = staffOnly;
        this.icon = icon;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public boolean defaultValue() {
        return defaultValue;
    }

    public Scope scope() {
        return scope;
    }

    /** PvP steht auf jedem Spielergrundstück aus und bleibt allein Sache des Teams. */
    public boolean staffOnly() {
        return staffOnly;
    }

    public Material icon() {
        return icon;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static PlotFlag byId(String id) {
        if (id == null) {
            return null;
        }
        String wanted = id.replace('-', '_');
        for (PlotFlag flag : values()) {
            if (flag.name().equalsIgnoreCase(wanted) || flag.displayName.equalsIgnoreCase(id)) {
                return flag;
            }
        }
        return null;
    }

    public enum Scope {
        /** Mitglieder dürfen ohnehin; die Flagge entscheidet nur über alle anderen. */
        STRANGERS,
        /** Gilt für jeden, auch für den Besitzer - eine Eigenschaft des Ortes, keine Erlaubnis. */
        EVERYONE
    }
}
