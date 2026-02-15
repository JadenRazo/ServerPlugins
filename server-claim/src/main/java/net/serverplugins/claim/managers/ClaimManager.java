package net.serverplugins.claim.managers;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.cache.LRUClaimCache;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimSettings;
import net.serverplugins.claim.models.ClaimedChunk;
import net.serverplugins.claim.models.PlayerChunkPool;
import net.serverplugins.claim.models.PlayerClaimData;
import net.serverplugins.claim.models.XpSource;
import net.serverplugins.claim.pricing.ExponentialPricing;
import net.serverplugins.claim.repository.ClaimGroupRepository;
import net.serverplugins.claim.repository.ClaimRepository;
import net.serverplugins.claim.util.InputValidator;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class ClaimManager {

    private final ServerClaim plugin;
    private final ClaimRepository repository;
    private final ClaimGroupRepository groupRepository;
    private final ExponentialPricing pricing;
    // Chunk position -> Claim ID mapping
    private final Map<String, Integer> chunkToClaimId = new ConcurrentHashMap<>();
    // Claim ID -> Claim object (single source of truth) - LRU cache with max 1000 entries
    private final LRUClaimCache claimCache = new LRUClaimCache(1000);
    // Player UUID -> PlayerClaimData cache (to avoid sync DB calls on main thread)
    private final Map<UUID, PlayerClaimData> playerDataCache = new ConcurrentHashMap<>();
    // Player UUID -> used chunk count cache
    private final Map<UUID, Integer> usedChunkCountCache = new ConcurrentHashMap<>();
    // Player UUID -> claim count cache (O(1) instead of O(n))
    private final Map<UUID, Integer> claimCountCache = new ConcurrentHashMap<>();
    // Global purchased chunks counter (O(1) instead of O(n))
    private final AtomicInteger globalPurchasedChunks = new AtomicInteger(0);
    // Player chunk pool cache (player UUID -> PlayerChunkPool)
    private final Map<UUID, PlayerChunkPool> playerChunkPoolCache = new ConcurrentHashMap<>();

    private volatile boolean cacheLoaded = false;

    // Track when pending inputs were created for timeout cleanup
    private final Map<UUID, Long> pendingInputTimestamps = new ConcurrentHashMap<>();
    // Cleanup task reference
    private BukkitTask pendingInputCleanupTask;
    // Timeout for pending inputs (10 minutes in milliseconds)
    private static final long PENDING_INPUT_TIMEOUT_MS = 10 * 60 * 1000;

    public ClaimManager(
            ServerClaim plugin,
            ClaimRepository repository,
            ClaimGroupRepository groupRepository,
            ExponentialPricing pricing) {
        this.plugin = plugin;
        this.repository = repository;
        this.groupRepository = groupRepository;
        this.pricing = pricing;

        // Pre-load all claims into cache on startup (async)
        preloadAllClaims();
        // Start periodic cleanup of stale pending inputs
        startPendingInputCleanup();
    }

    /**
     * Start periodic cleanup task for stale pending inputs. Removes entries older than
     * PENDING_INPUT_TIMEOUT_MS.
     */
    private void startPendingInputCleanup() {
        // Run every minute to check for stale entries
        pendingInputCleanupTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimerAsynchronously(
                                plugin,
                                () -> {
                                    long now = System.currentTimeMillis();
                                    int cleaned = 0;

                                    // Clean up entries older than timeout
                                    Iterator<Map.Entry<UUID, Long>> iterator =
                                            pendingInputTimestamps.entrySet().iterator();
                                    while (iterator.hasNext()) {
                                        Map.Entry<UUID, Long> entry = iterator.next();
                                        if (now - entry.getValue() > PENDING_INPUT_TIMEOUT_MS) {
                                            UUID uuid = entry.getKey();
                                            iterator.remove();

                                            // Remove from all pending input maps
                                            if (pendingTrustInputs.remove(uuid) != null) cleaned++;
                                            if (pendingAdminSearch.remove(uuid) != null) cleaned++;
                                            if (pendingClaimRename.remove(uuid) != null) cleaned++;
                                            if (pendingGroupRename.remove(uuid) != null) cleaned++;
                                            if (pendingChunkTransfers.remove(uuid) != null)
                                                cleaned++;
                                        }
                                    }

                                    if (cleaned > 0) {
                                        plugin.getLogger()
                                                .fine(
                                                        "Cleaned up "
                                                                + cleaned
                                                                + " stale pending input(s)");
                                    }
                                },
                                20L * 60,
                                20L * 60); // Every minute
    }

    /** Stop the pending input cleanup task. Call on plugin disable. */
    public void stop() {
        if (pendingInputCleanupTask != null) {
            pendingInputCleanupTask.cancel();
            pendingInputCleanupTask = null;
        }
    }

    /** Get the maximum number of claims a player can have based on permissions. */
    public int getMaxClaims(Player player) {
        int maxCheck = plugin.getClaimConfig().getMaxClaimsCheck();

        // Check permissions from highest to lowest
        for (int i = maxCheck; i >= 1; i--) {
            if (player.hasPermission("serverclaim.claims." + i)) {
                return i;
            }
        }

        return plugin.getClaimConfig().getDefaultMaxClaims();
    }

    /**
     * Get maximum chunks allowed per profile based on LuckPerms permissions. Checks
     * serverclaim.chunks.<number> permissions from highest to lowest.
     *
     * @param player The player to check
     * @return Maximum chunks per profile (capacity limit for chunk allocation)
     */
    public int getMaxChunksPerProfile(Player player) {
        int maxCheck = plugin.getClaimConfig().getMaxChunksCheck(); // e.g., 1000

        // Check permissions from highest to lowest
        for (int i = maxCheck; i >= 1; i--) {
            if (player.hasPermission("serverclaim.chunks." + i)) {
                return i;
            }
        }

        // Return default if no permission found
        return plugin.getClaimConfig().getDefaultMaxChunksPerProfile(); // 100
    }

    /** Get the current number of claims a player has. O(1) operation using cached count. */
    public int getClaimCount(UUID uuid) {
        Integer count = claimCountCache.get(uuid);
        if (count != null) {
            return count;
        }

        // Cache miss - calculate from claim cache (only on first access)
        count =
                (int)
                        claimCache.values().stream()
                                .filter(c -> c.getOwnerUuid().equals(uuid))
                                .count();

        claimCountCache.put(uuid, count);
        return count;
    }

    /**
     * Gets the total number of claims on the server. Used for performance tier determination in
     * particle rendering. O(1) operation using cache size.
     */
    public int getTotalClaimCount() {
        return claimCache.size();
    }

    /**
     * Get the total number of PURCHASED chunks across ALL claims/profiles for a player. Used for
     * global pricing to prevent gaming by creating new profiles for cheap chunks. O(n) operation -
     * iterates through all cached claims for this player. Note: Cannot use AtomicInteger as this is
     * per-player, not global.
     */
    public int getGlobalPurchasedChunks(UUID uuid) {
        return claimCache.values().stream()
                .filter(c -> c.getOwnerUuid().equals(uuid))
                .mapToInt(Claim::getPurchasedChunks)
                .sum();
    }

    /**
     * Get the global price for the next chunk purchase for a specific claim/profile. Uses global
     * chunk count (across ALL profiles) + profile order multiplier.
     *
     * @deprecated Use getNextChunkPrice(Claim) instead for per-profile pricing
     */
    @Deprecated
    public double getNextGlobalPrice(UUID playerId, Claim targetClaim) {
        int globalPurchased = getGlobalPurchasedChunks(playerId);
        return pricing.getGlobalPrice(globalPurchased + 1, targetClaim.getClaimOrder());
    }

    /**
     * Get the price for the next chunk purchase for a specific claim/profile. Uses per-profile
     * pricing based on the claim's purchased chunks.
     *
     * @param claim The claim to get the next chunk price for
     * @return The price for the next chunk in this claim
     */
    public double getNextChunkPrice(Claim claim) {
        int profilePurchased = claim.getPurchasedChunks();
        int claimOrder = claim.getClaimOrder();
        return pricing.getPrice(profilePurchased + 1, claimOrder);
    }

    /** Check if a player can create a new claim (separate from existing claims). */
    public boolean canCreateNewClaim(Player player) {
        int maxClaims = getMaxClaims(player);
        int currentClaims = getClaimCount(player.getUniqueId());
        return currentClaims < maxClaims;
    }

    /**
     * Pre-load all claims into cache to avoid synchronous DB queries on main thread. Uses batch
     * loading for chunks to eliminate N+1 query problem. Also initializes claim count and global
     * purchased chunks caches.
     */
    private void preloadAllClaims() {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                long start = System.currentTimeMillis();

                                // Load all claims WITHOUT their chunks first (fast - single query)
                                List<Claim> allClaims = repository.getAllClaims();

                                if (allClaims.isEmpty()) {
                                    cacheLoaded = true;
                                    plugin.getLogger().info("No claims to load into cache");
                                    return;
                                }

                                long claimsLoaded = System.currentTimeMillis();

                                // Batch load all chunks in ONE query instead of N queries
                                List<Integer> claimIds =
                                        allClaims.stream().map(Claim::getId).toList();

                                Map<Integer, List<ClaimedChunk>> chunksByClaimId =
                                        repository.loadChunksBatch(claimIds);
                                long chunksLoaded = System.currentTimeMillis();

                                // Track claim counts per player for cache initialization
                                Map<UUID, Integer> tempClaimCounts = new HashMap<>();
                                int totalPurchasedChunks = 0;
                                int totalChunks = 0;

                                // Assign chunks to claims and build cache
                                for (Claim claim : allClaims) {
                                    // Populate the cached owner name
                                    PlayerClaimData playerData =
                                            repository.getPlayerData(claim.getOwnerUuid());
                                    if (playerData != null) {
                                        claim.setCachedOwnerName(playerData.getUsername());
                                    }

                                    // Add batch-loaded chunks to claim
                                    List<ClaimedChunk> chunks = chunksByClaimId.get(claim.getId());
                                    if (chunks != null) {
                                        for (ClaimedChunk chunk : chunks) {
                                            claim.addChunk(chunk);
                                            String key =
                                                    chunk.getWorld()
                                                            + ":"
                                                            + chunk.getChunkX()
                                                            + ":"
                                                            + chunk.getChunkZ();
                                            chunkToClaimId.put(key, claim.getId());
                                            totalChunks++;
                                        }
                                    }

                                    // Add to claim cache
                                    claimCache.put(claim.getId(), claim);

                                    // Update claim count cache
                                    UUID ownerUuid = claim.getOwnerUuid();
                                    tempClaimCounts.put(
                                            ownerUuid,
                                            tempClaimCounts.getOrDefault(ownerUuid, 0) + 1);

                                    // Accumulate purchased chunks for global counter
                                    totalPurchasedChunks += claim.getPurchasedChunks();
                                }

                                // Initialize claim count cache
                                claimCountCache.putAll(tempClaimCounts);

                                // Initialize global purchased chunks counter
                                globalPurchasedChunks.set(totalPurchasedChunks);

                                // Load all player chunk pools into cache
                                try {
                                    Map<UUID, PlayerChunkPool> pools =
                                            repository.getAllPlayerChunkPools();
                                    playerChunkPoolCache.putAll(pools);
                                    plugin.getLogger()
                                            .info(
                                                    "Loaded "
                                                            + pools.size()
                                                            + " player chunk pools into cache");
                                } catch (SQLException e) {
                                    plugin.getLogger()
                                            .severe(
                                                    "Failed to load player chunk pools: "
                                                            + e.getMessage());
                                    e.printStackTrace();
                                }

                                cacheLoaded = true;
                                long elapsed = System.currentTimeMillis() - start;
                                long claimTime = claimsLoaded - start;
                                long chunkTime = chunksLoaded - claimsLoaded;
                                long processingTime = elapsed - chunksLoaded;

                                plugin.getLogger()
                                        .info(
                                                "Loaded "
                                                        + allClaims.size()
                                                        + " claims into cache ("
                                                        + totalChunks
                                                        + " chunks, "
                                                        + totalPurchasedChunks
                                                        + " purchased) in "
                                                        + elapsed
                                                        + "ms (claims: "
                                                        + claimTime
                                                        + "ms, chunks: "
                                                        + chunkTime
                                                        + "ms, "
                                                        + "processing: "
                                                        + processingTime
                                                        + "ms) - Cache stats: "
                                                        + claimCache.getStats());
                            } catch (Exception e) {
                                plugin.getLogger()
                                        .severe(
                                                "Failed to preload claims cache: "
                                                        + e.getMessage());
                                e.printStackTrace();
                                cacheLoaded = true; // Mark as loaded anyway to prevent hanging
                            }
                        });
    }

    public CompletableFuture<ClaimResult> claimChunk(Player player, Chunk chunk) {
        return CompletableFuture.supplyAsync(
                () -> {
                    String cacheKey = getCacheKey(chunk);

                    try {
                        // Check if world allows claiming
                        if (!plugin.getClaimConfig().isWorldAllowed(chunk.getWorld().getName())) {
                            plugin.getLogger()
                                    .fine(
                                            "Player "
                                                    + player.getName()
                                                    + " tried to claim in disallowed world: "
                                                    + chunk.getWorld().getName());
                            return ClaimResult.failure("world-not-allowed");
                        }

                        Claim existing = getClaimAt(chunk);
                        if (existing != null) {
                            plugin.getLogger()
                                    .fine(
                                            "Chunk at "
                                                    + chunk.getX()
                                                    + ", "
                                                    + chunk.getZ()
                                                    + " already claimed by "
                                                    + existing.getOwnerUuid());
                            return ClaimResult.failure("already-claimed");
                        }

                        // Ensure player data exists (for username caching)
                        PlayerClaimData playerData = repository.getPlayerData(player.getUniqueId());
                        if (playerData == null) {
                            playerData =
                                    new PlayerClaimData(
                                            player.getUniqueId(),
                                            player.getName(),
                                            plugin.getClaimConfig().getStartingChunks());
                            repository.savePlayerData(playerData);
                            plugin.getLogger()
                                    .info("Created new player data for " + player.getName());
                        }

                        // Get all claims by this player in this world
                        List<Claim> playerClaimsInWorld =
                                getCachedClaimsByOwnerInWorld(
                                        player.getUniqueId(), chunk.getWorld().getName());

                        // Find if chunk is adjacent to any existing claim
                        Claim adjacentClaim = null;
                        for (Claim c : playerClaimsInWorld) {
                            if (!c.getChunks().isEmpty()
                                    && c.isAdjacentTo(chunk.getX(), chunk.getZ())) {
                                adjacentClaim = c;
                                break;
                            }
                        }

                        Claim claim;
                        boolean needsAutoAllocation = false;

                        if (adjacentClaim != null) {
                            // Add to existing adjacent claim
                            claim = adjacentClaim;

                            // Check if claim has available chunks from current allocation
                            if (!claim.hasAvailableChunks()) {
                                // Claim is full - try to auto-allocate from global pool
                                PlayerChunkPool pool =
                                        playerChunkPoolCache.get(player.getUniqueId());
                                if (pool == null) {
                                    try {
                                        pool =
                                                repository.getOrCreatePlayerChunkPool(
                                                        player.getUniqueId());
                                        playerChunkPoolCache.put(player.getUniqueId(), pool);
                                    } catch (Exception e) {
                                        plugin.getLogger()
                                                .severe(
                                                        "Failed to load chunk pool for "
                                                                + player.getName()
                                                                + ": "
                                                                + e.getMessage());
                                        return ClaimResult.failure("error");
                                    }
                                }

                                // Calculate available chunks in global pool
                                int totalPurchased = pool.getPurchasedChunks();
                                int totalAllocated;
                                try {
                                    totalAllocated =
                                            repository.getTotalAllocatedChunks(
                                                    player.getUniqueId());
                                } catch (Exception e) {
                                    plugin.getLogger()
                                            .severe(
                                                    "Failed to get allocated chunks for "
                                                            + player.getName()
                                                            + ": "
                                                            + e.getMessage());
                                    return ClaimResult.failure("error");
                                }
                                int availableInPool = totalPurchased - totalAllocated;

                                // Check if global pool has chunks available
                                if (availableInPool <= 0) {
                                    plugin.getLogger()
                                            .warning(
                                                    "Player "
                                                            + player.getName()
                                                            + " tried to claim chunk but has no chunks in global pool (Purchased: "
                                                            + totalPurchased
                                                            + ", Allocated: "
                                                            + totalAllocated
                                                            + ")");
                                    return ClaimResult.failure("no-chunks-left");
                                }

                                // Check profile capacity limit
                                int maxPerProfile = getMaxChunksPerProfile(player);
                                if (claim.getPurchasedChunks() >= maxPerProfile) {
                                    plugin.getLogger()
                                            .warning(
                                                    "Player "
                                                            + player.getName()
                                                            + " reached profile capacity limit ("
                                                            + maxPerProfile
                                                            + ")");
                                    return ClaimResult.failure("profile-limit-reached");
                                }

                                needsAutoAllocation = true;
                                plugin.getLogger()
                                        .fine(
                                                "Auto-allocating 1 chunk from global pool to claim ID "
                                                        + claim.getId()
                                                        + " (Available: "
                                                        + availableInPool
                                                        + ")");
                            }

                            plugin.getLogger()
                                    .fine("Adding chunk to existing claim ID " + claim.getId());
                        } else if (!playerClaimsInWorld.isEmpty()) {
                            // Player has claims but chunk is not adjacent to any
                            // Check if player can create a new claim (globally, not just in this
                            // world)
                            int maxClaims = getMaxClaims(player);
                            int currentClaims = getClaimCount(player.getUniqueId());
                            if (currentClaims >= maxClaims) {
                                plugin.getLogger()
                                        .warning(
                                                "Player "
                                                        + player.getName()
                                                        + " tried to claim non-adjacent chunk but reached max claims limit ("
                                                        + currentClaims
                                                        + "/"
                                                        + maxClaims
                                                        + ")");
                                return ClaimResult.failure("not-adjacent");
                            }

                            // Player can create a new claim - start a fresh one
                            claim = null;
                            plugin.getLogger()
                                    .fine(
                                            "Player can create new claim (current: "
                                                    + currentClaims
                                                    + ", max: "
                                                    + maxClaims
                                                    + ")");
                        } else {
                            // Player has no claims in this world - will create a new one
                            claim = null;
                            plugin.getLogger()
                                    .fine(
                                            "Player has no claims in this world, creating first claim");
                        }

                        final Claim finalClaim;
                        final boolean finalNeedsAutoAllocation = needsAutoAllocation;

                        if (claim == null) {
                            // Calculate claim order (1st, 2nd, 3rd claim, etc.)
                            int claimOrder = getClaimCount(player.getUniqueId()) + 1;

                            Claim newClaim =
                                    new Claim(player.getUniqueId(), chunk.getWorld().getName());
                            // Set per-claim chunk pool
                            newClaim.setTotalChunks(
                                    plugin.getClaimConfig().getStartingChunksPerClaim());
                            newClaim.setPurchasedChunks(0);
                            newClaim.setClaimOrder(claimOrder);
                            // Set default settings
                            newClaim.setSettings(
                                    new ClaimSettings(
                                            plugin.getClaimConfig().getDefaultPvp(),
                                            plugin.getClaimConfig().getDefaultFireSpread(),
                                            plugin.getClaimConfig().getDefaultExplosions(),
                                            plugin.getClaimConfig().getDefaultHostileSpawns(),
                                            plugin.getClaimConfig().getDefaultMobGriefing(),
                                            plugin.getClaimConfig().getDefaultPassiveSpawns(),
                                            false, // crop trampling disabled by default (crops
                                            // protected)
                                            true // leaf decay enabled by default
                                            ));
                            // Cache the owner name immediately
                            newClaim.setCachedOwnerName(playerData.getUsername());

                            repository.saveClaim(newClaim);

                            if (newClaim.getId() == 0) {
                                plugin.getLogger()
                                        .severe(
                                                "CRITICAL: Failed to save new claim for "
                                                        + player.getName()
                                                        + " - claim ID is 0 after save");
                                return ClaimResult.failure("error");
                            }

                            plugin.getLogger()
                                    .info(
                                            "Created new claim with ID: "
                                                    + newClaim.getId()
                                                    + " for "
                                                    + player.getName()
                                                    + " (Order: "
                                                    + claimOrder
                                                    + ", Starting chunks: "
                                                    + newClaim.getTotalChunks()
                                                    + ")");

                            finalClaim = newClaim;
                        } else {
                            finalClaim = claim;
                        }

                        // Wrap chunk creation in transaction to ensure atomicity
                        final ClaimedChunk claimedChunk =
                                ClaimedChunk.fromBukkitChunk(finalClaim.getId(), chunk);

                        try {
                            repository.executeInTransaction(
                                    () -> {
                                        // Save chunk to database
                                        repository.saveChunk(claimedChunk);

                                        // Auto-allocate from global pool if needed
                                        if (finalNeedsAutoAllocation) {
                                            // Increment allocated chunks for this profile
                                            finalClaim.addPurchasedChunks(1);
                                            finalClaim.setTotalChunks(
                                                    finalClaim.getTotalChunks() + 1);
                                            repository.saveClaimChunkData(finalClaim);
                                            plugin.getLogger()
                                                    .info(
                                                            "Auto-allocated 1 chunk from global pool to profile '"
                                                                    + finalClaim.getName()
                                                                    + "' (ID: "
                                                                    + finalClaim.getId()
                                                                    + ")");
                                        }
                                    });

                            // Only update cache after successful database commit
                            finalClaim.addChunk(claimedChunk);

                            // Add new claim to cache if this was a new claim
                            if (claim == null) {
                                claimCache.put(finalClaim.getId(), finalClaim);
                                // Increment claim count cache for owner
                                claimCountCache.compute(
                                        player.getUniqueId(), (k, v) -> v == null ? 1 : v + 1);
                            }

                            // Update chunk -> claim ID mapping
                            chunkToClaimId.put(cacheKey, finalClaim.getId());
                            // Update used chunk count cache
                            incrementUsedChunkCount(player.getUniqueId());

                            // Grant XP for claiming a chunk
                            if (plugin.getLevelManager() != null) {
                                plugin.getLevelManager()
                                        .grantXp(
                                                finalClaim.getId(),
                                                player.getUniqueId(),
                                                XpSource.CHUNK_CLAIMED);
                            }

                            // Log chunk claim activity
                            if (plugin.getAuditLogRepository() != null) {
                                plugin.getAuditLogRepository()
                                        .logActivity(
                                                finalClaim.getId(),
                                                player,
                                                net.serverplugins.claim.repository.AuditLogRepository
                                                        .ActivityType.CHUNK_PURCHASE,
                                                String.format(
                                                        "Claimed chunk at %s (%d, %d)",
                                                        chunk.getWorld().getName(),
                                                        chunk.getX(),
                                                        chunk.getZ()),
                                                null);
                            }

                            plugin.getLogger()
                                    .info(
                                            "Successfully claimed chunk at "
                                                    + chunk.getX()
                                                    + ", "
                                                    + chunk.getZ()
                                                    + " for "
                                                    + player.getName()
                                                    + " (Claim ID: "
                                                    + finalClaim.getId()
                                                    + ", Now "
                                                    + finalClaim.getChunks().size()
                                                    + "/"
                                                    + finalClaim.getTotalChunks()
                                                    + " chunks used)");

                            // Notify BlueMap to update markers
                            notifyBlueMapUpdate();

                            return ClaimResult.success(finalClaim);

                        } catch (Exception dbError) {
                            // Transaction failed and was rolled back - clean up
                            plugin.getLogger()
                                    .severe(
                                            "Database transaction failed while claiming chunk at "
                                                    + chunk.getX()
                                                    + ", "
                                                    + chunk.getZ()
                                                    + " for player "
                                                    + player.getName()
                                                    + ": "
                                                    + dbError.getMessage());
                            dbError.printStackTrace();

                            // If this was a new claim, remove it from cache since it wasn't saved
                            if (claim == null && finalClaim.getId() != 0) {
                                claimCache.remove(finalClaim.getId());
                            }

                            return ClaimResult.failure("error");
                        }

                    } catch (Exception e) {
                        plugin.getLogger()
                                .severe(
                                        "Error claiming chunk at "
                                                + chunk.getX()
                                                + ", "
                                                + chunk.getZ()
                                                + " for player "
                                                + player.getName()
                                                + ": "
                                                + e.getMessage());
                        e.printStackTrace();
                        return ClaimResult.failure("error");
                    }
                });
    }

    public CompletableFuture<ClaimResult> unclaimChunk(Player player, Chunk chunk) {
        return CompletableFuture.supplyAsync(
                () -> {
                    String cacheKey = getCacheKey(chunk);

                    Claim claim = getClaimAt(chunk);
                    if (claim == null) {
                        return ClaimResult.failure("no-claim-here");
                    }

                    if (!claim.isOwner(player.getUniqueId())
                            && !player.hasPermission("serverclaim.admin")) {
                        return ClaimResult.failure("not-your-claim");
                    }

                    ClaimedChunk toRemove =
                            claim.getChunks().stream()
                                    .filter(c -> c.matches(chunk))
                                    .findFirst()
                                    .orElse(null);

                    if (toRemove != null) {
                        repository.deleteChunk(toRemove);
                        claim.removeChunk(toRemove);
                        chunkToClaimId.remove(cacheKey);
                        // Update used chunk count cache for claim owner
                        decrementUsedChunkCount(claim.getOwnerUuid());

                        // Log chunk unclaim activity
                        if (plugin.getAuditLogRepository() != null) {
                            plugin.getAuditLogRepository()
                                    .logActivity(
                                            claim.getId(),
                                            player,
                                            net.serverplugins.claim.repository.AuditLogRepository
                                                    .ActivityType.CHUNK_UNCLAIM,
                                            String.format(
                                                    "Unclaimed chunk at %s (%d, %d)",
                                                    chunk.getWorld().getName(),
                                                    chunk.getX(),
                                                    chunk.getZ()),
                                            null);
                        }

                        // DON'T delete empty claims - preserve purchased chunk slots
                        // Players keep their purchased chunks even when all chunks are unclaimed
                        // This allows them to re-claim later without losing their investment
                        // To explicitly delete a claim, players should use a dedicated delete
                        // command
                        if (claim.getChunks().isEmpty()) {
                            plugin.getLogger()
                                    .info(
                                            String.format(
                                                    "Profile '%s' (ID: %d) is now empty but preserved with %d purchased chunk slots",
                                                    claim.getName(),
                                                    claim.getId(),
                                                    claim.getPurchasedChunks()));
                        }

                        // Notify BlueMap to update markers
                        notifyBlueMapUpdate();
                    }

                    return ClaimResult.success(claim);
                });
    }

    /**
     * @deprecated Use {@link #purchaseChunkForClaim(Player, Claim, Consumer)} for per-claim
     *     purchasing
     */
    @Deprecated
    public CompletableFuture<Boolean> purchaseChunk(Player player) {
        return CompletableFuture.supplyAsync(
                () -> {
                    PlayerClaimData playerData = repository.getPlayerData(player.getUniqueId());
                    if (playerData == null) return false;

                    int nextChunk = playerData.getPurchasedChunks() + 1;
                    double price = pricing.getPrice(nextChunk);

                    if (price < 0) return false;

                    if (plugin.getEconomy() == null || !plugin.getEconomy().has(player, price)) {
                        return false;
                    }

                    plugin.getEconomy().withdraw(player, price);
                    playerData.addChunks(1);
                    repository.savePlayerData(playerData);
                    // Update cache
                    updateCachedPlayerData(player.getUniqueId(), playerData);

                    return true;
                });
    }

    /** Get the next chunk number for a specific claim (1-based, what chunk they'll be buying). */
    public int getNextChunkNumberForClaim(Claim claim) {
        // total purchased + starting chunks = current total chunks
        // next chunk number = current used chunks + 1 within the claim's pool
        return claim.getChunks().size() + 1;
    }

    /** Get the price for the next chunk for a specific claim, including claim order multiplier. */
    public double getNextChunkPriceForClaim(Claim claim) {
        int chunkNumber = getNextChunkNumberForClaim(claim);
        int claimOrder = claim.getClaimOrder();
        return pricing.getPrice(chunkNumber, claimOrder);
    }

    /** Get remaining chunks that can be claimed for a specific claim. */
    public int getRemainingChunksForClaim(Claim claim) {
        return claim.getRemainingChunks();
    }

    /**
     * Purchase a chunk for a specific claim using GLOBAL pricing. Price is based on total chunks
     * owned across ALL profiles, plus profile multiplier.
     */
    public void purchaseChunkForClaim(
            Player player, Claim claim, Consumer<PurchaseResult> callback) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();

        // Verify ownership
        if (!claim.isOwner(playerId) && !player.hasPermission("serverclaim.admin")) {
            callback.accept(PurchaseResult.NO_PLAYER_DATA);
            return;
        }

        int nextChunkNumber = getNextChunkNumberForClaim(claim);
        // Use per-profile pricing: price based on this profile's purchased chunks
        final int profilePurchased = claim.getPurchasedChunks();
        final int claimOrder = claim.getClaimOrder();
        double price = pricing.getPrice(profilePurchased + 1, claimOrder);

        // Debug logging for price verification
        plugin.getLogger()
                .info(
                        String.format(
                                "Chunk purchase for %s: Profile #%d, Chunk #%d, Price: $%.2f",
                                playerName, claimOrder, profilePurchased + 1, price));

        // Check if max chunks per claim/profile reached (50 per profile)
        if (nextChunkNumber > pricing.getMaxChunksPerClaim()) {
            callback.accept(PurchaseResult.MAX_CHUNKS_REACHED);
            return;
        }

        // Check if economy is available
        if (plugin.getEconomy() == null) {
            plugin.getLogger().warning("Economy provider is null during chunk purchase");
            callback.accept(PurchaseResult.ECONOMY_ERROR);
            return;
        }

        // Check if player has enough money
        if (!plugin.getEconomy().has(player, price)) {
            callback.accept(PurchaseResult.INSUFFICIENT_FUNDS);
            return;
        }

        // Withdraw money on main thread
        Runnable withdrawAndProcess =
                () -> {
                    boolean withdrawn = plugin.getEconomy().withdraw(player, price);
                    if (!withdrawn) {
                        plugin.getLogger()
                                .warning(
                                        "Withdrawal failed for "
                                                + playerName
                                                + " - amount: $"
                                                + price);
                        callback.accept(PurchaseResult.ECONOMY_ERROR);
                        return;
                    }

                    final double priceToRefund = price;

                    // Perform database operations asynchronously with transaction
                    plugin.getServer()
                            .getScheduler()
                            .runTaskAsynchronously(
                                    plugin,
                                    () -> {
                                        try {
                                            // Wrap database operation in transaction for atomicity
                                            repository.executeInTransaction(
                                                    () -> {
                                                        // Update claim's chunk data
                                                        claim.addPurchasedChunks(1);
                                                        repository.saveClaimChunkData(claim);
                                                    });

                                            // Increment global purchased chunks counter after
                                            // successful transaction
                                            globalPurchasedChunks.incrementAndGet();

                                            // Only after successful commit, notify success
                                            plugin.getServer()
                                                    .getScheduler()
                                                    .runTask(
                                                            plugin,
                                                            () -> {
                                                                // Log chunk purchase activity
                                                                if (plugin.getAuditLogRepository()
                                                                        != null) {
                                                                    plugin.getAuditLogRepository()
                                                                            .logActivity(
                                                                                    claim.getId(),
                                                                                    playerId,
                                                                                    net.serverplugins
                                                                                            .claim
                                                                                            .repository
                                                                                            .AuditLogRepository
                                                                                            .ActivityType
                                                                                            .CHUNK_PURCHASE,
                                                                                    String.format(
                                                                                            "Purchased chunk #%d for profile #%d",
                                                                                            nextChunkNumber,
                                                                                            claimOrder),
                                                                                    priceToRefund);
                                                                }

                                                                plugin.getLogger()
                                                                        .info(
                                                                                "Player "
                                                                                        + playerName
                                                                                        + " purchased chunk #"
                                                                                        + nextChunkNumber
                                                                                        + " for claim '"
                                                                                        + claim
                                                                                                .getName()
                                                                                        + "' (ID: "
                                                                                        + claim
                                                                                                .getId()
                                                                                        + ") for $"
                                                                                        + String
                                                                                                .format(
                                                                                                        "%.2f",
                                                                                                        priceToRefund)
                                                                                        + " (Profile order: "
                                                                                        + claimOrder
                                                                                        + ", multiplier: "
                                                                                        + pricing
                                                                                                .formatMultiplier(
                                                                                                        claimOrder)
                                                                                        + ")");

                                                                // Notify BlueMap to update markers
                                                                notifyBlueMapUpdate();

                                                                callback.accept(
                                                                        PurchaseResult.SUCCESS);
                                                            });

                                        } catch (Exception e) {
                                            // Transaction failed and was rolled back automatically
                                            plugin.getLogger()
                                                    .severe(
                                                            "Database transaction failed during claim chunk purchase for "
                                                                    + playerName
                                                                    + ": "
                                                                    + e.getMessage());
                                            e.printStackTrace();

                                            // Refund the player since database operation failed
                                            plugin.getServer()
                                                    .getScheduler()
                                                    .runTask(
                                                            plugin,
                                                            () -> {
                                                                try {
                                                                    plugin.getEconomy()
                                                                            .deposit(
                                                                                    player,
                                                                                    priceToRefund);
                                                                    plugin.getLogger()
                                                                            .warning(
                                                                                    "Refunded $"
                                                                                            + String
                                                                                                    .format(
                                                                                                            "%.2f",
                                                                                                            priceToRefund)
                                                                                            + " to "
                                                                                            + playerName
                                                                                            + " after purchase error (transaction rolled back)");
                                                                } catch (Exception refundError) {
                                                                    plugin.getLogger()
                                                                            .severe(
                                                                                    "CRITICAL: Failed to refund "
                                                                                            + playerName
                                                                                            + " after error! Amount: $"
                                                                                            + priceToRefund);
                                                                    refundError.printStackTrace();
                                                                }
                                                                callback.accept(
                                                                        PurchaseResult
                                                                                .DATABASE_ERROR);
                                                            });
                                        }
                                    });
                };

        // Ensure withdrawal happens on main thread
        if (plugin.getServer().isPrimaryThread()) {
            withdrawAndProcess.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, withdrawAndProcess);
        }
    }

    /**
     * Purchase chunks in bulk and add to player's global pool. This is the new preferred method for
     * chunk purchases that uses the global chunk pool system. Uses exponential pricing based on
     * player's current global chunk count.
     *
     * @param player The player purchasing chunks
     * @param amount Number of chunks to purchase (must be 1, 5, 10, 50, or 100)
     * @param callback Success/failure callback
     */
    public void purchaseChunksBulk(Player player, int amount, Consumer<PurchaseResult> callback) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();

        // 1. VALIDATE AMOUNT
        if (amount <= 0
                || (amount != 1 && amount != 5 && amount != 10 && amount != 50 && amount != 100)) {
            callback.accept(PurchaseResult.INVALID_AMOUNT);
            return;
        }

        // 2. GET CURRENT GLOBAL CHUNKS
        PlayerChunkPool pool = playerChunkPoolCache.get(playerId);
        int currentGlobalChunks = pool != null ? pool.getPurchasedChunks() : 0;

        // 3. CALCULATE PRICE (exponential based on current global chunks)
        double totalPrice =
                plugin.getClaimConfig().calculateGlobalChunkPrice(currentGlobalChunks, amount);

        // 3. CHECK ECONOMY
        if (plugin.getEconomy() == null) {
            plugin.getLogger().warning("Economy provider is null during bulk chunk purchase");
            callback.accept(PurchaseResult.ECONOMY_ERROR);
            return;
        }

        // 4. CHECK FUNDS
        if (!plugin.getEconomy().has(player, totalPrice)) {
            callback.accept(PurchaseResult.INSUFFICIENT_FUNDS);
            return;
        }

        // 5. WITHDRAW MONEY (main thread)
        Runnable withdrawAndProcess =
                () -> {
                    boolean withdrawn = plugin.getEconomy().withdraw(player, totalPrice);
                    if (!withdrawn) {
                        plugin.getLogger()
                                .warning(
                                        "Withdrawal failed for "
                                                + playerName
                                                + " - amount: $"
                                                + totalPrice);
                        callback.accept(PurchaseResult.ECONOMY_ERROR);
                        return;
                    }

                    final double priceToRefund = totalPrice;

                    // 6. UPDATE DATABASE (async transaction)
                    plugin.getServer()
                            .getScheduler()
                            .runTaskAsynchronously(
                                    plugin,
                                    () -> {
                                        try {
                                            repository.executeInTransaction(
                                                    () -> {
                                                        // Increment player's global chunk pool
                                                        // atomically
                                                        repository.incrementPlayerChunks(
                                                                playerId, amount, totalPrice);
                                                    });

                                            // SUCCESS: Update cache and callback
                                            plugin.getServer()
                                                    .getScheduler()
                                                    .runTask(
                                                            plugin,
                                                            () -> {
                                                                PlayerChunkPool updatedPool =
                                                                        playerChunkPoolCache
                                                                                .computeIfAbsent(
                                                                                        playerId,
                                                                                        k ->
                                                                                                new PlayerChunkPool(
                                                                                                        playerId));
                                                                updatedPool.addChunks(
                                                                        amount, totalPrice);

                                                                double avgPerChunk =
                                                                        totalPrice / amount;
                                                                plugin.getLogger()
                                                                        .info(
                                                                                String.format(
                                                                                        "Player %s purchased %d chunks for $%.2f (avg $%.2f per chunk) - Total pool: %d chunks",
                                                                                        playerName,
                                                                                        amount,
                                                                                        totalPrice,
                                                                                        avgPerChunk,
                                                                                        updatedPool
                                                                                                .getPurchasedChunks()));

                                                                callback.accept(
                                                                        PurchaseResult.SUCCESS);
                                                            });

                                        } catch (Exception e) {
                                            // TRANSACTION FAILED: Refund money
                                            plugin.getLogger()
                                                    .severe(
                                                            "Bulk chunk purchase failed for "
                                                                    + playerName
                                                                    + ": "
                                                                    + e.getMessage());
                                            e.printStackTrace();

                                            plugin.getServer()
                                                    .getScheduler()
                                                    .runTask(
                                                            plugin,
                                                            () -> {
                                                                try {
                                                                    plugin.getEconomy()
                                                                            .deposit(
                                                                                    player,
                                                                                    priceToRefund);
                                                                    plugin.getLogger()
                                                                            .warning(
                                                                                    "Refunded $"
                                                                                            + String
                                                                                                    .format(
                                                                                                            "%.2f",
                                                                                                            priceToRefund)
                                                                                            + " to "
                                                                                            + playerName
                                                                                            + " after purchase error");
                                                                } catch (Exception refundError) {
                                                                    plugin.getLogger()
                                                                            .severe(
                                                                                    "CRITICAL: Failed to refund "
                                                                                            + playerName
                                                                                            + " after purchase error! Amount: $"
                                                                                            + priceToRefund);
                                                                    refundError.printStackTrace();
                                                                }
                                                                callback.accept(
                                                                        PurchaseResult
                                                                                .DATABASE_ERROR);
                                                            });
                                        }
                                    });
                };

        // Ensure withdrawal happens on main thread
        if (plugin.getServer().isPrimaryThread()) {
            withdrawAndProcess.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, withdrawAndProcess);
        }
    }

    /**
     * Allocate chunks from player's global pool to a specific profile. This allows players to
     * distribute their purchased chunks across different profiles.
     *
     * @param player The player allocating chunks
     * @param claim The claim/profile to allocate chunks to
     * @param amount Number of chunks to allocate
     * @return CompletableFuture with the result of the allocation
     */
    public CompletableFuture<AllocationResult> allocateChunksToProfile(
            Player player, Claim claim, int amount) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();

        // 1. VERIFY OWNERSHIP
        if (!claim.isOwner(playerId) && !player.hasPermission("serverclaim.admin")) {
            return CompletableFuture.completedFuture(AllocationResult.NOT_OWNER);
        }

        // 2. CHECK GLOBAL POOL AVAILABILITY
        PlayerChunkPool pool = playerChunkPoolCache.get(playerId);
        if (pool == null) {
            return CompletableFuture.completedFuture(AllocationResult.INSUFFICIENT_CHUNKS);
        }

        // Calculate available chunks (purchased - already allocated across all profiles)
        int totalAllocated;
        try {
            totalAllocated = repository.getTotalAllocatedChunks(playerId);
        } catch (Exception e) {
            plugin.getLogger()
                    .severe(
                            "Failed to get total allocated chunks for "
                                    + playerName
                                    + ": "
                                    + e.getMessage());
            return CompletableFuture.completedFuture(AllocationResult.DATABASE_ERROR);
        }

        int availableChunks = pool.getPurchasedChunks() - totalAllocated;

        if (availableChunks < amount) {
            plugin.getLogger()
                    .info(
                            String.format(
                                    "Allocation blocked for %s: Requested %d chunks but only %d available (Purchased: %d, Allocated: %d)",
                                    playerName,
                                    amount,
                                    availableChunks,
                                    pool.getPurchasedChunks(),
                                    totalAllocated));
            return CompletableFuture.completedFuture(AllocationResult.INSUFFICIENT_CHUNKS);
        }

        // 3. CHECK PROFILE CAPACITY LIMIT
        int maxPerProfile = getMaxChunksPerProfile(player);
        int currentAllocated = claim.getPurchasedChunks();

        if (currentAllocated + amount > maxPerProfile) {
            plugin.getLogger()
                    .info(
                            String.format(
                                    "Allocation blocked for %s: Current %d + Amount %d = %d exceeds max %d",
                                    playerName,
                                    currentAllocated,
                                    amount,
                                    currentAllocated + amount,
                                    maxPerProfile));
            return CompletableFuture.completedFuture(AllocationResult.PROFILE_CAPACITY_EXCEEDED);
        }

        // 4. ALLOCATE (update database asynchronously)
        CompletableFuture<AllocationResult> future = new CompletableFuture<>();

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                repository.executeInTransaction(
                                        () -> {
                                            // Increase claim's allocated chunks
                                            claim.addPurchasedChunks(amount);
                                            claim.setTotalChunks(claim.getTotalChunks() + amount);
                                            repository.saveClaimChunkData(claim);
                                        });

                                // Update cache on main thread
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    plugin.getLogger()
                                                            .info(
                                                                    String.format(
                                                                            "Player %s allocated %d chunks to claim '%s' (ID: %d) - Profile now has %d/%d chunks",
                                                                            playerName,
                                                                            amount,
                                                                            claim.getName(),
                                                                            claim.getId(),
                                                                            claim
                                                                                    .getPurchasedChunks(),
                                                                            maxPerProfile));
                                                    future.complete(AllocationResult.SUCCESS);
                                                });

                            } catch (Exception e) {
                                plugin.getLogger()
                                        .severe(
                                                "Chunk allocation failed for "
                                                        + playerName
                                                        + ": "
                                                        + e.getMessage());
                                e.printStackTrace();
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    future.complete(
                                                            AllocationResult.DATABASE_ERROR);
                                                });
                            }
                        });

        return future;
    }

    public Claim getClaimAt(Chunk chunk) {
        return getClaimAt(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public Claim getClaimAt(String world, int chunkX, int chunkZ) {
        String cacheKey = world + ":" + chunkX + ":" + chunkZ;

        // Check if we have a claim ID for this chunk
        Integer claimId = chunkToClaimId.get(cacheKey);
        if (claimId != null) {
            Claim cachedClaim = claimCache.get(claimId);
            if (cachedClaim != null) {
                return cachedClaim;
            }
        }

        // IMPORTANT: If cache is loaded and chunk not in cache, it's wilderness
        // Do NOT make synchronous DB queries on the main thread
        if (cacheLoaded) {
            return null; // Wilderness - not claimed
        }

        // Cache not yet loaded - check if we're on main thread
        if (plugin.getServer().isPrimaryThread()) {
            // NEVER block main thread with DB queries
            plugin.getLogger()
                    .warning("Claim cache not loaded yet, treating as wilderness: " + cacheKey);
            return null;
        }

        // We're on an async thread, safe to query DB
        Claim claim = repository.getClaimAt(world, chunkX, chunkZ);
        if (claim != null) {
            // Check if we already have this claim cached (from another chunk)
            Claim existingClaim = claimCache.get(claim.getId());
            if (existingClaim != null) {
                // Use the existing cached claim object
                chunkToClaimId.put(cacheKey, claim.getId());
                return existingClaim;
            }
            // Add to cache
            claimCache.put(claim.getId(), claim);
            chunkToClaimId.put(cacheKey, claim.getId());
        }
        return claim;
    }

    public List<Claim> getPlayerClaims(UUID uuid) {
        // Return cached claims if available, otherwise load from DB
        List<Claim> cachedClaims =
                claimCache.values().stream().filter(c -> c.getOwnerUuid().equals(uuid)).toList();

        if (!cachedClaims.isEmpty()) {
            return cachedClaims;
        }

        // Load from DB and cache them
        List<Claim> claims = repository.getClaimsByOwner(uuid);
        for (Claim claim : claims) {
            if (!claimCache.containsKey(claim.getId())) {
                claimCache.put(claim.getId(), claim);
                // Also populate chunk mappings
                for (ClaimedChunk chunk : claim.getChunks()) {
                    String key =
                            chunk.getWorld() + ":" + chunk.getChunkX() + ":" + chunk.getChunkZ();
                    chunkToClaimId.put(key, claim.getId());
                }
            }
        }
        return claims;
    }

    /**
     * Gets all claims where the player has management access. This includes claims the player owns
     * AND claims where they are a member with management permissions (MANAGE_PROFILE_INFO,
     * MANAGE_MEMBERS, etc.). Used for the "Claims You Manage" section in MyProfilesGui.
     *
     * @param uuid The player's UUID
     * @return List of claims the player can manage
     */
    public List<Claim> getAccessibleClaims(UUID uuid) {
        return repository.getAccessibleClaims(uuid, groupRepository);
    }

    /**
     * Gets claims where the player has management access but is NOT the owner. Used to show "Claims
     * You Manage" separately from owned claims.
     *
     * @param uuid The player's UUID
     * @return List of claims the player manages but doesn't own
     */
    public List<Claim> getManagedClaims(UUID uuid) {
        return getAccessibleClaims(uuid).stream().filter(c -> !c.isOwner(uuid)).toList();
    }

    /**
     * Get ALL cached claims by owner in a specific world. Supports multiple claims per player per
     * world.
     */
    private List<Claim> getCachedClaimsByOwnerInWorld(UUID ownerUuid, String world) {
        // Get all claims from cache that match owner and world
        List<Claim> claims =
                claimCache.values().stream()
                        .filter(
                                c ->
                                        c.getOwnerUuid().equals(ownerUuid)
                                                && c.getWorld().equals(world))
                        .toList();

        if (!claims.isEmpty() || cacheLoaded) {
            return claims;
        }

        // Only query DB if NOT on main thread
        if (plugin.getServer().isPrimaryThread()) {
            return List.of();
        }

        // Fall back to database (we're async) - get all claims by owner in world
        List<Claim> dbClaims = repository.getClaimsByOwnerInWorld(ownerUuid, world);
        for (Claim claim : dbClaims) {
            if (!claimCache.containsKey(claim.getId())) {
                claimCache.put(claim.getId(), claim);
                for (ClaimedChunk chunk : claim.getChunks()) {
                    String key =
                            chunk.getWorld() + ":" + chunk.getChunkX() + ":" + chunk.getChunkZ();
                    chunkToClaimId.put(key, claim.getId());
                }
            }
        }
        return dbClaims;
    }

    /**
     * Get cached claim by owner in a specific world (returns first found)
     *
     * @deprecated Use {@link #getCachedClaimsByOwnerInWorld(UUID, String)} to support multiple
     *     claims
     */
    @Deprecated
    private Claim getCachedClaimByOwnerInWorld(UUID ownerUuid, String world) {
        List<Claim> claims = getCachedClaimsByOwnerInWorld(ownerUuid, world);
        return claims.isEmpty() ? null : claims.get(0);
    }

    /**
     * Get player data from cache, with optional synchronous DB fallback. If data is not in cache: -
     * On main thread: returns null and triggers async load (callers must handle null) - On async
     * thread: performs synchronous DB lookup
     */
    public PlayerClaimData getPlayerData(UUID uuid) {
        PlayerClaimData cached = playerDataCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        // Data not in cache - check if we can safely do a sync lookup
        if (plugin.getServer().isPrimaryThread()) {
            // NEVER block main thread with DB queries - trigger async load
            loadPlayerDataAsync(uuid, null);
            return null; // Callers must handle null
        }

        // Safe on async thread - do synchronous lookup
        PlayerClaimData data = repository.getPlayerData(uuid);
        if (data != null) {
            // Cache it for future use
            playerDataCache.put(uuid, data);
        }
        return data;
    }

    /**
     * Get player's chunk pool from cache, creating if doesn't exist. This method is synchronous and
     * should be called from async context if not cached.
     *
     * @param playerUuid The player's UUID
     * @return PlayerChunkPool instance or null if on main thread and not cached
     */
    public PlayerChunkPool getPlayerChunkPool(UUID playerUuid) {
        PlayerChunkPool cached = playerChunkPoolCache.get(playerUuid);
        if (cached != null) {
            return cached;
        }

        // Not in cache - check if we can safely do a sync lookup
        if (plugin.getServer().isPrimaryThread()) {
            // NEVER block main thread with DB queries
            plugin.getLogger()
                    .warning("Attempted to load chunk pool for " + playerUuid + " on main thread");
            return null; // Callers must handle null
        }

        // Safe on async thread - do synchronous lookup
        try {
            PlayerChunkPool pool = repository.getOrCreatePlayerChunkPool(playerUuid);
            if (pool != null) {
                // Cache it for future use
                playerChunkPoolCache.put(playerUuid, pool);
            }
            return pool;
        } catch (SQLException e) {
            plugin.getLogger()
                    .severe("Failed to load chunk pool for " + playerUuid + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Get player data with a callback for async loading. Preferred method when you need guaranteed
     * data access.
     */
    public void getPlayerDataAsync(
            UUID uuid, java.util.function.Consumer<PlayerClaimData> callback) {
        PlayerClaimData cached = playerDataCache.get(uuid);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            PlayerClaimData data = repository.getPlayerData(uuid);
                            if (data != null) {
                                playerDataCache.put(uuid, data);
                            }
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(plugin, () -> callback.accept(data));
                        });
    }

    /** Get remaining chunks for a player. Uses cache when available, falls back to DB lookup. */
    public int getRemainingChunks(UUID uuid) {
        PlayerClaimData data = getPlayerData(uuid);
        if (data == null) return plugin.getClaimConfig().getStartingChunks();

        Integer usedCount = usedChunkCountCache.get(uuid);
        if (usedCount == null) {
            // Count from cached claims first
            usedCount =
                    (int)
                            claimCache.values().stream()
                                    .filter(c -> c.getOwnerUuid().equals(uuid))
                                    .mapToLong(c -> c.getChunks().size())
                                    .sum();

            // If cache might be incomplete, also check DB
            if (usedCount == 0 && !cacheLoaded) {
                usedCount = repository.getUsedChunkCount(uuid);
            }
            usedChunkCountCache.put(uuid, usedCount);
        }
        return data.getTotalChunks() - usedCount;
    }

    /** Get next chunk price for a player. Uses cache when available, falls back to DB lookup. */
    public double getNextChunkPrice(UUID uuid) {
        PlayerClaimData data = getPlayerData(uuid);
        int purchased = data != null ? data.getPurchasedChunks() : 0;
        return pricing.getPrice(purchased + 1);
    }

    /** Load player data into cache asynchronously. Call on player join. */
    public void loadPlayerDataAsync(UUID uuid, String playerName) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            PlayerClaimData data = repository.getPlayerData(uuid);
                            if (data == null) {
                                data =
                                        new PlayerClaimData(
                                                uuid,
                                                playerName,
                                                plugin.getClaimConfig().getStartingChunks());
                                repository.savePlayerData(data);
                            }
                            playerDataCache.put(uuid, data);

                            // Also cache used chunk count
                            int usedCount = repository.getUsedChunkCount(uuid);
                            usedChunkCountCache.put(uuid, usedCount);

                            // Load chunk pool into cache and migrate/correct from old system if
                            // needed
                            try {
                                PlayerChunkPool pool = repository.getOrCreatePlayerChunkPool(uuid);
                                if (pool != null) {
                                    // Query database directly for accurate sum (not cached objects)
                                    int totalPurchasedFromClaims =
                                            repository.getTotalAllocatedChunks(uuid);

                                    // Migration/correction needed if pool doesn't match allocated
                                    // chunks
                                    // This is a one-time migration from old per-profile system to
                                    // new global pool
                                    boolean needsUpdate = false;
                                    boolean isInitialMigration = false;

                                    if (pool.getPurchasedChunks() == 0
                                            && totalPurchasedFromClaims > 0) {
                                        // Initial migration: pool empty but player has claims
                                        needsUpdate = true;
                                        isInitialMigration = true;
                                        plugin.getLogger()
                                                .info(
                                                        "Migrating "
                                                                + uuid
                                                                + " to global chunk pool: "
                                                                + totalPurchasedFromClaims
                                                                + " chunks");
                                    } else if (pool.getPurchasedChunks()
                                            < totalPurchasedFromClaims) {
                                        // Correction: pool has wrong value (less than allocated)
                                        // Only log if it's a significant difference (not just off
                                        // by 1-2)
                                        int difference =
                                                totalPurchasedFromClaims
                                                        - pool.getPurchasedChunks();
                                        needsUpdate = true;
                                        if (difference > 5) {
                                            plugin.getLogger()
                                                    .info(
                                                            "Correcting chunk pool for "
                                                                    + uuid
                                                                    + ": "
                                                                    + pool.getPurchasedChunks()
                                                                    + " → "
                                                                    + totalPurchasedFromClaims);
                                        } else {
                                            plugin.getLogger()
                                                    .fine(
                                                            "Minor correction for "
                                                                    + uuid
                                                                    + ": "
                                                                    + pool.getPurchasedChunks()
                                                                    + " → "
                                                                    + totalPurchasedFromClaims);
                                        }
                                    }

                                    if (needsUpdate) {
                                        pool =
                                                new PlayerChunkPool(
                                                        uuid,
                                                        totalPurchasedFromClaims,
                                                        pool.getTotalSpent(), // Preserve total
                                                        // spent if it
                                                        // exists
                                                        pool.getLastPurchase(), // Preserve last
                                                        // purchase if
                                                        // it exists
                                                        pool.getCreatedAt(),
                                                        java.time.Instant.now());
                                        repository.savePlayerChunkPool(pool);
                                    }

                                    playerChunkPoolCache.put(uuid, pool);
                                }
                            } catch (SQLException e) {
                                plugin.getLogger()
                                        .severe(
                                                "Failed to load chunk pool for "
                                                        + uuid
                                                        + ": "
                                                        + e.getMessage());
                            }
                        });
    }

    /** Unload player data from cache. Call on player quit. */
    public void unloadPlayerData(UUID uuid) {
        playerDataCache.remove(uuid);
        usedChunkCountCache.remove(uuid);
        playerChunkPoolCache.remove(uuid);
    }

    /**
     * Clears all player-related state including caches and pending inputs. Call on player quit to
     * prevent memory leaks. Note: Does NOT clear claimCountCache as that's needed for offline
     * players.
     */
    public void clearPlayerState(UUID uuid) {
        playerDataCache.remove(uuid);
        usedChunkCountCache.remove(uuid);
        playerChunkPoolCache.remove(uuid);
        pendingTrustInputs.remove(uuid);
        pendingAdminSearch.remove(uuid);
        pendingClaimRename.remove(uuid);
        pendingGroupRename.remove(uuid);
        pendingChunkTransfers.remove(uuid);
        pendingInputTimestamps.remove(uuid);
    }

    /** Get claim cache statistics for monitoring. */
    public String getCacheStats() {
        return claimCache.getStats();
    }

    /** Get global purchased chunks counter value. */
    public int getGlobalPurchasedChunksTotal() {
        return globalPurchasedChunks.get();
    }

    /**
     * Invalidate claim count cache for a player. Called when claim ownership changes (transfer,
     * etc.).
     */
    public void invalidateClaimCountCache(UUID uuid) {
        claimCountCache.remove(uuid);
    }

    /** Update cached player data after a purchase or change. */
    public void updateCachedPlayerData(UUID uuid, PlayerClaimData data) {
        playerDataCache.put(uuid, data);
    }

    /**
     * Update chunk cache - used for claim merging and transfers.
     *
     * @param world World name
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param claimId New claim ID
     */
    public void updateChunkCache(String world, int chunkX, int chunkZ, int claimId) {
        String key = world + ":" + chunkX + ":" + chunkZ;
        chunkToClaimId.put(key, claimId);
    }

    /** Increment the cached used chunk count. */
    public void incrementUsedChunkCount(UUID uuid) {
        usedChunkCountCache.compute(uuid, (k, v) -> v == null ? 1 : v + 1);
    }

    /** Decrement the cached used chunk count. */
    public void decrementUsedChunkCount(UUID uuid) {
        usedChunkCountCache.compute(uuid, (k, v) -> v == null ? 0 : Math.max(0, v - 1));
    }

    public void invalidateCache(Chunk chunk) {
        String cacheKey = getCacheKey(chunk);
        Integer claimId = chunkToClaimId.remove(cacheKey);
        // Don't remove from claimCache as other chunks may still reference it
    }

    /** Completely invalidate a claim from cache (all chunks) */
    public void invalidateClaim(int claimId) {
        Claim claim = claimCache.remove(claimId);
        if (claim != null) {
            for (ClaimedChunk chunk : claim.getChunks()) {
                String key = chunk.getWorld() + ":" + chunk.getChunkX() + ":" + chunk.getChunkZ();
                chunkToClaimId.remove(key);
            }
        }
    }

    /** Delete a claim completely (database + cache) */
    public void deleteClaim(Claim claim) {
        if (claim == null) return;

        UUID ownerUuid = claim.getOwnerUuid();
        int chunkCount = claim.getChunks().size();
        int purchasedChunks = claim.getPurchasedChunks();

        // Remove from cache first
        for (ClaimedChunk chunk : claim.getChunks()) {
            String key = chunk.getWorld() + ":" + chunk.getChunkX() + ":" + chunk.getChunkZ();
            chunkToClaimId.remove(key);
        }
        claimCache.remove(claim.getId());

        // Delete from database
        repository.deleteClaim(claim);

        // Update used chunk count for owner
        Integer currentCount = usedChunkCountCache.get(ownerUuid);
        if (currentCount != null) {
            usedChunkCountCache.put(ownerUuid, Math.max(0, currentCount - chunkCount));
        }

        // Decrement claim count cache for owner
        claimCountCache.compute(ownerUuid, (k, v) -> v == null || v <= 1 ? null : v - 1);

        // REMOVED: globalPurchasedChunks.addAndGet(-purchasedChunks);
        // Chunks now stay in player's global pool automatically!
        // The allocated chunks (purchasedChunks) return to the player's pool and can be reused.

        plugin.getLogger()
                .info(
                        "Deleted claim ID "
                                + claim.getId()
                                + " ("
                                + claim.getName()
                                + ") with "
                                + chunkCount
                                + " chunks. Player retains "
                                + purchasedChunks
                                + " allocated chunks in global pool. Owner: "
                                + ownerUuid);

        // Notify BlueMap to update markers
        notifyBlueMapUpdate();
    }

    /** Get a claim by ID (from cache or database) */
    public Claim getClaimById(int claimId) {
        Claim cached = claimCache.get(claimId);
        if (cached != null) {
            return cached;
        }
        Claim claim = repository.getClaimById(claimId);
        if (claim != null) {
            claimCache.put(claim.getId(), claim);
            for (ClaimedChunk chunk : claim.getChunks()) {
                String key = chunk.getWorld() + ":" + chunk.getChunkX() + ":" + chunk.getChunkZ();
                chunkToClaimId.put(key, claim.getId());
            }
        }
        return claim;
    }

    private String getCacheKey(Chunk chunk) {
        return chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    // Trust input handling
    private final Map<UUID, Claim> pendingTrustInputs = new ConcurrentHashMap<>();

    public void awaitTrustInput(Player player, Claim claim) {
        UUID uuid = player.getUniqueId();
        pendingTrustInputs.put(uuid, claim);
        pendingInputTimestamps.put(uuid, System.currentTimeMillis());
    }

    public Claim getPendingTrustInput(UUID uuid) {
        return pendingTrustInputs.remove(uuid);
    }

    public boolean hasPendingTrustInput(UUID uuid) {
        return pendingTrustInputs.containsKey(uuid);
    }

    // Admin search input handling
    private final Map<UUID, String> pendingAdminSearch = new ConcurrentHashMap<>();

    public void awaitAdminSearch(Player player) {
        UUID uuid = player.getUniqueId();
        pendingAdminSearch.put(uuid, "");
        pendingInputTimestamps.put(uuid, System.currentTimeMillis());
    }

    public boolean hasPendingAdminSearch(UUID uuid) {
        return pendingAdminSearch.containsKey(uuid);
    }

    public void clearAdminSearch(UUID uuid) {
        pendingAdminSearch.remove(uuid);
    }

    // Claim rename input handling
    private final Map<UUID, Claim> pendingClaimRename = new ConcurrentHashMap<>();

    public void awaitClaimRenameInput(Player player, Claim claim) {
        UUID uuid = player.getUniqueId();
        pendingClaimRename.put(uuid, claim);
        pendingInputTimestamps.put(uuid, System.currentTimeMillis());
    }

    public Claim getPendingClaimRename(UUID uuid) {
        return pendingClaimRename.remove(uuid);
    }

    public boolean hasPendingClaimRename(UUID uuid) {
        return pendingClaimRename.containsKey(uuid);
    }

    /**
     * Rename a claim and save to database. Validates and sanitizes the name to prevent injection
     * attacks.
     *
     * @return true if rename was successful, false if validation failed
     */
    public boolean renameClaim(Claim claim, String newName) {
        // Validate and sanitize the name
        InputValidator.ValidationResult validation = InputValidator.validateName(newName);

        if (!validation.isValid()) {
            plugin.getLogger()
                    .warning(
                            "Invalid claim name attempted: '"
                                    + newName
                                    + "' - "
                                    + validation.getError());
            return false;
        }

        claim.setName(validation.getValue());
        repository.updateClaimName(claim);

        // Notify BlueMap to update markers
        notifyBlueMapUpdate();

        return true;
    }

    // Group rename input handling
    private final Map<UUID, GroupRenameContext> pendingGroupRename = new ConcurrentHashMap<>();

    public void awaitGroupRenameInput(
            Player player, Claim claim, net.serverplugins.claim.models.CustomGroup group) {
        UUID uuid = player.getUniqueId();
        pendingGroupRename.put(uuid, new GroupRenameContext(claim, group));
        pendingInputTimestamps.put(uuid, System.currentTimeMillis());
    }

    public GroupRenameContext getPendingGroupRename(UUID uuid) {
        return pendingGroupRename.remove(uuid);
    }

    public boolean hasPendingGroupRename(UUID uuid) {
        return pendingGroupRename.containsKey(uuid);
    }

    public record GroupRenameContext(Claim claim, net.serverplugins.claim.models.CustomGroup group) {}

    public record ClaimResult(boolean success, String messageKey, Claim claim) {
        public static ClaimResult success(Claim claim) {
            return new ClaimResult(true, null, claim);
        }

        public static ClaimResult failure(String messageKey) {
            return new ClaimResult(false, messageKey, null);
        }
    }

    /** Result of a chunk purchase attempt */
    public enum PurchaseResult {
        SUCCESS,
        NO_PLAYER_DATA,
        MAX_CHUNKS_REACHED,
        INSUFFICIENT_FUNDS,
        ECONOMY_ERROR,
        DATABASE_ERROR,
        INVALID_AMOUNT;

        public String getMessage() {
            return switch (this) {
                case SUCCESS -> "Purchase successful!";
                case NO_PLAYER_DATA -> "Player data not found";
                case MAX_CHUNKS_REACHED -> "Maximum chunks reached";
                case INSUFFICIENT_FUNDS -> "Insufficient funds";
                case ECONOMY_ERROR -> "Economy error occurred";
                case DATABASE_ERROR -> "Database error occurred";
                case INVALID_AMOUNT -> "Invalid purchase amount";
            };
        }
    }

    /** Result of a chunk allocation attempt (from global pool to profile) */
    public enum AllocationResult {
        SUCCESS,
        NOT_OWNER,
        INSUFFICIENT_CHUNKS,
        PROFILE_CAPACITY_EXCEEDED,
        DATABASE_ERROR;

        public String getMessage() {
            return switch (this) {
                case SUCCESS -> "Chunks allocated successfully!";
                case NOT_OWNER -> "You don't own this profile";
                case INSUFFICIENT_CHUNKS -> "Not enough chunks in global pool";
                case PROFILE_CAPACITY_EXCEEDED -> "Profile capacity limit exceeded";
                case DATABASE_ERROR -> "Database error occurred";
            };
        }
    }

    /**
     * Asynchronous chunk purchase - performs database operations off the main thread. This method:
     * 1. Pre-checks using cached data (non-blocking) 2. Withdraws money on main thread (economy
     * plugins require this) 3. Performs all database operations asynchronously 4. On success:
     * updates cache and calls callback with SUCCESS on main thread 5. On failure: refunds the
     * player and calls callback with error result on main thread
     *
     * @param player The player purchasing a chunk
     * @param callback Consumer to receive the PurchaseResult on main thread
     */
    public void purchaseChunkAsync(Player player, Consumer<PurchaseResult> callback) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();

        // Step 1: Pre-check using cached data (fast, non-blocking)
        PlayerClaimData cachedData = playerDataCache.get(playerId);

        // If no cached data, we need to load it first - return error for now
        if (cachedData == null) {
            callback.accept(PurchaseResult.NO_PLAYER_DATA);
            return;
        }

        int nextChunk = cachedData.getPurchasedChunks() + 1;
        double price = pricing.getPrice(nextChunk);

        // Check if max chunks reached
        if (price < 0) {
            callback.accept(PurchaseResult.MAX_CHUNKS_REACHED);
            return;
        }

        // Check if economy is available
        if (plugin.getEconomy() == null) {
            plugin.getLogger().warning("Economy provider is null during chunk purchase");
            callback.accept(PurchaseResult.ECONOMY_ERROR);
            return;
        }

        // Check if player has enough money
        if (!plugin.getEconomy().has(player, price)) {
            callback.accept(PurchaseResult.INSUFFICIENT_FUNDS);
            return;
        }

        // Step 2: Withdraw money on main thread (economy plugins often require this)
        // This must run on main thread for thread safety
        Runnable withdrawAndProcess =
                () -> {
                    boolean withdrawn = plugin.getEconomy().withdraw(player, price);
                    if (!withdrawn) {
                        plugin.getLogger()
                                .warning(
                                        "Withdrawal failed for "
                                                + playerName
                                                + " - amount: $"
                                                + price);
                        callback.accept(PurchaseResult.ECONOMY_ERROR);
                        return;
                    }

                    final double priceToRefund = price;
                    final int expectedTotal = cachedData.getTotalChunks() + 1;

                    // Step 3: Perform all database operations asynchronously
                    plugin.getServer()
                            .getScheduler()
                            .runTaskAsynchronously(
                                    plugin,
                                    () -> {
                                        try {
                                            // Fetch current player data from database
                                            PlayerClaimData playerData =
                                                    repository.getPlayerData(playerId);
                                            if (playerData == null) {
                                                // Create new player data if it doesn't exist
                                                playerData =
                                                        new PlayerClaimData(
                                                                playerId,
                                                                playerName,
                                                                plugin.getClaimConfig()
                                                                        .getStartingChunks());
                                                repository.savePlayerData(playerData);
                                                // Reload to get generated ID
                                                playerData = repository.getPlayerData(playerId);
                                            }

                                            // Update player data
                                            playerData.addChunks(1);
                                            boolean saved = repository.savePlayerData(playerData);

                                            if (!saved) {
                                                plugin.getLogger()
                                                        .severe(
                                                                "Database save returned false for "
                                                                        + playerName
                                                                        + " - Refunding $"
                                                                        + priceToRefund);
                                                // Refund on main thread
                                                plugin.getServer()
                                                        .getScheduler()
                                                        .runTask(
                                                                plugin,
                                                                () -> {
                                                                    plugin.getEconomy()
                                                                            .deposit(
                                                                                    player,
                                                                                    priceToRefund);
                                                                    callback.accept(
                                                                            PurchaseResult
                                                                                    .DATABASE_ERROR);
                                                                });
                                                return;
                                            }

                                            // Verify the save actually worked by re-reading from
                                            // database
                                            PlayerClaimData verifyData =
                                                    repository.getPlayerData(playerId);
                                            if (verifyData == null
                                                    || verifyData.getTotalChunks()
                                                            < expectedTotal) {
                                                // Save failed! Refund the player
                                                plugin.getLogger()
                                                        .severe(
                                                                "DATABASE SAVE FAILED for "
                                                                        + playerName
                                                                        + " - Expected total: "
                                                                        + expectedTotal
                                                                        + ", Got: "
                                                                        + (verifyData != null
                                                                                ? verifyData
                                                                                        .getTotalChunks()
                                                                                : "null")
                                                                        + " - REFUNDING $"
                                                                        + priceToRefund);
                                                // Refund on main thread
                                                plugin.getServer()
                                                        .getScheduler()
                                                        .runTask(
                                                                plugin,
                                                                () -> {
                                                                    plugin.getEconomy()
                                                                            .deposit(
                                                                                    player,
                                                                                    priceToRefund);
                                                                    callback.accept(
                                                                            PurchaseResult
                                                                                    .DATABASE_ERROR);
                                                                });
                                                return;
                                            }

                                            // Step 4: On success, update cache and call callback on
                                            // main thread
                                            final PlayerClaimData finalVerifyData = verifyData;
                                            plugin.getServer()
                                                    .getScheduler()
                                                    .runTask(
                                                            plugin,
                                                            () -> {
                                                                updateCachedPlayerData(
                                                                        playerId, finalVerifyData);

                                                                plugin.getLogger()
                                                                        .info(
                                                                                "Player "
                                                                                        + playerName
                                                                                        + " purchased chunk #"
                                                                                        + nextChunk
                                                                                        + " for $"
                                                                                        + String
                                                                                                .format(
                                                                                                        "%.2f",
                                                                                                        priceToRefund)
                                                                                        + " (Total chunks: "
                                                                                        + finalVerifyData
                                                                                                .getTotalChunks()
                                                                                        + ", verified)");

                                                                callback.accept(
                                                                        PurchaseResult.SUCCESS);
                                                            });

                                        } catch (Exception e) {
                                            // Step 5: On DB failure, refund and call callback with
                                            // error on main thread
                                            plugin.getLogger()
                                                    .severe(
                                                            "Error during async chunk purchase for "
                                                                    + playerName
                                                                    + ": "
                                                                    + e.getMessage());
                                            e.printStackTrace();

                                            plugin.getServer()
                                                    .getScheduler()
                                                    .runTask(
                                                            plugin,
                                                            () -> {
                                                                try {
                                                                    plugin.getEconomy()
                                                                            .deposit(
                                                                                    player,
                                                                                    priceToRefund);
                                                                    plugin.getLogger()
                                                                            .warning(
                                                                                    "Refunded $"
                                                                                            + String
                                                                                                    .format(
                                                                                                            "%.2f",
                                                                                                            priceToRefund)
                                                                                            + " to "
                                                                                            + playerName
                                                                                            + " after purchase error");
                                                                } catch (Exception refundError) {
                                                                    plugin.getLogger()
                                                                            .severe(
                                                                                    "CRITICAL: Failed to refund "
                                                                                            + playerName
                                                                                            + " after error! Amount: $"
                                                                                            + priceToRefund);
                                                                }
                                                                callback.accept(
                                                                        PurchaseResult
                                                                                .DATABASE_ERROR);
                                                            });
                                        }
                                    });
                };

        // Ensure withdrawal happens on main thread
        if (plugin.getServer().isPrimaryThread()) {
            withdrawAndProcess.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, withdrawAndProcess);
        }
    }

    /**
     * Synchronous chunk purchase - runs on calling thread (should be main thread) This properly
     * checks withdrawal success and handles errors.
     *
     * @deprecated Use {@link #purchaseChunkAsync(Player, Consumer)} instead to avoid blocking the
     *     main thread
     */
    @Deprecated
    public PurchaseResult purchaseChunkSync(Player player) {
        double priceToRefund = 0;
        boolean moneyWithdrawn = false;

        try {
            PlayerClaimData playerData = repository.getPlayerData(player.getUniqueId());
            if (playerData == null) {
                // Create new player data if it doesn't exist
                playerData =
                        new PlayerClaimData(
                                player.getUniqueId(),
                                player.getName(),
                                plugin.getClaimConfig().getStartingChunks());
                repository.savePlayerData(playerData);
            }

            int nextChunk = playerData.getPurchasedChunks() + 1;
            int expectedTotal = playerData.getTotalChunks() + 1;
            double price = pricing.getPrice(nextChunk);
            priceToRefund = price;

            if (price < 0) {
                return PurchaseResult.MAX_CHUNKS_REACHED;
            }

            if (plugin.getEconomy() == null) {
                plugin.getLogger().warning("Economy provider is null during chunk purchase");
                return PurchaseResult.ECONOMY_ERROR;
            }

            if (!plugin.getEconomy().has(player, price)) {
                return PurchaseResult.INSUFFICIENT_FUNDS;
            }

            // Withdraw and check success
            boolean withdrawn = plugin.getEconomy().withdraw(player, price);
            if (!withdrawn) {
                plugin.getLogger()
                        .warning(
                                "Withdrawal failed for "
                                        + player.getName()
                                        + " - amount: $"
                                        + price);
                return PurchaseResult.ECONOMY_ERROR;
            }
            moneyWithdrawn = true;

            // Update and save player data
            playerData.addChunks(1);
            boolean saved = repository.savePlayerData(playerData);
            if (!saved) {
                plugin.getLogger()
                        .severe(
                                "Database save returned false for "
                                        + player.getName()
                                        + " - Immediate refund of $"
                                        + price);
                plugin.getEconomy().deposit(player, price);
                return PurchaseResult.DATABASE_ERROR;
            }

            // CRITICAL: Verify the save actually worked by re-reading from database
            PlayerClaimData verifyData = repository.getPlayerData(player.getUniqueId());
            if (verifyData == null || verifyData.getTotalChunks() < expectedTotal) {
                // Save failed! Refund the player
                plugin.getLogger()
                        .severe(
                                "DATABASE SAVE FAILED for "
                                        + player.getName()
                                        + " - Expected total: "
                                        + expectedTotal
                                        + ", Got: "
                                        + (verifyData != null
                                                ? verifyData.getTotalChunks()
                                                : "null")
                                        + " - REFUNDING $"
                                        + price);
                plugin.getEconomy().deposit(player, price);
                return PurchaseResult.DATABASE_ERROR;
            }

            // Update cache with verified data
            updateCachedPlayerData(player.getUniqueId(), verifyData);

            plugin.getLogger()
                    .info(
                            "Player "
                                    + player.getName()
                                    + " purchased chunk #"
                                    + nextChunk
                                    + " for $"
                                    + String.format("%.2f", price)
                                    + " (Total chunks: "
                                    + verifyData.getTotalChunks()
                                    + ", verified)");

            return PurchaseResult.SUCCESS;
        } catch (Exception e) {
            plugin.getLogger()
                    .severe(
                            "Error during chunk purchase for "
                                    + player.getName()
                                    + ": "
                                    + e.getMessage());
            e.printStackTrace();

            // Refund if money was already withdrawn
            if (moneyWithdrawn && priceToRefund > 0 && plugin.getEconomy() != null) {
                try {
                    plugin.getEconomy().deposit(player, priceToRefund);
                    plugin.getLogger()
                            .warning(
                                    "Refunded $"
                                            + String.format("%.2f", priceToRefund)
                                            + " to "
                                            + player.getName()
                                            + " after purchase error");
                } catch (Exception refundError) {
                    plugin.getLogger()
                            .severe(
                                    "CRITICAL: Failed to refund "
                                            + player.getName()
                                            + " after error! Amount: $"
                                            + priceToRefund);
                }
            }
            return PurchaseResult.DATABASE_ERROR;
        }
    }

    // ==================== CONNECTED CHUNKS (FLOOD-FILL) ====================

    /**
     * Gets all chunks connected to the given chunk within the same claim. Uses flood-fill algorithm
     * to find adjacent chunks.
     *
     * @param claim The claim containing the chunks
     * @param start The starting chunk
     * @return List of connected chunks including the start chunk
     */
    public List<ClaimedChunk> getConnectedChunks(Claim claim, ClaimedChunk start) {
        Set<String> visited = new HashSet<>();
        Queue<ClaimedChunk> queue = new LinkedList<>();
        List<ClaimedChunk> result = new ArrayList<>();

        queue.add(start);

        while (!queue.isEmpty()) {
            ClaimedChunk current = queue.poll();
            String key = current.getWorld() + ":" + current.getChunkX() + ":" + current.getChunkZ();

            if (visited.contains(key)) continue;
            visited.add(key);
            result.add(current);

            // Check 4 adjacent chunks in the same claim
            for (ClaimedChunk adj : claim.getChunks()) {
                if (isAdjacent(current, adj)
                        && !visited.contains(
                                adj.getWorld() + ":" + adj.getChunkX() + ":" + adj.getChunkZ())) {
                    queue.add(adj);
                }
            }
        }

        return result;
    }

    /** Checks if two chunks are adjacent (share an edge). */
    private boolean isAdjacent(ClaimedChunk a, ClaimedChunk b) {
        if (!a.getWorld().equals(b.getWorld())) return false;

        int dx = Math.abs(a.getChunkX() - b.getChunkX());
        int dz = Math.abs(a.getChunkZ() - b.getChunkZ());

        // Adjacent means exactly 1 step in either X or Z (not both)
        return (dx == 1 && dz == 0) || (dx == 0 && dz == 1);
    }

    // ==================== CHUNK CACHE MANAGEMENT ====================

    /** Invalidate a specific chunk from the cache. */
    public void invalidateChunkCache(String world, int chunkX, int chunkZ) {
        String key = world + ":" + chunkX + ":" + chunkZ;
        chunkToClaimId.remove(key);
    }

    /** Update the chunk -> claim ID mapping in cache. */
    public void updateChunkClaimMapping(ClaimedChunk chunk, int claimId) {
        String key = chunk.getWorld() + ":" + chunk.getChunkX() + ":" + chunk.getChunkZ();
        chunkToClaimId.put(key, claimId);
    }

    /** Decrement used chunk count by a specific amount. */
    public void decrementUsedChunkCount(UUID uuid, int amount) {
        usedChunkCountCache.compute(uuid, (k, v) -> v == null ? 0 : Math.max(0, v - amount));
    }

    // ==================== CHUNK TRANSFER INPUT HANDLING ====================

    // Pending chunk transfer data: player UUID -> TransferRequest
    private final Map<UUID, ChunkTransferRequest> pendingChunkTransfers = new ConcurrentHashMap<>();

    /** Request object for pending chunk transfers. */
    public static class ChunkTransferRequest {
        private final Claim claim;
        private final ClaimedChunk chunk;
        private final boolean bulkTransfer; // Transfer connected chunks

        public ChunkTransferRequest(Claim claim, ClaimedChunk chunk, boolean bulkTransfer) {
            this.claim = claim;
            this.chunk = chunk;
            this.bulkTransfer = bulkTransfer;
        }

        public Claim getClaim() {
            return claim;
        }

        public ClaimedChunk getChunk() {
            return chunk;
        }

        public boolean isBulkTransfer() {
            return bulkTransfer;
        }
    }

    /** Start waiting for player name input for chunk transfer. */
    public void awaitChunkTransferInput(
            Player player, Claim claim, ClaimedChunk chunk, boolean bulkTransfer) {
        UUID uuid = player.getUniqueId();
        pendingChunkTransfers.put(uuid, new ChunkTransferRequest(claim, chunk, bulkTransfer));
        pendingInputTimestamps.put(uuid, System.currentTimeMillis());
    }

    /** Check if player has pending chunk transfer. */
    public boolean hasPendingChunkTransfer(UUID uuid) {
        return pendingChunkTransfers.containsKey(uuid);
    }

    /** Get and remove pending chunk transfer request. */
    public ChunkTransferRequest getPendingChunkTransfer(UUID uuid) {
        return pendingChunkTransfers.remove(uuid);
    }

    /** Cancel pending chunk transfer. */
    public void cancelPendingChunkTransfer(UUID uuid) {
        pendingChunkTransfers.remove(uuid);
    }

    /**
     * Notify BlueMap to update markers (if installed). Uses reflection to avoid compile-time
     * dependency.
     */
    private void notifyBlueMapUpdate() {
        try {
            Class<?> bluemapClass = Class.forName("net.serverplugins.bluemap.ServerBlueMap");
            java.lang.reflect.Method getInstanceMethod = bluemapClass.getMethod("getInstance");
            Object bluemapInstance = getInstanceMethod.invoke(null);
            if (bluemapInstance != null) {
                java.lang.reflect.Method markDirtyMethod = bluemapClass.getMethod("markDirty");
                markDirtyMethod.invoke(bluemapInstance);
            }
        } catch (ClassNotFoundException ignored) {
            // ServerBlueMap not installed
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to notify BlueMap: " + e.getMessage());
        }
    }
}
