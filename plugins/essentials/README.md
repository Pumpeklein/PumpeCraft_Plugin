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

`/rename` und `/sign` werden mit PP bezahlt. Die Formel lautet Grundpreis plus
Zeichenpreis pro Nicht-Leerzeichen plus Itemwert-Aufschlag. Rename kostet standardmäßig
175 PP + 15 PP je Zeichen + 15 % des Itemwerts. Sign kostet 100 PP + 8 PP je Zeichen
der optionalen Nachricht + 10 % des Itemwerts. Material, Stackgröße, Haltbarkeit und
Verzauberungen bestimmen den Itemwert; alle Faktoren lassen sich in `config.yml` anpassen.

Player names can also be entered with an `@` prefix, for example `/openinv @Fabienne`.

## Permissions

- `pumpecraft.essentials.*`
- `pumpecraft.essentials.openinv`
- `pumpecraft.essentials.opendender`
- `pumpecraft.essentials.rename`
- `pumpecraft.essentials.sign`
- `pumpecraft.essentials.seen`

Die Inventarberechtigungen sind standardmäßig deaktiviert. `rename` und `sign`
sind als bezahlte Spielerfunktionen standardmäßig aktiviert.
