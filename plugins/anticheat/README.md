# PumpeAntiCheat

Server-side checks for Speed, Fly, NoFall, FastPlace, FastBreak, Reach,
AutoClicker and Scaffold. Floodgate and Geyser are optional and detected at runtime.

Bedrock players receive separate, more tolerant thresholds. Movement checks
also ignore legitimate states such as creative/spectator mode, flight, Elytra,
vehicles, liquids, climbing, teleports, velocity and relevant potion effects.

The plugin records violation levels and alerts staff. It does not automatically
ban players. Clearly invalid movement, reach, placing and breaking can be
cancelled once the configured violation level is reached.

## Command

- `/anticheat status [player]`
- `/anticheat violations [player]`
- `/anticheat reset <player>`
- `/anticheat reload`

All permissions are declared in `plugin.yml` and default to `false`.
