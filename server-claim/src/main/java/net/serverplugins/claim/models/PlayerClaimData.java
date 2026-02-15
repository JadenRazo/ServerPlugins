package net.serverplugins.claim.models;

import java.time.Instant;
import java.util.UUID;

public class PlayerClaimData {

    private UUID uuid;
    private String username;
    private int totalChunks;
    private int purchasedChunks;
    private Instant createdAt;
    private Instant updatedAt;

    public PlayerClaimData(
            UUID uuid,
            String username,
            int totalChunks,
            int purchasedChunks,
            Instant createdAt,
            Instant updatedAt) {
        this.uuid = uuid;
        this.username = username;
        this.totalChunks = totalChunks;
        this.purchasedChunks = purchasedChunks;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public PlayerClaimData(UUID uuid, String username, int startingChunks) {
        this.uuid = uuid;
        this.username = username;
        this.totalChunks = startingChunks;
        this.purchasedChunks = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public int getPurchasedChunks() {
        return purchasedChunks;
    }

    public void setPurchasedChunks(int purchasedChunks) {
        this.purchasedChunks = purchasedChunks;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public void addChunks(int amount) {
        this.totalChunks += amount;
        this.purchasedChunks += amount;
        this.updatedAt = Instant.now();
    }
}
