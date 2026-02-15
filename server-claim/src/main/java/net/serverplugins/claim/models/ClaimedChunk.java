package net.serverplugins.claim.models;

import java.time.Instant;
import java.util.Objects;
import org.bukkit.Chunk;

public class ClaimedChunk {

    private int id;
    private int claimId;
    private String world;
    private int chunkX;
    private int chunkZ;
    private Instant claimedAt;

    public ClaimedChunk(
            int id, int claimId, String world, int chunkX, int chunkZ, Instant claimedAt) {
        this.id = id;
        this.claimId = claimId;
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.claimedAt = claimedAt;
    }

    public ClaimedChunk(int claimId, String world, int chunkX, int chunkZ) {
        this.claimId = claimId;
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.claimedAt = Instant.now();
    }

    public static ClaimedChunk fromBukkitChunk(int claimId, Chunk chunk) {
        return new ClaimedChunk(claimId, chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getClaimId() {
        return claimId;
    }

    public void setClaimId(int claimId) {
        this.claimId = claimId;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
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

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    public int getBlockX() {
        return chunkX << 4;
    }

    public int getBlockZ() {
        return chunkZ << 4;
    }

    public boolean matches(Chunk chunk) {
        return chunk.getWorld().getName().equals(world)
                && chunk.getX() == chunkX
                && chunk.getZ() == chunkZ;
    }

    public boolean matches(String worldName, int x, int z) {
        return worldName.equals(world) && x == chunkX && z == chunkZ;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClaimedChunk that = (ClaimedChunk) o;
        return chunkX == that.chunkX && chunkZ == that.chunkZ && Objects.equals(world, that.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, chunkX, chunkZ);
    }
}
