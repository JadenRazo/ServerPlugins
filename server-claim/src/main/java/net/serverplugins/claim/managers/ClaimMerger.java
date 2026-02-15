package net.serverplugins.claim.managers;

import java.util.*;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimedChunk;
import net.serverplugins.claim.repository.ClaimRepository;
import org.bukkit.entity.Player;

/**
 * Handles merging of adjacent claims into a single claim. This feature allows players to
 * consolidate multiple claims that are next to each other.
 */
public class ClaimMerger {

    private final ServerClaim plugin;
    private final ClaimRepository repository;

    public ClaimMerger(ServerClaim plugin, ClaimRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    /**
     * Check if two claims can be merged. Requirements: - Both claims must exist - Both claims must
     * belong to the same owner - Both claims must be in the same world - Claims must be adjacent
     * (share at least one edge)
     *
     * @param claim1 First claim
     * @param claim2 Second claim
     * @return MergeValidation result
     */
    public MergeValidation canMerge(Claim claim1, Claim claim2) {
        // Check both claims exist
        if (claim1 == null || claim2 == null) {
            return new MergeValidation(false, "One or both claims do not exist");
        }

        // Check same claim
        if (claim1.getId() == claim2.getId()) {
            return new MergeValidation(false, "Cannot merge a claim with itself");
        }

        // Check same owner
        if (!claim1.getOwnerUuid().equals(claim2.getOwnerUuid())) {
            return new MergeValidation(false, "Claims must have the same owner");
        }

        // Check same world
        if (!claim1.getWorld().equals(claim2.getWorld())) {
            return new MergeValidation(false, "Claims must be in the same world");
        }

        // Check if claims are adjacent
        if (!areAdjacent(claim1, claim2)) {
            return new MergeValidation(false, "Claims are not adjacent to each other");
        }

        return new MergeValidation(true, "Claims can be merged");
    }

    /**
     * Check if two claims share at least one adjacent edge.
     *
     * @param claim1 First claim
     * @param claim2 Second claim
     * @return true if claims are adjacent
     */
    private boolean areAdjacent(Claim claim1, Claim claim2) {
        for (ClaimedChunk chunk1 : claim1.getChunks()) {
            for (ClaimedChunk chunk2 : claim2.getChunks()) {
                if (chunk1.getWorld().equals(chunk2.getWorld())
                        && areChunksAdjacent(chunk1, chunk2)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if two chunks are adjacent (share an edge, not just a corner).
     *
     * @param chunk1 First chunk
     * @param chunk2 Second chunk
     * @return true if chunks share an edge
     */
    private boolean areChunksAdjacent(ClaimedChunk chunk1, ClaimedChunk chunk2) {
        int dx = Math.abs(chunk1.getChunkX() - chunk2.getChunkX());
        int dz = Math.abs(chunk1.getChunkZ() - chunk2.getChunkZ());

        // Adjacent means exactly 1 block away in one direction, 0 in the other
        return (dx == 1 && dz == 0) || (dx == 0 && dz == 1);
    }

    /**
     * Merge two claims into one. The target claim will absorb all chunks from the source claim. The
     * source claim will be deleted.
     *
     * @param player Player performing the merge (for permissions and notifications)
     * @param targetClaim Claim that will remain and absorb chunks
     * @param sourceClaim Claim that will be merged into target and deleted
     * @return MergeResult containing success status and details
     */
    public MergeResult mergeClaims(Player player, Claim targetClaim, Claim sourceClaim) {
        // Validate merge
        MergeValidation validation = canMerge(targetClaim, sourceClaim);
        if (!validation.isValid()) {
            return new MergeResult(false, validation.getMessage(), 0);
        }

        // Verify ownership
        if (!targetClaim.isOwner(player.getUniqueId())
                && !player.hasPermission("serverclaim.admin")) {
            return new MergeResult(false, "You don't own these claims", 0);
        }

        int chunksMerged = sourceClaim.getChunks().size();

        // Create a copy of chunks to transfer since we'll be modifying the source claim
        List<ClaimedChunk> chunksToTransfer = new ArrayList<>(sourceClaim.getChunks());

        try {
            // Wrap entire merge operation in a transaction for atomicity
            repository.executeInTransaction(
                    () -> {
                        // Transfer all chunks from source to target
                        for (ClaimedChunk chunk : chunksToTransfer) {
                            // Update chunk ownership in database
                            repository.updateChunkClaim(
                                    chunk.getWorld(),
                                    chunk.getChunkX(),
                                    chunk.getChunkZ(),
                                    targetClaim.getId());
                        }

                        // Update target claim's chunk totals
                        int newTotalChunks =
                                targetClaim.getTotalChunks() + sourceClaim.getTotalChunks();
                        int newPurchasedChunks =
                                targetClaim.getPurchasedChunks() + sourceClaim.getPurchasedChunks();

                        targetClaim.setTotalChunks(newTotalChunks);
                        targetClaim.setPurchasedChunks(newPurchasedChunks);
                        repository.saveClaimChunkData(targetClaim);

                        // Delete the source claim from database
                        repository.deleteClaim(sourceClaim);
                    });

            // Transaction committed successfully - now update caches
            for (ClaimedChunk chunk : chunksToTransfer) {
                // Update in-memory
                sourceClaim.removeChunk(chunk);
                targetClaim.addChunk(chunk);

                // Update cache
                plugin.getClaimManager()
                        .updateChunkCache(
                                chunk.getWorld(),
                                chunk.getChunkX(),
                                chunk.getChunkZ(),
                                targetClaim.getId());
            }

            // Remove source claim from cache
            plugin.getClaimManager().invalidateClaim(sourceClaim.getId());

            // Log the merge
            plugin.getLogger()
                    .info(
                            "Player "
                                    + player.getName()
                                    + " merged claim "
                                    + sourceClaim.getId()
                                    + " ("
                                    + chunksMerged
                                    + " chunks) into claim "
                                    + targetClaim.getId()
                                    + " ("
                                    + targetClaim.getName()
                                    + ") - transaction committed");

            return new MergeResult(
                    true,
                    "Successfully merged "
                            + sourceClaim.getName()
                            + " into "
                            + targetClaim.getName(),
                    chunksMerged);

        } catch (Exception e) {
            // Transaction failed and was automatically rolled back
            plugin.getLogger()
                    .severe(
                            "Claim merge transaction failed for claims "
                                    + targetClaim.getId()
                                    + " and "
                                    + sourceClaim.getId()
                                    + ": "
                                    + e.getMessage());
            e.printStackTrace();

            return new MergeResult(
                    false,
                    "Database error occurred while merging claims. No changes were made.",
                    0);
        }
    }

    /**
     * Find all claims adjacent to a given claim that belong to the same owner.
     *
     * @param claim Claim to find adjacent claims for
     * @return List of adjacent claims with the same owner
     */
    public List<Claim> findMergeableClaims(Claim claim) {
        List<Claim> result = new ArrayList<>();
        List<Claim> allPlayerClaims = repository.getClaimsByOwner(claim.getOwnerUuid());

        for (Claim otherClaim : allPlayerClaims) {
            if (otherClaim.getId() == claim.getId()) {
                continue; // Skip same claim
            }

            if (!otherClaim.getWorld().equals(claim.getWorld())) {
                continue; // Skip different world
            }

            if (areAdjacent(claim, otherClaim)) {
                result.add(otherClaim);
            }
        }

        return result;
    }

    /** Result of merge validation check. */
    public record MergeValidation(boolean valid, String message) {
        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }

    /** Result of claim merge operation. */
    public static class MergeResult {
        private final boolean success;
        private final String message;
        private final int chunksMerged;

        public MergeResult(boolean success, String message, int chunksMerged) {
            this.success = success;
            this.message = message;
            this.chunksMerged = chunksMerged;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public int getChunksMerged() {
            return chunksMerged;
        }
    }
}
