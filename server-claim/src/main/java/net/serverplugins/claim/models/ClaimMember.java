package net.serverplugins.claim.models;

import java.util.UUID;

public class ClaimMember {

    private int claimId;
    private UUID playerUuid;
    @Deprecated private ClaimGroup group; // Legacy field for backwards compatibility
    private Integer groupId; // New field for custom groups (nullable during migration)

    // Legacy constructor
    @Deprecated
    public ClaimMember(int claimId, UUID playerUuid, ClaimGroup group) {
        this.claimId = claimId;
        this.playerUuid = playerUuid;
        this.group = group;
        this.groupId = null;
    }

    // New constructor with groupId
    public ClaimMember(int claimId, UUID playerUuid, int groupId) {
        this.claimId = claimId;
        this.playerUuid = playerUuid;
        this.groupId = groupId;
        this.group = null;
    }

    // Full constructor for migration scenarios
    public ClaimMember(int claimId, UUID playerUuid, ClaimGroup group, Integer groupId) {
        this.claimId = claimId;
        this.playerUuid = playerUuid;
        this.group = group;
        this.groupId = groupId;
    }

    public int getClaimId() {
        return claimId;
    }

    public void setClaimId(int claimId) {
        this.claimId = claimId;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    @Deprecated
    public ClaimGroup getGroup() {
        return group;
    }

    @Deprecated
    public void setGroup(ClaimGroup group) {
        this.group = group;
    }

    public Integer getGroupId() {
        return groupId;
    }

    public void setGroupId(Integer groupId) {
        this.groupId = groupId;
    }

    /** Checks if this member has been migrated to the custom group system. */
    public boolean hasCustomGroup() {
        return groupId != null;
    }
}
