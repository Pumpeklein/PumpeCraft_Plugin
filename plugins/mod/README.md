# PumpeMod

Planned area for moderation commands, punishments, reports and staff tools.

## Commands

- `/report <Spieler> <Grund>` - reports a known player to the team.
- `/reports [unseen|all]` - shows unseen or all open reports.
- `/warn <Spieler> <Grund>` - warns a known player and stores the warning count.
- `/mute <Spieler> <Minuten> [Grund]` - mutes a known player for the given minutes.
- `/ban <Spieler> <Grund> [Zeit]` - bans a known player permanently or for a duration.
- `/vanish` - toggles staff vanish with fake leave/join visibility.

Player names can also be entered with an `@` prefix, for example `/report @Fabienne Griefing`.
Targets can be online or known offline players.

Ban durations support plain minutes and suffixes: `30`, `30m`, `2h`, `7d`, `1w`.

## Permissions

- `pumpecraft.mod.*`
- `pumpecraft.mod.report`
- `pumpecraft.mod.reports`
- `pumpecraft.mod.warn`
- `pumpecraft.mod.mute`
- `pumpecraft.mod.ban`
- `pumpecraft.mod.vanish`

All permissions default to `false` and should be assigned through LuckPerms.

Recommended LuckPerms setup:

```mcfunction
/lp group support permission set pumpecraft.mod.* true
/lp group default permission set pumpecraft.mod.report true
```
