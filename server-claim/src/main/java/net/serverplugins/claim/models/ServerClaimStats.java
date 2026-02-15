package net.serverplugins.claim.models;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Server-wide claim statistics. Provides comprehensive metrics about all claims on the server. */
public class ServerClaimStats {

    private Instant calculatedAt;

    // Global counts
    private int totalClaims;
    private int totalChunks;
    private int totalNations;
    private int totalPlayers;

    // Economy metrics
    private double totalBankMoney;
    private double averageBankBalance;

    // Top lists
    private List<TopOwner> topOwners;
    private List<TopClaim> wealthiestClaims;

    // Distribution
    private Map<String, Integer> claimsByWorld;
    private Map<String, Integer> chunksByWorld;

    // Upkeep metrics
    private double totalUpkeepCosts;
    private int claimsInGracePeriod;
    private int claimsAtRisk;

    // Activity metrics (last 30 days)
    private int chunksClaimedLastMonth;
    private int chunksUnclaimedLastMonth;
    private int newClaimsLastMonth;
    private int deletedClaimsLastMonth;

    public ServerClaimStats() {
        this.calculatedAt = Instant.now();
    }

    // Getters and setters
    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(Instant calculatedAt) {
        this.calculatedAt = calculatedAt;
    }

    public int getTotalClaims() {
        return totalClaims;
    }

    public void setTotalClaims(int totalClaims) {
        this.totalClaims = totalClaims;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public int getTotalNations() {
        return totalNations;
    }

    public void setTotalNations(int totalNations) {
        this.totalNations = totalNations;
    }

    public int getTotalPlayers() {
        return totalPlayers;
    }

    public void setTotalPlayers(int totalPlayers) {
        this.totalPlayers = totalPlayers;
    }

    public double getTotalBankMoney() {
        return totalBankMoney;
    }

    public void setTotalBankMoney(double totalBankMoney) {
        this.totalBankMoney = totalBankMoney;
    }

    public double getAverageBankBalance() {
        return averageBankBalance;
    }

    public void setAverageBankBalance(double averageBankBalance) {
        this.averageBankBalance = averageBankBalance;
    }

    public List<TopOwner> getTopOwners() {
        return topOwners;
    }

    public void setTopOwners(List<TopOwner> topOwners) {
        this.topOwners = topOwners;
    }

    public List<TopClaim> getWealthiestClaims() {
        return wealthiestClaims;
    }

    public void setWealthiestClaims(List<TopClaim> wealthiestClaims) {
        this.wealthiestClaims = wealthiestClaims;
    }

    public Map<String, Integer> getClaimsByWorld() {
        return claimsByWorld;
    }

    public void setClaimsByWorld(Map<String, Integer> claimsByWorld) {
        this.claimsByWorld = claimsByWorld;
    }

    public Map<String, Integer> getChunksByWorld() {
        return chunksByWorld;
    }

    public void setChunksByWorld(Map<String, Integer> chunksByWorld) {
        this.chunksByWorld = chunksByWorld;
    }

    public double getTotalUpkeepCosts() {
        return totalUpkeepCosts;
    }

    public void setTotalUpkeepCosts(double totalUpkeepCosts) {
        this.totalUpkeepCosts = totalUpkeepCosts;
    }

    public int getClaimsInGracePeriod() {
        return claimsInGracePeriod;
    }

    public void setClaimsInGracePeriod(int claimsInGracePeriod) {
        this.claimsInGracePeriod = claimsInGracePeriod;
    }

    public int getClaimsAtRisk() {
        return claimsAtRisk;
    }

    public void setClaimsAtRisk(int claimsAtRisk) {
        this.claimsAtRisk = claimsAtRisk;
    }

    public int getChunksClaimedLastMonth() {
        return chunksClaimedLastMonth;
    }

    public void setChunksClaimedLastMonth(int chunksClaimedLastMonth) {
        this.chunksClaimedLastMonth = chunksClaimedLastMonth;
    }

    public int getChunksUnclaimedLastMonth() {
        return chunksUnclaimedLastMonth;
    }

    public void setChunksUnclaimedLastMonth(int chunksUnclaimedLastMonth) {
        this.chunksUnclaimedLastMonth = chunksUnclaimedLastMonth;
    }

    public int getNewClaimsLastMonth() {
        return newClaimsLastMonth;
    }

    public void setNewClaimsLastMonth(int newClaimsLastMonth) {
        this.newClaimsLastMonth = newClaimsLastMonth;
    }

    public int getDeletedClaimsLastMonth() {
        return deletedClaimsLastMonth;
    }

    public void setDeletedClaimsLastMonth(int deletedClaimsLastMonth) {
        this.deletedClaimsLastMonth = deletedClaimsLastMonth;
    }

    /** Top claim owner by chunk count. */
    public static class TopOwner {
        private final String playerName;
        private final int chunkCount;
        private final int claimCount;

        public TopOwner(String playerName, int chunkCount, int claimCount) {
            this.playerName = playerName;
            this.chunkCount = chunkCount;
            this.claimCount = claimCount;
        }

        public String getPlayerName() {
            return playerName;
        }

        public int getChunkCount() {
            return chunkCount;
        }

        public int getClaimCount() {
            return claimCount;
        }
    }

    /** Wealthiest claim by bank balance. */
    public static class TopClaim {
        private final String claimName;
        private final String ownerName;
        private final double balance;

        public TopClaim(String claimName, String ownerName, double balance) {
            this.claimName = claimName;
            this.ownerName = ownerName;
            this.balance = balance;
        }

        public String getClaimName() {
            return claimName;
        }

        public String getOwnerName() {
            return ownerName;
        }

        public double getBalance() {
            return balance;
        }
    }
}
