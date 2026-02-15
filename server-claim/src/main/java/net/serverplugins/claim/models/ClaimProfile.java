package net.serverplugins.claim.models;

import org.bukkit.Material;

public class ClaimProfile {

    private int id;
    private int claimId;
    private String name;
    private ProfileColor color;
    private Material icon;
    private String particleEffect;
    private boolean active;
    private int slotIndex;
    private ClaimSettings settings;
    private GroupPermissions groupPermissions;

    // Per-profile particle settings (v6.0)
    private DustEffect selectedDustEffect;
    private ProfileColor selectedProfileColor;
    private boolean particlesEnabled = true;
    private boolean staticParticleMode = false;

    public ClaimProfile(
            int id,
            int claimId,
            String name,
            ProfileColor color,
            String particleEffect,
            boolean active,
            int slotIndex) {
        this.id = id;
        this.claimId = claimId;
        this.name = name;
        this.color = color;
        this.icon = color.getGlassPaneMaterial();
        this.particleEffect = particleEffect;
        this.active = active;
        this.slotIndex = slotIndex;
        this.settings = new ClaimSettings();
        this.groupPermissions = new GroupPermissions(id);
    }

    public ClaimProfile(int claimId, String name, ProfileColor color, int slotIndex) {
        this.claimId = claimId;
        this.name = name;
        this.color = color;
        this.icon = color.getGlassPaneMaterial();
        this.particleEffect = "DUST";
        this.active = slotIndex == 0;
        this.slotIndex = slotIndex;
        this.settings = new ClaimSettings();
        this.groupPermissions = new GroupPermissions(0);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
        // Only create new GroupPermissions if null, don't overwrite existing permissions
        if (this.groupPermissions == null) {
            this.groupPermissions = new GroupPermissions(id);
        }
    }

    public int getClaimId() {
        return claimId;
    }

    public void setClaimId(int claimId) {
        this.claimId = claimId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProfileColor getColor() {
        return color;
    }

    public void setColor(ProfileColor color) {
        this.color = color;
    }

    public Material getIcon() {
        return icon;
    }

    public void setIcon(Material icon) {
        this.icon = icon;
    }

    public String getParticleEffect() {
        return particleEffect;
    }

    public void setParticleEffect(String particleEffect) {
        this.particleEffect = particleEffect;
    }

    public DustEffect getDustEffect() {
        if (particleEffect == null || particleEffect.isEmpty() || particleEffect.equals("DUST")) {
            return null; // No custom dust effect, use profile color
        }
        return DustEffect.fromString(particleEffect);
    }

    public void setDustEffect(DustEffect dustEffect) {
        if (dustEffect == null) {
            this.particleEffect = "DUST";
        } else {
            this.particleEffect = dustEffect.name();
        }
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public void setSlotIndex(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public ClaimSettings getSettings() {
        return settings;
    }

    public void setSettings(ClaimSettings settings) {
        this.settings = settings;
    }

    public GroupPermissions getGroupPermissions() {
        return groupPermissions;
    }

    public void setGroupPermissions(GroupPermissions groupPermissions) {
        this.groupPermissions = groupPermissions;
    }

    // Per-profile particle settings getters/setters
    public DustEffect getSelectedDustEffect() {
        return selectedDustEffect;
    }

    public void setSelectedDustEffect(DustEffect effect) {
        this.selectedDustEffect = effect;
    }

    public ProfileColor getSelectedProfileColor() {
        return selectedProfileColor;
    }

    public void setSelectedProfileColor(ProfileColor color) {
        this.selectedProfileColor = color;
    }

    public boolean isParticlesEnabled() {
        return particlesEnabled;
    }

    public void setParticlesEnabled(boolean enabled) {
        this.particlesEnabled = enabled;
    }

    public boolean isStaticParticleMode() {
        return staticParticleMode;
    }

    public void setStaticParticleMode(boolean staticMode) {
        this.staticParticleMode = staticMode;
    }
}
