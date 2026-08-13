# PumpePlaytime

Tracks player playtime for PumpeCraft.

## Features

- Starts tracking when a player joins.
- Stores playtime per player UUID in MariaDB through PumpeDatabase.
- Imports an existing `plugins/PumpePlaytime/playtime-data.yml` once and leaves it untouched as a backup.
- Tracks active time from joining until the player is detected as AFK.
- Tracks AFK time after 10 minutes without movement, chat, commands, inventory use or other interactions and switches back to active immediately after activity.
- Calculates total playtime as active time plus AFK time.
- Adds `[AFK]` in the tab list while a player is AFK.
- Notifies the player when entering or leaving AFK mode.

## Commands

- `/playtime` - shows total, active and AFK playtime.

## Permissions

- `pumpecraft.playtime.use` - defaults to `true`.
