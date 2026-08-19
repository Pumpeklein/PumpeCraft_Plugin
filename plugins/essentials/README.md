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
- `/back` - teleportiert zum zuletzt verlassenen Punkt.
- `/back history` - zeigt die letzten Punkte mit anklickbaren Koordinaten und Index.
- `/back select <Index>` - teleportiert zum Punkt mit diesem Index.
- `/back user <Spieler>` - setzt einen anderen Spieler auf dessen letzten Punkt zurück.
- `/back user <Spieler> history` - zeigt den Verlauf eines anderen Spielers, auch offline.
- `/back user <Spieler> <Index>` - setzt einen anderen Spieler auf den Punkt mit diesem Index zurück.

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

Player names can also be entered with an `@` prefix, for example `/openinv @Fabienne`.

## Permissions

- `pumpecraft.essentials.*`
- `pumpecraft.essentials.openinv`
- `pumpecraft.essentials.opendender`
- `pumpecraft.essentials.rename`
- `pumpecraft.essentials.sign`
- `pumpecraft.essentials.seen`
- `pumpecraft.essentials.back`
- `pumpecraft.essentials.back.death`
- `pumpecraft.essentials.back.others`

Die Inventarberechtigungen sind standardmäßig deaktiviert. `rename` und `sign`
sind als bezahlte Spielerfunktionen standardmäßig aktiviert. Die drei `back`-Rechte
sind standardmäßig deaktiviert und für Moderatoren und Admins gedacht.
