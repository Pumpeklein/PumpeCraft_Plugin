# PumpeAntiCheat

Server-side checks for Speed, Fly, NoFall, FastPlace, FastBreak, Reach,
AutoClicker, Scaffold and Xray. Floodgate and Geyser are optional and detected at runtime.

Bedrock players receive separate, more tolerant thresholds. Movement checks
also ignore legitimate states such as creative/spectator mode, flight, Elytra,
vehicles, liquids, climbing, teleports, velocity and relevant potion effects.

The plugin records violation levels and alerts staff. It does not automatically
ban players. Clearly invalid movement, reach, placing and breaking can be
cancelled once the configured violation level is reached.

Xray is detected statistically from newly discovered rare ore veins, mining
ratios and repeated direct paths. Connected blocks in one vein count as one
discovery. Xray only alerts staff and never removes mined blocks or items.

## Client detection

Staff receive a delayed join message with Java/Bedrock platform and the client
brand reported through `minecraft:brand`. Registered plugin channels are
periodically matched against configurable signatures for common mod loaders,
clients and known cheat clients. Detected signatures are reported once per
session. If the server sends a resource pack, its load or rejection status is
also reported.

Paper cannot inspect arbitrary local mods or user-selected resource packs.
Clients can hide or spoof brands and channels, so these results are indicators
and must not be treated as definitive proof by themselves.

## Command

- `/anticheat status [player]`
- `/anticheat violations [player]`
- `/anticheat reset <player>`
- `/anticheat reload`

All permissions are declared in `plugin.yml` and default to `false`.
