package net.serverplugins.admin.punishment;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.serverplugins.admin.ServerAdmin;
import org.bukkit.configuration.ConfigurationSection;

public class ReasonManager {

    private final ServerAdmin plugin;
    private final PunishmentRepository repository;
    private final Map<String, ReasonPreset> reasons = new LinkedHashMap<>();

    public ReasonManager(ServerAdmin plugin, PunishmentRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        loadReasons();
    }

    public final void loadReasons() {
        reasons.clear();

        ConfigurationSection punishmentSection =
                plugin.getConfig().getConfigurationSection("punishment");
        if (punishmentSection == null || !punishmentSection.getBoolean("enabled", true)) {
            loadDefaultReasons();
            return;
        }

        ConfigurationSection reasonsSection = punishmentSection.getConfigurationSection("reasons");
        if (reasonsSection == null) {
            loadDefaultReasons();
            return;
        }

        for (String reasonId : reasonsSection.getKeys(false)) {
            ConfigurationSection reasonSection = reasonsSection.getConfigurationSection(reasonId);
            if (reasonSection == null) continue;

            String displayName = reasonSection.getString("display-name", reasonId);
            String description = reasonSection.getString("description", "");

            List<PunishmentType> applicableTypes = new ArrayList<>();
            List<String> typeStrings = reasonSection.getStringList("applicable-types");
            for (String typeStr : typeStrings) {
                PunishmentType type = PunishmentType.fromString(typeStr);
                if (type != null) {
                    applicableTypes.add(type);
                }
            }

            ReasonPreset preset =
                    new ReasonPreset(reasonId, displayName, description, applicableTypes);

            ConfigurationSection escalationSection =
                    reasonSection.getConfigurationSection("escalation");
            if (escalationSection != null) {
                for (String offenseNumStr : escalationSection.getKeys(false)) {
                    try {
                        int offenseNum = Integer.parseInt(offenseNumStr);
                        ConfigurationSection levelSection =
                                escalationSection.getConfigurationSection(offenseNumStr);
                        if (levelSection != null) {
                            PunishmentType type =
                                    PunishmentType.fromString(levelSection.getString("type"));
                            if (type == null) continue;

                            String durationStr = levelSection.getString("duration");
                            Long durationMs = EscalationPreset.parseDuration(durationStr);

                            preset.addEscalation(
                                    offenseNum, new ReasonPreset.EscalationLevel(type, durationMs));
                        }
                    } catch (NumberFormatException e) {
                        plugin.getLogger()
                                .warning(
                                        "Invalid offense number in reason "
                                                + reasonId
                                                + ": "
                                                + offenseNumStr);
                    }
                }
            }

            reasons.put(reasonId, preset);
        }

        if (reasons.isEmpty()) {
            loadDefaultReasons();
        }

        plugin.getLogger().info("Loaded " + reasons.size() + " punishment reasons.");
    }

    private void loadDefaultReasons() {
        // Chat-related reasons
        addDefaultReason(
                "spam",
                "Spam/Flooding",
                "Repeated messages or chat flooding",
                Arrays.asList(PunishmentType.WARN, PunishmentType.MUTE),
                new Object[][] {
                    {1, PunishmentType.WARN, null},
                    {2, PunishmentType.MUTE, 30 * 60 * 1000L},
                    {3, PunishmentType.MUTE, 6 * 60 * 60 * 1000L},
                    {4, PunishmentType.MUTE, 24 * 60 * 60 * 1000L},
                    {5, PunishmentType.BAN, 3 * 24 * 60 * 60 * 1000L},
                    {6, PunishmentType.BAN, null}
                });

        addDefaultReason(
                "caps-abuse",
                "Caps Abuse",
                "Excessive use of capital letters",
                Arrays.asList(PunishmentType.WARN, PunishmentType.MUTE),
                new Object[][] {
                    {1, PunishmentType.WARN, null},
                    {2, PunishmentType.MUTE, 15 * 60 * 1000L},
                    {3, PunishmentType.MUTE, 60 * 60 * 1000L},
                    {4, PunishmentType.MUTE, 6 * 60 * 60 * 1000L}
                });

        addDefaultReason(
                "inappropriate-language",
                "Inappropriate Language",
                "Profanity or inappropriate content",
                Arrays.asList(PunishmentType.WARN, PunishmentType.MUTE),
                new Object[][] {
                    {1, PunishmentType.WARN, null},
                    {2, PunishmentType.MUTE, 60 * 60 * 1000L},
                    {3, PunishmentType.MUTE, 6 * 60 * 60 * 1000L},
                    {4, PunishmentType.MUTE, 24 * 60 * 60 * 1000L},
                    {5, PunishmentType.BAN, 3 * 24 * 60 * 60 * 1000L}
                });

        addDefaultReason(
                "advertising",
                "Advertising",
                "Promoting other servers or services",
                Arrays.asList(PunishmentType.WARN, PunishmentType.MUTE, PunishmentType.BAN),
                new Object[][] {
                    {1, PunishmentType.MUTE, 60 * 60 * 1000L},
                    {2, PunishmentType.MUTE, 24 * 60 * 60 * 1000L},
                    {3, PunishmentType.BAN, 7 * 24 * 60 * 60 * 1000L},
                    {4, PunishmentType.BAN, null}
                });

        addDefaultReason(
                "toxicity",
                "Toxicity/Rudeness",
                "Disrespectful or toxic behavior",
                Arrays.asList(PunishmentType.WARN, PunishmentType.MUTE),
                new Object[][] {
                    {1, PunishmentType.WARN, null},
                    {2, PunishmentType.MUTE, 60 * 60 * 1000L},
                    {3, PunishmentType.MUTE, 24 * 60 * 60 * 1000L},
                    {4, PunishmentType.BAN, 3 * 24 * 60 * 60 * 1000L},
                    {5, PunishmentType.BAN, null}
                });

        addDefaultReason(
                "harassment",
                "Harassment",
                "Targeting or harassing another player",
                Arrays.asList(PunishmentType.WARN, PunishmentType.MUTE, PunishmentType.BAN),
                new Object[][] {
                    {1, PunishmentType.MUTE, 6 * 60 * 60 * 1000L},
                    {2, PunishmentType.MUTE, 24 * 60 * 60 * 1000L},
                    {3, PunishmentType.BAN, 7 * 24 * 60 * 60 * 1000L},
                    {4, PunishmentType.BAN, null}
                });

        addDefaultReason(
                "slur-hate-speech",
                "Slur/Hate Speech",
                "Discriminatory or hateful language",
                Arrays.asList(PunishmentType.MUTE, PunishmentType.BAN),
                new Object[][] {
                    {1, PunishmentType.MUTE, 24 * 60 * 60 * 1000L},
                    {2, PunishmentType.BAN, 7 * 24 * 60 * 60 * 1000L},
                    {3, PunishmentType.BAN, null}
                });

        addDefaultReason(
                "staff-disrespect",
                "Staff Disrespect",
                "Disrespecting or ignoring staff",
                Arrays.asList(PunishmentType.WARN, PunishmentType.MUTE),
                new Object[][] {
                    {1, PunishmentType.WARN, null},
                    {2, PunishmentType.MUTE, 60 * 60 * 1000L},
                    {3, PunishmentType.MUTE, 6 * 60 * 60 * 1000L},
                    {4, PunishmentType.MUTE, 24 * 60 * 60 * 1000L}
                });

        // Gameplay reasons
        addDefaultReason(
                "griefing",
                "Griefing",
                "Destroying or damaging builds",
                Arrays.asList(PunishmentType.WARN, PunishmentType.KICK, PunishmentType.BAN),
                new Object[][] {
                    {1, PunishmentType.WARN, null},
                    {2, PunishmentType.BAN, 24 * 60 * 60 * 1000L},
                    {3, PunishmentType.BAN, 7 * 24 * 60 * 60 * 1000L},
                    {4, PunishmentType.BAN, null}
                });

        addDefaultReason(
                "stealing",
                "Stealing",
                "Taking items without permission",
                Arrays.asList(PunishmentType.WARN, PunishmentType.KICK, PunishmentType.BAN),
                new Object[][] {
                    {1, PunishmentType.WARN, null},
                    {2, PunishmentType.BAN, 24 * 60 * 60 * 1000L},
                    {3, PunishmentType.BAN, 7 * 24 * 60 * 60 * 1000L},
                    {4, PunishmentType.BAN, null}
                });

        addDefaultReason(
                "scamming",
                "Scamming",
                "Deceiving players in trades",
                Arrays.asList(PunishmentType.WARN, PunishmentType.BAN),
                new Object[][] {
                    {1, PunishmentType.WARN, null},
                    {2, PunishmentType.BAN, 3 * 24 * 60 * 60 * 1000L},
                    {3, PunishmentType.BAN, 14 * 24 * 60 * 60 * 1000L},
                    {4, PunishmentType.BAN, null}
                });

        // Cheating reasons
        addDefaultReason(
                "hacked-client",
                "Hacked Client",
                "Using illegal modifications (KillAura, Fly, Speed)",
                Arrays.asList(PunishmentType.BAN),
                new Object[][] {
                    {1, PunishmentType.BAN, 7 * 24 * 60 * 60 * 1000L},
                    {2, PunishmentType.BAN, 30 * 24 * 60 * 60 * 1000L},
                    {3, PunishmentType.BAN, null}
                });

        addDefaultReason(
                "xray",
                "X-Ray",
                "Using x-ray texture packs or mods",
                Arrays.asList(PunishmentType.BAN),
                new Object[][] {
                    {1, PunishmentType.BAN, 7 * 24 * 60 * 60 * 1000L},
                    {2, PunishmentType.BAN, 30 * 24 * 60 * 60 * 1000L},
                    {3, PunishmentType.BAN, null}
                });

        addDefaultReason(
                "exploiting",
                "Exploiting",
                "Abusing bugs or exploits",
                Arrays.asList(PunishmentType.WARN, PunishmentType.BAN),
                new Object[][] {
                    {1, PunishmentType.WARN, null},
                    {2, PunishmentType.BAN, 7 * 24 * 60 * 60 * 1000L},
                    {3, PunishmentType.BAN, null}
                });

        addDefaultReason(
                "duping",
                "Duping",
                "Duplicating items",
                Arrays.asList(PunishmentType.BAN),
                new Object[][] {
                    {1, PunishmentType.BAN, 14 * 24 * 60 * 60 * 1000L},
                    {2, PunishmentType.BAN, null}
                });

        // General
        addDefaultReason(
                "other",
                "Custom Reason",
                "Enter a custom reason",
                Arrays.asList(
                        PunishmentType.WARN,
                        PunishmentType.MUTE,
                        PunishmentType.KICK,
                        PunishmentType.BAN),
                new Object[][] {{1, PunishmentType.WARN, null}});
    }

    private void addDefaultReason(
            String id,
            String displayName,
            String description,
            List<PunishmentType> applicableTypes,
            Object[][] escalations) {
        ReasonPreset preset = new ReasonPreset(id, displayName, description, applicableTypes);
        for (Object[] esc : escalations) {
            int offense = (int) esc[0];
            PunishmentType type = (PunishmentType) esc[1];
            Long duration = (Long) esc[2];
            preset.addEscalation(offense, new ReasonPreset.EscalationLevel(type, duration));
        }
        reasons.put(id, preset);
    }

    public ReasonPreset getReason(String id) {
        return reasons.get(id.toLowerCase());
    }

    public Collection<ReasonPreset> getReasons() {
        return Collections.unmodifiableCollection(reasons.values());
    }

    public List<ReasonPreset> getReasonsForType(PunishmentType type) {
        return reasons.values().stream()
                .filter(r -> r.isApplicableTo(type))
                .collect(Collectors.toList());
    }

    public int getOffenseCount(UUID playerUuid, String reasonId) {
        return repository.getReasonOffenseCount(playerUuid, reasonId);
    }

    public CompletableFuture<Integer> getOffenseCountAsync(UUID playerUuid, String reasonId) {
        return CompletableFuture.supplyAsync(
                () -> repository.getReasonOffenseCount(playerUuid, reasonId));
    }

    public void incrementOffenseCount(UUID playerUuid, String reasonId) {
        repository.incrementReasonOffenseCount(playerUuid, reasonId);
    }

    public ReasonPreset.EscalationLevel getNextEscalation(UUID playerUuid, String reasonId) {
        ReasonPreset preset = getReason(reasonId);
        if (preset == null) return null;

        int currentOffenses = getOffenseCount(playerUuid, reasonId);
        return preset.getEscalation(currentOffenses + 1);
    }

    public void resetOffenses(UUID playerUuid, String reasonId) {
        repository.resetReasonOffenseCount(playerUuid, reasonId);
    }

    public void resetAllOffenses(UUID playerUuid) {
        repository.resetAllReasonOffenses(playerUuid);
    }
}
