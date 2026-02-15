package net.serverplugins.claim.models;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Represents a saved claim configuration template. Templates allow players to save their claim
 * settings and apply them to other claims, providing a quick way to replicate configurations across
 * multiple claims.
 */
public class ClaimTemplate {

    private int id;
    private UUID ownerUuid;
    private String templateName;
    private String description;

    // Claim settings
    private boolean pvpEnabled;
    private boolean fireSpread;
    private boolean mobSpawning;
    private boolean explosionDamage;
    private boolean pistonPush;
    private boolean fluidFlow;
    private boolean leafDecay;
    private boolean cropGrowth;

    // Permissions and groups (stored as CSV/serialized strings)
    private String groupPermissions;
    private String customGroupsCsv;

    // Metadata
    private int timesUsed;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public ClaimTemplate() {
        // Default constructor
    }

    public ClaimTemplate(UUID ownerUuid, String templateName) {
        this.ownerUuid = ownerUuid;
        this.templateName = templateName;
        this.timesUsed = 0;
    }

    /**
     * Creates a template from an existing claim's settings. Captures all configurable settings
     * including flags and permissions.
     */
    public static ClaimTemplate fromClaim(Claim claim, String templateName, String description) {
        if (claim == null) {
            throw new IllegalArgumentException("Claim cannot be null");
        }

        ClaimTemplate template = new ClaimTemplate();
        template.setOwnerUuid(claim.getOwnerUuid());
        template.setTemplateName(templateName);
        template.setDescription(description);

        // Copy claim settings
        ClaimSettings settings = claim.getSettings();
        if (settings != null) {
            template.setPvpEnabled(settings.isPvpEnabled());
            template.setFireSpread(settings.isFireSpread());
            template.setMobSpawning(settings.isHostileSpawns());
            template.setExplosionDamage(settings.isExplosions());
            template.setLeafDecay(settings.isLeafDecay());
            // Note: Some settings may be WorldGuard flags, not in ClaimSettings
            template.setPistonPush(false); // Default for now
            template.setFluidFlow(true); // Default for now
            template.setCropGrowth(true); // Default for now
        }

        // Serialize group permissions if present
        if (claim.getGroupPermissions() != null) {
            template.setGroupPermissions(claim.getGroupPermissions().serialize());
        }

        // Serialize custom groups if present
        if (claim.hasCustomGroups()) {
            template.setCustomGroupsCsv(CustomGroup.serializeToCSV(claim.getCustomGroups()));
        }

        return template;
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    public void setPvpEnabled(boolean pvpEnabled) {
        this.pvpEnabled = pvpEnabled;
    }

    public boolean isFireSpread() {
        return fireSpread;
    }

    public boolean isFireSpreadEnabled() {
        return fireSpread;
    }

    public void setFireSpread(boolean fireSpread) {
        this.fireSpread = fireSpread;
    }

    public boolean isMobSpawning() {
        return mobSpawning;
    }

    public boolean isMobSpawningEnabled() {
        return mobSpawning;
    }

    public void setMobSpawning(boolean mobSpawning) {
        this.mobSpawning = mobSpawning;
    }

    public boolean isExplosionDamage() {
        return explosionDamage;
    }

    public boolean isExplosionDamageEnabled() {
        return explosionDamage;
    }

    public void setExplosionDamage(boolean explosionDamage) {
        this.explosionDamage = explosionDamage;
    }

    public boolean isPistonPush() {
        return pistonPush;
    }

    public void setPistonPush(boolean pistonPush) {
        this.pistonPush = pistonPush;
    }

    public boolean isFluidFlow() {
        return fluidFlow;
    }

    public void setFluidFlow(boolean fluidFlow) {
        this.fluidFlow = fluidFlow;
    }

    public boolean isLeafDecay() {
        return leafDecay;
    }

    public void setLeafDecay(boolean leafDecay) {
        this.leafDecay = leafDecay;
    }

    public boolean isCropGrowth() {
        return cropGrowth;
    }

    public void setCropGrowth(boolean cropGrowth) {
        this.cropGrowth = cropGrowth;
    }

    public String getGroupPermissions() {
        return groupPermissions;
    }

    public void setGroupPermissions(String groupPermissions) {
        this.groupPermissions = groupPermissions;
    }

    public String getCustomGroupsCsv() {
        return customGroupsCsv;
    }

    public void setCustomGroupsCsv(String customGroupsCsv) {
        this.customGroupsCsv = customGroupsCsv;
    }

    public int getTimesUsed() {
        return timesUsed;
    }

    public void setTimesUsed(int timesUsed) {
        this.timesUsed = timesUsed;
    }

    public void incrementTimesUsed() {
        this.timesUsed++;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "ClaimTemplate{"
                + "id="
                + id
                + ", ownerUuid="
                + ownerUuid
                + ", templateName='"
                + templateName
                + '\''
                + ", timesUsed="
                + timesUsed
                + '}';
    }
}
