# PumpeDeathMessages

Custom death messages for PumpeCraft.

## Features

- Replaces vanilla death messages with custom German messages.
- Covers every current Paper `DamageCause` with at least 5 message variants.
- Avoids using the same message template twice in a row globally, even across different players.
- Tracks player death counts in MariaDB through PumpeDatabase.
- Imports an existing `plugins/PumpeDeathMessages/death-message-data.yml` once and leaves it untouched as a backup.
- Every 5th death for a player uses a special milestone message.

The messages are intentionally varied, sarcastic and less vanilla-like.
