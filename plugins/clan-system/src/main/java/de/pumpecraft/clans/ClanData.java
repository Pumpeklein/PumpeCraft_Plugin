package de.pumpecraft.clans;

import java.util.List;
import java.util.UUID;

final class ClanData {
    private ClanData() {
    }

    record PlayerIdentity(UUID playerId, String playerName) {
    }

    record Clan(
        long id,
        String name,
        String tag,
        String tagColor,
        UUID ownerId,
        String ownerName,
        long createdAt,
        int memberCount
    ) {
    }

    record Member(UUID playerId, String playerName, String role, long joinedAt) {
        boolean owner() {
            return role.equalsIgnoreCase("OWNER");
        }

        boolean coOwner() {
            return role.equalsIgnoreCase("CO_OWNER");
        }

        boolean canManageMembership() {
            return owner() || coOwner();
        }

        String displayRole() {
            if (owner()) {
                return "Owner";
            }
            if (coOwner()) {
                return "Co-Owner";
            }
            return "Member";
        }
    }

    record ClanDetails(Clan clan, List<Member> members) {
    }

    record TabEntry(long clanId, UUID playerId, String playerName, String tag, String tagColor) {
    }

    record Invitation(long clanId, String clanName, String clanTag, long expiresAt) {
    }

    record JoinRequest(long clanId, PlayerIdentity player, long createdAt) {
    }

    record PlayerBase(
        UUID ownerId,
        String ownerName,
        UUID worldId,
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        boolean publicBase,
        long visitCount,
        long likeCount,
        long uniqueVisitors,
        long createdAt,
        long updatedAt
    ) {
    }

    record Directory(
        List<String> clanTags,
        List<String> knownPlayerNames,
        List<String> memberNames,
        List<String> baseOwnerNames
    ) {
        static Directory empty() {
            return new Directory(List.of(), List.of(), List.of(), List.of());
        }
    }

    enum CreateClanResult {
        CREATED,
        ALREADY_MEMBER,
        NAME_TAKEN,
        TAG_TAKEN
    }

    enum AcceptInviteResult {
        ACCEPTED,
        ALREADY_MEMBER,
        NOT_INVITED,
        INVITATION_EXPIRED,
        CLAN_FULL
    }

    enum RenameClanResult {
        RENAMED,
        NOT_OWNER,
        NAME_TAKEN
    }

    enum ChangeRoleResult {
        CHANGED,
        NOT_OWNER,
        NOT_MEMBER,
        OWNER_PROTECTED
    }

    enum CreateJoinRequestResult {
        REQUESTED,
        ALREADY_REQUESTED,
        ALREADY_MEMBER,
        CLAN_NOT_FOUND
    }

    enum ResolveJoinRequestResult {
        ACCEPTED,
        DENIED,
        NOT_ALLOWED,
        NOT_FOUND,
        ALREADY_MEMBER,
        CLAN_FULL
    }

    enum RemoveMemberResult {
        REMOVED,
        NOT_MEMBER,
        OWNER_MUST_DELETE
    }
}
