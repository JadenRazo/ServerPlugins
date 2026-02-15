package net.serverplugins.admin.punishment;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.serverplugins.admin.ServerAdmin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public class CategoryManager {

    private final ServerAdmin plugin;
    private final PunishmentRepository repository;
    private final Map<String, OffenseCategory> categories = new LinkedHashMap<>();

    public CategoryManager(ServerAdmin plugin, PunishmentRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        loadCategories();
    }

    public final void loadCategories() {
        categories.clear();

        ConfigurationSection punishmentSection =
                plugin.getConfig().getConfigurationSection("punishment");
        if (punishmentSection == null || !punishmentSection.getBoolean("enabled", true)) {
            loadDefaultCategories();
            return;
        }

        ConfigurationSection categoriesSection =
                punishmentSection.getConfigurationSection("categories");
        if (categoriesSection == null) {
            loadDefaultCategories();
            return;
        }

        for (String categoryId : categoriesSection.getKeys(false)) {
            ConfigurationSection catSection = categoriesSection.getConfigurationSection(categoryId);
            if (catSection == null) continue;

            String displayName = catSection.getString("display-name", categoryId);
            String description = catSection.getString("description", "");
            Material icon =
                    Material.getMaterial(catSection.getString("icon", "PAPER").toUpperCase());
            if (icon == null) icon = Material.PAPER;

            OffenseCategory category =
                    new OffenseCategory(categoryId, displayName, description, icon);

            ConfigurationSection escalationSection =
                    catSection.getConfigurationSection("escalation");
            if (escalationSection != null) {
                for (String offenseNumStr : escalationSection.getKeys(false)) {
                    try {
                        int offenseNum = Integer.parseInt(offenseNumStr);
                        ConfigurationSection presetSection =
                                escalationSection.getConfigurationSection(offenseNumStr);
                        if (presetSection != null) {
                            PunishmentType type =
                                    PunishmentType.fromString(presetSection.getString("type"));
                            if (type == null) continue;

                            String durationStr = presetSection.getString("duration");
                            Long durationMs = EscalationPreset.parseDuration(durationStr);

                            String reason =
                                    presetSection.getString(
                                            "reason", displayName + " - Offense #" + offenseNum);

                            category.addEscalation(
                                    offenseNum, new EscalationPreset(type, durationMs, reason));
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger()
                                .warning(
                                        "Invalid offense number in category "
                                                + categoryId
                                                + ": "
                                                + offenseNumStr);
                    }
                }
            }

            categories.put(categoryId, category);
        }

        if (categories.isEmpty()) {
            loadDefaultCategories();
        }
    }

    private void loadDefaultCategories() {
        OffenseCategory chat = OffenseCategory.createDefault("chat");
        chat.addEscalation(
                1,
                new EscalationPreset(PunishmentType.WARN, null, "Chat violation - First offense"));
        chat.addEscalation(
                2,
                new EscalationPreset(
                        PunishmentType.MUTE, 30 * 60 * 1000L, "Chat violation - Second offense"));
        chat.addEscalation(
                3,
                new EscalationPreset(
                        PunishmentType.MUTE,
                        6 * 60 * 60 * 1000L,
                        "Chat violation - Third offense"));
        chat.addEscalation(
                4,
                new EscalationPreset(
                        PunishmentType.MUTE,
                        24 * 60 * 60 * 1000L,
                        "Chat violation - Fourth offense"));
        chat.addEscalation(
                5,
                new EscalationPreset(
                        PunishmentType.BAN,
                        3 * 24 * 60 * 60 * 1000L,
                        "Chat violation - Fifth offense"));
        chat.addEscalation(
                6,
                new EscalationPreset(PunishmentType.BAN, null, "Chat violation - Final offense"));
        categories.put("chat", chat);

        OffenseCategory grief = OffenseCategory.createDefault("grief");
        grief.addEscalation(
                1, new EscalationPreset(PunishmentType.WARN, null, "Griefing - First offense"));
        grief.addEscalation(
                2,
                new EscalationPreset(
                        PunishmentType.BAN, 24 * 60 * 60 * 1000L, "Griefing - Second offense"));
        grief.addEscalation(
                3,
                new EscalationPreset(
                        PunishmentType.BAN, 7 * 24 * 60 * 60 * 1000L, "Griefing - Third offense"));
        grief.addEscalation(
                4, new EscalationPreset(PunishmentType.BAN, null, "Griefing - Final offense"));
        categories.put("grief", grief);

        OffenseCategory cheat = OffenseCategory.createDefault("cheat");
        cheat.addEscalation(
                1,
                new EscalationPreset(
                        PunishmentType.BAN, 7 * 24 * 60 * 60 * 1000L, "Cheating - First offense"));
        cheat.addEscalation(
                2,
                new EscalationPreset(
                        PunishmentType.BAN,
                        30 * 24 * 60 * 60 * 1000L,
                        "Cheating - Second offense"));
        cheat.addEscalation(
                3, new EscalationPreset(PunishmentType.BAN, null, "Cheating - Final offense"));
        categories.put("cheat", cheat);

        OffenseCategory toxicity = OffenseCategory.createDefault("toxicity");
        toxicity.addEscalation(
                1,
                new EscalationPreset(
                        PunishmentType.MUTE, 60 * 60 * 1000L, "Toxic behavior - First offense"));
        toxicity.addEscalation(
                2,
                new EscalationPreset(
                        PunishmentType.MUTE,
                        24 * 60 * 60 * 1000L,
                        "Toxic behavior - Second offense"));
        toxicity.addEscalation(
                3,
                new EscalationPreset(
                        PunishmentType.BAN,
                        3 * 24 * 60 * 60 * 1000L,
                        "Toxic behavior - Third offense"));
        toxicity.addEscalation(
                4,
                new EscalationPreset(PunishmentType.BAN, null, "Toxic behavior - Final offense"));
        categories.put("toxicity", toxicity);

        OffenseCategory other = OffenseCategory.createDefault("other");
        other.addEscalation(1, new EscalationPreset(PunishmentType.WARN, null, "Rule violation"));
        categories.put("other", other);
    }

    public OffenseCategory getCategory(String id) {
        return categories.get(id.toLowerCase());
    }

    public Collection<OffenseCategory> getCategories() {
        return Collections.unmodifiableCollection(categories.values());
    }

    public int getOffenseCount(UUID playerUuid, String categoryId) {
        return repository.getOffenseCount(playerUuid, categoryId);
    }

    public CompletableFuture<Integer> getOffenseCountAsync(UUID playerUuid, String categoryId) {
        return CompletableFuture.supplyAsync(
                () -> repository.getOffenseCount(playerUuid, categoryId));
    }

    public void incrementOffenseCount(UUID playerUuid, String categoryId) {
        repository.incrementOffenseCount(playerUuid, categoryId);
    }

    public EscalationPreset getEscalationForPlayer(UUID playerUuid, String categoryId) {
        OffenseCategory category = getCategory(categoryId);
        if (category == null) return null;

        int currentOffenses = getOffenseCount(playerUuid, categoryId);
        return category.getEscalation(currentOffenses + 1);
    }

    public EscalationPreset getEscalationPreview(UUID playerUuid, String categoryId) {
        OffenseCategory category = getCategory(categoryId);
        if (category == null) return null;

        int currentOffenses = getOffenseCount(playerUuid, categoryId);
        return category.getEscalation(currentOffenses + 1);
    }

    public void resetOffenses(UUID playerUuid, String categoryId) {
        repository.resetOffenseCount(playerUuid, categoryId);
    }

    public void resetAllOffenses(UUID playerUuid) {
        repository.resetAllOffenses(playerUuid);
    }
}
