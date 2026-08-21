# PumpeBaseSystem

Player bases backed by `PumpeDatabase`: one base per player, with visibility, visit and like
statistics, a browsable directory and an in-game menu.

The tables (`pc_player_bases`, `pc_base_visitors`, `pc_base_likes`) are created by the Flyway
migration `V11__clans_and_player_bases.sql` in `plugins/database`. The module was split out of
`plugins/clan-system`; the permission nodes stay unchanged, so existing LuckPerms groups keep
working.

## Menu

`/base` without arguments opens the menu; `/base menu` does the same.

- **Base-Menü** — own base card (visibility, world, position, visits, unique visitors, likes,
  age), set or move the base, toggle visibility, open the visitor and like lists, open the
  directory, delete the base.
- **Alle Basen** — paginated directory of every base the viewer may see, sorted by likes, visits,
  last change or name. Staff with `pumpecraft.base.admin` also see private bases.
- **Base · Spieler** — detail view of a single base with visit and like buttons.
- **Besucher / Likes** — paginated lists of who visited or liked the own base; a click jumps to
  that player's base.
- **Base löschen?** — confirmation before the base and its statistics are removed.

## Commands

- `/base` opens the menu, `/base liste` opens the directory.
- `/base set [public|private]` stores the current location. Without an argument an existing base
  keeps its visibility; a new base uses `bases.default-public`.
- `/base public` and `/base private` change access.
- `/base visit [Spieler]` teleports to an accessible base.
- `/base like <Spieler>` toggles the like for that base.
- `/base info [Spieler]` displays visibility, visits, unique visitors and likes.
- `/base delete confirm` removes the base and its visit/like history; `/base delete` asks in
  the menu instead.
- Console: `/base info <Spieler>` and `/base as <Spieler> <Unterbefehl>`.

Private coordinates are only visible to the owner and to users with `pumpecraft.base.admin`.
For them the position in `/base info` is a clickable teleport target. Own visits and own likes do
not change any statistics; every further visit of the same player raises the total counter but not
the number of unique visitors.

## Configuration

`config.yml` carries `config-version` plus the `bases` section: `default-public`,
`visit-cooldown-seconds` (lock between visits of foreign bases), `browse-limit` (upper bound of
the directory) and `directory-refresh-seconds` (refresh interval of the tab completion cache).

## Permissions

All permission nodes and defaults are defined in `permissions.yml` and are registered dynamically
for LuckPerms. No permission nodes are embedded in the Java classes.
