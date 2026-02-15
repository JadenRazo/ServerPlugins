package net.serverplugins.claim.models;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a player's global chunk pool. This pool persists across claim/profile deletions and
 * stores all chunks purchased by the player. Players allocate chunks from this pool to individual
 * profiles.
 */
public class PlayerChunkPool {
    private UUID playerUuid;
    private int purchasedChunks; // Total chunks in global pool
    private double totalSpent; // Total money spent on chunks
    private Instant lastPurchase;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Constructor for creating a new chunk pool
     *
     * @param playerUuid The player's UUID
     */
    public PlayerChunkPool(UUID playerUuid) {
        this.playerUuid = playerUuid;
        this.purchasedChunks = 0;
        this.totalSpent = 0.0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Full constructor for loading from database
     *
     * @param playerUuid The player's UUID
     * @param purchasedChunks Total chunks purchased
     * @param totalSpent Total money spent
     * @param lastPurchase Last purchase timestamp
     * @param createdAt Creation timestamp
     * @param updatedAt Last update timestamp
     */
    public PlayerChunkPool(
            UUID playerUuid,
            int purchasedChunks,
            double totalSpent,
            Instant lastPurchase,
            Instant createdAt,
            Instant updatedAt) {
        this.playerUuid = playerUuid;
        this.purchasedChunks = purchasedChunks;
        this.totalSpent = totalSpent;
        this.lastPurchase = lastPurchase;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Add chunks to the pool
     *
     * @param amount Number of chunks to add
     * @param cost Cost of the purchase
     */
    public void addChunks(int amount, double cost) {
        this.purchasedChunks += amount;
        this.totalSpent += cost;
        this.lastPurchase = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * Calculate available chunks (not yet allocated to profiles) Note: This is calculated by the
     * manager based on allocated chunks across all claims
     *
     * @return Total purchased chunks (actual available chunks need to be calculated externally)
     */
    public int getAvailableChunks() {
        return purchasedChunks;
    }

    // Getters and setters
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public int getPurchasedChunks() {
        return purchasedChunks;
    }

    public void setPurchasedChunks(int purchasedChunks) {
        this.purchasedChunks = purchasedChunks;
        this.updatedAt = Instant.now();
    }

    public double getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(double totalSpent) {
        this.totalSpent = totalSpent;
        this.updatedAt = Instant.now();
    }

    public Instant getLastPurchase() {
        return lastPurchase;
    }

    public void setLastPurchase(Instant lastPurchase) {
        this.lastPurchase = lastPurchase;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "PlayerChunkPool{"
                + "playerUuid="
                + playerUuid
                + ", purchasedChunks="
                + purchasedChunks
                + ", totalSpent="
                + totalSpent
                + ", lastPurchase="
                + lastPurchase
                + '}';
    }
}
