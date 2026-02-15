package net.serverplugins.claim.models;

import java.time.Instant;
import java.util.UUID;

public class BankTransaction {

    public enum TransactionType {
        DEPOSIT("Deposit", true),
        WITHDRAW("Withdrawal", false),
        UPKEEP("Upkeep Payment", false),
        TAX("Tax", false),
        REFUND("Refund", true),
        NATION_TAX("Nation Tax", false);

        private final String displayName;
        private final boolean positive;

        TransactionType(String displayName, boolean positive) {
            this.displayName = displayName;
            this.positive = positive;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isPositive() {
            return positive;
        }
    }

    private int id;
    private int claimId;
    private UUID playerUuid;
    private TransactionType type;
    private double amount;
    private double balanceAfter;
    private String description;
    private Instant createdAt;

    public BankTransaction() {
        this.createdAt = Instant.now();
    }

    public BankTransaction(
            int claimId,
            UUID playerUuid,
            TransactionType type,
            double amount,
            double balanceAfter,
            String description) {
        this.claimId = claimId;
        this.playerUuid = playerUuid;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public BankTransaction(
            int id,
            int claimId,
            UUID playerUuid,
            TransactionType type,
            double amount,
            double balanceAfter,
            String description,
            Instant createdAt) {
        this.id = id;
        this.claimId = claimId;
        this.playerUuid = playerUuid;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.createdAt = createdAt;
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

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public double getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(double balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getFormattedAmount() {
        String prefix = type.isPositive() ? "+" : "-";
        return prefix + "$" + String.format("%.2f", Math.abs(amount));
    }
}
