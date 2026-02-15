package net.serverplugins.admin.punishment;

import java.util.*;

public class ReasonPreset {
    private final String id;
    private final String displayName;
    private final String description;
    private final List<PunishmentType> applicableTypes;
    private final Map<Integer, EscalationLevel> escalation;

    public ReasonPreset(
            String id,
            String displayName,
            String description,
            List<PunishmentType> applicableTypes) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.applicableTypes = applicableTypes != null ? applicableTypes : new ArrayList<>();
        this.escalation = new TreeMap<>();
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public List<PunishmentType> getApplicableTypes() {
        return Collections.unmodifiableList(applicableTypes);
    }

    public boolean isApplicableTo(PunishmentType type) {
        return applicableTypes.contains(type);
    }

    public void addEscalation(int offenseNumber, EscalationLevel level) {
        escalation.put(offenseNumber, level);
    }

    public EscalationLevel getEscalation(int offenseNumber) {
        EscalationLevel level = escalation.get(offenseNumber);
        if (level != null) {
            return level;
        }

        Integer maxKey = null;
        for (Integer key : escalation.keySet()) {
            if (key <= offenseNumber) {
                maxKey = key;
            }
        }

        if (maxKey != null) {
            return escalation.get(maxKey);
        }

        return escalation.isEmpty() ? null : escalation.values().iterator().next();
    }

    public int getMaxEscalationLevel() {
        return escalation.isEmpty()
                ? 0
                : escalation.keySet().stream().mapToInt(i -> i).max().orElse(0);
    }

    public Map<Integer, EscalationLevel> getAllEscalations() {
        return new TreeMap<>(escalation);
    }

    public static class EscalationLevel {
        private final PunishmentType type;
        private final Long durationMs;

        public EscalationLevel(PunishmentType type, Long durationMs) {
            this.type = type;
            this.durationMs = durationMs;
        }

        public PunishmentType getType() {
            return type;
        }

        public Long getDurationMs() {
            return durationMs;
        }

        public boolean isPermanent() {
            return type.hasDuration() && durationMs == null;
        }

        public String getFormattedDuration() {
            if (durationMs == null) {
                return "Permanent";
            }
            return EscalationPreset.formatDuration(durationMs);
        }
    }
}
