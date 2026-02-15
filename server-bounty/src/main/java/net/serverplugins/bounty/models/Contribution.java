package net.serverplugins.bounty.models;

import java.time.Instant;
import java.util.UUID;

public class Contribution {

    private int id;
    private final int bountyId;
    private final UUID placerUuid;
    private final String placerName;
    private final double amount;
    private final double taxPaid;
    private final Instant placedAt;

    public Contribution(
            int bountyId, UUID placerUuid, String placerName, double amount, double taxPaid) {
        this.bountyId = bountyId;
        this.placerUuid = placerUuid;
        this.placerName = placerName;
        this.amount = amount;
        this.taxPaid = taxPaid;
        this.placedAt = Instant.now();
    }

    public Contribution(
            int id,
            int bountyId,
            UUID placerUuid,
            String placerName,
            double amount,
            double taxPaid,
            Instant placedAt) {
        this.id = id;
        this.bountyId = bountyId;
        this.placerUuid = placerUuid;
        this.placerName = placerName;
        this.amount = amount;
        this.taxPaid = taxPaid;
        this.placedAt = placedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getBountyId() {
        return bountyId;
    }

    public UUID getPlacerUuid() {
        return placerUuid;
    }

    public String getPlacerName() {
        return placerName;
    }

    public double getAmount() {
        return amount;
    }

    public double getTaxPaid() {
        return taxPaid;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    // Alias methods for backward compatibility
    public UUID getContributorUuid() {
        return placerUuid;
    }

    public String getContributorName() {
        return placerName;
    }

    public Instant getContributedAt() {
        return placedAt;
    }
}
