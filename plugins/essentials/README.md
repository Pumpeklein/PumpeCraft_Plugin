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

Player names can also be entered with an `@` prefix, for example `/openinv @Fabienne`.

## Permissions

- `pumpecraft.essentials.*`
- `pumpecraft.essentials.openinv`
- `pumpecraft.essentials.opendender`

All permissions default to `false` and should be assigned through LuckPerms.
