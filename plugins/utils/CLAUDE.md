# PumpeUtils

Bibliotheks-Plugin ohne Spiellogik und ohne Zustand. Enthält ausschließlich Helfer, die
mindestens zwei Plugins brauchen könnten.

## Regel

Bevor irgendwo eine Hilfsmethode entsteht: hier nachsehen. Wenn sie hier fehlt und mehr als
ein Plugin sie brauchen könnte, gehört sie hierher — nicht in das aufrufende Plugin und erst
recht nicht kopiert. Wenn sie nur an einer Stelle Sinn ergibt, bleibt sie privat im
aufrufenden Plugin.

Nutzung: `depend: [PumpeUtils]` in der `plugin.yml`. Der Gradle-Classpath kommt automatisch
über `libraryModulePaths` in `build.gradle.kts`.

## Bestand

| Klasse | Inhalt |
| --- | --- |
| `Texts` | `decimal`, `percent`, `truncate`, `joinLimited` (Liste mit `(+N)`-Überlauf), `lower` |
| `Players` | `online`, `known`, `self`, `completeKnownNames`, `completeOnlineNames`, `filterPrefix`, `displayName`, `stripSelector` (`@Name`) |
| `Locations` | `horizontalDistance`, `samePosition`, `sameWorld`, `distanceToBox`, `aimDot`, `clamp` |
| `Cooldowns<K>` | Schlüssel-basierte Sperren: `tryAcquire`, `active`, `remainingMillis`, `purgeExpired` |
| `Rates` | Gleitfenster über `Deque<Long>`: `record`, `trim`, `spread` (Rate + Streuung der Intervalle) |
| `Staff` | `withPermission`, `broadcast` — Meldungen an Berechtigte |
| `Teleports` | `playerLink`, `locationLink`, `clickable`, `coordinates` — klickbare Teleport-Ziele in Chatmeldungen |
| `Configs` | `lowerStringList`, `matchesAny` (exakter Token-Abgleich mit `*`-Präfix) |

## Offene Migration

`PumpeMod`, `PumpeClanSystem` und `PumpeSkills` haben noch eigene Kopien von
`findKnownPlayer`, `completeKnownPlayers` und `format(double)`. Wer dort ohnehin etwas
anfasst, ersetzt sie durch `Players` beziehungsweise `Texts`.
