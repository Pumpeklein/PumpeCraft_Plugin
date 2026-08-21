# PumpeBaseSystem

Zwei Dinge, die zusammengehören: die **Base** als ein Punkt, den man teilt, und das **Grundstück**
als Fläche, die einem gehört. Beides liegt in `PumpeDatabase`, beides hat ein Menü.

## Grundstücke

Ein Grundstück ist ein achsenparalleles Rechteck über die **volle Welthöhe** - ohne Y-Grenzen
bleibt die Zugehörigkeit eines Blocks eine Frage von zwei Koordinaten, und niemand muss raten, ob
sein Keller noch dazugehört. Nur das Team kann eine Höhe festlegen (`/plot admin height <min> <max>`,
zurück mit `full`), etwa um unter dem Spawn noch graben zu lassen. Zwei Grundstücke dürfen dann
übereinander liegen, solange sich ihre Höhen nicht berühren.

### Anlegen

Beides geht vollständig im Menü: `/plot` → **Neues Grundstück** holt das Messer, zeigt Fläche,
Lagefaktor, Preis und Urteil, und kauft nach einer Namenseingabe. Die Namenseingabe läuft über
einen Amboss - eine Truhe kennt keine Tastatur, und der Amboss ist die einzige Oberfläche, in der
ein Spieler tippen kann, ohne ins Chatfenster zu wechseln.

Am Messer setzt **Linksklick** die erste Ecke, **Rechtsklick** die zweite. Ab der zweiten Ecke steht
die Grenze als **buntes Glas** in der Welt: grün, wenn die Auswahl kaufbar ist, rot, wenn nicht. Die
Blöcke stehen nur im Client des Betrachters, die Welt bleibt unberührt. Nach dem Kauf zieht das
System das Messer wieder ein.

Auf einem fertigen Grundstück blendet **Grenze anzeigen** im Menü dieselbe Markierung dauerhaft ein
(`/plot show` tut es ebenso).

Der Preis ist **Fläche × Preis je Block × Lagefaktor**. Der Lagefaktor ist 1,0 im Umkreis von
`full-price-radius` um 0/0, fällt von dort gleichmäßig ab und erreicht bei `cheapest-at` seinen
Mindestwert. Bauland am Spawn bleibt damit knapp, ohne die Ferne zu verschenken; die Formel ist
eine Gerade, damit ein Spieler sie im Kopf nachvollziehen kann.

Beim Verkauf über `/plot sell confirm` gibt es `refund-percent` des gezahlten Preises zurück.
Gebaute Blöcke bleiben stehen - verkauft wird der Anspruch, nicht das Bauwerk.

### Rollen und Flaggen

**Rollen** regeln, was Mitglieder dürfen, **Flaggen**, was alle anderen und die Welt dürfen. Diese
Trennung ist der Grund, warum ein Besitzer noch erklären kann, warum etwas geht oder nicht.

| Rolle | Darf |
| --- | --- |
| Besitzer | alles, dazu verkaufen |
| Verwalter | bauen, Mitglieder aufnehmen, Flaggen umlegen |
| Mitglied | bauen |

Für das Team führt `/plot` direkt in das Grundstück, auf dem es gerade steht - und die Liste zeigt
oben einen Knopf **Grundstück hier**. Ohne das käme man an ein fremdes Grundstück nur über seinen
Namen heran.

Mitglieder kommen über das Menü: **Spieler hinzufügen** listet die Onlinespieler, die nächsten
zuerst - wer jemanden aufnimmt, meint fast immer den, der neben ihm steht. **Suchen** tippt einen
Namen ein und findet auch Spieler, die offline sind. Per Befehl geht `/plot trust <Spieler>
[manager]`, entfernen `/plot untrust <Spieler>`; im Menü wechselt ein Linksklick die Rolle, ein
Rechtsklick entfernt.

Flaggen kennen zwei Reichweiten. Solche für **Fremde** lassen Mitglieder unberührt, solche für
**alle** beschreiben den Ort und gelten auch für den Besitzer - Tierschutz schützt sonst nur vor
Fremden, und genau das war nie gemeint.

Jede Flagge benennt, was **erlaubt** ist, nie was geschützt wird. "Tierschutz an" liest sich wie
Schutz, hieße in diesem Modell aber erlaubter Schaden; die Flagge heißt deshalb *Tiere verletzen*.
Und jeder Block hat genau eine zuständige Flagge: Betten entscheiden sich an *Schlafen*, Behälter an
*Behälter*, alles Übrige an *Benutzen*. Stünde ein Bett zusätzlich unter *Benutzen*, gewänne immer
die strengere von zweien - und wer das Schlafen erlaubt, bekäme trotzdem ein verschlossenes Bett.

| Flagge | Standard | Reichweite |
| --- | --- | --- |
| Betreten | an | Fremde |
| Bauen | aus | Fremde |
| Benutzen | an | Fremde |
| Behälter | aus | Fremde |
| Schlafen | an | Fremde |
| PvP | aus | alle, **nur Team** |
| Tiere verletzen | aus | alle |
| Monster | an | alle |
| Monsterschäden | aus | alle |
| Explosionen | aus | alle |
| Feuer | aus | alle |
| Ackerland | aus | alle |
| Fallende Blöcke | an | alle |
| Eisschmelze | an | alle |
| Schnee und Eis | an | alle |
| Korallen | an | alle |
| Laub | an | alle |

PvP steht auf jedem Spielergrundstück aus und lässt sich nur vom Team setzen; im Flaggenmenü eines
Spielers taucht die Zeile deshalb gar nicht erst auf. PvP gilt außerdem beidseitig: Steht einer der
beiden Beteiligten auf einem Grundstück ohne PvP, fällt der Schlag aus - sonst wäre ein Grundstück
am Rand einer Kampfzone ein Ort, von dem aus man gefahrlos schießt.

Im Menü schaltet ein Linksklick um, ein Rechtsklick setzt auf den Standard zurück; per Befehl
`/plot flag <Flagge> <on|off|default>`. Nur abweichende Schalter werden gespeichert - ein geänderter
Standard wirkt dadurch sofort auf alle Grundstücke, die ihn nie angefasst haben.

Durchgesetzt wird das für Abbau, Platzieren, Eimer, Benutzen, Behälter, Schlafen, Zündung,
Feuerausbreitung, Explosionen, Kolben über die Grenze, Kampf, Tiere, Monsterspawn, fallende Blöcke,
Eis, Schnee, Korallen, Laub und blockverändernde Wesen.

### Wer die Rechte verliert, wird hinausgesetzt

Wem die Rechte entzogen werden, während er auf dem Grundstück steht, dem einfach jeden Schritt zu
verbieten hieße, ihn dort festzuhalten. Er wird deshalb über die nächste Kante hinausgesetzt - auf
einen Platz mit festem Boden, zwei freien Blöcken und ohne Lava, Feuer oder Kaktus darunter.

### Admingebiete

`/plot admin claim <Name>` legt dieselbe Mechanik ohne Besitzer und ohne Preis an - gedacht für
Spawn und Ähnliches. Mit den Standardflaggen heißt das: betreten ja, Türen ja, bauen nein,
Behälter nein. Mitglieder lassen sich wie überall eintragen. `/plot admin delete <Name>` entfernt,
`/plot admin height <min> <max>` begrenzt die Höhe, `/plot admin reload` liest alles neu ein.

### Nachschlagen

Alle Grundstücke liegen im Speicher, nach Chunk sortiert. Jeder Blockabbau und jeder Schritt fragt
dort nach; eine Datenbankabfrage käme dafür nicht in Frage, eine flache Liste ebenso wenig.

### Rechte

`pumpecraft.plot.use` und `pumpecraft.plot.claim` stehen standardmäßig auf **an**, `admin` und
`bypass` auf aus. Die Voreinstellungen stehen in der `plugin.yml` und nicht nur in der
`permissions.yml`: Ein Rechte-Plugin liest die Standardwerte beim Laden aus der `plugin.yml`; erst
zur Laufzeit angemeldete Rechte erreichen es unter Umständen nicht mehr.

### Konfiguration

`config.yml`, Sektion `plots`: `allowed-worlds`, `price-per-block`, `minimum-price`,
`refund-percent`, `distance.*` (`full-price-radius`, `cheapest-at`, `minimum-factor`),
`limits.*` (`max-per-player`, `min-size`, `max-size`) und `selection-tool`.

## Basen

Eine Base ist ein Punkt mit Sichtbarkeit, Besuchs- und Like-Zählern. Sie lässt sich nur dort
setzen, wo man auch bauen darf - sonst wäre sie ein Besuchsziel mitten in fremdem Eigentum.

The tables (`pc_player_bases`, `pc_base_visitors`, `pc_base_likes`) are created by the Flyway
migration `V11__clans_and_player_bases.sql`, the plot tables (`pc_plots`, `pc_plot_members`,
`pc_plot_flags`) by `V24__plots.sql` und `V25__plot_height.sql` in `plugins/database`. The module was split out of
`plugins/clan-system`; the permission nodes stay unchanged, so existing LuckPerms groups keep
working.

## Menu

`/base` without arguments opens the menu; `/base menu` does the same.

- **Base-Menü** — own base card (visibility, world, position, visits, unique visitors, likes,
  age), set or move the base, toggle visibility, open the visitor and like lists, open the
  directory, delete the base.
- **Alle Basen** — paginated directory of every base the viewer may see, sorted by likes, visits,
  last change or name. Staff with `pumpecraft.base.admin` also see private bases.
- **Base · Spieler** — detail view of a single base with visit and like buttons.
- **Besucher / Likes** — paginated lists of who visited or liked the own base; a click jumps to
  that player's base.
- **Base löschen?** — confirmation before the base and its statistics are removed.

## Commands

- `/base` opens the menu, `/base liste` opens the directory.
- `/base set [public|private]` stores the current location. Without an argument an existing base
  keeps its visibility; a new base uses `bases.default-public`.
- `/base public` and `/base private` change access.
- `/base visit [Spieler]` teleports to an accessible base.
- `/base like <Spieler>` toggles the like for that base.
- `/base info [Spieler]` displays visibility, visits, unique visitors and likes.
- `/base delete confirm` removes the base and its visit/like history; `/base delete` asks in
  the menu instead.
- Console: `/base info <Spieler>` and `/base as <Spieler> <Unterbefehl>`.

Private coordinates are only visible to the owner and to users with `pumpecraft.base.admin`.
For them the position in `/base info` is a clickable teleport target. Own visits and own likes do
not change any statistics; every further visit of the same player raises the total counter but not
the number of unique visitors.

## Configuration

`config.yml` carries `config-version` plus the `bases` section: `default-public`,
`visit-cooldown-seconds` (lock between visits of foreign bases), `browse-limit` (upper bound of
the directory) and `directory-refresh-seconds` (refresh interval of the tab completion cache).

## Permissions

All permission nodes and defaults are defined in `permissions.yml` and are registered dynamically
for LuckPerms. No permission nodes are embedded in the Java classes.
