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

`/rename` und `/sign` werden mit PP bezahlt. Der Preis berücksichtigt Material,
Stackgröße, Haltbarkeit und Verzauberungen und lässt sich in `config.yml` anpassen.

Player names can also be entered with an `@` prefix, for example `/openinv @Fabienne`.

## Permissions

- `pumpecraft.essentials.*`
- `pumpecraft.essentials.openinv`
- `pumpecraft.essentials.opendender`
- `pumpecraft.essentials.rename`
- `pumpecraft.essentials.sign`

Die Inventarberechtigungen sind standardmäßig deaktiviert. `rename` und `sign`
sind als bezahlte Spielerfunktionen standardmäßig aktiviert.
