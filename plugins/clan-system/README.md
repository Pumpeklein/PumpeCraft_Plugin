# PumpeClanSystem

Persistent clan and player-base system backed by `PumpeDatabase`.

## Clans

- `/clan create <Name> <Tag>` creates a clan with a 2-4 character tag and makes the player its owner.
- `/clan info [Clanname|Tag]` displays owner, creation date and members.
- `/clan whois <Spieler>` displays the clan of an online or known offline player.
- `/clan invite <Spieler>` stores an invitation for online or known offline players.
- `/clan request <ClanTag>` sends a persistent request to join a clan.
- `/clan requests` lists pending requests with clickable accept and deny actions.
- `/clan request accept|deny <Spieler>` handles a pending request.
- `/clan accept <ClanTag>` accepts an active invitation.
- `/clan leave` leaves the current clan. Owners must delete their clan.
- `/clan kick <Spieler>` removes an offline or online member.
- `/clan rename <NeuerName>` lets the owner rename the clan.
- `/clan role <Spieler> <co-owner|member>` lets the owner assign internal roles.
- `/clan transfer <Spieler> confirm` transfers ownership and makes the previous owner co-owner.
- `/clan color <Farbe>` changes the colored clan tag in the player list.
- `/clan delete confirm` permanently deletes the clan.
- `/clan admin-delete <ClanTag> <Begründung>` allows authorized staff to delete any clan.

New clan names, renamed clans and clan tags are checked against the editable
`clan-name-blacklist.yml`. The check ignores case, separators, accents, common
leetspeak substitutions and repeated letters. Existing clans are not changed
automatically when the blacklist is edited.

Open invitations are shown again whenever the invited player joins. After an
invitation is accepted, every online clan member is notified about the new member.
Clan selection completion only suggests clan tags; invite completion includes known
offline players from `pc_players`.

Clan tags use the same compact badge in the tab list, chat messages and the
scoreboard name tag above each player. They are refreshed from a cache and do
not query MariaDB from the server thread. Pending invitations are shown on join
and can be accepted by clicking the chat action.

## Clan roles

- `Owner` controls roles, rename, deletion, tag color, invitations and kicks.
- `Co-Owner` can invite players and accept or deny join requests.
- `Member` cannot perform clan management actions.

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
