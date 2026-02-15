package net.serverplugins.claim.models;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

public class PlayerRewardsData {

    private final UUID uuid;
    private boolean particlesEnabled;
    private boolean staticParticleMode;
    private DustEffect selectedDustEffect;
    private ProfileColor selectedProfileColor;

    public PlayerRewardsData(UUID uuid) {
        this.uuid = uuid;
        this.particlesEnabled = true;
        this.staticParticleMode = true; // Default to static mode (no blinking)
        this.selectedDustEffect = null; // NULL means no global setting
        this.selectedProfileColor = null; // NULL means no global setting
    }

    public PlayerRewardsData(
            UUID uuid,
            boolean particlesEnabled,
            boolean staticParticleMode,
            DustEffect selectedDustEffect,
            ProfileColor selectedProfileColor) {
        this.uuid = uuid;
        this.particlesEnabled = particlesEnabled;
        this.staticParticleMode = staticParticleMode;
        this.selectedDustEffect =
                selectedDustEffect; // Allow NULL to distinguish "no setting" from "set to WHITE"
        this.selectedProfileColor =
                selectedProfileColor; // Allow NULL to distinguish "no setting" from "set to WHITE"
    }

    public UUID getUuid() {
        return uuid;
    }

    public boolean isParticlesEnabled() {
        return particlesEnabled;
    }

    public void setParticlesEnabled(boolean particlesEnabled) {
        this.particlesEnabled = particlesEnabled;
    }

    public boolean isStaticParticleMode() {
        return staticParticleMode;
    }

    public void setStaticParticleMode(boolean staticParticleMode) {
        this.staticParticleMode = staticParticleMode;
    }

    public DustEffect getSelectedDustEffect() {
        return selectedDustEffect;
    }

    public void setSelectedDustEffect(DustEffect selectedDustEffect) {
        this.selectedDustEffect = selectedDustEffect; // Allow NULL to represent "no global setting"
    }

    public ProfileColor getSelectedProfileColor() {
        return selectedProfileColor;
    }

    public void setSelectedProfileColor(ProfileColor selectedProfileColor) {
        this.selectedProfileColor =
                selectedProfileColor; // Allow NULL to represent "no global setting"
    }

    public Set<DustEffect> getUnlockedDustEffects(long playtimeMinutes) {
        Set<DustEffect> unlocked = EnumSet.noneOf(DustEffect.class);
        for (DustEffect effect : DustEffect.values()) {
            if (effect.isUnlockedFor(playtimeMinutes)) {
                unlocked.add(effect);
            }
        }
        return unlocked;
    }

    public Set<ProfileColor> getUnlockedProfileColors(long playtimeMinutes) {
        Set<ProfileColor> unlocked = EnumSet.noneOf(ProfileColor.class);
        for (ProfileColor color : ProfileColor.values()) {
            if (color.isUnlockedFor(playtimeMinutes)) {
                unlocked.add(color);
            }
        }
        return unlocked;
    }

    public DustEffect getNextLockedDustEffect(long playtimeMinutes) {
        DustEffect next = null;
        long minDiff = Long.MAX_VALUE;

        for (DustEffect effect : DustEffect.values()) {
            if (!effect.isUnlockedFor(playtimeMinutes)) {
                long diff = effect.getRequiredPlaytimeMinutes() - playtimeMinutes;
                if (diff < minDiff) {
                    minDiff = diff;
                    next = effect;
                }
            }
        }
        return next;
    }

    public ProfileColor getNextLockedProfileColor(long playtimeMinutes) {
        ProfileColor next = null;
        long minDiff = Long.MAX_VALUE;

        for (ProfileColor color : ProfileColor.values()) {
            if (!color.isUnlockedFor(playtimeMinutes)) {
                long diff = color.getRequiredPlaytimeMinutes() - playtimeMinutes;
                if (diff < minDiff) {
                    minDiff = diff;
                    next = color;
                }
            }
        }
        return next;
    }

    public int getUnlockedDustEffectCount(long playtimeMinutes) {
        return (int)
                java.util.Arrays.stream(DustEffect.values())
                        .filter(e -> e.isUnlockedFor(playtimeMinutes))
                        .count();
    }

    public int getUnlockedProfileColorCount(long playtimeMinutes) {
        return (int)
                java.util.Arrays.stream(ProfileColor.values())
                        .filter(c -> c.isUnlockedFor(playtimeMinutes))
                        .count();
    }
}
