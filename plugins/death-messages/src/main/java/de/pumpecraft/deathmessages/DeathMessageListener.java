package de.pumpecraft.deathmessages;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.projectiles.ProjectileSource;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class DeathMessageListener implements Listener {
    private final DeathCounterRepository repository;
    private final DeathMessageLibrary library = new DeathMessageLibrary();
    private final Random random = new Random();
    private String lastTemplate = "";

    public DeathMessageListener(DeathCounterRepository repository) {
        this.repository = repository;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        int deathCount = repository.incrementDeaths(player.getUniqueId(), dimension(player));
        DeathContext context = createContext(player, deathCount);

        String template = chooseTemplate(context);
        lastTemplate = template;

        event.deathMessage(Component.text(render(template, context), NamedTextColor.GRAY));
    }

    private String dimension(Player player) {
        return switch (player.getWorld().getEnvironment()) {
            case NETHER -> "NETHER";
            case THE_END -> "END";
            default -> "OVERWORLD";
        };
    }

    private DeathContext createContext(Player player, int deathCount) {
        EntityDamageEvent damageEvent = player.getLastDamageCause();
        DamageCause cause = damageEvent == null ? DamageCause.CUSTOM : damageEvent.getCause();
        String killerName = findKillerName(damageEvent);

        return new DeathContext(player.getName(), killerName, deathCount, cause);
    }

    private String findKillerName(EntityDamageEvent damageEvent) {
        if (!(damageEvent instanceof EntityDamageByEntityEvent entityDamageEvent)) {
            return "das Universum";
        }

        Entity damager = entityDamageEvent.getDamager();
        if (damager instanceof Player player) {
            return player.getName();
        }

        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player.getName();
            }
        }

        Component customNameComponent = damager.customName();
        String customName = customNameComponent == null ? null : PlainTextComponentSerializer.plainText().serialize(customNameComponent);
        return customName == null || customName.isBlank() ? readableEntityName(damager) : customName;
    }

    private String readableEntityName(Entity entity) {
        String[] parts = entity.getType().name().toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private String chooseTemplate(DeathContext context) {
        List<String> templates = new ArrayList<>();
        if (context.deathCount() % 5 == 0) {
            templates.addAll(library.milestones());
        } else {
            templates.addAll(library.messagesFor(context.cause()));
        }

        templates.removeIf(template -> Objects.equals(template, lastTemplate));
        if (templates.isEmpty()) {
            templates = context.deathCount() % 5 == 0
                ? library.milestones()
                : library.messagesFor(context.cause());
        }

        return templates.get(random.nextInt(templates.size()));
    }

    private String render(String template, DeathContext context) {
        return template
            .replace("{player}", context.playerName())
            .replace("{killer}", context.killerName())
            .replace("{deaths}", String.valueOf(context.deathCount()))
            .replace("{previousDeaths}", String.valueOf(context.deathCount() - 1));
    }

    private record DeathContext(String playerName, String killerName, int deathCount, DamageCause cause) {
    }

    private static final class DeathMessageLibrary {
        private final Map<DamageCause, List<String>> messages = new EnumMap<>(DamageCause.class);
        private final List<String> milestones = List.of(
            "{player} ist zum {deaths}. Mal gestorben und nennt das vermutlich Training.",
            "{player} hat nach {previousDeaths} Toden immer noch keinen Lernprozess gestartet.",
            "{player} sammelt Tode wie andere Leute Dias. Stand: {deaths}.",
            "{player} beweist zum {deaths}. Mal, dass Optimismus keine Rüstung ersetzt.",
            "{player} ist wieder tot. Ab {deaths} nennen wir es Tradition.",
            "{player} hat {deaths} Tode erreicht. Der Respawn-Button kennt den Namen schon.",
            "{player} macht aus Sterben eine Karriere. Aktueller Rekord: {deaths}.",
            "{player} hat den Tod erneut getestet. Ergebnis: funktioniert noch."
        );

        @SuppressWarnings("deprecation")
        DeathMessageLibrary() {
            put(DamageCause.KILL,
                "{player} wurde per Admin-Finger aus der Existenz geschnipst.",
                "{player} hat den unsichtbaren Hammer der Gerechtigkeit kassiert.",
                "{player} wurde gelöscht. Papierkorb wurde nicht gefunden.",
                "{player} bekam ein sehr direktes Nein vom Server.",
                "{player} wurde technisch gesehen fachgerecht entfernt."
            );
            put(DamageCause.WORLD_BORDER,
                "{player} dachte, Grenzen gelten nur für andere.",
                "{player} hat mit der Worldborder diskutiert und verloren.",
                "{player} wollte die Karte verlassen. Die Karte war dagegen.",
                "{player} wurde von der roten Linie pädagogisch korrigiert.",
                "{player} fand heraus, dass Weltgrenzen keine Deko sind."
            );
            put(DamageCause.CONTACT,
                "{player} hat einen Kaktus umarmt. Der Kaktus fand es komisch.",
                "{player} hat Stacheln für Dekoration gehalten.",
                "{player} verlor gegen eine Pflanze. Botanisch peinlich.",
                "{player} wurde von Grünzeug auseinandergenommen.",
                "{player} hat den Kaktus angefasst und die Quittung bekommen."
            );
            put(DamageCause.ENTITY_ATTACK,
                "{player} wurde von {killer} zurück in die Lobby philosophiert.",
                "{killer} hat {player} sehr überzeugend widersprochen.",
                "{player} fand {killer}s Argumente schlagkräftig.",
                "{killer} hat {player} gezeigt, wo der Respawn wohnt.",
                "{player} wurde von {killer} handlich zusammengefaltet."
            );
            put(DamageCause.ENTITY_SWEEP_ATTACK,
                "{player} stand im Schwung von {killer}. Taktisch eher unklug.",
                "{killer} hat {player} mit einem Rundumschlag aussortiert.",
                "{player} wurde von {killer}s Sweep mitgenommen wie Kleingeld.",
                "{killer} hat {player} nebenbei weggewischt.",
                "{player} lernte: Abstand ist keine Theorie."
            );
            put(DamageCause.PROJECTILE,
                "{player} wurde von {killer} geliefert. Per Luftpost.",
                "{killer} traf {player}; {player} traf den Boden der Tatsachen.",
                "{player} hat ein Projektil mit dem Gesicht gefangen.",
                "{killer} hat {player} aus der Ferne abbestellt.",
                "{player} entdeckte, dass Pfeile schneller sind als Ausreden."
            );
            put(DamageCause.SUFFOCATION,
                "{player} hat Wände zu persönlich genommen.",
                "{player} steckte fest und machte daraus ein Finale.",
                "{player} wurde von einem Block eng betreut.",
                "{player} hat vergessen, dass Atmen nicht optional ist.",
                "{player} verschwand in der Wand wie ein schlechter Zaubertrick."
            );
            put(DamageCause.FALL,
                "{player} testete Gravitation. Gravitation gewann.",
                "{player} verwechselte Mut mit fehlenden Treppen.",
                "{player} nahm den schnellen Weg nach unten. Einmalig.",
                "{player} fiel tief, aber nicht elegant.",
                "{player} hat den Boden frontal begrüßt."
            );
            put(DamageCause.FIRE,
                "{player} wurde knusprig, aber niemand hatte Hunger.",
                "{player} hat Feuer unterschätzt. Klassiker.",
                "{player} ging in Flammen auf und nannte es Stimmung.",
                "{player} wurde gegrillt, ohne eingeladen zu sein.",
                "{player} fand heraus: Feuer ist heiß. Überraschung."
            );
            put(DamageCause.FIRE_TICK,
                "{player} brannte weiter und dachte wohl, das geht von selbst weg.",
                "{player} ignorierte die Flammen zu lange.",
                "{player} wurde langsam aber sicher geröstet.",
                "{player} hat das Löschen verschoben. Für immer.",
                "{player} wurde vom Nachbrennen erledigt."
            );
            put(DamageCause.MELTING,
                "{player} ist geschmolzen wie Selbstvertrauen im Nether.",
                "{player} wurde flüssig beeindruckt.",
                "{player} hat Hitze nicht gut verarbeitet.",
                "{player} verwandelte sich in eine schlechte Pfütze.",
                "{player} löste sich thermisch aus der Runde."
            );
            put(DamageCause.LAVA,
                "{player} nahm ein Bad in Lava. Wellness war es nicht.",
                "{player} wurde von Lava umarmt und nicht losgelassen.",
                "{player} dachte, Orange bedeutet freundlich.",
                "{player} fand den schärfsten Whirlpool des Servers.",
                "{player} wurde flüssig gegrillt."
            );
            put(DamageCause.DROWNING,
                "{player} hat Wasser geatmet. Schlechte Idee.",
                "{player} wurde vom Ozean aus dem Chat entfernt.",
                "{player} vergaß, dass Luft ein Feature ist.",
                "{player} ging unter und blieb beim Fehler.",
                "{player} hat Schwimmen mit Sterben verwechselt."
            );
            put(DamageCause.BLOCK_EXPLOSION,
                "{player} wurde von Blöcken in Einzelteile diskutiert.",
                "{player} stand zu nah an der falschen Idee.",
                "{player} bekam Architektur mit Nachdruck.",
                "{player} wurde vom Gelände neu sortiert.",
                "{player} hat Explosionen als Einladung verstanden."
            );
            put(DamageCause.ENTITY_EXPLOSION,
                "{player} wurde spektakulär weggesprengt.",
                "{player} traf eine Explosion und verlor das Gespräch.",
                "{player} wurde von TNT-Logik überzeugt.",
                "{player} stand da, wo gleich nichts mehr stand.",
                "{player} wurde in Partikelform verabschiedet."
            );
            put(DamageCause.VOID,
                "{player} fiel aus der Welt. Auch eine Art Ragequit.",
                "{player} fand das Loch unter der Map.",
                "{player} wurde vom Nichts angenommen.",
                "{player} hat den Boden verpasst. Komplett.",
                "{player} ging dahin, wo selbst Koordinaten aufgeben."
            );
            put(DamageCause.LIGHTNING,
                "{player} wurde vom Himmel persönlich beleidigt.",
                "{player} bekam göttliches Feedback. Sehr direkt.",
                "{player} wurde geblitzt, aber nicht wegen Tempo.",
                "{player} hat Gewitter unterschätzt.",
                "{player} wurde vom Wetter ausgesucht."
            );
            put(DamageCause.SUICIDE,
                "{player} hat sich selbst aus dem Spiel genommen.",
                "{player} drückte innerlich auf Deinstallieren.",
                "{player} hat den eigenen Plan überlebt. Kurz nicht.",
                "{player} war sein härtester Gegner.",
                "{player} beendete die Diskussion mit sich selbst."
            );
            put(DamageCause.STARVATION,
                "{player} verhungerte. Essen war wohl zu meta.",
                "{player} wurde von einem leeren Magen besiegt.",
                "{player} ignorierte Hunger bis zum Abspann.",
                "{player} fand heraus, dass Brot nützlich ist.",
                "{player} hat Nahrung als optional markiert. Fehler."
            );
            put(DamageCause.POISON,
                "{player} wurde von Gift langsam ausgelacht.",
                "{player} trank/berührte etwas Dummes und zahlte.",
                "{player} wurde chemisch überzeugt.",
                "{player} hat Gift zu lange wirken lassen.",
                "{player} verlor gegen innere Werte."
            );
            put(DamageCause.MAGIC,
                "{player} wurde von Magie fachgerecht zerlegt.",
                "{player} verstand die Zauberei erst nach dem Respawn.",
                "{player} bekam Hokuspokus mit Nebenwirkungen.",
                "{player} wurde magisch aus der Runde entfernt.",
                "{player} hat gegen Glitzer verloren. Hart."
            );
            put(DamageCause.WITHER,
                "{player} wurde verwelkt wie Salat im Nether.",
                "{player} hat Wither-Schaden zu lange ignoriert.",
                "{player} wurde vom Verfall persönlich abgeholt.",
                "{player} zerbröselte stilvoll, aber nutzlos.",
                "{player} wurde vom Wither-Effekt langsam beleidigt."
            );
            put(DamageCause.FALLING_BLOCK,
                "{player} wurde von einem Block überdacht. Endgültig.",
                "{player} stand unter schlechter Statik.",
                "{player} bekam Schwerkraft plus Baumaterial.",
                "{player} wurde von Sand/Kies didaktisch getroffen.",
                "{player} lernte: Nach oben schauen hilft manchmal."
            );
            put(DamageCause.THORNS,
                "{player} schlug zu und bekam die Quittung.",
                "{player} verlor gegen Rücksendung mit Dornen.",
                "{killer}s Rüstung hat {player} ausgelacht.",
                "{player} hat sich an {killer} selbst wehgetan.",
                "{player} wurde vom Gegenargument der Dornen erledigt."
            );
            put(DamageCause.DRAGON_BREATH,
                "{player} inhalierte Drachenatem. Medizinisch fragwürdig.",
                "{player} wurde von Drachenmundgeruch entfernt.",
                "{player} stand in lila Nein.",
                "{player} hat Drachenatem für Nebel gehalten.",
                "{player} wurde vom Ender Dragon aromatisch besiegt."
            );
            put(DamageCause.FLY_INTO_WALL,
                "{player} flog in eine Wand. Navigation: null Punkte.",
                "{player} hat Elytra mit Abrissbirne verwechselt.",
                "{player} wurde von einer Wand gestoppt. Überraschend effektiv.",
                "{player} suchte den Durchgang mit dem Gesicht.",
                "{player} war schneller als der eigene Plan."
            );
            put(DamageCause.HOT_FLOOR,
                "{player} tanzte auf Magma bis zum Respawn.",
                "{player} stand auf heißem Boden und blieb dumm stehen.",
                "{player} wurde von Magma-Sneakers abgelehnt.",
                "{player} lernte, dass der Boden Lava sein kann.",
                "{player} hat Hot Floor gespielt und verloren."
            );
            put(DamageCause.CAMPFIRE,
                "{player} wurde am Lagerfeuer zu nah dran erzählt.",
                "{player} kuschelte mit einem Campfire. Kurz.",
                "{player} wurde zum Snack am Feuer.",
                "{player} hat Camping falsch verstanden.",
                "{player} wurde gemütlich weggebrutzelt."
            );
            put(DamageCause.CRAMMING,
                "{player} wurde in der Menge zerquetscht.",
                "{player} hatte zu viele Nachbarn und zu wenig Platz.",
                "{player} starb an Gruppendynamik.",
                "{player} wurde von Körperkontakt besiegt.",
                "{player} fand heraus, dass Kuscheln Limits hat."
            );
            put(DamageCause.DRYOUT,
                "{player} trocknete aus. Fischige Leistung.",
                "{player} wurde zu lange an der Luft gelagert.",
                "{player} hat Wasserrechte ignoriert.",
                "{player} verdorrte wie ein schlecht geparkter Fisch.",
                "{player} wurde von Trockenheit erledigt."
            );
            put(DamageCause.FREEZE,
                "{player} wurde tiefgekühlt und aussortiert.",
                "{player} hat Schnee zu ernst genommen.",
                "{player} fror ein wie ein schlechter Gedanke.",
                "{player} wurde von Kälte langsam ausgeloggt.",
                "{player} fand Powder Snow gar nicht powderful."
            );
            put(DamageCause.SONIC_BOOM,
                "{player} wurde vom Warden angeschrien und zerlegt.",
                "{player} bekam Schall mit Hausverbot.",
                "{player} hat den Soundcheck nicht überlebt.",
                "{player} wurde akustisch aus der Welt entfernt.",
                "{player} lernte: Leise sein wäre eine Option gewesen."
            );
            put(DamageCause.CUSTOM,
                "{player} starb auf eine Weise, die selbst Minecraft peinlich ist.",
                "{player} wurde von unbekannter Dummheit erwischt.",
                "{player} hat einen mysteriösen Fehler im Lebensplan.",
                "{player} wurde vom Server mit Schulterzucken beerdigt.",
                "{player} starb. Ursache: vermutlich Skill Issue."
            );
        }

        List<String> milestones() {
            return milestones;
        }

        List<String> messagesFor(DamageCause cause) {
            return messages.getOrDefault(cause, messages.get(DamageCause.CUSTOM));
        }

        private void put(DamageCause cause, String first, String second, String third, String fourth, String fifth) {
            messages.put(cause, List.of(first, second, third, fourth, fifth));
        }
    }
}
