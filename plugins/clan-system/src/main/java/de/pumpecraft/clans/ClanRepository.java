package de.pumpecraft.clans;

import de.pumpecraft.clans.ClanData.AcceptInviteResult;
import de.pumpecraft.clans.ClanData.ChangeRoleResult;
import de.pumpecraft.clans.ClanData.Clan;
import de.pumpecraft.clans.ClanData.ClanDetails;
import de.pumpecraft.clans.ClanData.CreateClanResult;
import de.pumpecraft.clans.ClanData.CreateJoinRequestResult;
import de.pumpecraft.clans.ClanData.Directory;
import de.pumpecraft.clans.ClanData.Invitation;
import de.pumpecraft.clans.ClanData.JoinRequest;
import de.pumpecraft.clans.ClanData.Member;
import de.pumpecraft.clans.ClanData.PlayerBase;
import de.pumpecraft.clans.ClanData.PlayerIdentity;
import de.pumpecraft.clans.ClanData.RemoveMemberResult;
import de.pumpecraft.clans.ClanData.RenameClanResult;
import de.pumpecraft.clans.ClanData.ResolveJoinRequestResult;
import de.pumpecraft.clans.ClanData.TabEntry;
import de.pumpecraft.clans.ClanData.TransferOwnershipResult;
import de.pumpecraft.database.DatabaseService;
import de.pumpecraft.database.Databases;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class ClanRepository {
    private final DatabaseService database;

    ClanRepository(PumpeClanSystemPlugin plugin) {
        database = Databases.require(plugin);
    }

    Optional<PlayerIdentity> findKnownPlayer(String playerName) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT player_uuid, player_name
                  FROM pc_players
                 WHERE LOWER(player_name) = LOWER(?)
                 ORDER BY last_seen DESC
                 LIMIT 1
                """
            )) {
                statement.setString(1, playerName);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return Optional.of(new PlayerIdentity(
                            UUID.fromString(result.getString("player_uuid")),
                            result.getString("player_name")
                        ));
                    }
                }
            }
            return Optional.empty();
        });
    }

    CreateClanResult createClan(
        PlayerIdentity owner,
        String clanName,
        String clanTag,
        long createdAt
    ) {
        return database.inTransaction(connection -> {
            if (clanForPlayer(connection, owner.playerId()).isPresent()) {
                return CreateClanResult.ALREADY_MEMBER;
            }
            if (clanExists(connection, "clan_name", clanName)) {
                return CreateClanResult.NAME_TAKEN;
            }
            if (clanExists(connection, "clan_tag", clanTag)) {
                return CreateClanResult.TAG_TAKEN;
            }

            long clanId;
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_clans
                    (clan_name, clan_tag, tag_color, owner_uuid, owner_name, created_at)
                VALUES (?, ?, 'AQUA', ?, ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            )) {
                statement.setString(1, clanName);
                statement.setString(2, clanTag.toUpperCase(Locale.ROOT));
                statement.setString(3, owner.playerId().toString());
                statement.setString(4, owner.playerName());
                statement.setLong(5, createdAt);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Clan insert returned no generated key.");
                    }
                    clanId = keys.getLong(1);
                }
            }
            insertMember(connection, clanId, owner, "OWNER", createdAt);
            return CreateClanResult.CREATED;
        });
    }

    Optional<Clan> clanForPlayer(UUID playerId) {
        return database.withConnection(connection -> clanForPlayer(connection, playerId));
    }

    Optional<Member> member(UUID playerId) {
        return database.withConnection(connection -> member(connection, playerId));
    }

    Optional<ClanDetails> clanDetails(String nameOrTag) {
        return database.withConnection(connection -> {
            Optional<Clan> clan = findClan(connection, nameOrTag);
            if (clan.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ClanDetails(clan.get(), members(connection, clan.get().id())));
        });
    }

    Optional<ClanDetails> clanDetailsForPlayer(UUID playerId) {
        return database.withConnection(connection -> {
            Optional<Clan> clan = clanForPlayer(connection, playerId);
            if (clan.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new ClanDetails(clan.get(), members(connection, clan.get().id())));
        });
    }

    boolean deleteClan(long clanId, UUID ownerId) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM pc_clans WHERE id = ? AND owner_uuid = ?"
            )) {
                statement.setLong(1, clanId);
                statement.setString(2, ownerId.toString());
                return statement.executeUpdate() > 0;
            }
        });
    }

    boolean adminDeleteClan(
        long clanId,
        PlayerIdentity deletedBy,
        String reason,
        long deletedAt
    ) {
        return database.inTransaction(connection -> {
            int audited;
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_clan_deletion_audit
                    (clan_id, clan_name, clan_tag, owner_uuid, owner_name,
                     deleted_by_uuid, deleted_by_name, reason, deleted_at)
                SELECT id, clan_name, clan_tag, owner_uuid, owner_name, ?, ?, ?, ?
                  FROM pc_clans
                 WHERE id = ?
                """
            )) {
                statement.setString(1, deletedBy.playerId().toString());
                statement.setString(2, deletedBy.playerName());
                statement.setString(3, reason);
                statement.setLong(4, deletedAt);
                statement.setLong(5, clanId);
                audited = statement.executeUpdate();
            }
            if (audited == 0) {
                return false;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM pc_clans WHERE id = ?"
            )) {
                statement.setLong(1, clanId);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Audited clan could not be deleted: " + clanId);
                }
            }
            return true;
        });
    }

    boolean setTagColor(long clanId, UUID ownerId, String color) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pc_clans SET tag_color = ? WHERE id = ? AND owner_uuid = ?"
            )) {
                statement.setString(1, color);
                statement.setLong(2, clanId);
                statement.setString(3, ownerId.toString());
                return statement.executeUpdate() > 0;
            }
        });
    }

    RenameClanResult renameClan(long clanId, UUID ownerId, String newName) {
        return database.inTransaction(connection -> {
            if (!ownsClan(connection, clanId, ownerId)) {
                return RenameClanResult.NOT_OWNER;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM pc_clans WHERE LOWER(clan_name) = LOWER(?) AND id <> ?"
            )) {
                statement.setString(1, newName);
                statement.setLong(2, clanId);
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        return RenameClanResult.NAME_TAKEN;
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pc_clans SET clan_name = ? WHERE id = ? AND owner_uuid = ?"
            )) {
                statement.setString(1, newName);
                statement.setLong(2, clanId);
                statement.setString(3, ownerId.toString());
                return statement.executeUpdate() > 0
                    ? RenameClanResult.RENAMED
                    : RenameClanResult.NOT_OWNER;
            }
        });
    }

    ChangeRoleResult changeMemberRole(
        long clanId,
        UUID ownerId,
        String memberName,
        String newRole
    ) {
        return database.inTransaction(connection -> {
            if (!ownsClan(connection, clanId, ownerId)) {
                return ChangeRoleResult.NOT_OWNER;
            }
            Optional<Member> target = member(connection, clanId, memberName);
            if (target.isEmpty()) {
                return ChangeRoleResult.NOT_MEMBER;
            }
            if (target.get().owner()) {
                return ChangeRoleResult.OWNER_PROTECTED;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pc_clan_members SET member_role = ? WHERE clan_id = ? AND player_uuid = ?"
            )) {
                statement.setString(1, newRole);
                statement.setLong(2, clanId);
                statement.setString(3, target.get().playerId().toString());
                statement.executeUpdate();
                return ChangeRoleResult.CHANGED;
            }
        });
    }

    TransferOwnershipResult transferOwnership(
        long clanId,
        UUID ownerId,
        String newOwnerName
    ) {
        return database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT owner_uuid FROM pc_clans WHERE id = ? FOR UPDATE"
            )) {
                statement.setLong(1, clanId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()
                        || !ownerId.toString().equals(result.getString("owner_uuid"))) {
                        return TransferOwnershipResult.NOT_OWNER;
                    }
                }
            }
            Optional<Member> target = member(connection, clanId, newOwnerName);
            if (target.isEmpty()) {
                return TransferOwnershipResult.NOT_MEMBER;
            }
            if (target.get().owner()) {
                return TransferOwnershipResult.ALREADY_OWNER;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pc_clan_members SET member_role = 'CO_OWNER' WHERE clan_id = ? AND player_uuid = ?"
            )) {
                statement.setLong(1, clanId);
                statement.setString(2, ownerId.toString());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pc_clan_members SET member_role = 'OWNER' WHERE clan_id = ? AND player_uuid = ?"
            )) {
                statement.setLong(1, clanId);
                statement.setString(2, target.get().playerId().toString());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pc_clans SET owner_uuid = ?, owner_name = ? WHERE id = ? AND owner_uuid = ?"
            )) {
                statement.setString(1, target.get().playerId().toString());
                statement.setString(2, target.get().playerName());
                statement.setLong(3, clanId);
                statement.setString(4, ownerId.toString());
                return statement.executeUpdate() > 0
                    ? TransferOwnershipResult.TRANSFERRED
                    : TransferOwnershipResult.NOT_OWNER;
            }
        });
    }

    void addNotifications(Collection<UUID> playerIds, String message, long createdAt) {
        if (playerIds.isEmpty()) {
            return;
        }
        database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO pc_clan_notifications (player_uuid, message, created_at) VALUES (?, ?, ?)"
            )) {
                for (UUID playerId : playerIds) {
                    statement.setString(1, playerId.toString());
                    statement.setString(2, message);
                    statement.setLong(3, createdAt);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            return null;
        });
    }

    List<String> takeNotifications(UUID playerId) {
        return database.inTransaction(connection -> {
            List<String> messages = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT message FROM pc_clan_notifications WHERE player_uuid = ? ORDER BY created_at, id"
            )) {
                statement.setString(1, playerId.toString());
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        messages.add(result.getString("message"));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM pc_clan_notifications WHERE player_uuid = ?"
            )) {
                statement.setString(1, playerId.toString());
                statement.executeUpdate();
            }
            return List.copyOf(messages);
        });
    }

    CreateJoinRequestResult createJoinRequest(
        PlayerIdentity player,
        String nameOrTag,
        long now
    ) {
        return database.inTransaction(connection -> {
            if (clanForPlayer(connection, player.playerId()).isPresent()) {
                return CreateJoinRequestResult.ALREADY_MEMBER;
            }
            Optional<Clan> clan = findClan(connection, nameOrTag);
            if (clan.isEmpty()) {
                return CreateJoinRequestResult.CLAN_NOT_FOUND;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT IGNORE INTO pc_clan_join_requests
                    (clan_id, player_uuid, player_name, created_at)
                VALUES (?, ?, ?, ?)
                """
            )) {
                statement.setLong(1, clan.get().id());
                statement.setString(2, player.playerId().toString());
                statement.setString(3, player.playerName());
                statement.setLong(4, now);
                return statement.executeUpdate() > 0
                    ? CreateJoinRequestResult.REQUESTED
                    : CreateJoinRequestResult.ALREADY_REQUESTED;
            }
        });
    }

    List<JoinRequest> joinRequests(long clanId) {
        return database.withConnection(connection -> {
            List<JoinRequest> requests = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT player_uuid, player_name, created_at
                  FROM pc_clan_join_requests
                 WHERE clan_id = ?
                 ORDER BY created_at
                """
            )) {
                statement.setLong(1, clanId);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        requests.add(new JoinRequest(
                            clanId,
                            new PlayerIdentity(
                                UUID.fromString(result.getString("player_uuid")),
                                result.getString("player_name")
                            ),
                            result.getLong("created_at")
                        ));
                    }
                }
            }
            return requests;
        });
    }

    ResolveJoinRequestResult acceptJoinRequest(
        UUID actorId,
        String playerName,
        int maxMembers,
        long now
    ) {
        return database.inTransaction(connection -> {
            Optional<Member> actor = member(connection, actorId);
            Optional<Clan> clan = clanForPlayer(connection, actorId);
            if (actor.isEmpty() || clan.isEmpty() || !actor.get().canManageMembership()) {
                return ResolveJoinRequestResult.NOT_ALLOWED;
            }
            Optional<PlayerIdentity> target = joinRequestPlayer(
                connection, clan.get().id(), playerName);
            if (target.isEmpty()) {
                return ResolveJoinRequestResult.NOT_FOUND;
            }
            if (clanForPlayer(connection, target.get().playerId()).isPresent()) {
                deleteJoinRequests(connection, target.get().playerId());
                return ResolveJoinRequestResult.ALREADY_MEMBER;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM pc_clans WHERE id = ? FOR UPDATE"
            )) {
                statement.setLong(1, clan.get().id());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return ResolveJoinRequestResult.NOT_FOUND;
                    }
                }
            }
            if (memberCount(connection, clan.get().id()) >= maxMembers) {
                return ResolveJoinRequestResult.CLAN_FULL;
            }
            insertMember(connection, clan.get().id(), target.get(), "MEMBER", now);
            deleteJoinRequests(connection, target.get().playerId());
            deleteInvitations(connection, target.get().playerId());
            return ResolveJoinRequestResult.ACCEPTED;
        });
    }

    ResolveJoinRequestResult denyJoinRequest(UUID actorId, String playerName) {
        return database.inTransaction(connection -> {
            Optional<Member> actor = member(connection, actorId);
            Optional<Clan> clan = clanForPlayer(connection, actorId);
            if (actor.isEmpty() || clan.isEmpty() || !actor.get().canManageMembership()) {
                return ResolveJoinRequestResult.NOT_ALLOWED;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM pc_clan_join_requests WHERE clan_id = ? AND LOWER(player_name) = LOWER(?)"
            )) {
                statement.setLong(1, clan.get().id());
                statement.setString(2, playerName);
                return statement.executeUpdate() > 0
                    ? ResolveJoinRequestResult.DENIED
                    : ResolveJoinRequestResult.NOT_FOUND;
            }
        });
    }

    boolean invite(
        long clanId,
        PlayerIdentity target,
        PlayerIdentity invitedBy,
        long createdAt,
        long expiresAt
    ) {
        return database.inTransaction(connection -> {
            if (clanForPlayer(connection, target.playerId()).isPresent()) {
                return false;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_clan_invitations
                    (clan_id, player_uuid, player_name, invited_by_uuid,
                     invited_by_name, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    player_name = VALUES(player_name),
                    invited_by_uuid = VALUES(invited_by_uuid),
                    invited_by_name = VALUES(invited_by_name),
                    created_at = VALUES(created_at),
                    expires_at = VALUES(expires_at)
                """
            )) {
                statement.setLong(1, clanId);
                statement.setString(2, target.playerId().toString());
                statement.setString(3, target.playerName());
                statement.setString(4, invitedBy.playerId().toString());
                statement.setString(5, invitedBy.playerName());
                statement.setLong(6, createdAt);
                statement.setLong(7, expiresAt);
                statement.executeUpdate();
                return true;
            }
        });
    }

    AcceptInviteResult acceptInvitation(
        PlayerIdentity player,
        String nameOrTag,
        int maxMembers,
        long now
    ) {
        return database.inTransaction(connection -> {
            if (clanForPlayer(connection, player.playerId()).isPresent()) {
                return AcceptInviteResult.ALREADY_MEMBER;
            }
            Optional<Clan> clan = findClan(connection, nameOrTag);
            if (clan.isEmpty()) {
                return AcceptInviteResult.NOT_INVITED;
            }
            long expiresAt;
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT expires_at
                  FROM pc_clan_invitations
                 WHERE clan_id = ? AND player_uuid = ?
                 FOR UPDATE
                """
            )) {
                statement.setLong(1, clan.get().id());
                statement.setString(2, player.playerId().toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return AcceptInviteResult.NOT_INVITED;
                    }
                    expiresAt = result.getLong("expires_at");
                }
            }
            if (expiresAt <= now) {
                deleteInvitations(connection, player.playerId());
                return AcceptInviteResult.INVITATION_EXPIRED;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM pc_clans WHERE id = ? FOR UPDATE"
            )) {
                statement.setLong(1, clan.get().id());
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return AcceptInviteResult.NOT_INVITED;
                    }
                }
            }
            if (memberCount(connection, clan.get().id()) >= maxMembers) {
                return AcceptInviteResult.CLAN_FULL;
            }
            insertMember(connection, clan.get().id(), player, "MEMBER", now);
            deleteInvitations(connection, player.playerId());
            deleteJoinRequests(connection, player.playerId());
            return AcceptInviteResult.ACCEPTED;
        });
    }

    RemoveMemberResult leaveClan(UUID playerId) {
        return database.inTransaction(connection -> {
            Optional<Member> member = member(connection, playerId);
            if (member.isEmpty()) {
                return RemoveMemberResult.NOT_MEMBER;
            }
            if (member.get().owner()) {
                return RemoveMemberResult.OWNER_MUST_DELETE;
            }
            deleteMember(connection, playerId);
            return RemoveMemberResult.REMOVED;
        });
    }

    RemoveMemberResult kickMember(long clanId, UUID ownerId, String memberName) {
        return database.inTransaction(connection -> {
            if (!ownsClan(connection, clanId, ownerId)) {
                return RemoveMemberResult.NOT_MEMBER;
            }
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT player_uuid, player_name, member_role, joined_at
                  FROM pc_clan_members
                 WHERE clan_id = ? AND LOWER(player_name) = LOWER(?)
                 LIMIT 1
                """
            )) {
                statement.setLong(1, clanId);
                statement.setString(2, memberName);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return RemoveMemberResult.NOT_MEMBER;
                    }
                    Member member = mapMember(result);
                    if (member.owner()) {
                        return RemoveMemberResult.OWNER_MUST_DELETE;
                    }
                    deleteMember(connection, member.playerId());
                    return RemoveMemberResult.REMOVED;
                }
            }
        });
    }

    List<Invitation> invitations(UUID playerId, long now) {
        return database.withConnection(connection -> {
            List<Invitation> invitations = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT c.id, c.clan_name, c.clan_tag, i.expires_at
                  FROM pc_clan_invitations i
                  JOIN pc_clans c ON c.id = i.clan_id
                 WHERE i.player_uuid = ? AND i.expires_at > ?
                 ORDER BY i.created_at DESC
                """
            )) {
                statement.setString(1, playerId.toString());
                statement.setLong(2, now);
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        invitations.add(new Invitation(
                            result.getLong("id"),
                            result.getString("clan_name"),
                            result.getString("clan_tag"),
                            result.getLong("expires_at")
                        ));
                    }
                }
            }
            return invitations;
        });
    }

    List<TabEntry> tabEntries() {
        return database.withConnection(connection -> {
            List<TabEntry> entries = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT c.id AS clan_id, m.player_uuid, m.player_name, c.clan_tag, c.tag_color
                  FROM pc_clan_members m
                  JOIN pc_clans c ON c.id = m.clan_id
                """
            ); ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    entries.add(new TabEntry(
                        result.getLong("clan_id"),
                        UUID.fromString(result.getString("player_uuid")),
                        result.getString("player_name"),
                        result.getString("clan_tag"),
                        result.getString("tag_color")
                    ));
                }
            }
            return entries;
        });
    }

    Directory directory() {
        return database.withConnection(connection -> new Directory(
            strings(
                connection,
                "SELECT clan_tag AS value FROM pc_clans ORDER BY clan_tag",
                "value"
            ),
            strings(
                connection,
                "SELECT player_name AS value FROM pc_players ORDER BY player_name",
                "value"
            ),
            strings(connection, "SELECT player_name FROM pc_clan_members ORDER BY player_name", "player_name"),
            strings(connection, "SELECT owner_name FROM pc_player_bases ORDER BY owner_name", "owner_name")
        ));
    }

    void cleanupExpiredInvitations(long now) {
        database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM pc_clan_invitations WHERE expires_at <= ?"
            )) {
                statement.setLong(1, now);
                statement.executeUpdate();
            }
            return null;
        });
    }

    void touchPlayer(PlayerIdentity player) {
        database.inTransaction(connection -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_players
                    (player_uuid, player_name, platform, first_seen, last_seen)
                VALUES (?, ?, 'UNKNOWN', ?, ?)
                ON DUPLICATE KEY UPDATE
                    player_name = VALUES(player_name),
                    last_seen = VALUES(last_seen)
                """
            )) {
                statement.setString(1, player.playerId().toString());
                statement.setString(2, player.playerName());
                statement.setLong(3, now);
                statement.setLong(4, now);
                statement.executeUpdate();
            }
            updatePlayerName(connection, "pc_clan_members", "player_uuid", "player_name", player);
            updatePlayerName(connection, "pc_clans", "owner_uuid", "owner_name", player);
            updatePlayerName(connection, "pc_clan_invitations", "player_uuid", "player_name", player);
            updatePlayerName(connection, "pc_clan_join_requests", "player_uuid", "player_name", player);
            updatePlayerName(
                connection,
                "pc_clan_invitations",
                "invited_by_uuid",
                "invited_by_name",
                player
            );
            updatePlayerName(connection, "pc_player_bases", "owner_uuid", "owner_name", player);
            updatePlayerName(connection, "pc_base_visitors", "visitor_uuid", "visitor_name", player);
            updatePlayerName(connection, "pc_base_likes", "liker_uuid", "liker_name", player);
            return null;
        });
    }

    void setBase(PlayerIdentity owner, BaseLocation location, boolean publicBase, long now) {
        database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_player_bases
                    (owner_uuid, owner_name, world_uuid, world_name, x, y, z,
                     yaw, pitch, is_public, visit_count, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                ON DUPLICATE KEY UPDATE
                    owner_name = VALUES(owner_name),
                    world_uuid = VALUES(world_uuid),
                    world_name = VALUES(world_name),
                    x = VALUES(x), y = VALUES(y), z = VALUES(z),
                    yaw = VALUES(yaw), pitch = VALUES(pitch),
                    is_public = VALUES(is_public),
                    updated_at = VALUES(updated_at)
                """
            )) {
                statement.setString(1, owner.playerId().toString());
                statement.setString(2, owner.playerName());
                statement.setString(3, location.worldId().toString());
                statement.setString(4, location.worldName());
                statement.setDouble(5, location.x());
                statement.setDouble(6, location.y());
                statement.setDouble(7, location.z());
                statement.setFloat(8, location.yaw());
                statement.setFloat(9, location.pitch());
                statement.setBoolean(10, publicBase);
                statement.setLong(11, now);
                statement.setLong(12, now);
                statement.executeUpdate();
            }
            return null;
        });
    }

    Optional<PlayerBase> baseForPlayer(UUID playerId) {
        return database.withConnection(connection -> findBase(connection, "b.owner_uuid = ?", playerId.toString()));
    }

    Optional<PlayerBase> baseForName(String playerName) {
        return database.withConnection(connection -> findBase(connection, "LOWER(b.owner_name) = LOWER(?)", playerName));
    }

    boolean setBaseVisibility(UUID ownerId, boolean publicBase) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pc_player_bases SET is_public = ?, updated_at = ? WHERE owner_uuid = ?"
            )) {
                statement.setBoolean(1, publicBase);
                statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, ownerId.toString());
                return statement.executeUpdate() > 0;
            }
        });
    }

    boolean deleteBase(UUID ownerId) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM pc_player_bases WHERE owner_uuid = ?"
            )) {
                statement.setString(1, ownerId.toString());
                return statement.executeUpdate() > 0;
            }
        });
    }

    boolean likeBase(UUID ownerId, PlayerIdentity liker, long now) {
        return database.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT IGNORE INTO pc_base_likes
                    (owner_uuid, liker_uuid, liker_name, created_at)
                VALUES (?, ?, ?, ?)
                """
            )) {
                statement.setString(1, ownerId.toString());
                statement.setString(2, liker.playerId().toString());
                statement.setString(3, liker.playerName());
                statement.setLong(4, now);
                return statement.executeUpdate() > 0;
            }
        });
    }

    void recordVisit(UUID ownerId, PlayerIdentity visitor, long now) {
        database.inTransaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pc_player_bases SET visit_count = visit_count + 1 WHERE owner_uuid = ?"
            )) {
                statement.setString(1, ownerId.toString());
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(
                """
                INSERT INTO pc_base_visitors
                    (owner_uuid, visitor_uuid, visitor_name, visit_count, last_visited_at)
                VALUES (?, ?, ?, 1, ?)
                ON DUPLICATE KEY UPDATE
                    visitor_name = VALUES(visitor_name),
                    visit_count = visit_count + 1,
                    last_visited_at = VALUES(last_visited_at)
                """
            )) {
                statement.setString(1, ownerId.toString());
                statement.setString(2, visitor.playerId().toString());
                statement.setString(3, visitor.playerName());
                statement.setLong(4, now);
                statement.executeUpdate();
            }
            return null;
        });
    }

    private Optional<Clan> clanForPlayer(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            """
            SELECT c.*, COUNT(all_members.player_uuid) AS member_count
              FROM pc_clan_members own_member
              JOIN pc_clans c ON c.id = own_member.clan_id
              LEFT JOIN pc_clan_members all_members ON all_members.clan_id = c.id
             WHERE own_member.player_uuid = ?
             GROUP BY c.id
            """
        )) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapClan(result)) : Optional.empty();
            }
        }
    }

    private Optional<Clan> findClan(Connection connection, String nameOrTag) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            """
            SELECT c.*, COUNT(m.player_uuid) AS member_count
              FROM pc_clans c
              LEFT JOIN pc_clan_members m ON m.clan_id = c.id
             WHERE LOWER(c.clan_name) = LOWER(?) OR LOWER(c.clan_tag) = LOWER(?)
             GROUP BY c.id
             LIMIT 1
            """
        )) {
            statement.setString(1, nameOrTag);
            statement.setString(2, nameOrTag);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapClan(result)) : Optional.empty();
            }
        }
    }

    private Clan mapClan(ResultSet result) throws SQLException {
        return new Clan(
            result.getLong("id"),
            result.getString("clan_name"),
            result.getString("clan_tag"),
            result.getString("tag_color"),
            UUID.fromString(result.getString("owner_uuid")),
            result.getString("owner_name"),
            result.getLong("created_at"),
            result.getInt("member_count")
        );
    }

    private List<Member> members(Connection connection, long clanId) throws SQLException {
        List<Member> members = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
            """
            SELECT player_uuid, player_name, member_role, joined_at
              FROM pc_clan_members
             WHERE clan_id = ?
             ORDER BY CASE member_role
                        WHEN 'OWNER' THEN 0
                        WHEN 'CO_OWNER' THEN 1
                        ELSE 2
                      END,
                      joined_at,
                      player_name
            """
        )) {
            statement.setLong(1, clanId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    members.add(mapMember(result));
                }
            }
        }
        return members;
    }

    private Member mapMember(ResultSet result) throws SQLException {
        return new Member(
            UUID.fromString(result.getString("player_uuid")),
            result.getString("player_name"),
            result.getString("member_role"),
            result.getLong("joined_at")
        );
    }

    private Optional<Member> member(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            """
            SELECT player_uuid, player_name, member_role, joined_at
              FROM pc_clan_members
             WHERE player_uuid = ?
            """
        )) {
            statement.setString(1, playerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapMember(result)) : Optional.empty();
            }
        }
    }

    private Optional<Member> member(
        Connection connection,
        long clanId,
        String playerName
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            """
            SELECT player_uuid, player_name, member_role, joined_at
              FROM pc_clan_members
             WHERE clan_id = ? AND LOWER(player_name) = LOWER(?)
             LIMIT 1
            """
        )) {
            statement.setLong(1, clanId);
            statement.setString(2, playerName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(mapMember(result)) : Optional.empty();
            }
        }
    }

    private Optional<PlayerIdentity> joinRequestPlayer(
        Connection connection,
        long clanId,
        String playerName
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            """
            SELECT player_uuid, player_name
              FROM pc_clan_join_requests
             WHERE clan_id = ? AND LOWER(player_name) = LOWER(?)
             LIMIT 1
             FOR UPDATE
            """
        )) {
            statement.setLong(1, clanId);
            statement.setString(2, playerName);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(new PlayerIdentity(
                        UUID.fromString(result.getString("player_uuid")),
                        result.getString("player_name")
                    ));
                }
            }
        }
        return Optional.empty();
    }

    private void insertMember(
        Connection connection,
        long clanId,
        PlayerIdentity player,
        String role,
        long joinedAt
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            """
            INSERT INTO pc_clan_members
                (clan_id, player_uuid, player_name, member_role, joined_at)
            VALUES (?, ?, ?, ?, ?)
            """
        )) {
            statement.setLong(1, clanId);
            statement.setString(2, player.playerId().toString());
            statement.setString(3, player.playerName());
            statement.setString(4, role);
            statement.setLong(5, joinedAt);
            statement.executeUpdate();
        }
    }

    private void deleteMember(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM pc_clan_members WHERE player_uuid = ?"
        )) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
    }

    private void deleteInvitations(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM pc_clan_invitations WHERE player_uuid = ?"
        )) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
    }

    private void deleteJoinRequests(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "DELETE FROM pc_clan_join_requests WHERE player_uuid = ?"
        )) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
    }

    private int memberCount(Connection connection, long clanId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM pc_clan_members WHERE clan_id = ?"
        )) {
            statement.setLong(1, clanId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private boolean ownsClan(Connection connection, long clanId, UUID ownerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT 1 FROM pc_clans WHERE id = ? AND owner_uuid = ?"
        )) {
            statement.setLong(1, clanId);
            statement.setString(2, ownerId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private boolean clanExists(Connection connection, String column, String value) throws SQLException {
        String sql = switch (column) {
            case "clan_name" -> "SELECT 1 FROM pc_clans WHERE LOWER(clan_name) = LOWER(?)";
            case "clan_tag" -> "SELECT 1 FROM pc_clans WHERE LOWER(clan_tag) = LOWER(?)";
            default -> throw new IllegalArgumentException("Unsupported clan column: " + column);
        };
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private Optional<PlayerBase> findBase(Connection connection, String where, String value)
        throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            """
            SELECT b.*,
                   (SELECT COUNT(*) FROM pc_base_likes l WHERE l.owner_uuid = b.owner_uuid)
                       AS like_count,
                   (SELECT COUNT(*) FROM pc_base_visitors v WHERE v.owner_uuid = b.owner_uuid)
                       AS unique_visitors
              FROM pc_player_bases b
             WHERE %s
             ORDER BY b.updated_at DESC
             LIMIT 1
            """.formatted(where)
        )) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PlayerBase(
                    UUID.fromString(result.getString("owner_uuid")),
                    result.getString("owner_name"),
                    UUID.fromString(result.getString("world_uuid")),
                    result.getString("world_name"),
                    result.getDouble("x"),
                    result.getDouble("y"),
                    result.getDouble("z"),
                    result.getFloat("yaw"),
                    result.getFloat("pitch"),
                    result.getBoolean("is_public"),
                    result.getLong("visit_count"),
                    result.getLong("like_count"),
                    result.getLong("unique_visitors"),
                    result.getLong("created_at"),
                    result.getLong("updated_at")
                ));
            }
        }
    }

    private List<String> strings(Connection connection, String sql, String column) throws SQLException {
        List<String> values = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                values.add(result.getString(column));
            }
        }
        return List.copyOf(values);
    }

    private void updatePlayerName(
        Connection connection,
        String table,
        String idColumn,
        String nameColumn,
        PlayerIdentity player
    ) throws SQLException {
        Set<String> allowedTables = Set.of(
            "pc_clan_members",
            "pc_clans",
            "pc_clan_invitations",
            "pc_clan_join_requests",
            "pc_player_bases",
            "pc_base_visitors",
            "pc_base_likes"
        );
        if (!allowedTables.contains(table)) {
            throw new IllegalArgumentException("Unsupported player name table: " + table);
        }
        String sql = "UPDATE " + table + " SET " + nameColumn + " = ? WHERE " + idColumn + " = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, player.playerName());
            statement.setString(2, player.playerId().toString());
            statement.executeUpdate();
        }
    }

    record BaseLocation(
        UUID worldId,
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
    ) {
    }
}
