# PumpePlaytime

Tracks player playtime for PumpeCraft.

## Features

- Starts tracking when a player joins.
- Stores playtime per player UUID in MariaDB through PumpeDatabase.
- Imports an existing `plugins/PumpePlaytime/playtime-data.yml` once and leaves it untouched as a backup.
- Checks activity every second and tracks movement, chat, commands, inventory use and other interactions.
- Detects AFK after 10 minutes without activity and then reclassifies those full 10 minutes from active to AFK time.
- Switches back to active immediately after activity and starts a fresh 10-minute AFK timer.
- Calculates total playtime as active time plus AFK time.
- Adds `[AFK]` in the tab list while a player is AFK.
- Notifies the player when entering or leaving AFK mode.

## Commands

- `/playtime` - shows total, active and AFK playtime.

## Permissions

- `pumpecraft.playtime.use` - defaults to `true`.
