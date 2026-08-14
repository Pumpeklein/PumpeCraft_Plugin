# PumpeClanSystem

Persistent clan and player-base system backed by `PumpeDatabase`.

## Clans

- `/clan erstellen <Name> <Tag>` creates a clan and makes the player its owner.
- `/clan info [Clanname|Tag]` displays owner, creation date and members.
- `/clan einladen <Spieler>` stores an invitation for online or known offline players.
- `/clan annehmen <Clanname|Tag>` accepts an active invitation.
- `/clan verlassen` leaves the current clan. Owners must delete their clan.
- `/clan kicken <Spieler>` removes an offline or online member.
- `/clan tagfarbe <Farbe>` changes the colored clan tag in the player list.
- `/clan löschen bestätigen` permanently deletes the clan.

Clan tags are refreshed from a cache and do not query MariaDB from the server
thread. Pending invitations are shown on join and can be accepted by clicking
the chat action.

## Player bases

- `/base setzen [öffentlich|privat]` stores the current location.
- `/base öffentlich` and `/base privat` change access.
- `/base besuchen [Spieler]` teleports to an accessible base.
- `/base liken <Spieler>` adds one persistent like per player.
- `/base info [Spieler]` displays visibility, visits, unique visitors and likes.
- `/base löschen bestätigen` removes the base and its visit/like history.

Private coordinates are only visible to the owner and users with
`pumpecraft.base.admin`. Own visits and own likes do not increase statistics.

## Permissions

All permission nodes and defaults are defined in `permissions.yml` and are
registered dynamically for LuckPerms. No permission nodes are embedded in the
Java command classes.
