package net.serverplugins.bounty.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class Bounty {

    private int id;
    private final UUID targetUuid;
    private final String targetName;
    private double totalAmount;
    private final Instant createdAt;
    private Instant updatedAt;
    private List<Contribution> contributions;

    public Bounty(UUID targetUuid, String targetName) {
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.totalAmount = 0.0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.contributions = new ArrayList<>();
    }

    public Bounty(
            int id,
            UUID targetUuid,
            String targetName,
            double totalAmount,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.contributions = new ArrayList<>();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<Contribution> getContributions() {
        return contributions;
    }

    public void setContributions(List<Contribution> contributions) {
        this.contributions = contributions;
    }

    public void addContribution(Contribution contribution) {
        this.contributions.add(contribution);
        this.totalAmount += contribution.getAmount();
        this.updatedAt = Instant.now();
    }

    public Contribution getTopContributor() {
        if (contributions == null || contributions.isEmpty()) {
            return null;
        }
        return contributions.stream()
                .max(Comparator.comparingDouble(Contribution::getAmount))
                .orElse(null);
    }

    public int getContributorCount() {
        if (contributions == null) {
            return 0;
        }
        return (int)
                contributions.stream().map(Contribution::getContributorUuid).distinct().count();
    }
}
