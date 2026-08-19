# PumpeEssentials

Online players use the custom live mirror. Offline inventories and ender chests
are loaded and saved directly through Paper's 26.1.2 playerdata implementation.
No additional inventory plugin is required.

Planned area for basic server commands and quality-of-life features.

## Commands

- `/openinv <Spieler>` - opens a structured inventory view with armor, main hand, offhand, crafting area, main inventory and hotbar.
- `/opendender <Spieler>` - opens the target player's ender chest.
- `/openender <Spieler>` - alias for `/opendender`.
- `/openec <Spieler>` - alias for `/opendender`.
- `/rename <Name>` - benennt das Item in der Haupthand ohne XP-Kosten um.
- `/sign [Nachricht]` - versieht das Item mit Name, Datum, Uhrzeit und einer optionalen Nachricht.
- `/seen <Spieler>` - zeigt, ob ein Spieler online ist oder wann er zuletzt online war.
- `/bc <Nachricht>` - sendet eine Nachricht ohne Absender in den globalen Chat (`/broadcast` als Alias).
- `/back` - teleportiert zum zuletzt verlassenen Punkt.
- `/back history` - zeigt die letzten Punkte mit anklickbaren Koordinaten und Index.
- `/back select <Index>` - teleportiert zum Punkt mit diesem Index.
- `/back user <Spieler>` - setzt einen anderen Spieler auf dessen letzten Punkt zurück.
- `/back user <Spieler> history` - zeigt den Verlauf eines anderen Spielers, auch offline.
- `/back user <Spieler> <Index>` - setzt einen anderen Spieler auf den Punkt mit diesem Index zurück.
- `/sit` - setzt den Spieler an Ort und Stelle hin, erneut aufgerufen steht er wieder auf.
- `/crawl` - versetzt den Spieler in die Krabbelhaltung, erneut aufgerufen richtet er sich auf.

`/rename` und `/sign` werden mit PP bezahlt. Die Formel lautet Grundpreis plus
Zeichenpreis pro Nicht-Leerzeichen plus Itemwert-Aufschlag. Rename kostet standardmäßig
175 PP + 15 PP je Zeichen + 15 % des Itemwerts. Sign kostet 100 PP + 8 PP je Zeichen
der optionalen Nachricht + 10 % des Itemwerts. Material, Stackgröße, Haltbarkeit und
Verzauberungen bestimmen den Itemwert; alle Faktoren lassen sich in `config.yml` anpassen.

## Rücksprungpunkte

Vor jedem Teleport und bei jedem Tod wird die verlassene Position gespeichert - für **jeden**
Spieler, nicht nur für Berechtigte. Nur so kann ein Teammitglied einen Spieler ohne eigene
Rechte über `/back user` zurücksetzen. Die Punkte liegen in `pc_back_locations` und überstehen
Neustarts; je Spieler bleiben die letzten `back.history-size` erhalten, ältere werden gelöscht.

Ein Spieler ohne `pumpecraft.essentials.back.death` sieht und nutzt nur Teleportpunkte; seine
Todespunkte werden trotzdem aufgezeichnet und bleiben dem Team über `/back user` zugänglich.
Die Indizes in `/back select` beziehen sich immer auf die zuletzt angezeigte Liste - `1` ist
der jüngste Punkt.

In `config.yml` steuert `back.history-size` die Verlaufslänge, `back.minimum-distance`
unterdrückt Kurzsprünge innerhalb derselben Welt, und `back.teleport-causes` legt fest,
welche Teleportgründe überhaupt einen Punkt erzeugen.

## Haltungen

`/sit` macht den Spieler zum Passagier eines unsichtbaren Markers - nur so sehen die anderen
die Sitzhaltung und der Sitzende bleibt stehen, wo er ist. Schleichen steigt ab, `/sit` ebenso.
Der Marker wird nicht gespeichert; ein Absturz hinterlässt keine Reste in der Welt.

Der Sitz steht genau dort, wo der Spieler stand - gleiche Höhe, gleiche X/Z. Das ist kein
Näherungswert: Ein Reiter wird 0.6 unter dem Anhängepunkt seines Fahrzeugs abgesetzt
(`Avatar.DEFAULT_VEHICLE_ATTACHMENT`), und genau 0.594 über seiner Entity-Position liegt der
Fuß eines sitzenden Spielermodells - die Beine knicken auf Hüfthöhe 0.75 um 81° ab, gerendert
mit Maßstab 0.9375. Beide Werte heben sich auf, `sit.seat-offset` ist deshalb 0.0.

Bezugspunkt ist die Standhöhe des Spielers, nicht das Blockraster: Stufen, Platten, Wege und
Ackerland enden zwischen zwei Blockgrenzen, und nur die tatsächliche Standhöhe kennt diesen
Unterschied. Auch X und Z bleiben unverändert - ein Zentrieren auf die Blockmitte würde von
der unteren Stufe einer Treppe seitlich wegsetzen.

Beim Aufstehen wird aktiv auf den Ausgangspunkt zurückgesetzt: Der Rumpf des Sitzenden liegt
0.6 unter der Oberfläche, ohne Rückgabe stünde er anschließend im Boden.

`/crawl` besteht aus zwei Hälften: Die feste `SWIMMING`-Pose regelt, was Server und andere
Spieler sehen, und ein Deckblock, den nur der krabbelnde Spieler geschickt bekommt, sorgt dafür,
dass seine eigene Spiellogik ihn nicht aufstehen lässt und durch einen Block hohe Lücken lässt.
Der Deckblock wird beim Aufstehen durch den echten Blockzustand ersetzt. `crawl.cover-block`
muss ein kollidierender, unsichtbarer Block sein - alles andere wäre für den Spieler eine Wand,
die sonst niemand sieht.

Seine Unterkante muss über die Krabbelhöhe des Spielers und **unter dessen Hockhöhe** liegen.
Der Client wählt die Pose seines eigenen Spielers allein: Passt er stehend nicht hin, versucht
er es zuerst hockend und erst danach krabbelnd. Ein Deckel, der nur die Standhöhe verbietet,
lässt ihn deshalb bloß hocken - genau das Bild, das man unter einer Stufe bekommt. Berührt der
Deckel umgekehrt die Krabbelbox, bricht der Client die Posenwahl ganz ab und der Spieler bleibt
stehen.

Beide Höhen liest der Server aus den Maßen, die er für diesen Spieler führt; damit tragen sie
`Attribute.SCALE` und die Maße der Serverversion, ohne dass hier Zahlen stehen. Die Höhe folgt
außerdem der genauen Standfläche, nicht dem Blockraster: Auf Pfaden, Ackerland, Platten und
Stufen endet sie zwischen zwei Blockgrenzen, weshalb bei jeder Positionsänderung neu gerechnet
wird und nicht erst beim Blockwechsel.

Passt in dieses Fenster kein voller Block - weil der Spieler verkleinert ist oder auf halber
Höhe steht -, tritt eine nur diesem Client gezeigte obere Steinstufe an seine Stelle. Damit
beeinflusst die Spielergröße die Nutzung von `/crawl` ebenso wenig wie die Nutzung von `/sit`.
Schleichen beendet das Krabbeln. Beide Haltungen enden außerdem bei
Tod, Verlassen des Servers, Flug, Gleitflug, Vanish und Wechsel in den Zuschauermodus; sie
schließen einander aus.

Player names can also be entered with an `@` prefix, for example `/openinv @Fabienne`.

## Permissions

- `pumpecraft.essentials.*`
- `pumpecraft.essentials.openinv`
- `pumpecraft.essentials.opendender`
- `pumpecraft.essentials.rename`
- `pumpecraft.essentials.sign`
- `pumpecraft.essentials.seen`
- `pumpecraft.essentials.broadcast`
- `pumpecraft.essentials.back`
- `pumpecraft.essentials.back.death`
- `pumpecraft.essentials.back.others`
- `pumpecraft.essentials.sit`
- `pumpecraft.essentials.crawl`

Die Inventar- und Broadcast-Berechtigungen sind standardmäßig deaktiviert. `rename` und `sign`
sind als bezahlte Spielerfunktionen standardmäßig aktiviert. Die drei `back`-Rechte
sowie `broadcast` sind für Moderatoren und Admins gedacht. `sit` und `crawl` sind als
Spielerfunktionen standardmäßig aktiviert.
