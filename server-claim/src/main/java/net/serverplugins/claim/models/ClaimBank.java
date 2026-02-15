package net.serverplugins.claim.models;

import java.time.Instant;

public class ClaimBank {

    private int claimId;
    private double balance;
    private double minimumBalanceWarning;
    private Instant lastUpkeepPayment;
    private Instant nextUpkeepDue;
    private Instant gracePeriodStart;

    public ClaimBank(int claimId) {
        this.claimId = claimId;
        this.balance = 0.0;
        this.minimumBalanceWarning = 100.0;
        this.lastUpkeepPayment = null;
        this.nextUpkeepDue = null;
        this.gracePeriodStart = null;
    }

    public ClaimBank(
            int claimId,
            double balance,
            double minimumBalanceWarning,
            Instant lastUpkeepPayment,
            Instant nextUpkeepDue,
            Instant gracePeriodStart) {
        this.claimId = claimId;
        this.balance = balance;
        this.minimumBalanceWarning = minimumBalanceWarning;
        this.lastUpkeepPayment = lastUpkeepPayment;
        this.nextUpkeepDue = nextUpkeepDue;
        this.gracePeriodStart = gracePeriodStart;
    }

    public int getClaimId() {
        return claimId;
    }

    public void setClaimId(int claimId) {
        this.claimId = claimId;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public double getMinimumBalanceWarning() {
        return minimumBalanceWarning;
    }

    public void setMinimumBalanceWarning(double minimumBalanceWarning) {
        this.minimumBalanceWarning = minimumBalanceWarning;
    }

    public Instant getLastUpkeepPayment() {
        return lastUpkeepPayment;
    }

    public void setLastUpkeepPayment(Instant lastUpkeepPayment) {
        this.lastUpkeepPayment = lastUpkeepPayment;
    }

    public Instant getNextUpkeepDue() {
        return nextUpkeepDue;
    }

    public void setNextUpkeepDue(Instant nextUpkeepDue) {
        this.nextUpkeepDue = nextUpkeepDue;
    }

    public Instant getGracePeriodStart() {
        return gracePeriodStart;
    }

    public void setGracePeriodStart(Instant gracePeriodStart) {
        this.gracePeriodStart = gracePeriodStart;
    }

    public boolean isInGracePeriod() {
        return gracePeriodStart != null;
    }

    public boolean isLowBalance() {
        return balance < minimumBalanceWarning;
    }

    public boolean canAffordUpkeep(double upkeepCost) {
        return balance >= upkeepCost;
    }

    public void deposit(double amount) {
        if (amount > 0) {
            this.balance += amount;
        }
    }

    public boolean withdraw(double amount) {
        if (amount > 0 && balance >= amount) {
            this.balance -= amount;
            return true;
        }
        return false;
    }

    /**
     * Pay upkeep from the bank balance.
     *
     * @param amount The upkeep amount to pay
     * @return true if payment was successful, false if insufficient balance
     */
    public boolean payUpkeep(double amount) {
        if (amount <= 0) {
            return true; // Nothing to pay
        }
        if (this.balance < amount) {
            return false; // Insufficient balance
        }
        this.balance -= amount;
        this.lastUpkeepPayment = Instant.now();
        if (this.gracePeriodStart != null && this.balance >= 0) {
            this.gracePeriodStart = null;
        }
        return true;
    }
}
