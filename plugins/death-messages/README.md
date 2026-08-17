# PumpeDeathMessages

Custom death, join and leave messages for PumpeCraft.

## Features

- Replaces vanilla death messages with custom German messages.
- Covers every current Paper `DamageCause` with 9 message variants.
- Every 5th death for a player uses a special milestone message (14 variants).
- Replaces the vanilla join and leave messages with the same kind of custom German messages;
  a player joining for the very first time gets his own welcome pool.
- Avoids using the same message template twice in a row globally, even across different players:
  once for all death messages (across causes and milestones), once for all join and leave messages.
- Tracks player death counts in MariaDB through PumpeDatabase.
- Imports an existing `plugins/PumpeDeathMessages/death-message-data.yml` once and leaves it untouched as a backup.

The messages are intentionally varied, sarcastic and less vanilla-like.

## Platzhalter

| Platzhalter | Bedeutung | Verfügbar in |
| --- | --- | --- |
| `{player}` | Name des Spielers | Tod, Join, Leave |
| `{killer}` | Name des Verursachers, sonst `das Universum` | Tod |
| `{deaths}` | Tode inklusive diesem | Tod |
| `{previousDeaths}` | Tode vor diesem | Tod |

## Join- und Leave-Meldungen

Die Texte liegen in `ConnectionMessages` in [plugins/utils](../utils/CLAUDE.md), nicht hier: Der
Vanish aus [plugins/mod](../mod/README.md) täuscht dasselbe Verlassen vor und zieht aus demselben
Topf. Eine eigene Formulierung dort würde einen versteckten Teamler sofort verraten.

Beide Handler laufen auf `EventPriority.LOW`, damit der Vanish die Meldung eines versteckten
Teamlers danach noch streichen kann.
