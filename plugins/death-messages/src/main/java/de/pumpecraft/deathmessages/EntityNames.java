package de.pumpecraft.deathmessages;

import java.util.Locale;
import java.util.Map;
import org.bukkit.entity.EntityType;

/**
 * Deutsche Namen für alles, was einen Spieler umbringen kann. Ohne Artikel, wie Vanilla es auch
 * macht - "von Skelett erschossen" liest sich wie ein Name und passt damit in jede Vorlage,
 * egal ob der Platzhalter vorne oder hinten steht.
 *
 * <p>Geschlüsselt wird über den Namen des Typs statt über die Konstante: So kostet ein Mob, den
 * die API-Version noch nicht kennt, keinen Compilerfehler.
 */
final class EntityNames {
    private static final Map<String, String> NAMES = Map.ofEntries(
        Map.entry("zombie", "Zombie"),
        Map.entry("zombie_villager", "Zombiedorfbewohner"),
        Map.entry("husk", "Wüstenzombie"),
        Map.entry("drowned", "Ertrunkener"),
        Map.entry("skeleton", "Skelett"),
        Map.entry("stray", "Eiswanderer"),
        Map.entry("bogged", "Sumpfskelett"),
        Map.entry("wither_skeleton", "Witherskelett"),
        Map.entry("creeper", "Creeper"),
        Map.entry("spider", "Spinne"),
        Map.entry("cave_spider", "Höhlenspinne"),
        Map.entry("enderman", "Enderman"),
        Map.entry("endermite", "Endermite"),
        Map.entry("silverfish", "Silberfischchen"),
        Map.entry("witch", "Hexe"),
        Map.entry("slime", "Schleim"),
        Map.entry("magma_cube", "Magmawürfel"),
        Map.entry("blaze", "Lohe"),
        Map.entry("ghast", "Ghast"),
        Map.entry("phantom", "Phantom"),
        Map.entry("piglin", "Piglin"),
        Map.entry("piglin_brute", "Piglin-Barbar"),
        Map.entry("zombified_piglin", "Zombifizierter Piglin"),
        Map.entry("hoglin", "Hoglin"),
        Map.entry("zoglin", "Zoglin"),
        Map.entry("guardian", "Wächter"),
        Map.entry("elder_guardian", "Großer Wächter"),
        Map.entry("shulker", "Shulker"),
        Map.entry("pillager", "Plünderer"),
        Map.entry("vindicator", "Diener"),
        Map.entry("evoker", "Magier"),
        Map.entry("illusioner", "Illusionist"),
        Map.entry("vex", "Plagegeist"),
        Map.entry("ravager", "Verwüster"),
        Map.entry("warden", "Wärter"),
        Map.entry("breeze", "Bö"),
        Map.entry("creaking", "Knarzer"),
        Map.entry("wither", "Wither"),
        Map.entry("ender_dragon", "Enderdrache"),
        Map.entry("iron_golem", "Eisengolem"),
        Map.entry("snow_golem", "Schneegolem"),
        Map.entry("wolf", "Wolf"),
        Map.entry("polar_bear", "Eisbär"),
        Map.entry("panda", "Panda"),
        Map.entry("llama", "Lama"),
        Map.entry("trader_llama", "Händlerlama"),
        Map.entry("goat", "Ziege"),
        Map.entry("bee", "Biene"),
        Map.entry("dolphin", "Delfin"),
        Map.entry("pufferfish", "Kugelfisch"),
        Map.entry("fox", "Fuchs"),
        Map.entry("cat", "Katze"),
        Map.entry("ocelot", "Ozelot"),
        Map.entry("villager", "Dorfbewohner"),
        Map.entry("wandering_trader", "Fahrender Händler"),
        Map.entry("armadillo", "Gürteltier"),
        Map.entry("camel", "Kamel"),
        Map.entry("arrow", "Pfeil"),
        Map.entry("spectral_arrow", "Spektralpfeil"),
        Map.entry("trident", "Dreizack"),
        Map.entry("fireball", "Feuerkugel"),
        Map.entry("small_fireball", "Kleine Feuerkugel"),
        Map.entry("dragon_fireball", "Drachenfeuerkugel"),
        Map.entry("wither_skull", "Witherschädel"),
        Map.entry("shulker_bullet", "Shulkergeschoss"),
        Map.entry("llama_spit", "Lamaspucke"),
        Map.entry("snowball", "Schneeball"),
        Map.entry("egg", "Ei"),
        Map.entry("ender_pearl", "Enderperle"),
        Map.entry("firework_rocket", "Feuerwerksrakete"),
        Map.entry("tnt", "TNT"),
        Map.entry("tnt_minecart", "TNT-Lore"),
        Map.entry("lightning_bolt", "Blitz"),
        Map.entry("falling_block", "Fallender Block"),
        Map.entry("area_effect_cloud", "Effektwolke"),
        Map.entry("evoker_fangs", "Zauberzähne"),
        Map.entry("player", "Spieler")
    );

    private EntityNames() {
    }

    static String german(EntityType type) {
        String key = type.name().toLowerCase(Locale.ROOT);
        return NAMES.getOrDefault(key, readable(key));
    }

    private static String readable(String key) {
        StringBuilder builder = new StringBuilder();
        for (String part : key.split("_")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }
}
