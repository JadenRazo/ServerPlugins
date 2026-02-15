package net.serverplugins.claim.repository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.AbstractMap;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.serverplugins.api.database.Database;
import net.serverplugins.claim.models.ChunkTransfer;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimGroup;
import net.serverplugins.claim.models.ClaimProfile;
import net.serverplugins.claim.models.ClaimSettings;
import net.serverplugins.claim.models.ClaimWarp;
import net.serverplugins.claim.models.ClaimedChunk;
import net.serverplugins.claim.models.CustomGroup;
import net.serverplugins.claim.models.DustEffect;
import net.serverplugins.claim.models.GroupPermissions;
import net.serverplugins.claim.models.PlayerChunkPool;
import net.serverplugins.claim.models.PlayerClaimData;
import net.serverplugins.claim.models.ProfileColor;
import net.serverplugins.claim.models.WarpVisibility;

public class ClaimRepository {

    private final Database database;
    private static final Logger LOGGER = Logger.getLogger("ServerClaimRepository");

    /**
     * Thread-local storage for transaction connections. When a transaction is active, all database
     * operations use this connection.
     */
    private static final ThreadLocal<Connection> transactionConnection = new ThreadLocal<>();

    /** Thread-local flag to track if a transaction is active. */
    private static final ThreadLocal<Boolean> inTransaction = new ThreadLocal<>();

    public ClaimRepository(Database database) {
        this.database = database;
    }

    // ==================== TRANSACTION SUPPORT ====================

    /**
     * Begin a new database transaction. All subsequent database operations on this thread will use
     * the same connection until commit() or rollback() is called.
     *
     * @throws SQLException if transaction cannot be started or another transaction is already
     *     active
     */
    public void beginTransaction() throws SQLException {
        if (Boolean.TRUE.equals(inTransaction.get())) {
            throw new SQLException("Transaction already active on this thread");
        }

        try {
            Connection conn = database.getConnection();
            conn.setAutoCommit(false);
            transactionConnection.set(conn);
            inTransaction.set(true);
            LOGGER.fine("Transaction started on thread: " + Thread.currentThread().getName());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to begin transaction", e);
            cleanupTransaction();
            throw new SQLException("Failed to begin transaction: " + e.getMessage(), e);
        }
    }

    /**
     * Commit the current transaction. Commits all changes made since beginTransaction() was called
     * and releases the connection.
     *
     * @throws SQLException if commit fails or no transaction is active
     */
    public void commit() throws SQLException {
        if (!Boolean.TRUE.equals(inTransaction.get())) {
            throw new SQLException("No active transaction to commit");
        }

        Connection conn = transactionConnection.get();
        try {
            conn.commit();
            LOGGER.fine("Transaction committed on thread: " + Thread.currentThread().getName());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to commit transaction", e);
            throw new SQLException("Failed to commit transaction: " + e.getMessage(), e);
        } finally {
            cleanupTransaction();
        }
    }

    /**
     * Rollback the current transaction. Discards all changes made since beginTransaction() was
     * called and releases the connection.
     *
     * @throws SQLException if rollback fails or no transaction is active
     */
    public void rollback() throws SQLException {
        if (!Boolean.TRUE.equals(inTransaction.get())) {
            throw new SQLException("No active transaction to rollback");
        }

        Connection conn = transactionConnection.get();
        try {
            conn.rollback();
            LOGGER.warning(
                    "Transaction rolled back on thread: " + Thread.currentThread().getName());
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Failed to rollback transaction", e);
            throw new SQLException("Failed to rollback transaction: " + e.getMessage(), e);
        } finally {
            cleanupTransaction();
        }
    }

    /**
     * Execute an operation within a transaction with automatic rollback on error. This is the
     * recommended way to use transactions as it handles cleanup automatically.
     *
     * <p>Example usage:
     *
     * <pre>
     * repository.executeInTransaction(() -> {
     *     repository.saveClaim(claim);
     *     repository.addChunk(claimId, chunk);
     *     bankManager.withdraw(playerId, cost);
     * });
     * </pre>
     *
     * @param operation The operation to execute within the transaction
     * @throws SQLException if the transaction fails or the operation throws an exception
     */
    public void executeInTransaction(TransactionOperation operation) throws SQLException {
        beginTransaction();
        try {
            operation.execute();
            commit();
        } catch (Exception e) {
            try {
                rollback();
            } catch (SQLException rollbackEx) {
                LOGGER.log(Level.SEVERE, "Failed to rollback after error", rollbackEx);
                // Add rollback exception as suppressed
                e.addSuppressed(rollbackEx);
            }

            // Log the original error with full context
            LOGGER.log(
                    Level.SEVERE, "Transaction failed and was rolled back: " + e.getMessage(), e);

            if (e instanceof SQLException) {
                throw (SQLException) e;
            } else {
                throw new SQLException("Transaction failed: " + e.getMessage(), e);
            }
        }
    }

    /** Functional interface for transaction operations. */
    @FunctionalInterface
    public interface TransactionOperation {
        void execute() throws Exception;
    }

    /**
     * Check if a transaction is currently active on this thread.
     *
     * @return true if a transaction is active, false otherwise
     */
    public boolean isTransactionActive() {
        return Boolean.TRUE.equals(inTransaction.get());
    }

    /** Clean up transaction state and restore auto-commit. Called after commit or rollback. */
    private void cleanupTransaction() {
        Connection conn = transactionConnection.get();
        if (conn != null) {
            try {
                conn.setAutoCommit(true);
                conn.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error cleaning up transaction connection", e);
            }
        }
        transactionConnection.remove();
        inTransaction.remove();
    }

    /**
     * Get the current connection, either from an active transaction or a new connection. Internal
     * use only - repository methods should use this instead of database.getConnection().
     *
     * @return Connection to use for database operations
     * @throws SQLException if connection cannot be obtained
     */
    private Connection getConnection() throws SQLException {
        Connection conn = transactionConnection.get();
        if (conn != null && Boolean.TRUE.equals(inTransaction.get())) {
            return conn;
        }
        return database.getConnection();
    }

    /**
     * Execute an update query, using transaction connection if active. This method throws
     * SQLException and should be used in transactional contexts.
     *
     * @param sql SQL query to execute
     * @param params Query parameters
     * @return Number of rows affected
     * @throws SQLException if execution fails
     */
    private int executeUpdate(String sql, Object... params) throws SQLException {
        // If we're in a transaction, use the transaction connection
        if (Boolean.TRUE.equals(inTransaction.get())) {
            Connection conn = transactionConnection.get();
            try (var stmt = conn.prepareStatement(sql)) {
                database.setParameters(stmt, params);
                return stmt.executeUpdate();
            }
        }
        // Otherwise use normal database execution
        return database.executeUpdate(sql, params);
    }

    /**
     * Execute an update query without checked exceptions (for backward compatibility). Uses
     * RuntimeException wrapper for non-transactional code.
     *
     * @param sql SQL query to execute
     * @param params Query parameters
     */
    private void executeUpdateSafe(String sql, Object... params) {
        try {
            executeUpdate(sql, params);
        } catch (SQLException e) {
            throw new RuntimeException("Database update failed: " + sql, e);
        }
    }

    /**
     * Execute a query using transaction connection if active, otherwise use database.query().
     *
     * @param sql SQL query
     * @param mapper ResultSet mapper function
     * @param params Query parameters
     * @return Mapped result
     */
    private <T> T executeQuery(String sql, Database.ResultSetMapper<T> mapper, Object... params) {
        // If we're in a transaction, use the transaction connection
        if (Boolean.TRUE.equals(inTransaction.get())) {
            Connection conn = transactionConnection.get();
            try (var stmt = conn.prepareStatement(sql)) {
                database.setParameters(stmt, params);
                try (var rs = stmt.executeQuery()) {
                    return mapper.map(rs);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Database query failed in transaction: " + sql, e);
            }
        }
        // Otherwise use normal database query
        return database.query(sql, mapper, params);
    }

    public CompletableFuture<PlayerClaimData> getOrCreatePlayerData(
            UUID uuid, String username, int startingChunks) {
        return CompletableFuture.supplyAsync(
                () -> {
                    PlayerClaimData data = getPlayerData(uuid);
                    if (data == null) {
                        data = new PlayerClaimData(uuid, username, startingChunks);
                        savePlayerData(data);
                    }
                    return data;
                });
    }

    public PlayerClaimData getPlayerData(UUID uuid) {
        return executeQuery(
                "SELECT * FROM server_player_claims WHERE uuid = ?",
                rs -> {
                    if (rs.next()) {
                        return new PlayerClaimData(
                                UUID.fromString(rs.getString("uuid")),
                                rs.getString("username"),
                                rs.getInt("total_chunks"),
                                rs.getInt("purchased_chunks"),
                                rs.getTimestamp("created_at").toInstant(),
                                rs.getTimestamp("updated_at").toInstant());
                    }
                    return null;
                },
                uuid.toString());
    }

    public boolean savePlayerData(PlayerClaimData data) {
        // Single attempt - connection pooling and HikariCP handle transient errors
        try {
            int affected =
                    executeUpdate(
                            "INSERT INTO server_player_claims (uuid, username, total_chunks, purchased_chunks, updated_at) "
                                    + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP) "
                                    + "ON DUPLICATE KEY UPDATE username = VALUES(username), total_chunks = VALUES(total_chunks), "
                                    + "purchased_chunks = VALUES(purchased_chunks), updated_at = CURRENT_TIMESTAMP",
                            data.getUuid().toString(),
                            data.getUsername(),
                            data.getTotalChunks(),
                            data.getPurchasedChunks());
            return affected > 0;
        } catch (SQLException e) {
            LOGGER.log(
                    Level.SEVERE,
                    "Failed to save player data for UUID "
                            + data.getUuid()
                            + " (username: "
                            + data.getUsername()
                            + ")",
                    e);
            return false;
        }
    }

    public Claim getClaimAt(String world, int chunkX, int chunkZ) {
        return executeQuery(
                "SELECT c.* FROM server_claims c "
                        + "INNER JOIN server_chunks ch ON c.id = ch.claim_id "
                        + "WHERE ch.world = ? AND ch.chunk_x = ? AND ch.chunk_z = ?",
                rs -> {
                    if (rs.next()) {
                        Claim claim = mapClaim(rs);
                        loadClaimChunks(claim);
                        loadClaimProfiles(claim);
                        loadTrustedPlayers(claim);
                        loadClaimMembers(claim);
                        loadBannedPlayers(claim);
                        return claim;
                    }
                    return null;
                },
                world,
                chunkX,
                chunkZ);
    }

    public List<Claim> getClaimsByOwner(UUID ownerUuid) {
        return getClaimsByOwner(ownerUuid, -1, -1);
    }

    /**
     * Get claims by owner with pagination support.
     *
     * @param ownerUuid Owner's UUID
     * @param limit Maximum number of claims to return (-1 for no limit)
     * @param offset Number of claims to skip (-1 for no offset)
     * @return List of claims
     */
    public List<Claim> getClaimsByOwner(UUID ownerUuid, int limit, int offset) {
        StringBuilder query =
                new StringBuilder(
                        "SELECT * FROM server_claims WHERE owner_uuid = ? ORDER BY created_at DESC");

        if (limit > 0) {
            query.append(" LIMIT ").append(limit);
            if (offset > 0) {
                query.append(" OFFSET ").append(offset);
            }
        }

        return executeQuery(
                query.toString(),
                rs -> {
                    List<Claim> claims = new ArrayList<>();
                    while (rs.next()) {
                        Claim claim = mapClaim(rs);
                        loadClaimChunks(claim);
                        loadClaimProfiles(claim);
                        loadTrustedPlayers(claim);
                        loadClaimMembers(claim);
                        loadBannedPlayers(claim);
                        claims.add(claim);
                    }
                    LOGGER.info(
                            "Loaded "
                                    + claims.size()
                                    + " claims for "
                                    + ownerUuid
                                    + (limit > 0
                                            ? " (limit: " + limit + ", offset: " + offset + ")"
                                            : ""));
                    return claims;
                },
                ownerUuid.toString());
    }

    /**
     * Get total count of claims for an owner (useful for pagination)
     *
     * @param ownerUuid Owner's UUID
     * @return Total number of claims
     */
    public int getClaimCountForOwner(UUID ownerUuid) {
        return executeQuery(
                "SELECT COUNT(*) as count FROM server_claims WHERE owner_uuid = ?",
                rs -> {
                    if (rs.next()) {
                        return rs.getInt("count");
                    }
                    return 0;
                },
                ownerUuid.toString());
    }

    /**
     * Load ALL claims from the database (used for cache preloading) WARNING: This can be slow for
     * servers with many claims. Consider using getAllClaimsPaginated() instead.
     */
    public List<Claim> getAllClaims() {
        LOGGER.fine(
                "Loading all claims from database (this may take a while for large servers)...");
        long startTime = System.currentTimeMillis();

        List<Claim> claims =
                executeQuery(
                        "SELECT * FROM server_claims ORDER BY id",
                        rs -> {
                            List<Claim> result = new ArrayList<>();
                            while (rs.next()) {
                                Claim claim = mapClaim(rs);
                                loadClaimChunks(claim);
                                loadClaimProfiles(claim);
                                loadTrustedPlayers(claim);
                                loadClaimMembers(claim);
                                loadBannedPlayers(claim);
                                result.add(claim);
                            }
                            return result;
                        });

        long duration = System.currentTimeMillis() - startTime;
        LOGGER.fine("Loaded " + claims.size() + " claims in " + duration + "ms");
        return claims;
    }

    /**
     * Load claims in batches for better performance with large datasets.
     *
     * @param batchSize Number of claims to load per batch
     * @param batchNumber Batch number (0-indexed)
     * @return List of claims for the requested batch
     */
    public List<Claim> getAllClaimsPaginated(int batchSize, int batchNumber) {
        int offset = batchSize * batchNumber;

        return executeQuery(
                "SELECT * FROM server_claims ORDER BY id LIMIT ? OFFSET ?",
                rs -> {
                    List<Claim> claims = new ArrayList<>();
                    while (rs.next()) {
                        Claim claim = mapClaim(rs);
                        loadClaimChunks(claim);
                        loadClaimProfiles(claim);
                        loadTrustedPlayers(claim);
                        loadClaimMembers(claim);
                        loadBannedPlayers(claim);
                        claims.add(claim);
                    }
                    return claims;
                },
                batchSize,
                offset);
    }

    /**
     * Get total count of all claims in the database
     *
     * @return Total number of claims
     */
    public int getTotalClaimCount() {
        return executeQuery(
                "SELECT COUNT(*) as count FROM server_claims",
                rs -> {
                    if (rs.next()) {
                        return rs.getInt("count");
                    }
                    return 0;
                });
    }

    /**
     * Get claims with a limit (prevents loading all claims into memory). Useful for operations that
     * don't need to process every claim.
     *
     * @param limit Maximum number of claims to return
     * @return List of claims (up to limit)
     */
    public List<Claim> getClaimsWithLimit(int limit) {
        return executeQuery(
                "SELECT * FROM server_claims ORDER BY id LIMIT ?",
                rs -> {
                    List<Claim> claims = new ArrayList<>();
                    while (rs.next()) {
                        Claim claim = mapClaim(rs);
                        loadClaimChunks(claim);
                        loadClaimProfiles(claim);
                        loadTrustedPlayers(claim);
                        loadClaimMembers(claim);
                        loadBannedPlayers(claim);
                        claims.add(claim);
                    }
                    return claims;
                },
                limit);
    }

    /**
     * Get paginated claims for processing large datasets in batches. Uses LIMIT and OFFSET for
     * pagination.
     *
     * @param offset Number of claims to skip
     * @param limit Maximum number of claims to return
     * @return List of claims for this page
     */
    public List<Claim> getClaimsPaginated(int offset, int limit) {
        return executeQuery(
                "SELECT * FROM server_claims ORDER BY id LIMIT ? OFFSET ?",
                rs -> {
                    List<Claim> claims = new ArrayList<>();
                    while (rs.next()) {
                        Claim claim = mapClaim(rs);
                        loadClaimChunks(claim);
                        loadClaimProfiles(claim);
                        loadTrustedPlayers(claim);
                        loadClaimMembers(claim);
                        loadBannedPlayers(claim);
                        claims.add(claim);
                    }
                    return claims;
                },
                limit,
                offset);
    }

    /**
     * Get recent claims (most recently created).
     *
     * @param limit Maximum number of claims to return
     * @return List of recent claims
     */
    public List<Claim> getRecentClaims(int limit) {
        return executeQuery(
                "SELECT * FROM server_claims ORDER BY created_at DESC LIMIT ?",
                rs -> {
                    List<Claim> claims = new ArrayList<>();
                    while (rs.next()) {
                        Claim claim = mapClaim(rs);
                        loadClaimChunks(claim);
                        loadClaimProfiles(claim);
                        loadTrustedPlayers(claim);
                        loadClaimMembers(claim);
                        loadBannedPlayers(claim);
                        claims.add(claim);
                    }
                    return claims;
                },
                limit);
    }

    /**
     * Get top claim owners by number of claims (for leaderboards).
     *
     * @param limit Maximum number of owners to return
     * @return List of owner UUIDs with their claim counts
     */
    public List<Map.Entry<UUID, Integer>> getTopClaimOwners(int limit) {
        return executeQuery(
                "SELECT owner_uuid, COUNT(*) as claim_count FROM server_claims "
                        + "GROUP BY owner_uuid ORDER BY claim_count DESC LIMIT ?",
                rs -> {
                    List<Map.Entry<UUID, Integer>> results = new ArrayList<>();
                    while (rs.next()) {
                        UUID owner = UUID.fromString(rs.getString("owner_uuid"));
                        int count = rs.getInt("claim_count");
                        results.add(new AbstractMap.SimpleEntry<>(owner, count));
                    }
                    return results;
                },
                limit);
    }

    public Claim getClaimById(int claimId) {
        return executeQuery(
                "SELECT * FROM server_claims WHERE id = ?",
                rs -> {
                    if (rs.next()) {
                        Claim claim = mapClaim(rs);
                        loadClaimChunks(claim);
                        loadClaimProfiles(claim);
                        loadTrustedPlayers(claim);
                        loadClaimMembers(claim);
                        loadBannedPlayers(claim);
                        return claim;
                    }
                    return null;
                },
                claimId);
    }

    /**
     * Find player data by exact username (case-insensitive)
     *
     * @param username Exact username to search
     * @return PlayerClaimData if found, null otherwise
     */
    public PlayerClaimData getPlayerDataByExactUsername(String username) {
        return executeQuery(
                "SELECT * FROM server_player_claims WHERE LOWER(username) = LOWER(?)",
                rs -> {
                    if (rs.next()) {
                        return new PlayerClaimData(
                                UUID.fromString(rs.getString("uuid")),
                                rs.getString("username"),
                                rs.getInt("total_chunks"),
                                rs.getInt("purchased_chunks"),
                                rs.getTimestamp("created_at").toInstant(),
                                rs.getTimestamp("updated_at").toInstant());
                    }
                    return null;
                },
                username);
    }

    /**
     * Find player data by partial username match (case-insensitive)
     *
     * @param username Username or partial username to search
     * @return PlayerClaimData if found, null otherwise
     */
    public PlayerClaimData getPlayerDataByUsername(String username) {
        return executeQuery(
                "SELECT * FROM server_player_claims WHERE LOWER(username) LIKE LOWER(?) LIMIT 1",
                rs -> {
                    if (rs.next()) {
                        return new PlayerClaimData(
                                UUID.fromString(rs.getString("uuid")),
                                rs.getString("username"),
                                rs.getInt("total_chunks"),
                                rs.getInt("purchased_chunks"),
                                rs.getTimestamp("created_at").toInstant(),
                                rs.getTimestamp("updated_at").toInstant());
                    }
                    return null;
                },
                "%" + username + "%");
    }

    public Claim getClaimByOwnerInWorld(UUID ownerUuid, String world) {
        return executeQuery(
                "SELECT * FROM server_claims WHERE owner_uuid = ? AND world = ?",
                rs -> {
                    if (rs.next()) {
                        Claim claim = mapClaim(rs);
                        loadClaimChunks(claim);
                        loadClaimProfiles(claim);
                        loadTrustedPlayers(claim);
                        loadClaimMembers(claim);
                        loadBannedPlayers(claim);
                        return claim;
                    }
                    return null;
                },
                ownerUuid.toString(),
                world);
    }

    /**
     * Get ALL claims by owner in a specific world. Supports multiple claims per player per world.
     */
    public List<Claim> getClaimsByOwnerInWorld(UUID ownerUuid, String world) {
        return executeQuery(
                "SELECT * FROM server_claims WHERE owner_uuid = ? AND world = ?",
                rs -> {
                    List<Claim> claims = new ArrayList<>();
                    while (rs.next()) {
                        Claim claim = mapClaim(rs);
                        loadClaimChunks(claim);
                        loadClaimProfiles(claim);
                        loadTrustedPlayers(claim);
                        loadClaimMembers(claim);
                        loadBannedPlayers(claim);
                        claims.add(claim);
                    }
                    return claims;
                },
                ownerUuid.toString(),
                world);
    }

    public int getUsedChunkCount(UUID ownerUuid) {
        return executeQuery(
                "SELECT COUNT(*) as count FROM server_chunks ch "
                        + "INNER JOIN server_claims c ON ch.claim_id = c.id "
                        + "WHERE c.owner_uuid = ?",
                rs -> rs.next() ? rs.getInt("count") : 0,
                ownerUuid.toString());
    }

    public void saveClaim(Claim claim) {
        ClaimSettings s = claim.getSettings();
        if (claim.getId() == 0) {
            // Insert the claim with all new columns
            executeUpdateSafe(
                    "INSERT INTO server_claims (owner_uuid, name, world, welcome_message, teleport_protected, keep_inventory, "
                            + "total_chunks, purchased_chunks, claim_order, color, icon, particle_effect, "
                            + "pvp_enabled, fire_spread, explosions, hostile_spawns, mob_griefing, passive_spawns, crop_trampling, leaf_decay, group_permissions) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    claim.getOwnerUuid().toString(),
                    claim.getName(),
                    claim.getWorld(),
                    claim.getWelcomeMessage(),
                    claim.isTeleportProtected(),
                    claim.isKeepInventory(),
                    claim.getTotalChunks(),
                    claim.getPurchasedChunks(),
                    claim.getClaimOrder(),
                    claim.getColor().name(),
                    claim.getIcon() != null ? claim.getIcon().name() : null,
                    claim.getParticleEffect(),
                    s != null ? s.isPvpEnabled() : false,
                    s != null ? s.isFireSpread() : false,
                    s != null ? s.isExplosions() : false,
                    s != null ? s.isHostileSpawns() : true,
                    s != null ? s.isMobGriefing() : false,
                    s != null ? s.isPassiveSpawns() : true,
                    s != null ? s.isCropTrampling() : false,
                    s != null ? s.isLeafDecay() : true,
                    claim.getGroupPermissions() != null
                            ? claim.getGroupPermissions().serialize()
                            : null);

            // Get the generated ID
            executeQuery(
                    "SELECT MAX(id) as id FROM server_claims WHERE owner_uuid = ? AND world = ?",
                    rs -> {
                        if (rs.next()) claim.setId(rs.getInt("id"));
                        return null;
                    },
                    claim.getOwnerUuid().toString(),
                    claim.getWorld());
        } else {
            executeUpdateSafe(
                    "UPDATE server_claims SET name = ?, welcome_message = ?, teleport_protected = ?, keep_inventory = ? WHERE id = ?",
                    claim.getName(),
                    claim.getWelcomeMessage(),
                    claim.isTeleportProtected(),
                    claim.isKeepInventory(),
                    claim.getId());
        }
    }

    /** Save claim settings (appearance and protection toggles) */
    public void saveClaimSettings(Claim claim) {
        ClaimSettings s = claim.getSettings();
        executeUpdateSafe(
                "UPDATE server_claims SET "
                        + "color = ?, icon = ?, particle_effect = ?, group_permissions = ?, "
                        + "pvp_enabled = ?, fire_spread = ?, explosions = ?, hostile_spawns = ?, "
                        + "mob_griefing = ?, passive_spawns = ?, crop_trampling = ?, leaf_decay = ? "
                        + "WHERE id = ?",
                claim.getColor().name(),
                claim.getIcon() != null ? claim.getIcon().name() : null,
                claim.getParticleEffect(),
                claim.getGroupPermissions() != null
                        ? claim.getGroupPermissions().serialize()
                        : null,
                s != null ? s.isPvpEnabled() : false,
                s != null ? s.isFireSpread() : false,
                s != null ? s.isExplosions() : false,
                s != null ? s.isHostileSpawns() : true,
                s != null ? s.isMobGriefing() : false,
                s != null ? s.isPassiveSpawns() : true,
                s != null ? s.isCropTrampling() : false,
                s != null ? s.isLeafDecay() : true,
                claim.getId());
    }

    /** Save claim chunk pool data (total_chunks, purchased_chunks) */
    public void saveClaimChunkData(Claim claim) {
        executeUpdateSafe(
                "UPDATE server_claims SET total_chunks = ?, purchased_chunks = ? WHERE id = ?",
                claim.getTotalChunks(),
                claim.getPurchasedChunks(),
                claim.getId());
    }

    /** Update claim name */
    public void updateClaimName(Claim claim) {
        executeUpdateSafe(
                "UPDATE server_claims SET name = ? WHERE id = ?", claim.getName(), claim.getId());
    }

    /** Update claim color */
    public void updateClaimColor(Claim claim) {
        executeUpdateSafe(
                "UPDATE server_claims SET color = ? WHERE id = ?",
                claim.getColor().name(),
                claim.getId());
    }

    /**
     * Updates the entire claim record (general update method). Currently updates particle_enabled
     * flag.
     */
    public void updateClaim(Claim claim) {
        executeUpdateSafe(
                "UPDATE server_claims SET particle_enabled = ? WHERE id = ?",
                claim.isParticleEnabled(),
                claim.getId());
    }

    public void deleteClaim(Claim claim) {
        executeUpdateSafe("DELETE FROM server_claims WHERE id = ?", claim.getId());
    }

    public void saveChunk(ClaimedChunk chunk) {
        executeUpdateSafe(
                "INSERT INTO server_chunks (claim_id, world, chunk_x, chunk_z) VALUES (?, ?, ?, ?)",
                chunk.getClaimId(),
                chunk.getWorld(),
                chunk.getChunkX(),
                chunk.getChunkZ());
    }

    public void deleteChunk(ClaimedChunk chunk) {
        executeUpdateSafe(
                "DELETE FROM server_chunks WHERE claim_id = ? AND chunk_x = ? AND chunk_z = ?",
                chunk.getClaimId(),
                chunk.getChunkX(),
                chunk.getChunkZ());
    }

    /**
     * Update chunk ownership - used for claim merging.
     *
     * @param world World name
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @param newClaimId New claim ID to assign chunk to
     */
    public void updateChunkClaim(String world, int chunkX, int chunkZ, int newClaimId) {
        executeUpdateSafe(
                "UPDATE server_chunks SET claim_id = ? WHERE world = ? AND chunk_x = ? AND chunk_z = ?",
                newClaimId,
                world,
                chunkX,
                chunkZ);
    }

    public void saveProfile(ClaimProfile profile) {
        if (profile.getId() == 0) {
            executeUpdateSafe(
                    "INSERT INTO server_profiles (claim_id, name, color, icon, particle_effect, is_active, slot_index, group_permissions, "
                            + "selected_dust_effect, selected_profile_color, particles_enabled, static_particle_mode) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    profile.getClaimId(),
                    profile.getName(),
                    profile.getColor().name(),
                    profile.getIcon() != null ? profile.getIcon().name() : null,
                    profile.getParticleEffect(),
                    profile.isActive(),
                    profile.getSlotIndex(),
                    profile.getGroupPermissions() != null
                            ? profile.getGroupPermissions().serialize()
                            : null,
                    profile.getSelectedDustEffect() != null
                            ? profile.getSelectedDustEffect().name()
                            : null,
                    profile.getSelectedProfileColor() != null
                            ? profile.getSelectedProfileColor().name()
                            : null,
                    profile.isParticlesEnabled(),
                    profile.isStaticParticleMode());

            executeQuery(
                    "SELECT MAX(id) as id FROM server_profiles WHERE claim_id = ?",
                    rs -> {
                        if (rs.next()) profile.setId(rs.getInt("id"));
                        return null;
                    },
                    profile.getClaimId());

            saveProfileSettings(profile);
        } else {
            executeUpdateSafe(
                    "UPDATE server_profiles SET name = ?, color = ?, icon = ?, particle_effect = ?, is_active = ?, group_permissions = ?, "
                            + "selected_dust_effect = ?, selected_profile_color = ?, particles_enabled = ?, static_particle_mode = ? WHERE id = ?",
                    profile.getName(),
                    profile.getColor().name(),
                    profile.getIcon() != null ? profile.getIcon().name() : null,
                    profile.getParticleEffect(),
                    profile.isActive(),
                    profile.getGroupPermissions() != null
                            ? profile.getGroupPermissions().serialize()
                            : null,
                    profile.getSelectedDustEffect() != null
                            ? profile.getSelectedDustEffect().name()
                            : null,
                    profile.getSelectedProfileColor() != null
                            ? profile.getSelectedProfileColor().name()
                            : null,
                    profile.isParticlesEnabled(),
                    profile.isStaticParticleMode(),
                    profile.getId());
            saveProfileSettings(profile);
        }
    }

    public void saveGroupPermissions(ClaimProfile profile) {
        if (profile.getId() > 0 && profile.getGroupPermissions() != null) {
            executeUpdateSafe(
                    "UPDATE server_profiles SET group_permissions = ? WHERE id = ?",
                    profile.getGroupPermissions().serialize(),
                    profile.getId());
        }
    }

    public void deleteProfile(ClaimProfile profile) {
        executeUpdateSafe(
                "DELETE FROM server_profile_settings WHERE profile_id = ?", profile.getId());
        executeUpdateSafe("DELETE FROM server_profiles WHERE id = ?", profile.getId());
    }

    public void saveProfileSettings(ClaimProfile profile) {
        ClaimSettings s = profile.getSettings();
        executeUpdateSafe(
                "INSERT INTO server_profile_settings (profile_id, pvp_enabled, fire_spread, explosions, "
                        + "hostile_spawns, mob_griefing, passive_spawns, crop_trampling) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE pvp_enabled = VALUES(pvp_enabled), fire_spread = VALUES(fire_spread), "
                        + "explosions = VALUES(explosions), hostile_spawns = VALUES(hostile_spawns), "
                        + "mob_griefing = VALUES(mob_griefing), passive_spawns = VALUES(passive_spawns), "
                        + "crop_trampling = VALUES(crop_trampling)",
                profile.getId(),
                s.isPvpEnabled(),
                s.isFireSpread(),
                s.isExplosions(),
                s.isHostileSpawns(),
                s.isMobGriefing(),
                s.isPassiveSpawns(),
                s.isCropTrampling());
    }

    public void trustPlayer(int claimId, UUID playerUuid) {
        executeUpdateSafe(
                "INSERT INTO server_trusted_players (claim_id, player_uuid) VALUES (?, ?)",
                claimId,
                playerUuid.toString());
    }

    public void untrustPlayer(int claimId, UUID playerUuid) {
        executeUpdateSafe(
                "DELETE FROM server_trusted_players WHERE claim_id = ? AND player_uuid = ?",
                claimId,
                playerUuid.toString());
    }

    // Member management (legacy)
    @Deprecated
    public void saveMember(int claimId, UUID playerUuid, ClaimGroup group) {
        executeUpdateSafe(
                "INSERT INTO server_claim_members (claim_id, player_uuid, group_name) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE group_name = VALUES(group_name)",
                claimId,
                playerUuid.toString(),
                group.name());
    }

    // Member management with custom group
    public void saveMemberWithGroupId(int claimId, UUID playerUuid, int groupId) {
        executeUpdateSafe(
                "INSERT INTO server_claim_members (claim_id, player_uuid, group_id) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE group_id = VALUES(group_id)",
                claimId,
                playerUuid.toString(),
                groupId);
    }

    public void removeMember(int claimId, UUID playerUuid) {
        executeUpdateSafe(
                "DELETE FROM server_claim_members WHERE claim_id = ? AND player_uuid = ?",
                claimId,
                playerUuid.toString());
    }

    private void loadClaimMembers(Claim claim) {
        executeQuery(
                "SELECT player_uuid, group_name, group_id FROM server_claim_members WHERE claim_id = ?",
                rs -> {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("player_uuid"));

                        // Try to load custom group_id first (new system)
                        int groupId = rs.getInt("group_id");
                        if (!rs.wasNull() && groupId > 0) {
                            claim.setMemberGroupId(uuid, groupId);
                        }

                        // Also load legacy group_name for backwards compatibility
                        String groupName = rs.getString("group_name");
                        if (groupName != null && !groupName.isEmpty()) {
                            try {
                                ClaimGroup group = ClaimGroup.valueOf(groupName);
                                claim.setMemberGroup(uuid, group);
                            } catch (IllegalArgumentException e) {
                                claim.setMemberGroup(uuid, ClaimGroup.VISITOR);
                            }
                        }
                    }
                    return null;
                },
                claim.getId());
    }

    /**
     * Load custom groups for a claim from the database. Call this after mapClaim but before using
     * group permissions.
     */
    public void loadClaimCustomGroups(Claim claim, ClaimGroupRepository groupRepository) {
        List<CustomGroup> groups = groupRepository.getGroupsForClaim(claim.getId());
        claim.setCustomGroups(groups);
    }

    /**
     * Gets claims where player has management access (not just owned claims). Used for showing
     * "Claims You Manage" in MyProfilesGui.
     */
    public List<Claim> getAccessibleClaims(UUID playerUuid, ClaimGroupRepository groupRepository) {
        return executeQuery(
                "SELECT DISTINCT c.* FROM server_claims c "
                        + "LEFT JOIN server_claim_members m ON c.id = m.claim_id "
                        + "LEFT JOIN server_claim_groups g ON m.group_id = g.id "
                        + "WHERE c.owner_uuid = ? "
                        + "   OR (m.player_uuid = ? AND g.management_permissions IS NOT NULL AND g.management_permissions != '')",
                rs -> {
                    List<Claim> claims = new ArrayList<>();
                    while (rs.next()) {
                        Claim claim = mapClaim(rs);
                        loadClaimChunks(claim);
                        loadClaimProfiles(claim);
                        loadTrustedPlayers(claim);
                        loadClaimMembers(claim);
                        loadBannedPlayers(claim);
                        if (groupRepository != null) {
                            loadClaimCustomGroups(claim, groupRepository);
                        }
                        claims.add(claim);
                    }
                    return claims;
                },
                playerUuid.toString(),
                playerUuid.toString());
    }

    private Claim mapClaim(ResultSet rs) throws SQLException {
        Claim claim =
                new Claim(
                        rs.getInt("id"),
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getString("name"),
                        rs.getString("world"));

        // Load legacy fields (with null safety for existing databases)
        try {
            claim.setWelcomeMessage(rs.getString("welcome_message"));
            claim.setTeleportProtected(rs.getBoolean("teleport_protected"));
            claim.setKeepInventory(rs.getBoolean("keep_inventory"));
        } catch (SQLException ignored) {
            // Columns may not exist in older databases
        }

        // Load per-claim chunk pool data
        try {
            claim.setTotalChunks(rs.getInt("total_chunks"));
            claim.setPurchasedChunks(rs.getInt("purchased_chunks"));
            claim.setClaimOrder(rs.getInt("claim_order"));
        } catch (SQLException ignored) {
            // Default values already set in Claim constructor
        }

        // Load claim appearance
        try {
            String colorStr = rs.getString("color");
            if (colorStr != null) {
                claim.setColor(ProfileColor.fromString(colorStr));
            }
            String iconStr = rs.getString("icon");
            if (iconStr != null && !iconStr.isEmpty()) {
                try {
                    claim.setIcon(org.bukkit.Material.valueOf(iconStr));
                } catch (IllegalArgumentException ignored) {
                }
            }
            String particleEffect = rs.getString("particle_effect");
            if (particleEffect != null) {
                claim.setParticleEffect(particleEffect);
            }
            try {
                claim.setParticleEnabled(rs.getBoolean("particle_enabled"));
            } catch (SQLException ignored) {
                // Column might not exist in older databases, default is true
            }
        } catch (SQLException ignored) {
            // Default values already set
        }

        // Load claim settings directly
        try {
            boolean leafDecay = true; // Default to true
            try {
                leafDecay = rs.getBoolean("leaf_decay");
            } catch (SQLException ignored) {
                // Column might not exist in older databases
            }
            ClaimSettings settings =
                    new ClaimSettings(
                            rs.getBoolean("pvp_enabled"),
                            rs.getBoolean("fire_spread"),
                            rs.getBoolean("explosions"),
                            rs.getBoolean("hostile_spawns"),
                            rs.getBoolean("mob_griefing"),
                            rs.getBoolean("passive_spawns"),
                            rs.getBoolean("crop_trampling"),
                            leafDecay);
            claim.setSettings(settings);
        } catch (SQLException ignored) {
            // Default settings already set in Claim constructor
        }

        // Load group permissions
        try {
            String groupPermsStr = rs.getString("group_permissions");
            if (groupPermsStr != null && !groupPermsStr.isEmpty()) {
                claim.setGroupPermissions(
                        GroupPermissions.deserialize(claim.getId(), groupPermsStr));
            }
        } catch (SQLException ignored) {
            // No group permissions set
        }

        return claim;
    }

    private void loadClaimChunks(Claim claim) {
        executeQuery(
                "SELECT * FROM server_chunks WHERE claim_id = ?",
                rs -> {
                    while (rs.next()) {
                        claim.addChunk(
                                new ClaimedChunk(
                                        rs.getInt("id"),
                                        rs.getInt("claim_id"),
                                        rs.getString("world"),
                                        rs.getInt("chunk_x"),
                                        rs.getInt("chunk_z"),
                                        rs.getTimestamp("claimed_at").toInstant()));
                    }
                    return null;
                },
                claim.getId());

        // Synchronize purchasedChunks with actual chunks.size() to fix data integrity issues
        claim.syncChunkCounts();
    }

    /**
     * Batch load chunks for multiple claims at once (eliminates N+1 query problem). This is
     * significantly faster than calling loadClaimChunks() for each claim individually.
     *
     * @param claimIds Collection of claim IDs to load chunks for
     * @return Map of claim ID to list of chunks
     */
    public Map<Integer, List<ClaimedChunk>> loadChunksBatch(Collection<Integer> claimIds) {
        if (claimIds == null || claimIds.isEmpty()) {
            return new HashMap<>();
        }

        // Build parameterized IN clause
        StringBuilder sql = new StringBuilder("SELECT * FROM server_chunks WHERE claim_id IN (");
        for (int i = 0; i < claimIds.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append("?");
        }
        sql.append(") ORDER BY claim_id");

        return executeQuery(
                sql.toString(),
                rs -> {
                    Map<Integer, List<ClaimedChunk>> chunksByClaimId = new HashMap<>();
                    while (rs.next()) {
                        int claimId = rs.getInt("claim_id");
                        ClaimedChunk chunk =
                                new ClaimedChunk(
                                        rs.getInt("id"),
                                        claimId,
                                        rs.getString("world"),
                                        rs.getInt("chunk_x"),
                                        rs.getInt("chunk_z"),
                                        rs.getTimestamp("claimed_at").toInstant());
                        chunksByClaimId.computeIfAbsent(claimId, k -> new ArrayList<>()).add(chunk);
                    }
                    return chunksByClaimId;
                },
                claimIds.toArray());
    }

    private void loadClaimProfiles(Claim claim) {
        executeQuery(
                "SELECT p.*, s.* FROM server_profiles p "
                        + "LEFT JOIN server_profile_settings s ON p.id = s.profile_id "
                        + "WHERE p.claim_id = ?",
                rs -> {
                    while (rs.next()) {
                        ClaimProfile profile =
                                new ClaimProfile(
                                        rs.getInt("id"),
                                        rs.getInt("claim_id"),
                                        rs.getString("name"),
                                        ProfileColor.fromString(rs.getString("color")),
                                        rs.getString("particle_effect"),
                                        rs.getBoolean("is_active"),
                                        rs.getInt("slot_index"));

                        // Load icon
                        String iconStr = rs.getString("icon");
                        if (iconStr != null && !iconStr.isEmpty()) {
                            try {
                                profile.setIcon(org.bukkit.Material.valueOf(iconStr));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }

                        // Load group permissions
                        String groupPermsStr = rs.getString("group_permissions");
                        if (groupPermsStr != null && !groupPermsStr.isEmpty()) {
                            profile.setGroupPermissions(
                                    GroupPermissions.deserialize(profile.getId(), groupPermsStr));
                        }

                        // Load per-profile particle settings (v6.0)
                        try {
                            String dustEffectStr = rs.getString("selected_dust_effect");
                            if (dustEffectStr != null && !dustEffectStr.isEmpty()) {
                                profile.setSelectedDustEffect(DustEffect.fromString(dustEffectStr));
                            }

                            String profileColorStr = rs.getString("selected_profile_color");
                            if (profileColorStr != null && !profileColorStr.isEmpty()) {
                                profile.setSelectedProfileColor(
                                        ProfileColor.fromString(profileColorStr));
                            }

                            profile.setParticlesEnabled(rs.getBoolean("particles_enabled"));
                            profile.setStaticParticleMode(rs.getBoolean("static_particle_mode"));
                        } catch (SQLException ignored) {
                            // Columns might not exist in older databases, use defaults
                        }

                        ClaimSettings settings =
                                new ClaimSettings(
                                        rs.getBoolean("pvp_enabled"),
                                        rs.getBoolean("fire_spread"),
                                        rs.getBoolean("explosions"),
                                        rs.getBoolean("hostile_spawns"),
                                        rs.getBoolean("mob_griefing"),
                                        rs.getBoolean("passive_spawns"),
                                        rs.getBoolean("crop_trampling"),
                                        true // leaf decay enabled by default for legacy profiles
                                        );
                        profile.setSettings(settings);
                        claim.addProfile(profile);
                    }
                    return null;
                },
                claim.getId());
    }

    private void loadTrustedPlayers(Claim claim) {
        executeQuery(
                "SELECT player_uuid FROM server_trusted_players WHERE claim_id = ?",
                rs -> {
                    while (rs.next()) {
                        claim.trustPlayer(UUID.fromString(rs.getString("player_uuid")));
                    }
                    return null;
                },
                claim.getId());
    }

    private void loadBannedPlayers(Claim claim) {
        executeQuery(
                "SELECT player_uuid FROM server_banned_players WHERE claim_id = ?",
                rs -> {
                    while (rs.next()) {
                        claim.banPlayer(UUID.fromString(rs.getString("player_uuid")));
                    }
                    return null;
                },
                claim.getId());
    }

    // Ban management
    public void banPlayer(int claimId, UUID playerUuid) {
        executeUpdateSafe(
                "INSERT INTO server_banned_players (claim_id, player_uuid) VALUES (?, ?)",
                claimId,
                playerUuid.toString());
        // Also remove from trusted and members
        untrustPlayer(claimId, playerUuid);
        removeMember(claimId, playerUuid);
    }

    public void unbanPlayer(int claimId, UUID playerUuid) {
        executeUpdateSafe(
                "DELETE FROM server_banned_players WHERE claim_id = ? AND player_uuid = ?",
                claimId,
                playerUuid.toString());
    }

    /**
     * Get all players who have purchased chunks (purchased_chunks > 0) Used for refund calculations
     * when prices change
     */
    public List<PlayerClaimData> getAllPlayersWithPurchases() {
        return executeQuery(
                "SELECT * FROM server_player_claims WHERE purchased_chunks > 0",
                rs -> {
                    List<PlayerClaimData> players = new ArrayList<>();
                    while (rs.next()) {
                        players.add(
                                new PlayerClaimData(
                                        UUID.fromString(rs.getString("uuid")),
                                        rs.getString("username"),
                                        rs.getInt("total_chunks"),
                                        rs.getInt("purchased_chunks"),
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getTimestamp("updated_at").toInstant()));
                    }
                    return players;
                });
    }

    // ==================== WARP METHODS ====================

    public ClaimWarp getClaimWarp(int claimId) {
        return executeQuery(
                "SELECT * FROM server_claim_warps WHERE claim_id = ?",
                rs -> {
                    if (rs.next()) {
                        ClaimWarp warp =
                                new ClaimWarp(
                                        rs.getInt("claim_id"),
                                        rs.getDouble("warp_x"),
                                        rs.getDouble("warp_y"),
                                        rs.getDouble("warp_z"),
                                        rs.getFloat("warp_yaw"),
                                        rs.getFloat("warp_pitch"),
                                        WarpVisibility.valueOf(rs.getString("visibility_mode")),
                                        rs.getDouble("visit_cost"),
                                        rs.getString("description"),
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getTimestamp("updated_at").toInstant());
                        loadWarpAllowlist(warp);
                        loadWarpBlocklist(warp);
                        return warp;
                    }
                    return null;
                },
                claimId);
    }

    public void saveClaimWarp(ClaimWarp warp) {
        executeUpdateSafe(
                "INSERT INTO server_claim_warps (claim_id, warp_x, warp_y, warp_z, warp_yaw, warp_pitch, "
                        + "visibility_mode, visit_cost, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE warp_x = VALUES(warp_x), warp_y = VALUES(warp_y), "
                        + "warp_z = VALUES(warp_z), warp_yaw = VALUES(warp_yaw), warp_pitch = VALUES(warp_pitch), "
                        + "visibility_mode = VALUES(visibility_mode), visit_cost = VALUES(visit_cost), "
                        + "description = VALUES(description), updated_at = CURRENT_TIMESTAMP",
                warp.getClaimId(),
                warp.getX(),
                warp.getY(),
                warp.getZ(),
                warp.getYaw(),
                warp.getPitch(),
                warp.getVisibility().name(),
                warp.getVisitCost(),
                warp.getDescription());
    }

    public void deleteClaimWarp(int claimId) {
        executeUpdateSafe("DELETE FROM server_claim_warp_allowlist WHERE claim_id = ?", claimId);
        executeUpdateSafe("DELETE FROM server_claim_warp_blocklist WHERE claim_id = ?", claimId);
        executeUpdateSafe("DELETE FROM server_claim_warps WHERE claim_id = ?", claimId);
    }

    private void loadWarpAllowlist(ClaimWarp warp) {
        executeQuery(
                "SELECT player_uuid FROM server_claim_warp_allowlist WHERE claim_id = ?",
                rs -> {
                    while (rs.next()) {
                        warp.addToAllowlist(UUID.fromString(rs.getString("player_uuid")));
                    }
                    return null;
                },
                warp.getClaimId());
    }

    private void loadWarpBlocklist(ClaimWarp warp) {
        executeQuery(
                "SELECT player_uuid FROM server_claim_warp_blocklist WHERE claim_id = ?",
                rs -> {
                    while (rs.next()) {
                        warp.addToBlocklist(UUID.fromString(rs.getString("player_uuid")));
                    }
                    return null;
                },
                warp.getClaimId());
    }

    public void addToWarpAllowlist(int claimId, UUID playerUuid) {
        executeUpdateSafe(
                "INSERT IGNORE INTO server_claim_warp_allowlist (claim_id, player_uuid) VALUES (?, ?)",
                claimId,
                playerUuid.toString());
    }

    public void removeFromWarpAllowlist(int claimId, UUID playerUuid) {
        executeUpdateSafe(
                "DELETE FROM server_claim_warp_allowlist WHERE claim_id = ? AND player_uuid = ?",
                claimId,
                playerUuid.toString());
    }

    public void addToWarpBlocklist(int claimId, UUID playerUuid) {
        executeUpdateSafe(
                "INSERT IGNORE INTO server_claim_warp_blocklist (claim_id, player_uuid) VALUES (?, ?)",
                claimId,
                playerUuid.toString());
    }

    public void removeFromWarpBlocklist(int claimId, UUID playerUuid) {
        executeUpdateSafe(
                "DELETE FROM server_claim_warp_blocklist WHERE claim_id = ? AND player_uuid = ?",
                claimId,
                playerUuid.toString());
    }

    public List<Claim> getPublicWarpClaims() {
        return executeQuery(
                "SELECT c.* FROM server_claims c "
                        + "INNER JOIN server_claim_warps w ON c.id = w.claim_id "
                        + "WHERE w.visibility_mode = 'PUBLIC'",
                rs -> {
                    List<Claim> claims = new ArrayList<>();
                    while (rs.next()) {
                        Claim claim = mapClaim(rs);
                        loadClaimChunks(claim);
                        claims.add(claim);
                    }
                    return claims;
                });
    }

    public List<Claim> getVisitableClaims(UUID visitorUuid) {
        return executeQuery(
                "SELECT DISTINCT c.* FROM server_claims c "
                        + "INNER JOIN server_claim_warps w ON c.id = w.claim_id "
                        + "LEFT JOIN server_claim_warp_blocklist bl ON c.id = bl.claim_id AND bl.player_uuid = ? "
                        + "WHERE bl.player_uuid IS NULL "
                        + "AND (w.visibility_mode = 'PUBLIC' "
                        + "     OR (w.visibility_mode = 'ALLOWLIST' AND EXISTS ("
                        + "         SELECT 1 FROM server_claim_warp_allowlist al "
                        + "         WHERE al.claim_id = c.id AND al.player_uuid = ?)))",
                rs -> {
                    List<Claim> claims = new ArrayList<>();
                    while (rs.next()) {
                        Claim claim = mapClaim(rs);
                        loadClaimChunks(claim);
                        claims.add(claim);
                    }
                    return claims;
                },
                visitorUuid.toString(),
                visitorUuid.toString());
    }

    // ==================== CHUNK TRANSFER METHODS ====================

    /** Records a chunk transfer for audit purposes. */
    public void recordChunkTransfer(ChunkTransfer transfer) {
        executeUpdateSafe(
                "INSERT INTO server_chunk_transfers (chunk_world, chunk_x, chunk_z, from_claim_id, to_claim_id, "
                        + "from_owner_uuid, to_owner_uuid, transfer_type) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                transfer.getChunkWorld(),
                transfer.getChunkX(),
                transfer.getChunkZ(),
                transfer.getFromClaimId(),
                transfer.getToClaimId(),
                transfer.getFromOwnerUuid() != null ? transfer.getFromOwnerUuid().toString() : null,
                transfer.getToOwnerUuid() != null ? transfer.getToOwnerUuid().toString() : null,
                transfer.getTransferType().name());
    }

    /** Records a chunk unclaim event. */
    public void recordChunkUnclaim(ClaimedChunk chunk, Claim fromClaim) {
        ChunkTransfer transfer = ChunkTransfer.unclaim(chunk, fromClaim);
        recordChunkTransfer(transfer);
    }

    /** Records a chunk reassignment (move to different claim, same owner). */
    public void recordChunkReassignment(ClaimedChunk chunk, Claim fromClaim, Claim toClaim) {
        ChunkTransfer transfer = ChunkTransfer.reassignment(chunk, fromClaim, toClaim);
        recordChunkTransfer(transfer);
    }

    /** Records an ownership transfer of a chunk. */
    public void recordChunkOwnershipTransfer(
            ClaimedChunk chunk, Claim fromClaim, UUID toOwnerUuid, Integer toClaimId) {
        ChunkTransfer transfer =
                ChunkTransfer.ownershipTransfer(chunk, fromClaim, toOwnerUuid, toClaimId);
        recordChunkTransfer(transfer);
    }

    /**
     * Reassign a chunk to a different claim (same owner). Updates the chunk's claim_id in the
     * database.
     */
    public void reassignChunkToClaim(ClaimedChunk chunk, int newClaimId) {
        executeUpdateSafe(
                "UPDATE server_chunks SET claim_id = ? WHERE id = ?", newClaimId, chunk.getId());
    }

    /**
     * Transfer a chunk to a new owner by creating it in their claim. This deletes the chunk from
     * the old claim and creates it in the new one.
     *
     * @param chunk The chunk to transfer
     * @param newClaimId The destination claim ID
     * @return true if successful
     */
    public boolean transferChunkToNewClaim(ClaimedChunk chunk, int newClaimId) {
        try {
            // Delete from old claim
            executeUpdateSafe("DELETE FROM server_chunks WHERE id = ?", chunk.getId());

            // Insert into new claim
            executeUpdateSafe(
                    "INSERT INTO server_chunks (claim_id, world, chunk_x, chunk_z) VALUES (?, ?, ?, ?)",
                    newClaimId,
                    chunk.getWorld(),
                    chunk.getChunkX(),
                    chunk.getChunkZ());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Delete multiple chunks at once (bulk unclaim). */
    public void deleteChunks(List<ClaimedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return;

        for (ClaimedChunk chunk : chunks) {
            executeUpdateSafe(
                    "DELETE FROM server_chunks WHERE claim_id = ? AND chunk_x = ? AND chunk_z = ?",
                    chunk.getClaimId(),
                    chunk.getChunkX(),
                    chunk.getChunkZ());
        }
    }

    /** Get transfer history for a chunk. */
    public List<ChunkTransfer> getChunkTransferHistory(String world, int chunkX, int chunkZ) {
        return executeQuery(
                "SELECT * FROM server_chunk_transfers WHERE chunk_world = ? AND chunk_x = ? AND chunk_z = ? "
                        + "ORDER BY transferred_at DESC LIMIT 50",
                rs -> {
                    List<ChunkTransfer> transfers = new ArrayList<>();
                    while (rs.next()) {
                        ChunkTransfer transfer = new ChunkTransfer();
                        transfer.setId(rs.getInt("id"));
                        transfer.setChunkWorld(rs.getString("chunk_world"));
                        transfer.setChunkX(rs.getInt("chunk_x"));
                        transfer.setChunkZ(rs.getInt("chunk_z"));

                        int fromClaimId = rs.getInt("from_claim_id");
                        transfer.setFromClaimId(rs.wasNull() ? null : fromClaimId);

                        int toClaimId = rs.getInt("to_claim_id");
                        transfer.setToClaimId(rs.wasNull() ? null : toClaimId);

                        String fromUuid = rs.getString("from_owner_uuid");
                        transfer.setFromOwnerUuid(
                                fromUuid != null ? UUID.fromString(fromUuid) : null);

                        String toUuid = rs.getString("to_owner_uuid");
                        transfer.setToOwnerUuid(toUuid != null ? UUID.fromString(toUuid) : null);

                        transfer.setTransferType(
                                ChunkTransfer.TransferType.valueOf(rs.getString("transfer_type")));
                        transfer.setTransferredAt(rs.getTimestamp("transferred_at").toInstant());

                        transfers.add(transfer);
                    }
                    return transfers;
                },
                world,
                chunkX,
                chunkZ);
    }

    // ==================== TRANSACTIONAL HELPER METHODS ====================

    /**
     * Add a chunk to a claim within a transaction. This method can be called both inside and
     * outside a transaction. When called inside a transaction, it participates in that transaction.
     * When called outside a transaction, it executes immediately.
     *
     * @param claimId ID of the claim
     * @param world World name
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @throws SQLException if the operation fails
     */
    public void addChunk(int claimId, String world, int chunkX, int chunkZ) throws SQLException {
        executeUpdate(
                "INSERT INTO server_chunks (claim_id, world, chunk_x, chunk_z) VALUES (?, ?, ?, ?)",
                claimId,
                world,
                chunkX,
                chunkZ);
    }

    /**
     * Remove a chunk from any claim it belongs to. This method can be called both inside and
     * outside a transaction.
     *
     * @param world World name
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @throws SQLException if the operation fails
     */
    public void removeChunk(String world, int chunkX, int chunkZ) throws SQLException {
        executeUpdate(
                "DELETE FROM server_chunks WHERE world = ? AND chunk_x = ? AND chunk_z = ?",
                world,
                chunkX,
                chunkZ);
    }

    /**
     * Delete a claim by ID within a transaction context. This method deletes the claim and all
     * associated data (chunks, members, etc.). When called within a transaction, it participates in
     * that transaction.
     *
     * @param claimId ID of the claim to delete
     * @throws SQLException if the operation fails
     */
    public void deleteClaimById(int claimId) throws SQLException {
        // Delete in order of foreign key dependencies
        executeUpdate("DELETE FROM server_chunks WHERE claim_id = ?", claimId);
        executeUpdate("DELETE FROM server_trusted_players WHERE claim_id = ?", claimId);
        executeUpdate("DELETE FROM server_claim_members WHERE claim_id = ?", claimId);
        executeUpdate("DELETE FROM server_banned_players WHERE claim_id = ?", claimId);
        executeUpdate(
                "DELETE FROM server_profile_settings WHERE profile_id IN (SELECT id FROM server_profiles WHERE claim_id = ?)",
                claimId);
        executeUpdate("DELETE FROM server_profiles WHERE claim_id = ?", claimId);
        executeUpdate("DELETE FROM server_claim_warp_allowlist WHERE claim_id = ?", claimId);
        executeUpdate("DELETE FROM server_claim_warp_blocklist WHERE claim_id = ?", claimId);
        executeUpdate("DELETE FROM server_claim_warps WHERE claim_id = ?", claimId);
        executeUpdate("DELETE FROM server_claims WHERE id = ?", claimId);

        LOGGER.info("Deleted claim ID " + claimId + " and all associated data");
    }

    /**
     * Transfer multiple chunks from one claim to another atomically. This operation is
     * automatically wrapped in a transaction.
     *
     * @param chunks List of chunks to transfer
     * @param fromClaimId Source claim ID
     * @param toClaimId Destination claim ID
     * @throws SQLException if the transfer fails
     */
    public void transferChunksAtomic(List<ClaimedChunk> chunks, int fromClaimId, int toClaimId)
            throws SQLException {
        executeInTransaction(
                () -> {
                    for (ClaimedChunk chunk : chunks) {
                        // Verify the chunk belongs to the source claim
                        ClaimedChunk current =
                                executeQuery(
                                        "SELECT * FROM server_chunks WHERE world = ? AND chunk_x = ? AND chunk_z = ?",
                                        rs -> {
                                            if (rs.next()) {
                                                return new ClaimedChunk(
                                                        rs.getInt("id"),
                                                        rs.getInt("claim_id"),
                                                        rs.getString("world"),
                                                        rs.getInt("chunk_x"),
                                                        rs.getInt("chunk_z"),
                                                        rs.getTimestamp("claimed_at").toInstant());
                                            }
                                            return null;
                                        },
                                        chunk.getWorld(),
                                        chunk.getChunkX(),
                                        chunk.getChunkZ());

                        if (current == null || current.getClaimId() != fromClaimId) {
                            throw new SQLException(
                                    "Chunk at "
                                            + chunk.getWorld()
                                            + " ("
                                            + chunk.getChunkX()
                                            + ", "
                                            + chunk.getChunkZ()
                                            + ") does not belong to claim "
                                            + fromClaimId);
                        }

                        // Update the chunk's claim ID
                        reassignChunkToClaim(chunk, toClaimId);

                        // Record the transfer
                        Claim fromClaim = getClaimById(fromClaimId);
                        Claim toClaim = getClaimById(toClaimId);
                        if (fromClaim != null && toClaim != null) {
                            recordChunkReassignment(chunk, fromClaim, toClaim);
                        }
                    }
                    LOGGER.info(
                            "Transferred "
                                    + chunks.size()
                                    + " chunks from claim "
                                    + fromClaimId
                                    + " to claim "
                                    + toClaimId);
                });
    }

    /**
     * Update player chunk allocation and save claim data atomically. Useful when purchasing chunks
     * or modifying chunk pools.
     *
     * @param playerData Player's claim data to update
     * @param claim Claim to update
     * @throws SQLException if the operation fails
     */
    public void updatePlayerAndClaimDataAtomic(PlayerClaimData playerData, Claim claim)
            throws SQLException {
        executeInTransaction(
                () -> {
                    if (!savePlayerData(playerData)) {
                        throw new SQLException(
                                "Failed to save player data for " + playerData.getUuid());
                    }
                    saveClaim(claim);
                    saveClaimChunkData(claim);
                });
    }

    // ==================== PLAYER CHUNK POOL METHODS ====================

    /**
     * Get player's chunk pool from database (returns null if doesn't exist)
     *
     * @param playerUuid The player's UUID
     * @return PlayerChunkPool instance or null
     * @throws SQLException if database operation fails
     */
    public PlayerChunkPool getPlayerChunkPool(UUID playerUuid) throws SQLException {
        String query = "SELECT * FROM server_player_chunk_pool WHERE player_uuid = ?";
        return executeQuery(
                query,
                rs -> {
                    if (rs.next()) {
                        Timestamp lastPurchaseTs = rs.getTimestamp("last_purchase");
                        return new PlayerChunkPool(
                                playerUuid,
                                rs.getInt("purchased_chunks"),
                                rs.getDouble("total_spent"),
                                lastPurchaseTs != null ? lastPurchaseTs.toInstant() : null,
                                rs.getTimestamp("created_at").toInstant(),
                                rs.getTimestamp("updated_at").toInstant());
                    }
                    return null;
                },
                playerUuid.toString());
    }

    /**
     * Get total chunks allocated across all player's claim profiles
     *
     * @param playerUuid The player's UUID
     * @return Total chunks allocated to profiles
     * @throws SQLException if database operation fails
     */
    public int getTotalAllocatedChunks(UUID playerUuid) throws SQLException {
        String query =
                "SELECT COALESCE(SUM(purchased_chunks), 0) FROM server_claims WHERE owner_uuid = ?";
        return executeQuery(
                query,
                rs -> {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                    return 0;
                },
                playerUuid.toString());
    }

    /**
     * Get player's chunk pool, creating if doesn't exist
     *
     * @param playerUuid The player's UUID
     * @return PlayerChunkPool instance
     * @throws SQLException if database operation fails
     */
    public PlayerChunkPool getOrCreatePlayerChunkPool(UUID playerUuid) throws SQLException {
        String query = "SELECT * FROM server_player_chunk_pool WHERE player_uuid = ?";
        PlayerChunkPool pool =
                executeQuery(
                        query,
                        rs -> {
                            if (rs.next()) {
                                Timestamp lastPurchaseTs = rs.getTimestamp("last_purchase");
                                return new PlayerChunkPool(
                                        playerUuid,
                                        rs.getInt("purchased_chunks"),
                                        rs.getDouble("total_spent"),
                                        lastPurchaseTs != null ? lastPurchaseTs.toInstant() : null,
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getTimestamp("updated_at").toInstant());
                            }
                            return null;
                        },
                        playerUuid.toString());

        if (pool == null) {
            // Create new pool
            pool = new PlayerChunkPool(playerUuid);
            savePlayerChunkPool(pool);
        }

        return pool;
    }

    /**
     * Save or update player chunk pool
     *
     * @param pool The chunk pool to save
     * @throws SQLException if database operation fails
     */
    public void savePlayerChunkPool(PlayerChunkPool pool) throws SQLException {
        String query =
                "INSERT INTO server_player_chunk_pool "
                        + "(player_uuid, purchased_chunks, total_spent, last_purchase, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE "
                        + "purchased_chunks = VALUES(purchased_chunks), "
                        + "total_spent = VALUES(total_spent), "
                        + "last_purchase = VALUES(last_purchase), "
                        + "updated_at = VALUES(updated_at)";

        Timestamp lastPurchaseTs =
                pool.getLastPurchase() != null ? Timestamp.from(pool.getLastPurchase()) : null;
        Timestamp updatedAtTs = Timestamp.from(java.time.Instant.now());

        executeUpdate(
                query,
                pool.getPlayerUuid().toString(),
                pool.getPurchasedChunks(),
                pool.getTotalSpent(),
                lastPurchaseTs,
                updatedAtTs);
    }

    /**
     * Atomic increment of purchased chunks (transaction-safe)
     *
     * @param playerUuid Player's UUID
     * @param amount Number of chunks to add
     * @param cost Cost of the purchase
     * @throws SQLException if database operation fails
     */
    public void incrementPlayerChunks(UUID playerUuid, int amount, double cost)
            throws SQLException {
        String query =
                "INSERT INTO server_player_chunk_pool "
                        + "(player_uuid, purchased_chunks, total_spent, last_purchase, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE "
                        + "purchased_chunks = purchased_chunks + VALUES(purchased_chunks), "
                        + "total_spent = total_spent + VALUES(total_spent), "
                        + "last_purchase = VALUES(last_purchase), "
                        + "updated_at = VALUES(updated_at)";

        Timestamp now = Timestamp.from(java.time.Instant.now());

        executeUpdate(query, playerUuid.toString(), amount, cost, now, now);
    }

    /**
     * Load all player chunk pools (for startup cache)
     *
     * @return Map of player UUID to PlayerChunkPool
     * @throws SQLException if database operation fails
     */
    public Map<UUID, PlayerChunkPool> getAllPlayerChunkPools() throws SQLException {
        Map<UUID, PlayerChunkPool> pools = new HashMap<>();
        String query = "SELECT * FROM server_player_chunk_pool";

        return executeQuery(
                query,
                rs -> {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                        Timestamp lastPurchaseTs = rs.getTimestamp("last_purchase");
                        PlayerChunkPool pool =
                                new PlayerChunkPool(
                                        uuid,
                                        rs.getInt("purchased_chunks"),
                                        rs.getDouble("total_spent"),
                                        lastPurchaseTs != null ? lastPurchaseTs.toInstant() : null,
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getTimestamp("updated_at").toInstant());
                        pools.put(uuid, pool);
                    }
                    return pools;
                });
    }
}
