package net.serverplugins.claim.models;

import java.util.List;
import java.util.UUID;

/**
 * Statistics for a player's claims. Provides comprehensive metrics about a player's claiming
 * activity.
 */
public class PlayerClaimStats {

    private final UUID playerUuid;
    private final String playerName;

    // Ownership stats
    private int totalClaims;
    private int totalChunks;
    private int totalPurchasedChunks;
    private double totalBankMoney;

    // Claim details
    private int mostValuableClaimId;
    private String mostValuableClaimName;
    private double mostValuableClaimBalance;

    private int largestClaimId;
    private String largestClaimName;
    private int largestClaimChunks;

    // Nation membership
    private int nationsJoined;
    private List<String> nationNames;

    // Calculated metrics
    private double averageClaimSize;
    private String oldestClaimName;
    private String newestClaimName;

    public PlayerClaimStats(UUID playerUuid, String playerName) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
    }

    // Getters and setters
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
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

    public int getTotalPurchasedChunks() {
        return totalPurchasedChunks;
    }

    public void setTotalPurchasedChunks(int totalPurchasedChunks) {
        this.totalPurchasedChunks = totalPurchasedChunks;
    }

    public double getTotalBankMoney() {
        return totalBankMoney;
    }

    public void setTotalBankMoney(double totalBankMoney) {
        this.totalBankMoney = totalBankMoney;
    }

    public int getMostValuableClaimId() {
        return mostValuableClaimId;
    }

    public void setMostValuableClaimId(int mostValuableClaimId) {
        this.mostValuableClaimId = mostValuableClaimId;
    }

    public String getMostValuableClaimName() {
        return mostValuableClaimName;
    }

    public void setMostValuableClaimName(String mostValuableClaimName) {
        this.mostValuableClaimName = mostValuableClaimName;
    }

    public double getMostValuableClaimBalance() {
        return mostValuableClaimBalance;
    }

    public void setMostValuableClaimBalance(double mostValuableClaimBalance) {
        this.mostValuableClaimBalance = mostValuableClaimBalance;
    }

    public int getLargestClaimId() {
        return largestClaimId;
    }

    public void setLargestClaimId(int largestClaimId) {
        this.largestClaimId = largestClaimId;
    }

    public String getLargestClaimName() {
        return largestClaimName;
    }

    public void setLargestClaimName(String largestClaimName) {
        this.largestClaimName = largestClaimName;
    }

    public int getLargestClaimChunks() {
        return largestClaimChunks;
    }

    public void setLargestClaimChunks(int largestClaimChunks) {
        this.largestClaimChunks = largestClaimChunks;
    }

    public int getNationsJoined() {
        return nationsJoined;
    }

    public void setNationsJoined(int nationsJoined) {
        this.nationsJoined = nationsJoined;
    }

    public List<String> getNationNames() {
        return nationNames;
    }

    public void setNationNames(List<String> nationNames) {
        this.nationNames = nationNames;
    }

    public double getAverageClaimSize() {
        return averageClaimSize;
    }

    public void setAverageClaimSize(double averageClaimSize) {
        this.averageClaimSize = averageClaimSize;
    }

    public String getOldestClaimName() {
        return oldestClaimName;
    }

    public void setOldestClaimName(String oldestClaimName) {
        this.oldestClaimName = oldestClaimName;
    }

    public String getNewestClaimName() {
        return newestClaimName;
    }

    public void setNewestClaimName(String newestClaimName) {
        this.newestClaimName = newestClaimName;
    }
}
