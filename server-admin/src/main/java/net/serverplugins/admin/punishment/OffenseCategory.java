package net.serverplugins.admin.punishment;

import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Material;

public class OffenseCategory {
    private final String id;
    private final String displayName;
    private final String description;
    private final Material icon;
    private final Map<Integer, EscalationPreset> escalation;

    public OffenseCategory(String id, String displayName, String description, Material icon) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
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

    public Material getIcon() {
        return icon;
    }

    public void addEscalation(int offenseNumber, EscalationPreset preset) {
        escalation.put(offenseNumber, preset);
    }

    public EscalationPreset getEscalation(int offenseNumber) {
        EscalationPreset preset = escalation.get(offenseNumber);
        if (preset != null) {
            return preset;
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

    public Map<Integer, EscalationPreset> getAllEscalations() {
        return new TreeMap<>(escalation);
    }

    public static OffenseCategory createDefault(String id) {
        return switch (id.toLowerCase()) {
            case "chat" ->
                    new OffenseCategory(
                            "chat",
                            "Chat Violations",
                            "Spam, caps abuse, advertising, inappropriate language",
                            Material.PAPER);
            case "grief" ->
                    new OffenseCategory(
                            "grief",
                            "Griefing",
                            "Block destruction, stealing, claim abuse",
                            Material.TNT);
            case "cheat" ->
                    new OffenseCategory(
                            "cheat",
                            "Cheating",
                            "Hacked clients, exploits, x-ray",
                            Material.DIAMOND_PICKAXE);
            case "toxicity" ->
                    new OffenseCategory(
                            "toxicity",
                            "Toxicity",
                            "Harassment, hate speech, targeted abuse",
                            Material.ROTTEN_FLESH);
            default ->
                    new OffenseCategory(
                            "other", "Other", "Miscellaneous offenses", Material.BARRIER);
        };
    }
}
