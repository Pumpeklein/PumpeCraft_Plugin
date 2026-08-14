# PumpeClanSystem

Persistent clan and player-base system backed by `PumpeDatabase`.

## Clans

- `/clan create <Name> <Tag>` creates a clan and makes the player its owner.
- `/clan info [Clanname|Tag]` displays owner, creation date and members.
- `/clan invite <Spieler>` stores an invitation for online or known offline players.
- `/clan accept <ClanTag>` accepts an active invitation.
- `/clan leave` leaves the current clan. Owners must delete their clan.
- `/clan kick <Spieler>` removes an offline or online member.
- `/clan color <Farbe>` changes the colored clan tag in the player list.
- `/clan delete confirm` permanently deletes the clan.

Open invitations are shown again whenever the invited player joins. After an
invitation is accepted, every online clan member is notified about the new member.
Clan selection completion only suggests clan tags; invite completion includes known
offline players from `pc_players`.

Clan tags are refreshed from a cache and do not query MariaDB from the server
thread. Pending invitations are shown on join and can be accepted by clicking
the chat action.

## Player bases

- `/base set [public|private]` stores the current location.
- `/base public` and `/base private` change access.
- `/base visit [Spieler]` teleports to an accessible base.
- `/base like <Spieler>` adds one persistent like per player.
- `/base info [Spieler]` displays visibility, visits, unique visitors and likes.
- `/base delete confirm` removes the base and its visit/like history.

Private coordinates are only visible to the owner and users with
`pumpecraft.base.admin`. Own visits and own likes do not increase statistics.

## Permissions

All permission nodes and defaults are defined in `permissions.yml` and are
registered dynamically for LuckPerms. No permission nodes are embedded in the
Java command classes.
