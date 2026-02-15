package net.serverplugins.claim.models;

import java.time.Instant;

public class War {

    public enum WarState {
        DECLARED("Declared", "War has been declared, waiting for active combat"),
        ACTIVE("Active", "War is ongoing, PvP enabled in territories"),
        CEASEFIRE("Ceasefire", "Temporary pause in hostilities"),
        ENDED("Ended", "War has concluded");

        private final String displayName;
        private final String description;

        WarState(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public static WarState fromString(String name) {
            try {
                return WarState.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                return DECLARED;
            }
        }
    }

    public enum WarOutcome {
        ATTACKER_WIN("Attacker Victory"),
        DEFENDER_WIN("Defender Victory"),
        SURRENDER("Surrender"),
        TRUCE("Mutual Truce"),
        TIMEOUT("Timeout/Stalemate");

        private final String displayName;

        WarOutcome(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static WarOutcome fromString(String name) {
            try {
                return WarOutcome.valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private int id;
    private Integer attackerNationId;
    private Integer attackerClaimId;
    private Integer defenderNationId;
    private Integer defenderClaimId;
    private WarState warState;
    private String declarationReason;
    private Instant declaredAt;
    private Instant activeAt;
    private Instant endedAt;
    private WarOutcome outcome;

    public War() {
        this.warState = WarState.DECLARED;
        this.declaredAt = Instant.now();
    }

    public War(
            Integer attackerNationId,
            Integer attackerClaimId,
            Integer defenderNationId,
            Integer defenderClaimId,
            String reason) {
        this.attackerNationId = attackerNationId;
        this.attackerClaimId = attackerClaimId;
        this.defenderNationId = defenderNationId;
        this.defenderClaimId = defenderClaimId;
        this.warState = WarState.DECLARED;
        this.declarationReason = reason;
        this.declaredAt = Instant.now();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Integer getAttackerNationId() {
        return attackerNationId;
    }

    public void setAttackerNationId(Integer attackerNationId) {
        this.attackerNationId = attackerNationId;
    }

    public Integer getAttackerClaimId() {
        return attackerClaimId;
    }

    public void setAttackerClaimId(Integer attackerClaimId) {
        this.attackerClaimId = attackerClaimId;
    }

    public Integer getDefenderNationId() {
        return defenderNationId;
    }

    public void setDefenderNationId(Integer defenderNationId) {
        this.defenderNationId = defenderNationId;
    }

    public Integer getDefenderClaimId() {
        return defenderClaimId;
    }

    public void setDefenderClaimId(Integer defenderClaimId) {
        this.defenderClaimId = defenderClaimId;
    }

    public WarState getWarState() {
        return warState;
    }

    public void setWarState(WarState warState) {
        this.warState = warState;
    }

    public String getDeclarationReason() {
        return declarationReason;
    }

    public void setDeclarationReason(String declarationReason) {
        this.declarationReason = declarationReason;
    }

    public Instant getDeclaredAt() {
        return declaredAt;
    }

    public void setDeclaredAt(Instant declaredAt) {
        this.declaredAt = declaredAt;
    }

    public Instant getActiveAt() {
        return activeAt;
    }

    public void setActiveAt(Instant activeAt) {
        this.activeAt = activeAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public WarOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(WarOutcome outcome) {
        this.outcome = outcome;
    }

    public boolean isNationWar() {
        return attackerNationId != null && defenderNationId != null;
    }

    public boolean isClaimWar() {
        return attackerClaimId != null && defenderClaimId != null;
    }

    public boolean isDeclared() {
        return warState == WarState.DECLARED;
    }

    public boolean isActive() {
        return warState == WarState.ACTIVE;
    }

    public boolean isCeasefire() {
        return warState == WarState.CEASEFIRE;
    }

    public boolean isEnded() {
        return warState == WarState.ENDED;
    }

    public void activate() {
        this.warState = WarState.ACTIVE;
        this.activeAt = Instant.now();
    }

    public void ceasefire() {
        this.warState = WarState.CEASEFIRE;
    }

    public void end(WarOutcome outcome) {
        this.warState = WarState.ENDED;
        this.outcome = outcome;
        this.endedAt = Instant.now();
    }

    public boolean involvesNation(int nationId) {
        return (attackerNationId != null && attackerNationId == nationId)
                || (defenderNationId != null && defenderNationId == nationId);
    }

    public boolean involvesClaim(int claimId) {
        return (attackerClaimId != null && attackerClaimId == claimId)
                || (defenderClaimId != null && defenderClaimId == claimId);
    }

    public boolean isAttacker(int nationId) {
        return attackerNationId != null && attackerNationId == nationId;
    }

    public boolean isDefender(int nationId) {
        return defenderNationId != null && defenderNationId == nationId;
    }
}
