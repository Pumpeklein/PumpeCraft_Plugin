# PumpeTrader

Temporary server trader for PumpeCraft.

## Commands

- `/trader <Zeit>` - spawns a temporary trader at your current location.
- `/trader del` - removes all active event traders before their timers expire.

Supported times: `30s`, `15m`, `1h`, `2h`, `1d`. Plain numbers are treated as minutes.

## Behavior

- Broadcasts the trader location when spawned; the wording comes from `TraderTopics` over
  `Messages.render`.
- Trader does not move, is silent, non-collidable and invulnerable.
- Trader despawns after the given time and broadcasts a despawn message.
- Trades are paid directly with PumpePoints through a protected cart menu.
- Left click adds one item, shift-left-click adds ten, right click removes one,
  shift-right-click removes ten, and middle click deselects that product completely.
- The paper and window title show the exact total. A separate confirmation window lists the
  selected quantities and final price before PumpePoints are charged.

## Special Items

Current trades:

- 1 Light Block for 4.500 PP
- 1 Invisible Item Frame for 1.500 PP
- 1 Invisible Glow Item Frame for 2.250 PP
- 1 Sponge for 500 PP

Invisible item frames and invisible glow item frames keep their special ability when placed and when broken again.

## Permission

- `pumpecraft.trader.*`
- `pumpecraft.trader.spawn`
- `pumpecraft.trader.delete`

All trader permissions default to `false` and should be assigned through LuckPerms.
