# PumpePlaytime

Tracks player playtime for PumpeCraft.

## Features

- Starts tracking when a player joins.
- Stores playtime per player UUID in `plugins/PumpePlaytime/playtime-data.yml`.
- Tracks total online time.
- Tracks AFK time after 10 minutes without movement or interaction.
- Tracks active time while the player is moving, looking around, interacting, using commands, clicking inventories, changing hotbar slots, dropping or picking up items.
- Adds `[AFK]` in the tab list while a player is AFK.

## Commands

- `/playtime` - shows total, active and AFK playtime.

## Permissions

- `pumpecraft.playtime.use` - defaults to `true`.
