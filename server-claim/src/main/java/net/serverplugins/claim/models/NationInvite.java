package net.serverplugins.claim.models;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a persistent nation invitation to a player. Invitations expire after a configurable
 * time period.
 */
public class NationInvite {

    private int id;
    private int nationId;
    private UUID playerUuid;
    private UUID inviterUuid;
    private Instant invitedAt;
    private Instant expiresAt;

    public NationInvite() {}

    public NationInvite(
            int id,
            int nationId,
            UUID playerUuid,
            UUID inviterUuid,
            Instant invitedAt,
            Instant expiresAt) {
        this.id = id;
        this.nationId = nationId;
        this.playerUuid = playerUuid;
        this.inviterUuid = inviterUuid;
        this.invitedAt = invitedAt;
        this.expiresAt = expiresAt;
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

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public UUID getInviterUuid() {
        return inviterUuid;
    }

    public void setInviterUuid(UUID inviterUuid) {
        this.inviterUuid = inviterUuid;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }

    public void setInvitedAt(Instant invitedAt) {
        this.invitedAt = invitedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Timestamp getExpiresAtTimestamp() {
        return expiresAt != null ? Timestamp.from(expiresAt) : null;
    }

    /**
     * Check if this invite has expired.
     *
     * @return true if the invite has expired, false otherwise
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Get time remaining until expiration in hours.
     *
     * @return hours until expiration, or -1 if expired
     */
    public long getHoursUntilExpiry() {
        if (expiresAt == null) {
            return Long.MAX_VALUE; // Never expires
        }

        long secondsRemaining = expiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        if (secondsRemaining <= 0) {
            return -1; // Expired
        }

        return secondsRemaining / 3600;
    }

    /**
     * Get time since invitation was sent in minutes.
     *
     * @return minutes since invitation
     */
    public long getMinutesSinceInvited() {
        if (invitedAt == null) {
            return 0;
        }

        long secondsElapsed = Instant.now().getEpochSecond() - invitedAt.getEpochSecond();
        return secondsElapsed / 60;
    }
}
