package net.serverplugins.claim.models;

public class ClaimSettings {

    private boolean pvpEnabled;
    private boolean fireSpread;
    private boolean explosions;
    private boolean hostileSpawns;
    private boolean mobGriefing;
    private boolean passiveSpawns;
    private boolean cropTrampling;
    private boolean leafDecay;

    public ClaimSettings() {
        this.pvpEnabled = false;
        this.fireSpread = false;
        this.explosions = false;
        this.hostileSpawns = true;
        this.mobGriefing = false;
        this.passiveSpawns = true;
        this.cropTrampling = false;
        this.leafDecay = true; // Default: leaves decay naturally
    }

    public ClaimSettings(
            boolean pvpEnabled,
            boolean fireSpread,
            boolean explosions,
            boolean hostileSpawns,
            boolean mobGriefing,
            boolean passiveSpawns,
            boolean cropTrampling,
            boolean leafDecay) {
        this.pvpEnabled = pvpEnabled;
        this.fireSpread = fireSpread;
        this.explosions = explosions;
        this.hostileSpawns = hostileSpawns;
        this.mobGriefing = mobGriefing;
        this.passiveSpawns = passiveSpawns;
        this.cropTrampling = cropTrampling;
        this.leafDecay = leafDecay;
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

    public void setFireSpread(boolean fireSpread) {
        this.fireSpread = fireSpread;
    }

    public boolean isExplosions() {
        return explosions;
    }

    public void setExplosions(boolean explosions) {
        this.explosions = explosions;
    }

    public boolean isHostileSpawns() {
        return hostileSpawns;
    }

    public void setHostileSpawns(boolean hostileSpawns) {
        this.hostileSpawns = hostileSpawns;
    }

    public boolean isMobGriefing() {
        return mobGriefing;
    }

    public void setMobGriefing(boolean mobGriefing) {
        this.mobGriefing = mobGriefing;
    }

    public boolean isPassiveSpawns() {
        return passiveSpawns;
    }

    public void setPassiveSpawns(boolean passiveSpawns) {
        this.passiveSpawns = passiveSpawns;
    }

    public boolean isCropTrampling() {
        return cropTrampling;
    }

    public void setCropTrampling(boolean cropTrampling) {
        this.cropTrampling = cropTrampling;
    }

    public boolean isLeafDecay() {
        return leafDecay;
    }

    public void setLeafDecay(boolean leafDecay) {
        this.leafDecay = leafDecay;
    }

    public void toggle(SettingType type) {
        switch (type) {
            case PVP -> pvpEnabled = !pvpEnabled;
            case FIRE_SPREAD -> fireSpread = !fireSpread;
            case EXPLOSIONS -> explosions = !explosions;
            case HOSTILE_SPAWNS -> hostileSpawns = !hostileSpawns;
            case MOB_GRIEFING -> mobGriefing = !mobGriefing;
            case PASSIVE_SPAWNS -> passiveSpawns = !passiveSpawns;
            case CROP_TRAMPLING -> cropTrampling = !cropTrampling;
            case LEAF_DECAY -> leafDecay = !leafDecay;
        }
    }

    public boolean getValue(SettingType type) {
        return switch (type) {
            case PVP -> pvpEnabled;
            case FIRE_SPREAD -> fireSpread;
            case EXPLOSIONS -> explosions;
            case HOSTILE_SPAWNS -> hostileSpawns;
            case MOB_GRIEFING -> mobGriefing;
            case PASSIVE_SPAWNS -> passiveSpawns;
            case CROP_TRAMPLING -> cropTrampling;
            case LEAF_DECAY -> leafDecay;
        };
    }

    public enum SettingType {
        PVP("PvP", "Allow player vs player combat"),
        FIRE_SPREAD("Fire Spread", "Allow fire to spread"),
        EXPLOSIONS("Explosions", "Allow explosion damage"),
        HOSTILE_SPAWNS("Hostile Mobs", "Allow hostile mob spawning"),
        MOB_GRIEFING("Mob Griefing", "Allow mobs to grief"),
        PASSIVE_SPAWNS("Passive Mobs", "Allow passive mob spawning"),
        CROP_TRAMPLING("Crop Trampling", "Allow trampling farmland"),
        LEAF_DECAY("Leaf Decay", "Allow leaves to decay naturally");

        private final String displayName;
        private final String description;

        SettingType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }
}
