package net.serverplugins.claim.models;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;

public class ClaimWarp {

    private final int claimId;
    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private WarpVisibility visibility;
    private double visitCost;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;

    private final Set<UUID> allowlist = new HashSet<>();
    private final Set<UUID> blocklist = new HashSet<>();

    public ClaimWarp(int claimId, Location location) {
        this.claimId = claimId;
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
        this.visibility = WarpVisibility.PRIVATE;
        this.visitCost = 0;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public ClaimWarp(
            int claimId,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            WarpVisibility visibility,
            double visitCost,
            String description,
            Instant createdAt,
            Instant updatedAt) {
        this.claimId = claimId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.visibility = visibility;
        this.visitCost = visitCost;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    public boolean canVisit(UUID visitorUuid, UUID ownerUuid, Set<UUID> trustedPlayers) {
        if (visitorUuid.equals(ownerUuid)) {
            return true;
        }

        if (blocklist.contains(visitorUuid)) {
            return false;
        }

        if (trustedPlayers != null && trustedPlayers.contains(visitorUuid)) {
            return true;
        }

        return switch (visibility) {
            case PUBLIC -> true;
            case ALLOWLIST -> allowlist.contains(visitorUuid);
            case PRIVATE -> false;
        };
    }

    public int getClaimId() {
        return claimId;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public WarpVisibility getVisibility() {
        return visibility;
    }

    public double getVisitCost() {
        return visitCost;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Set<UUID> getAllowlist() {
        return allowlist;
    }

    public Set<UUID> getBlocklist() {
        return blocklist;
    }

    public void setLocation(Location location) {
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
        this.updatedAt = Instant.now();
    }

    public void setVisibility(WarpVisibility visibility) {
        this.visibility = visibility;
        this.updatedAt = Instant.now();
    }

    public void setVisitCost(double visitCost) {
        this.visitCost = visitCost;
        this.updatedAt = Instant.now();
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public void addToAllowlist(UUID uuid) {
        allowlist.add(uuid);
    }

    public void removeFromAllowlist(UUID uuid) {
        allowlist.remove(uuid);
    }

    public void addToBlocklist(UUID uuid) {
        blocklist.add(uuid);
    }

    public void removeFromBlocklist(UUID uuid) {
        blocklist.remove(uuid);
    }
}
