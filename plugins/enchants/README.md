# PumpeEnchants

Eigene, unter `pumpeenchants:*` registrierte Verzauberungen für PumpeCraft.

## Technischer Ansatz

Die Verzauberungen werden während Papers Bootstrap-Phase additiv über
`RegistryEvents.ENCHANTMENT` registriert. Vanilla-Einträge werden dabei nicht verändert. Dadurch
funktionieren sie mit `/enchant`, erscheinen wie Vanilla-Verzauberungen auf Items und erzeugen
den normalen Verzauberungsglanz. Die eigentliche Spiellogik bleibt in diesem Plugin.

Alte PDC-basierte Items werden beim ersten Lesen automatisch in registrierte Enchantments
umgewandelt. Verzauberungsbücher behalten ihren englischen Namen und zeigen darunter statt einer
doppelten Namenszeile eine deutsche Beschreibung.

## Verzauberungen

### Werkzeuge

| Name | Stufen | Seltenheit | Ziel | Wirkung |
| --- | --- | --- | --- | --- |
| Telekinesis | I | Gewöhnlich | Werkzeuge | Drops und XP direkt ins Inventar, bei vollem Inventar fallen sie normal zu Boden |
| Auto Smelt | I | Selten | Spitzhacke, Schaufel | Erze, Netherit-Schutt und Sand werden beim Abbau geschmolzen; unverträglich mit Behutsamkeit |
| Vein Miner | I–III | Selten | Spitzhacke | Zusammenhängende Erzader, 8/16/32 Blöcke, Haltbarkeit pro Block |
| Lumberjack | I–II | Selten | Axt | Ganzer Baum, 32/64 Blöcke |
| Magnet | I–III | Gewöhnlich | Werkzeuge | Liegende Items im Umkreis 3/5/8 fliegen zum Spieler |

### Kampf

| Name | Stufen | Seltenheit | Ziel | Wirkung |
| --- | --- | --- | --- | --- |
| Lifesteal | I–III | Episch | Waffen | 5/10/15 % des ausgeteilten Schadens als Heilung, höchstens 4 HP pro Treffer |
| Execution | I–III | Episch | Waffen | +15/25/40 % Schaden, wenn das Ziel unter 30 % Leben ist |
| Thunder Strike | I–II | Legendär | Waffen | 5/10 % Chance auf Blitzeinschlag mit Zusatzschaden |
| Barb | I–II | Selten | Waffen | Zieht das Ziel heran, Abklingzeit 6/4 s |

### Rüstung

| Name | Stufen | Seltenheit | Ziel | Wirkung |
| --- | --- | --- | --- | --- |
| Soulbound | I | Legendär | Ausrüstung | Item bleibt beim Tod erhalten und kommt beim Respawn in denselben Slot zurück |
| Endurance | I–II | Episch | Brustpanzer | Unter 4 HP kurz Regeneration I/II, Abklingzeit 90/60 s |
| Featherweight | I–II | Gewöhnlich | Stiefel | Kein Fallschaden bis 6/12 Blöcke |
| Jump Spring | I–II | Gewöhnlich | Stiefel | Dauerhafter Sprungkraft-Effekt I/II |

### Serveranbindung

| Name | Stufen | Seltenheit | Ziel | Wirkung |
| --- | --- | --- | --- | --- |
| Scholar | I–III | Episch | Ausrüstung | +10/20/35 % Skill-Punkte über `PumpeSkills` |
| Lucky | I–II | Legendär | Werkzeuge | 0,5/1 % Chance auf PumpePoints beim Abbauen und Töten, mit Tageslimit |
| Clan Bond | I–II | Episch | Waffen | +5/10 % Schaden, solange ein Clanmitglied in 16 Blöcken steht |
| Courier | I | Selten | Werkzeuge | Sneak-Rechtsklick schickt alle vollen Stapel in den eigenen Briefkasten |

Pro Item sind zwei eigene Verzauberungen erlaubt (`anvil.max-enchants-per-item`).

## Regeln, die aus dem Zusammenspiel entstehen

**Reihenfolge beim Abbau.** Erst sammelt die Kette, dann wandelt Auto Smelt um, dann liefert
Telekinesis aus. Jede andere Reihenfolge frisst Drops. Lucky wirkt vor dem Schmelzen: die Rohdrops
kommen bereits vervielfacht aus dem Block, jeder Stapel wird danach umgewandelt.

**Behutsamkeit setzt Auto Smelt aus**, sonst würde aus dem behutsam abgebauten Erzblock
wieder ein Barren.

**Jeder Blocks einer Ader läuft durch ein echtes `BlockBreakEvent`.** Nur so kann der
Grundstücksschutz ihn ablehnen und `PumpeSkills` ihn zählen. Für dieselbe Zeit meldet
`EnchantService.breakingChain` dem AntiCheat, dass Reichweite und Abbaurate gerade nicht gelten —
32 Blöcke in einem Tick wären sonst ein Nuker-Befund.

**Eine Ader ist eine Handlung.** Lucky würfelt einmal pro Aktion, nicht einmal pro Block.

**Kettenabbau hat Cooldown.** Vein Miner verwendet den Miner-Skill, Lumberjack den
Farmer-Skill. Der Cooldown sinkt linear von 60 Sekunden auf Skill-Level 1 bis auf 30 Sekunden
auf Skill-Level 100. Ohne `PumpeSkills` gelten 60 Sekunden.

**Kampf in Schutzzonen.** Die Kampf-Verzauberungen hängen an `EventPriority.HIGH` mit
`ignoreCancelled`. `PumpeBaseSystem` bricht einen verbotenen Schlag bereits bei `LOW` ab, damit
greift in einer Schutzzone keine von ihnen — ohne eigene Abfrage der Zonen.

**Thunder Strike** schlägt nur optisch ein und legt seinen Schaden auf den laufenden Treffer. Ein
echter Blitz würde zünden und durch den Regionsschutz greifen.

**Soulbound** hält die Items zwischen Tod und Respawn zusätzlich in der Datenbank: dazwischen
kann der Server abstürzen, und was nur im Speicher lag, wäre dann weg. Beim nächsten Join wird
aufgeräumt, was ein Absturz liegen gelassen hat.

## Erwerb

Verzauberungsbücher tragen ein registriertes, gespeichertes Enchantment und werden im Amboss auf
das Item übertragen.
Zwei Bücher derselben Stufe ergeben die nächste Stufe. Unverträgliche oder wirkungslose
Kombinationen werden mit einer Meldung in der Aktionsleiste abgelehnt.

Natürlich generierte Container-Loot-Tabellen können selten ein Custom-Buch erhalten. Die Chance
steht für jedes Enchantment unter `enchants.<id>.loot-chance-percent`. Soulbound liegt standardmäßig
bei 0,05 %. Ein Teil der Bücher trägt zusätzlich ein oder selten zwei passende Vanilla-Enchantments;
solche Mischbücher lassen sich in beiden Richtungen im Amboss kombinieren.

Generierte Soulbound-, Lucky- und Mending-Bücher werden beim ersten Übergang in ein
Spielerinventar mit Finder, Datum und Uhrzeit versehen. Gleichzeitig kündigt eine von fünf fest
formulierten deutschen Nachrichten den Fund im Serverchat an; das AI-Plugin ist daran nicht
beteiligt. Verschieben und erneutes Aufheben lösen keine weitere Meldung aus.

Beim Serverstart werden vorhandene Custom-Bücher in geladenen Inventaren und Chunks auf das
aktuelle Buchformat gebracht. Spielerinventare und Enderkisten folgen beim Login, ungeladene
Container beim Laden oder Öffnen. Eigene Namen, fremde Lore, Vanilla-Enchantments und PDC-Daten
wie Item-Signaturen bleiben dabei erhalten. Das gilt auch für Bücher in Shulker-Kisten und Bundles.

## Befehle

- `/customenchant <Spieler> <Verzauberung> <Stufe>` – legt die Verzauberung auf das gehaltene
  Item. Hält der Spieler ein Buch, entsteht daraus ein Verzauberungsbuch.
  Der Adminbefehl darf dabei auch normalerweise nicht unterstützte Items verzaubern; Stufen,
  Inkompatibilitäten und das Enchantment-Limit gelten weiterhin.
  Aliase: `cenchant`, `verzaubern`.
- `/enchantbooks [Spieler]` – gibt jedes Buch jeder aktiven Verzauberung und Stufe einmal aus,
  zum Testen. Alias: `testenchants`.
- `/gen` – füllt den angesehenen Container mit einer zufälligen Vanilla-Kistenloot-Tabelle und
  würfelt anschließend dieselben Custom-Buch-Chancen wie bei natürlich generierten Kisten.

`/enchant <Spieler> pumpeenchants:<id> [Stufe]` kann die registrierten Enchantments ebenfalls
anwenden. `/enchant` bleibt dabei unverändert der Vanilla-Befehl.

## Berechtigungen

- `pumpecraft.enchants.admin` – Standard `op`, gilt für alle Verwaltungs- und Testbefehle.

## Konfiguration

Jede Verzauberung liegt unter `enchants.<id>` mit `enabled` und ihren Zahlen; stufenabhängige
Werte stehen unter `level-1`, `level-2`, `level-3`. Dazu kommen:

| Schlüssel | Standard | Bedeutung |
| --- | --- | --- |
| `tick-interval-ticks` | `10` | Takt für Magnet und Jump Spring |
| `anvil.level-cost` | `5` | Stufenkosten der Amboss-Kombination |
| `anvil.max-enchants-per-item` | `2` | Eigene Verzauberungen pro Item |
| `loot.vanilla-enchant-chance-percent` | `38` | Chance auf ein zusätzliches Vanilla-Enchantment auf einem Custom-Buch |
| `loot.second-vanilla-enchant-chance-percent` | `6` | Chance auf ein zweites Vanilla-Enchantment nach dem ersten |
| `enchants.lucky.daily-limit` | `250` | PumpePoints pro Spieler und Tag aus Lucky |

Das Tageslimit von Lucky liegt im Speicher: ein Neustart schenkt einen frischen Tag. Das
stoppt den Dauergrind an einer Stelle, nicht den Neustart-Trick.

## Abhängigkeiten

Erforderliche Server-Abhängigkeiten im `paper-plugin.yml` sind `PumpeUtils` und `PumpeDatabase`;
die Datenbank trägt Soulbound (`V29__soulbound_items.sql`).

`PumpeTransactions`, `PumpeMailbox`, `PumpeClanSystem` und `PumpeBaseSystem` sind optionale
Server-Abhängigkeiten. Lucky, Courier und Clan Bond holen ihren Dienst über den `ServicesManager`
und fallen ohne das jeweilige Plugin einfach aus.

Zwei Plugins fragen umgekehrt hier an, beide über `softdepend: [PumpeEnchants]`:
`PumpeSkills` für den Scholar-Zuschlag und `PumpeAntiCheat` für die Ausnahme während eines
Vein-Miner-Abbaus.

## Dienst

`EnchantService` liegt als Bukkit-Service bereit: `level`, `activeLevel`, `equippedLevel`, `list`,
`set`, `remove`, `createBook`, `settings` und `breakingChain`.
