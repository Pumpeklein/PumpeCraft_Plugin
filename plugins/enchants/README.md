# PumpeEnchants

Eigene Verzauberungen für PumpeCraft, ohne Eingriff in die Paper-Registry.

## Technischer Ansatz

Eine Verzauberung ist ein Eintrag im `PersistentDataContainer` des Items plus eine gerenderte
Lore-Zeile. Echte Enchantments über `RegistryEvents.ENCHANTMENT` bräuchten einen Bootstrapper und
`paper-plugin.yml`, während alle Module hier auf `plugin.yml` mit `depend:` laufen. Der Preis:
kein Vanilla-Verzauberungstisch, der Erwerb läuft über Bücher und den Amboss.

Eigene Lore-Zeilen werden an ihrem Text erkannt, nicht an einem Marker im Stil: `ItemSanitizer`
aus `PumpeAntiCheat` baut zu lange Zeilen als reinen Text neu auf und würde einen Marker
mitsamt der Formatierung verlieren. Fremde Lore bleibt beim Rendern erhalten.

## Verzauberungen

### Werkzeuge

| Name | Stufen | Seltenheit | Ziel | Wirkung |
| --- | --- | --- | --- | --- |
| Telekinese | I | Gewöhnlich | Werkzeuge | Drops und XP direkt ins Inventar, bei vollem Inventar fallen sie normal zu Boden |
| Schmelzofen | I | Selten | Spitzhacke, Schaufel | Erze, Netherit-Schutt und Sand werden beim Abbau geschmolzen; unverträglich mit Behutsamkeit |
| Aderabbau | I–III | Selten | Spitzhacke | Zusammenhängende Erzader, 8/16/32 Blöcke, Haltbarkeit pro Block |
| Forstwirt | I–II | Selten | Axt | Ganzer Baum, 32/64 Blöcke |
| Magnet | I–III | Gewöhnlich | Werkzeuge | Liegende Items im Umkreis 3/5/8 fliegen zum Spieler |

### Kampf

| Name | Stufen | Seltenheit | Ziel | Wirkung |
| --- | --- | --- | --- | --- |
| Aderlass | I–III | Episch | Waffen | 5/10/15 % des ausgeteilten Schadens als Heilung, höchstens 4 HP pro Treffer |
| Hinrichtung | I–III | Episch | Waffen | +15/25/40 % Schaden, wenn das Ziel unter 30 % Leben ist |
| Donnerschlag | I–II | Legendär | Waffen | 5/10 % Chance auf Blitzeinschlag mit Zusatzschaden |
| Widerhaken | I–II | Selten | Waffen | Zieht das Ziel heran, Abklingzeit 6/4 s |

### Rüstung

| Name | Stufen | Seltenheit | Ziel | Wirkung |
| --- | --- | --- | --- | --- |
| Seelenbindung | I | Legendär | Ausrüstung | Item bleibt beim Tod erhalten und kommt beim Respawn in denselben Slot zurück |
| Ausdauer | I–II | Episch | Brustpanzer | Unter 4 HP kurz Regeneration I/II, Abklingzeit 90/60 s |
| Federleicht | I–II | Gewöhnlich | Stiefel | Kein Fallschaden bis 6/12 Blöcke |
| Sprungfeder | I–II | Gewöhnlich | Stiefel | Dauerhafter Sprungkraft-Effekt I/II |

### Serveranbindung

| Name | Stufen | Seltenheit | Ziel | Wirkung |
| --- | --- | --- | --- | --- |
| Gelehrter | I–III | Episch | Ausrüstung | +10/20/35 % Skill-Punkte über `PumpeSkills` |
| Glückspilz | I–II | Legendär | Werkzeuge | 0,5/1 % Chance auf PumpePoints beim Abbauen und Töten, mit Tageslimit |
| Clanbande | I–II | Episch | Waffen | +5/10 % Schaden, solange ein Clanmitglied in 16 Blöcken steht |
| Kurier | I | Selten | Werkzeuge | Sneak-Rechtsklick schickt alle vollen Stapel in den eigenen Briefkasten |

Pro Item sind zwei eigene Verzauberungen erlaubt (`anvil.max-enchants-per-item`).

## Regeln, die aus dem Zusammenspiel entstehen

**Reihenfolge beim Abbau.** Erst sammelt die Kette, dann wandelt der Schmelzofen um, dann liefert
Telekinese aus. Jede andere Reihenfolge frisst Drops. Glück wirkt vor dem Schmelzen: die Rohdrops
kommen bereits vervielfacht aus dem Block, jeder Stapel wird danach umgewandelt.

**Behutsamkeit setzt den Schmelzofen aus**, sonst würde aus dem behutsam abgebauten Erzblock
wieder ein Barren.

**Jeder Blocks einer Ader läuft durch ein echtes `BlockBreakEvent`.** Nur so kann der
Grundstücksschutz ihn ablehnen und `PumpeSkills` ihn zählen. Für dieselbe Zeit meldet
`EnchantService.breakingChain` dem AntiCheat, dass Reichweite und Abbaurate gerade nicht gelten —
32 Blöcke in einem Tick wären sonst ein Nuker-Befund.

**Eine Ader ist eine Handlung.** Glückspilz würfelt einmal pro Aktion, nicht einmal pro Block.

**Kampf in Schutzzonen.** Die Kampf-Verzauberungen hängen an `EventPriority.HIGH` mit
`ignoreCancelled`. `PumpeBaseSystem` bricht einen verbotenen Schlag bereits bei `LOW` ab, damit
greift in einer Schutzzone keine von ihnen — ohne eigene Abfrage der Zonen.

**Donnerschlag** schlägt nur optisch ein und legt seinen Schaden auf den laufenden Treffer. Ein
echter Blitz würde zünden und durch den Regionsschutz greifen.

**Seelenbindung** hält die Items zwischen Tod und Respawn zusätzlich in der Datenbank: dazwischen
kann der Server abstürzen, und was nur im Speicher lag, wäre dann weg. Beim nächsten Join wird
aufgeräumt, was ein Absturz liegen gelassen hat.

## Erwerb

Verzauberungsbücher tragen die Verzauberung im PDC und werden im Amboss auf das Item übertragen.
Zwei Bücher derselben Stufe ergeben die nächste Stufe. Unverträgliche oder wirkungslose
Kombinationen werden mit einer Meldung in der Aktionsleiste abgelehnt.

## Befehle

- `/customenchant <Spieler> <Verzauberung> <Stufe>` – legt die Verzauberung auf das gehaltene
  Item. Hält der Spieler ein Buch, entsteht daraus ein Verzauberungsbuch.
  Aliase: `cenchant`, `verzaubern`.
- `/enchantbooks [Spieler]` – gibt jedes Buch jeder aktiven Verzauberung und Stufe einmal aus,
  zum Testen. Alias: `testenchants`.

`/enchant` bleibt bewusst der Vanilla-Befehl; ein Plugin-Befehl dieses Namens würde ihn verdecken.

## Berechtigungen

- `pumpecraft.enchants.admin` – Standard `op`, gilt für beide Befehle.

## Konfiguration

Jede Verzauberung liegt unter `enchants.<id>` mit `enabled` und ihren Zahlen; stufenabhängige
Werte stehen unter `level-1`, `level-2`, `level-3`. Dazu kommen:

| Schlüssel | Standard | Bedeutung |
| --- | --- | --- |
| `tick-interval-ticks` | `10` | Takt für Magnet und Sprungfeder |
| `anvil.level-cost` | `5` | Stufenkosten der Amboss-Kombination |
| `anvil.max-enchants-per-item` | `2` | Eigene Verzauberungen pro Item |
| `enchants.lucky.daily-limit` | `250` | PumpePoints pro Spieler und Tag aus Glückspilz |

Das Tageslimit von Glückspilz liegt im Speicher: ein Neustart schenkt einen frischen Tag. Das
stoppt den Dauergrind an einer Stelle, nicht den Neustart-Trick.

## Abhängigkeiten

`depend: [PumpeUtils, PumpeDatabase]` — die Datenbank trägt die Seelenbindung
(`V29__soulbound_items.sql`).

`softdepend: [PumpeTransactions, PumpeMailbox, PumpeClanSystem, PumpeBaseSystem]` — Glückspilz,
Kurier und Clanbande holen ihren Dienst über den `ServicesManager` und fallen ohne das jeweilige
Plugin einfach aus.

Zwei Plugins fragen umgekehrt hier an, beide über `softdepend: [PumpeEnchants]`:
`PumpeSkills` für den Gelehrter-Zuschlag und `PumpeAntiCheat` für die Ausnahme während eines
Aderabbaus.

## Dienst

`EnchantService` liegt als Bukkit-Service bereit: `level`, `activeLevel`, `equippedLevel`, `list`,
`set`, `remove`, `createBook`, `settings` und `breakingChain`.
