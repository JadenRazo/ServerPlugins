package net.serverplugins.bounty.models;

import java.time.Instant;
import java.util.UUID;

public class TrophyHead {

    private int id;
    private final UUID ownerUuid;
    private final UUID victimUuid;
    private final String victimName;
    private final double bountyAmount;
    private final String headData;
    private final Instant createdAt;
    private final Instant expiresAt;
    private boolean claimed;
    private Instant claimedAt;

    public TrophyHead(
            UUID ownerUuid,
            UUID victimUuid,
            String victimName,
            double bountyAmount,
            String headData,
            Instant expiresAt) {
        this.ownerUuid = ownerUuid;
        this.victimUuid = victimUuid;
        this.victimName = victimName;
        this.bountyAmount = bountyAmount;
        this.headData = headData;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
        this.claimed = false;
        this.claimedAt = null;
    }

    public TrophyHead(
            int id,
            UUID ownerUuid,
            UUID victimUuid,
            String victimName,
            double bountyAmount,
            String headData,
            Instant createdAt,
            Instant expiresAt,
            boolean claimed,
            Instant claimedAt) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.victimUuid = victimUuid;
        this.victimName = victimName;
        this.bountyAmount = bountyAmount;
        this.headData = headData;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.claimed = claimed;
        this.claimedAt = claimedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public UUID getVictimUuid() {
        return victimUuid;
    }

    public String getVictimName() {
        return victimName;
    }

    public double getBountyAmount() {
        return bountyAmount;
    }

    public String getHeadData() {
        return headData;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    // Alias for backward compatibility
    public Instant getKillTime() {
        return createdAt;
    }
}
