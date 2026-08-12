# PumpeMod

Moderation commands, punishments, reports and staff tools.

## Commands

- `/report <Spieler> <Grund>` - reports a known player to the team.
- `/reports [unseen|all]` - shows unseen or all open reports.
- `/warn <Spieler> <Grund>` - warns a known player and stores the warning count.
- `/mute <Spieler> <Zeit> [Grund]` - mutes a known player for the given duration.
- `/unmute <Spieler>` - lifts an active mute.
- `/ban <Spieler> <Grund> [Zeit]` - bans a known player permanently or for a duration.
- `/unban <Spieler> [Grund]` - lifts an active ban.
- `/vanish` - toggles staff vanish with fake leave/join visibility.

Player names can also be entered with an `@` prefix, for example `/report @Fabienne Griefing`.
Targets can be online or known offline players.

## Zeitangaben

Alle Strafbefehle nutzen dasselbe Format:

| Einheit | Suffix | Beispiel |
| --- | --- | --- |
| Sekunden | `s` | `30s` |
| Minuten | `m` | `10m` |
| Stunden | `h` | `2h` |
| Tage | `d` | `7d` |
| Wochen | `w` | `1w` |

Einheiten lassen sich kombinieren (`1h30m`, `2d12h`). Eine Zahl ohne Suffix gilt
als Minuten (`/mute Steve 10` = 10 Minuten). Maximum sind 10 Jahre.

Bei `/ban` steht die Zeit **hinten**: Das letzte Argument wird als Zeitangabe
gelesen, wenn es dem Format entspricht, sonst gehört es zum Grund. Ohne Zeit ist
der Ban permanent.

```mcfunction
/mute Steve 30s Spam im Chat
/mute Steve 1h30m Beleidigung
/ban Steve Griefing 7d
/ban Steve Cheating
/unmute Steve
/unban Steve Einspruch akzeptiert
```

## Bans und Mutes in der Datenbank

Bans und Mutes liegen ausschließlich in MariaDB, nicht in `banned-players.json`:

- Beim Login prüft `AsyncPlayerPreLoginEvent` die Tabelle `pc_punishments` und
  weist gebannte Spieler mit dem eigenen Ban-Bildschirm ab. Panel, Server und
  Datenbank zeigen damit denselben Stand.
- `/unban` setzt `revoked_at`, `revoked_by` und `revoke_reason`, statt Einträge zu
  löschen - die Historie bleibt im Panel sichtbar. Zusätzlich werden alte
  Einträge aus `banned-players.json` entfernt, damit Spieler nicht doppelt
  ausgesperrt bleiben.
- `/unmute` setzt `unmuted_at` und `unmuted_by`; abgelaufene Mutes bleiben
  ebenfalls als Historie stehen.
- Der aktive Mute wird beim Login einmalig geladen und im Speicher gehalten. Der
  Chat-Check läuft dadurch ohne Datenbankabfrage pro Nachricht.

Fällt die Datenbank beim Login aus, wird der Fehler geloggt und der Spieler
durchgelassen, statt den kompletten Login zu blockieren.

Die Spalten kommen mit Migration `V3__punishment_lifecycle.sql` aus
`plugins/database`.

## Permissions

- `pumpecraft.mod.*`
- `pumpecraft.mod.report`
- `pumpecraft.mod.reports`
- `pumpecraft.mod.warn`
- `pumpecraft.mod.mute`
- `pumpecraft.mod.unmute`
- `pumpecraft.mod.ban`
- `pumpecraft.mod.unban`
- `pumpecraft.mod.vanish`

All permissions default to `false` and should be assigned through LuckPerms.

Recommended LuckPerms setup:

```mcfunction
/lp group support permission set pumpecraft.mod.* true
/lp group default permission set pumpecraft.mod.report true
```
