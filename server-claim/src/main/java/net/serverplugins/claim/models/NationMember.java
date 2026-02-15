package net.serverplugins.claim.models;

import java.time.Instant;

public class NationMember {

    public enum NationRole {
        LEADER("Leader", true, true, true),
        OFFICER("Officer", true, true, false),
        MEMBER("Member", false, false, false);

        private final String displayName;
        private final boolean canInvite;
        private final boolean canKick;
        private final boolean canDisband;

        NationRole(String displayName, boolean canInvite, boolean canKick, boolean canDisband) {
            this.displayName = displayName;
            this.canInvite = canInvite;
            this.canKick = canKick;
            this.canDisband = canDisband;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean canInvite() {
            return canInvite;
        }

        public boolean canKick() {
            return canKick;
        }

        public boolean canDisband() {
            return canDisband;
        }

        public boolean isHigherThan(NationRole other) {
            return this.ordinal() < other.ordinal();
        }

        public static NationRole fromString(String name) {
            try {
                return NationRole.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                return MEMBER;
            }
        }
    }

    private int id;
    private int nationId;
    private int claimId;
    private NationRole role;
    private Instant joinedAt;
    private double contributedAmount;

    public NationMember() {
        this.role = NationRole.MEMBER;
        this.joinedAt = Instant.now();
        this.contributedAmount = 0.0;
    }

    public NationMember(int nationId, int claimId, NationRole role) {
        this.nationId = nationId;
        this.claimId = claimId;
        this.role = role;
        this.joinedAt = Instant.now();
        this.contributedAmount = 0.0;
    }

    public NationMember(
            int id,
            int nationId,
            int claimId,
            NationRole role,
            Instant joinedAt,
            double contributedAmount) {
        this.id = id;
        this.nationId = nationId;
        this.claimId = claimId;
        this.role = role;
        this.joinedAt = joinedAt;
        this.contributedAmount = contributedAmount;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getNationId() {
        return nationId;
    }

    public void setNationId(int nationId) {
        this.nationId = nationId;
    }

    public int getClaimId() {
        return claimId;
    }

    public void setClaimId(int claimId) {
        this.claimId = claimId;
    }

    public NationRole getRole() {
        return role;
    }

    public void setRole(NationRole role) {
        this.role = role;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
    }

    public double getContributedAmount() {
        return contributedAmount;
    }

    public void setContributedAmount(double contributedAmount) {
        this.contributedAmount = contributedAmount;
    }

    public void addContribution(double amount) {
        this.contributedAmount += amount;
    }

    public boolean canInvite() {
        return role.canInvite();
    }

    public boolean canKick() {
        return role.canKick();
    }

    public boolean canDisband() {
        return role.canDisband();
    }

    public boolean isLeader() {
        return role == NationRole.LEADER;
    }

    public boolean isOfficer() {
        return role == NationRole.OFFICER;
    }

    public void promote() {
        if (role == NationRole.MEMBER) {
            role = NationRole.OFFICER;
        }
    }

    public void demote() {
        if (role == NationRole.OFFICER) {
            role = NationRole.MEMBER;
        }
    }
}
