package net.serverplugins.claim.models;

import java.time.Instant;

public class NationRelation {

    public enum RelationType {
        NEUTRAL("Neutral", "<gray>", false, false),
        ALLY("Ally", "<green>", false, true),
        ENEMY("Enemy", "<red>", false, false),
        AT_WAR("At War", "<dark_red>", true, false),
        TRUCE("Truce", "<yellow>", false, false);

        private final String displayName;
        private final String colorTag;
        private final boolean allowsPvp;
        private final boolean friendlyFire;

        RelationType(String displayName, String colorTag, boolean allowsPvp, boolean friendlyFire) {
            this.displayName = displayName;
            this.colorTag = colorTag;
            this.allowsPvp = allowsPvp;
            this.friendlyFire = friendlyFire;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getColorTag() {
            return colorTag;
        }

        public boolean allowsPvp() {
            return allowsPvp;
        }

        public boolean hasFriendlyFire() {
            return friendlyFire;
        }

        public String getColored() {
            return colorTag + displayName + "</color>";
        }

        public static RelationType fromString(String name) {
            try {
                return RelationType.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                return NEUTRAL;
            }
        }
    }

    private int nationId;
    private int targetNationId;
    private RelationType relationType;
    private Instant establishedAt;

    public NationRelation() {
        this.relationType = RelationType.NEUTRAL;
        this.establishedAt = Instant.now();
    }

    public NationRelation(int nationId, int targetNationId, RelationType relationType) {
        this.nationId = nationId;
        this.targetNationId = targetNationId;
        this.relationType = relationType;
        this.establishedAt = Instant.now();
    }

    public NationRelation(
            int nationId, int targetNationId, RelationType relationType, Instant establishedAt) {
        this.nationId = nationId;
        this.targetNationId = targetNationId;
        this.relationType = relationType;
        this.establishedAt = establishedAt;
    }

    public int getNationId() {
        return nationId;
    }

    public void setNationId(int nationId) {
        this.nationId = nationId;
    }

    public int getTargetNationId() {
        return targetNationId;
    }

    public void setTargetNationId(int targetNationId) {
        this.targetNationId = targetNationId;
    }

    public RelationType getRelationType() {
        return relationType;
    }

    public void setRelationType(RelationType relationType) {
        this.relationType = relationType;
        this.establishedAt = Instant.now();
    }

    public Instant getEstablishedAt() {
        return establishedAt;
    }

    public void setEstablishedAt(Instant establishedAt) {
        this.establishedAt = establishedAt;
    }

    public boolean isAtWar() {
        return relationType == RelationType.AT_WAR;
    }

    public boolean isAlly() {
        return relationType == RelationType.ALLY;
    }

    public boolean isEnemy() {
        return relationType == RelationType.ENEMY || relationType == RelationType.AT_WAR;
    }

    public boolean isNeutral() {
        return relationType == RelationType.NEUTRAL;
    }

    public boolean isTruce() {
        return relationType == RelationType.TRUCE;
    }

    public boolean canDeclareWar() {
        return relationType == RelationType.ENEMY || relationType == RelationType.NEUTRAL;
    }

    public boolean canAlly() {
        return relationType != RelationType.AT_WAR && relationType != RelationType.ALLY;
    }
}
