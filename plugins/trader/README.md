# PumpeTrader

Temporary server trader for PumpeCraft.

## Commands

- `/trader <Zeit>` - spawns a temporary trader at your current location.

Supported times: `30s`, `15m`, `1h`, `2h`, `1d`. Plain numbers are treated as minutes.

## Behavior

- Broadcasts the trader location when spawned.
- Trader does not move, is silent, non-collidable and invulnerable.
- Trader despawns after the given time and broadcasts a despawn message.
- Trades use item combinations instead of emeralds and are priced for practical building use.

## Special Items

Current trades:

- 1 Light Block for 5 Soul Sand and 1 Cauldron
- 2 Invisible Item Frames for 6 Leather and 8 Glass Panes
- 1 Invisible Glow Item Frame for 2 Glow Ink Sacs and 8 Glass Panes
- 2 Sponges for 12 Prismarine Shards and 32 Kelp

Invisible item frames and invisible glow item frames keep their special ability when placed and when broken again.

## Permission

- `pumpecraft.trader.*`
- `pumpecraft.trader.spawn`

All trader permissions default to `false` and should be assigned through LuckPerms.
