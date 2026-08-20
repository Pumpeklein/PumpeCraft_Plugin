# PumpeTrader

Temporary server trader for PumpeCraft.

## Commands

- `/trader <Zeit>` - spawns a temporary trader at your current location.
- `/trader del` - removes all active event traders before their timers expire.

Supported times: `30s`, `15m`, `1h`, `2h`, `1d`. Plain numbers are treated as minutes.

## Behavior

- Broadcasts the trader location when spawned; the wording comes from `TraderTopics` over
  `Messages.render`, so [PumpeAI](../ai/README.md) can take it over when it runs.
- Trader does not move, is silent, non-collidable and invulnerable.
- Trader despawns after the given time and broadcasts a despawn message.
- Trades are paid directly with PumpePoints. The protected price item in the merchant input is
  supplied by the server and cannot be removed.

## Special Items

Current trades:

- 1 Light Block for 2.500 PP
- 2 Invisible Item Frames for 1.500 PP (750 PP each)
- 1 Invisible Glow Item Frame for 1.250 PP
- 2 Sponges for 200 PP (100 PP each)

Invisible item frames and invisible glow item frames keep their special ability when placed and when broken again.

## Permission

- `pumpecraft.trader.*`
- `pumpecraft.trader.spawn`
- `pumpecraft.trader.delete`

All trader permissions default to `false` and should be assigned through LuckPerms.
