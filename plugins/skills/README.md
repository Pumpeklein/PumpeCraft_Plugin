# PumpeSkills

Sieben Skills, die sich beim normalen Spielen von selbst füllen, plus ein
Tracking-Bereich für einzelne Aktionen.

## Commands

- `/skills` – öffnet eine Inventar-GUI mit Levelspanne, Fortschritt und Restpunkten
- `/skills <Skill>` – öffnet die GUI-Details mit Rang, Zählern und nächstem Reward
- `/skills top <Skill>` – Bestenliste der besten zehn Spieler
- `/skills <Spieler> [Skill]` – Werte eines anderen Spielers (`pumpecraft.skills.others`)
- `/skills help` – Kurzübersicht

Die Übersicht nutzt sieben feste Skill-Slots. Ein Klick öffnet die jeweilige
Detailansicht; der Rang-Button ruft die Top 10 auf. Zurück- und Schließen-Buttons
sind als Pfeil beziehungsweise Barriere ausgeführt. Das Standard-Inventar-GUI
funktioniert ohne Client-Mod und damit auch für Bedrock-Spieler über Geyser.

## Skills

| Skill | Gezählt wird | Punkte |
| --- | --- | --- |
| `fischer` | Fänge beim Angeln, getrennt nach Fisch, Schatz und Müll | Fisch 3, Schatz 30, Müll 1 |
| `miner` | Abgebautes Gestein und Erze | Stein 1, Kohle/Quarz 2, Eisen/Kupfer 4, Gold/Redstone/Lapis 6, Diamant/Smaragd 20, Netherit 40 |
| `mobs` | Getötete Monster, Tiere und Bosse | Monster 2, Tier 1, Boss 100 |
| `dorf` | Handel mit Villagern, gezahlte Smaragde, günstigster Handel | Handel 2, neuer Villager 5 |
| `farmer` | Reife Ernten, Holz, Erde und angelegtes Ackerland | Ernte 3, Abpflücken 2, Holz 1, Erde 1, Ackerland 1 |
| `builder` | Platzierte Blöcke | 1 pro Block |
| `tierfreund` | Gezähmte Tiere | 10 pro Tier |
| `allgemein` | Item-Nutzung, Verzehrtes und kaputt gegangene Werkzeuge | keine Punkte, reines Tracking |

Alle Punktwerte stehen zentral in
[SkillScoring.java](src/main/java/de/pumpecraft/skills/SkillScoring.java).

### Level

Level `n` beginnt bei `50 * (n-1)²` Punkten: Level 2 ab 50, Level 5 ab 800,
Level 10 ab 4.050, Level 50 ab 120.050. Maximum ist Level 100.

Die Anzeige trennt Gesamtpunkte und Level-Fortschritt. Bei 4.100 Gesamtpunkten
steht ein Spieler beispielsweise auf Level 10 bei `50 / 950`; bis Level 11
fehlen dann noch 900 Punkte.

### Level-Rewards

Jeder der sieben Level-Skills vergibt bei Level 10, 20, ... bis 100 einen
einmaligen Item-Reward. Inhalt und Beschriftung stehen in `config.yml`. Nicht
zugestellte Rewards bleiben in `pc_skill_rewards` offen und werden beim
nächsten Join ausgegeben. Ist das Inventar voll, landen Restitems am Spieler.

Bereits vor der Einführung erreichte Meilensteine werden beim ersten Join
nachgetragen. Der Primärschlüssel aus Spieler-UUID, Skill und Meilenstein
verhindert doppelte Ausgaben nach einem Neustart.

### Detailzähler

Neben der Punktzahl wird pro Skill mitgeschrieben, *womit* die Punkte verdient
wurden – etwa `ore.diamond_ore`, `mob.zombie`, `pet.wolf` oder `item.cod`. Die
Detailansicht zeigt daraus die fünf häufigsten Einträge mit dem übersetzten
Item- bzw. Mobnamen des Clients.

`dorf` hält zusätzlich `best_price`, den günstigsten je gemachten Handel in
Smaragden. Dieser Wert wird als Minimum geführt, nicht summiert.

## Schutz gegen Punkte-Farmen

- Es zählt nur der **Überlebensmodus**. Creative und Spectator werden ignoriert.
- Selbst platzierte Blöcke geben beim Abbauen **keine** Miner- oder
  Farmer-Punkte, und die Builder-Gutschrift wird wieder abgezogen. Die Schleife
  „Block setzen, Block abbauen“ bringt damit nichts.
- Nur voll ausgewachsene Pflanzen zählen als Ernte.
- Villager werden nur beim **ersten** Handel gezählt; die Paare stehen in
  `pc_skill_village_partners`.

Der Index der platzierten Blöcke liegt nur im Speicher und ist pro Welt auf
250.000 Einträge begrenzt (die ältesten fallen zuerst raus). Nach einem Neustart
ist er leer – ein dauerhafter Blockindex wäre teuer, und der Schutz zielt auf die
schnelle Wiederholungsschleife.

## Speicherung

- Beim Login werden die Zähler eines Spielers einmalig geladen, danach läuft
  alles im Speicher. Kein Event fasst pro Aktion die Datenbank an.
- Geschrieben wird asynchron alle 30 Sekunden, beim Verlassen und beim
  Herunterfahren. Schlägt ein Schreibvorgang fehl, bleiben die Werte als
  ungespeichert markiert und werden beim nächsten Durchlauf erneut versucht.
- Bestenlisten lesen direkt aus der Datenbank und können deshalb bis zu 30
  Sekunden hinter den Werten eines gerade aktiven Spielers liegen.

Tabellen aus Migration `V4__skill_stats.sql`:

| Tabelle | Inhalt |
| --- | --- |
| `pc_skill_stats` | ein Zähler je Spieler, Skill und Statistik-Schlüssel |
| `pc_skill_village_partners` | mit welchen Villagern ein Spieler schon gehandelt hat |
| `pc_players` | zuletzt bekannter Name je UUID |

Migration `V9__skill_rewards.sql` ergänzt `pc_skill_rewards` für einmalige und
gegebenenfalls noch ausstehende Level-Rewards. Migration
`V10__skill_reward_definitions.sql` stellt die aktuell konfigurierte
Reward-Liste auch dem Webpanel zur Verfügung.

`pc_players` wird bei jedem Login aktualisiert und macht UUID-basierte
Statistiken – auch Playtime und Tode – ohne externe Namensauflösung anzeigbar.

## Permissions

- `pumpecraft.skills.use` – eigene Skills und Bestenlisten ansehen (Standard: alle)
- `pumpecraft.skills.others` – Skills anderer Spieler ansehen (Standard: OP)

```mcfunction
/lp group support permission set pumpecraft.skills.others true
```
