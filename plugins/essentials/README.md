# PumpeEssentials

`PumpeEssentials` requires `InvSeePlusPlus` 0.31.15 or newer for Paper 26.1.2.
Online inventories use the custom
live mirror. Offline inventories and ender chests use InvSee++'s persistent
playerdata implementation so edits are written back safely.

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
