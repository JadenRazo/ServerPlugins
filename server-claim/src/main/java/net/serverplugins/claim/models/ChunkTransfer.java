package net.serverplugins.claim.models;

import java.time.Instant;
import java.util.UUID;

/** Represents a chunk transfer record for audit purposes. */
public class ChunkTransfer {

    public enum TransferType {
        OWNERSHIP, // Full ownership transfer to another player
        REASSIGN, // Moving chunk to a different claim (same owner)
        UNCLAIM // Chunk was unclaimed
    }

    private int id;
    private String chunkWorld;
    private int chunkX;
    private int chunkZ;
    private Integer fromClaimId;
    private Integer toClaimId;
    private UUID fromOwnerUuid;
    private UUID toOwnerUuid;
    private TransferType transferType;
    private Instant transferredAt;

    public ChunkTransfer() {
        this.transferredAt = Instant.now();
    }

    public ChunkTransfer(
            String chunkWorld,
            int chunkX,
            int chunkZ,
            Integer fromClaimId,
            Integer toClaimId,
            UUID fromOwnerUuid,
            UUID toOwnerUuid,
            TransferType transferType) {
        this.chunkWorld = chunkWorld;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.fromClaimId = fromClaimId;
        this.toClaimId = toClaimId;
        this.fromOwnerUuid = fromOwnerUuid;
        this.toOwnerUuid = toOwnerUuid;
        this.transferType = transferType;
        this.transferredAt = Instant.now();
    }

    // Convenience factory methods
    public static ChunkTransfer ownershipTransfer(
            ClaimedChunk chunk, Claim fromClaim, UUID toOwnerUuid, Integer toClaimId) {
        return new ChunkTransfer(
                chunk.getWorld(),
                chunk.getChunkX(),
                chunk.getChunkZ(),
                fromClaim.getId(),
                toClaimId,
                fromClaim.getOwnerUuid(),
                toOwnerUuid,
                TransferType.OWNERSHIP);
    }

    public static ChunkTransfer reassignment(ClaimedChunk chunk, Claim fromClaim, Claim toClaim) {
        return new ChunkTransfer(
                chunk.getWorld(),
                chunk.getChunkX(),
                chunk.getChunkZ(),
                fromClaim.getId(),
                toClaim.getId(),
                fromClaim.getOwnerUuid(),
                toClaim.getOwnerUuid(),
                TransferType.REASSIGN);
    }

    public static ChunkTransfer unclaim(ClaimedChunk chunk, Claim fromClaim) {
        return new ChunkTransfer(
                chunk.getWorld(),
                chunk.getChunkX(),
                chunk.getChunkZ(),
                fromClaim.getId(),
                null,
                fromClaim.getOwnerUuid(),
                null,
                TransferType.UNCLAIM);
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getChunkWorld() {
        return chunkWorld;
    }

    public void setChunkWorld(String chunkWorld) {
        this.chunkWorld = chunkWorld;
    }

    public int getChunkX() {
        return chunkX;
    }

    public void setChunkX(int chunkX) {
        this.chunkX = chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public void setChunkZ(int chunkZ) {
        this.chunkZ = chunkZ;
    }

    public Integer getFromClaimId() {
        return fromClaimId;
    }

    public void setFromClaimId(Integer fromClaimId) {
        this.fromClaimId = fromClaimId;
    }

    public Integer getToClaimId() {
        return toClaimId;
    }

    public void setToClaimId(Integer toClaimId) {
        this.toClaimId = toClaimId;
    }

    public UUID getFromOwnerUuid() {
        return fromOwnerUuid;
    }

    public void setFromOwnerUuid(UUID fromOwnerUuid) {
        this.fromOwnerUuid = fromOwnerUuid;
    }

    public UUID getToOwnerUuid() {
        return toOwnerUuid;
    }

    public void setToOwnerUuid(UUID toOwnerUuid) {
        this.toOwnerUuid = toOwnerUuid;
    }

    public TransferType getTransferType() {
        return transferType;
    }

    public void setTransferType(TransferType transferType) {
        this.transferType = transferType;
    }

    public Instant getTransferredAt() {
        return transferredAt;
    }

    public void setTransferredAt(Instant transferredAt) {
        this.transferredAt = transferredAt;
    }

    @Override
    public String toString() {
        return "ChunkTransfer{"
                + "world='"
                + chunkWorld
                + '\''
                + ", chunk="
                + chunkX
                + ","
                + chunkZ
                + ", type="
                + transferType
                + ", from="
                + fromClaimId
                + ", to="
                + toClaimId
                + '}';
    }
}
