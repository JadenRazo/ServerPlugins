package net.serverplugins.claim.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.serverplugins.api.database.Database;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.PlayerClaimStats;
import net.serverplugins.claim.models.ServerClaimStats;
import net.serverplugins.claim.repository.ClaimRepository;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Manager for calculating and caching claim statistics. Server statistics are cached for 5 minutes
 * to avoid expensive calculations.
 */
public class ClaimStatsManager {

    private final ServerClaim plugin;
    private final Database database;
    private final ClaimRepository claimRepository;
    private final Logger logger;
    private final Gson gson;

    // Cache for server statistics (refreshes every 5 minutes)
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000; // 5 minutes
    private final Map<String, CachedStat> statsCache = new ConcurrentHashMap<>();

    public ClaimStatsManager(
            ServerClaim plugin, Database database, ClaimRepository claimRepository) {
        this.plugin = plugin;
        this.database = database;
        this.claimRepository = claimRepository;
        this.logger = plugin.getLogger();
        this.gson = new GsonBuilder().create();
    }

    /**
     * Get player statistics. Calculates on-demand (no caching for player stats as they are
     * player-specific).
     */
    public PlayerClaimStats getPlayerStats(UUID playerUuid) {
        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUuid);
            String playerName = player.getName() != null ? player.getName() : "Unknown";

            PlayerClaimStats stats = new PlayerClaimStats(playerUuid, playerName);

            // Get all claims by player
            List<Claim> playerClaims = claimRepository.getClaimsByOwner(playerUuid);
            stats.setTotalClaims(playerClaims.size());

            if (playerClaims.isEmpty()) {
                return stats;
            }

            // Calculate total chunks and purchased chunks
            int totalChunks = 0;
            int totalPurchased = 0;
            double totalBankMoney = 0.0;

            Claim largestClaim = null;
            Claim wealthiestClaim = null;
            double maxBalance = 0.0;
            int maxChunks = 0;

            for (Claim claim : playerClaims) {
                int chunkCount = claim.getChunks().size();
                totalChunks += chunkCount;
                totalPurchased += claim.getPurchasedChunks();

                // Get bank balance
                double balance = getBankBalance(claim.getId());
                totalBankMoney += balance;

                // Track largest claim
                if (chunkCount > maxChunks) {
                    maxChunks = chunkCount;
                    largestClaim = claim;
                }

                // Track wealthiest claim
                if (balance > maxBalance) {
                    maxBalance = balance;
                    wealthiestClaim = claim;
                }
            }

            stats.setTotalChunks(totalChunks);
            stats.setTotalPurchasedChunks(totalPurchased);
            stats.setTotalBankMoney(totalBankMoney);

            // Set largest claim info
            if (largestClaim != null) {
                stats.setLargestClaimId(largestClaim.getId());
                stats.setLargestClaimName(largestClaim.getName());
                stats.setLargestClaimChunks(maxChunks);
            }

            // Set wealthiest claim info
            if (wealthiestClaim != null) {
                stats.setMostValuableClaimId(wealthiestClaim.getId());
                stats.setMostValuableClaimName(wealthiestClaim.getName());
                stats.setMostValuableClaimBalance(maxBalance);
            }

            // Calculate average claim size
            if (!playerClaims.isEmpty()) {
                stats.setAverageClaimSize((double) totalChunks / playerClaims.size());
            }

            // Get nation memberships
            List<String> nationNames = getNationNamesForPlayer(playerUuid);
            stats.setNationNames(nationNames);
            stats.setNationsJoined(nationNames.size());

            // Get oldest and newest claims
            if (!playerClaims.isEmpty()) {
                playerClaims.sort(Comparator.comparing(c -> c.getId())); // Oldest by ID
                stats.setOldestClaimName(playerClaims.get(0).getName());
                stats.setNewestClaimName(playerClaims.get(playerClaims.size() - 1).getName());
            }

            return stats;

        } catch (Exception e) {
            logger.severe(
                    "Error calculating player stats for " + playerUuid + ": " + e.getMessage());
            e.printStackTrace();
            return new PlayerClaimStats(playerUuid, "Unknown");
        }
    }

    /**
     * Get server statistics with caching. Cache refreshes every 5 minutes to avoid expensive
     * calculations.
     */
    public ServerClaimStats getServerStats() {
        return getCachedServerStats();
    }

    /** Get top owners by chunk count. */
    public List<ServerClaimStats.TopOwner> getTopOwners(int limit) {
        try {
            return database.query(
                    "SELECT pc.username, "
                            + "       SUM(chunk_count) as total_chunks, "
                            + "       COUNT(DISTINCT c.id) as claim_count "
                            + "FROM server_claims c "
                            + "JOIN server_player_claims pc ON c.owner_uuid = pc.uuid "
                            + "LEFT JOIN (SELECT claim_id, COUNT(*) as chunk_count FROM server_chunks GROUP BY claim_id) chunks "
                            + "ON c.id = chunks.claim_id "
                            + "GROUP BY pc.uuid, pc.username "
                            + "ORDER BY total_chunks DESC "
                            + "LIMIT ?",
                    rs -> {
                        List<ServerClaimStats.TopOwner> owners = new ArrayList<>();
                        while (rs.next()) {
                            owners.add(
                                    new ServerClaimStats.TopOwner(
                                            rs.getString("username"),
                                            rs.getInt("total_chunks"),
                                            rs.getInt("claim_count")));
                        }
                        return owners;
                    },
                    limit);
        } catch (Exception e) {
            logger.severe("Error getting top owners: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /** Get wealthiest claims by bank balance. */
    public List<ServerClaimStats.TopClaim> getWealthiestClaims(int limit) {
        try {
            return database.query(
                    "SELECT c.name as claim_name, "
                            + "       pc.username as owner_name, "
                            + "       b.balance "
                            + "FROM server_claims c "
                            + "JOIN server_player_claims pc ON c.owner_uuid = pc.uuid "
                            + "LEFT JOIN server_claim_banks b ON c.id = b.claim_id "
                            + "WHERE b.balance IS NOT NULL "
                            + "ORDER BY b.balance DESC "
                            + "LIMIT ?",
                    rs -> {
                        List<ServerClaimStats.TopClaim> claims = new ArrayList<>();
                        while (rs.next()) {
                            claims.add(
                                    new ServerClaimStats.TopClaim(
                                            rs.getString("claim_name"),
                                            rs.getString("owner_name"),
                                            rs.getDouble("balance")));
                        }
                        return claims;
                    },
                    limit);
        } catch (Exception e) {
            logger.severe("Error getting wealthiest claims: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /** Get activity metrics for the last N days. */
    public ActivityMetrics getActivityMetrics(int days) {
        try {
            Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);

            ActivityMetrics metrics = new ActivityMetrics();

            // Count chunk operations from audit log
            Integer chunksAdded =
                    database.query(
                            "SELECT COUNT(*) as count FROM server_claim_audit_log "
                                    + "WHERE activity_type = 'CHUNK_PURCHASE' AND timestamp >= ?",
                            rs -> rs.next() ? rs.getInt("count") : 0,
                            java.sql.Timestamp.from(cutoff));
            metrics.setChunksClaimed(chunksAdded != null ? chunksAdded : 0);

            Integer chunksRemoved =
                    database.query(
                            "SELECT COUNT(*) as count FROM server_claim_audit_log "
                                    + "WHERE activity_type = 'CHUNK_UNCLAIM' AND timestamp >= ?",
                            rs -> rs.next() ? rs.getInt("count") : 0,
                            java.sql.Timestamp.from(cutoff));
            metrics.setChunksUnclaimed(chunksRemoved != null ? chunksRemoved : 0);

            Integer membersAdded =
                    database.query(
                            "SELECT COUNT(*) as count FROM server_claim_audit_log "
                                    + "WHERE activity_type = 'MEMBER_ADDED' AND timestamp >= ?",
                            rs -> rs.next() ? rs.getInt("count") : 0,
                            java.sql.Timestamp.from(cutoff));
            metrics.setMembersAdded(membersAdded != null ? membersAdded : 0);

            Double totalDeposits =
                    database.query(
                            "SELECT SUM(amount) as total FROM server_claim_audit_log "
                                    + "WHERE activity_type = 'BANK_DEPOSIT' AND timestamp >= ?",
                            rs -> rs.next() ? rs.getDouble("total") : 0.0,
                            java.sql.Timestamp.from(cutoff));
            metrics.setTotalDeposits(totalDeposits != null ? totalDeposits : 0.0);

            Double totalWithdrawals =
                    database.query(
                            "SELECT SUM(amount) as total FROM server_claim_audit_log "
                                    + "WHERE activity_type = 'BANK_WITHDRAW' AND timestamp >= ?",
                            rs -> rs.next() ? rs.getDouble("total") : 0.0,
                            java.sql.Timestamp.from(cutoff));
            metrics.setTotalWithdrawals(totalWithdrawals != null ? totalWithdrawals : 0.0);

            return metrics;

        } catch (Exception e) {
            logger.severe("Error calculating activity metrics: " + e.getMessage());
            e.printStackTrace();
            return new ActivityMetrics();
        }
    }

    /** Force refresh server statistics cache. */
    public void refreshServerStats() {
        statsCache.remove("SERVER_OVERVIEW");
        getServerStats();
    }

    // Private helper methods

    private ServerClaimStats getCachedServerStats() {
        CachedStat cached = statsCache.get("SERVER_OVERVIEW");

        if (cached != null && !cached.isExpired()) {
            return gson.fromJson(cached.data, ServerClaimStats.class);
        }

        // Calculate new stats
        ServerClaimStats stats = calculateServerStats();

        // Store in cache
        statsCache.put("SERVER_OVERVIEW", new CachedStat(gson.toJson(stats)));

        return stats;
    }

    private ServerClaimStats calculateServerStats() {
        try {
            ServerClaimStats stats = new ServerClaimStats();

            // Get total claims
            Integer totalClaims =
                    database.query(
                            "SELECT COUNT(*) as count FROM server_claims",
                            rs -> rs.next() ? rs.getInt("count") : 0);
            stats.setTotalClaims(totalClaims != null ? totalClaims : 0);

            // Get total chunks
            Integer totalChunks =
                    database.query(
                            "SELECT COUNT(*) as count FROM server_chunks",
                            rs -> rs.next() ? rs.getInt("count") : 0);
            stats.setTotalChunks(totalChunks != null ? totalChunks : 0);

            // Get total nations
            Integer totalNations =
                    database.query(
                            "SELECT COUNT(*) as count FROM server_nations",
                            rs -> rs.next() ? rs.getInt("count") : 0);
            stats.setTotalNations(totalNations != null ? totalNations : 0);

            // Get unique players with claims
            Integer totalPlayers =
                    database.query(
                            "SELECT COUNT(DISTINCT owner_uuid) as count FROM server_claims",
                            rs -> rs.next() ? rs.getInt("count") : 0);
            stats.setTotalPlayers(totalPlayers != null ? totalPlayers : 0);

            // Get total bank money
            Double totalBankMoney =
                    database.query(
                            "SELECT SUM(balance) as total FROM server_claim_banks",
                            rs -> rs.next() ? rs.getDouble("total") : 0.0);
            stats.setTotalBankMoney(totalBankMoney != null ? totalBankMoney : 0.0);

            // Calculate average bank balance
            if (stats.getTotalClaims() > 0) {
                stats.setAverageBankBalance(stats.getTotalBankMoney() / stats.getTotalClaims());
            }

            // Get top owners
            stats.setTopOwners(getTopOwners(10));

            // Get wealthiest claims
            stats.setWealthiestClaims(getWealthiestClaims(10));

            // Get claims by world
            Map<String, Integer> claimsByWorld = getClaimsByWorld();
            stats.setClaimsByWorld(claimsByWorld);

            // Get chunks by world
            Map<String, Integer> chunksByWorld = getChunksByWorld();
            stats.setChunksByWorld(chunksByWorld);

            // Get upkeep metrics
            calculateUpkeepMetrics(stats);

            // Get activity metrics (last 30 days)
            ActivityMetrics activity = getActivityMetrics(30);
            stats.setChunksClaimedLastMonth(activity.getChunksClaimed());
            stats.setChunksUnclaimedLastMonth(activity.getChunksUnclaimed());

            return stats;

        } catch (Exception e) {
            logger.severe("Error calculating server statistics: " + e.getMessage());
            e.printStackTrace();
            return new ServerClaimStats();
        }
    }

    private Map<String, Integer> getClaimsByWorld() {
        try {
            return database.query(
                    "SELECT world, COUNT(*) as count FROM server_claims GROUP BY world",
                    rs -> {
                        Map<String, Integer> map = new HashMap<>();
                        while (rs.next()) {
                            map.put(rs.getString("world"), rs.getInt("count"));
                        }
                        return map;
                    });
        } catch (Exception e) {
            logger.severe("Error getting claims by world: " + e.getMessage());
            return new HashMap<>();
        }
    }

    private Map<String, Integer> getChunksByWorld() {
        try {
            return database.query(
                    "SELECT world, COUNT(*) as count FROM server_chunks GROUP BY world",
                    rs -> {
                        Map<String, Integer> map = new HashMap<>();
                        while (rs.next()) {
                            map.put(rs.getString("world"), rs.getInt("count"));
                        }
                        return map;
                    });
        } catch (Exception e) {
            logger.severe("Error getting chunks by world: " + e.getMessage());
            return new HashMap<>();
        }
    }

    private void calculateUpkeepMetrics(ServerClaimStats stats) {
        try {
            // Get claims in grace period
            Integer inGracePeriod =
                    database.query(
                            "SELECT COUNT(*) as count FROM server_claim_banks "
                                    + "WHERE grace_period_start IS NOT NULL",
                            rs -> rs.next() ? rs.getInt("count") : 0);
            stats.setClaimsInGracePeriod(inGracePeriod != null ? inGracePeriod : 0);

            // Get claims at risk (negative balance or in grace period)
            Integer atRisk =
                    database.query(
                            "SELECT COUNT(*) as count FROM server_claim_banks "
                                    + "WHERE balance < 0 OR grace_period_start IS NOT NULL",
                            rs -> rs.next() ? rs.getInt("count") : 0);
            stats.setClaimsAtRisk(atRisk != null ? atRisk : 0);

            // Calculate total upkeep costs (estimated)
            // This is a simplified calculation - actual upkeep varies
            Double totalUpkeep =
                    database.query(
                            "SELECT SUM(u.cost_per_chunk * chunk_count) as total "
                                    + "FROM server_claim_upkeep u "
                                    + "JOIN (SELECT claim_id, COUNT(*) as chunk_count FROM server_chunks GROUP BY claim_id) c "
                                    + "ON u.claim_id = c.claim_id",
                            rs -> rs.next() ? rs.getDouble("total") : 0.0);
            stats.setTotalUpkeepCosts(totalUpkeep != null ? totalUpkeep : 0.0);

        } catch (Exception e) {
            logger.severe("Error calculating upkeep metrics: " + e.getMessage());
        }
    }

    private double getBankBalance(int claimId) {
        try {
            Double balance =
                    database.query(
                            "SELECT balance FROM server_claim_banks WHERE claim_id = ?",
                            rs -> rs.next() ? rs.getDouble("balance") : 0.0,
                            claimId);
            return balance != null ? balance : 0.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private List<String> getNationNamesForPlayer(UUID playerUuid) {
        try {
            // Get all claims owned by player that are in nations
            return database.query(
                    "SELECT DISTINCT n.name "
                            + "FROM server_claims c "
                            + "JOIN server_nation_members nm ON c.id = nm.claim_id "
                            + "JOIN server_nations n ON nm.nation_id = n.id "
                            + "WHERE c.owner_uuid = ?",
                    rs -> {
                        List<String> names = new ArrayList<>();
                        while (rs.next()) {
                            names.add(rs.getString("name"));
                        }
                        return names;
                    },
                    playerUuid.toString());
        } catch (Exception e) {
            logger.severe("Error getting nation names for player: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /** Cached statistics entry. */
    private static class CachedStat {
        final String data;
        final long timestamp;

        CachedStat(String data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION_MS;
        }
    }

    /** Activity metrics data class. */
    public static class ActivityMetrics {
        private int chunksClaimed;
        private int chunksUnclaimed;
        private int membersAdded;
        private double totalDeposits;
        private double totalWithdrawals;

        public int getChunksClaimed() {
            return chunksClaimed;
        }

        public void setChunksClaimed(int chunksClaimed) {
            this.chunksClaimed = chunksClaimed;
        }

        public int getChunksUnclaimed() {
            return chunksUnclaimed;
        }

        public void setChunksUnclaimed(int chunksUnclaimed) {
            this.chunksUnclaimed = chunksUnclaimed;
        }

        public int getMembersAdded() {
            return membersAdded;
        }

        public void setMembersAdded(int membersAdded) {
            this.membersAdded = membersAdded;
        }

        public double getTotalDeposits() {
            return totalDeposits;
        }

        public void setTotalDeposits(double totalDeposits) {
            this.totalDeposits = totalDeposits;
        }

        public double getTotalWithdrawals() {
            return totalWithdrawals;
        }

        public void setTotalWithdrawals(double totalWithdrawals) {
            this.totalWithdrawals = totalWithdrawals;
        }
    }
}
