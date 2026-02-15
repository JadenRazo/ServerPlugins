package net.serverplugins.events.data;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import net.serverplugins.events.ServerEvents;
import net.serverplugins.events.events.ServerEvent;
import net.serverplugins.events.repository.EventsRepository;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/** Tracks event statistics, history, and leaderboards. */
public class EventStats {

    private final ServerEvents plugin;
    private final EventsRepository repository;
    private final boolean useDatabase;
    private final File dataFile;
    private FileConfiguration dataConfig;

    // In-memory cache
    private final Map<UUID, PlayerStats> playerStats = new HashMap<>();
    private final List<EventRecord> recentEvents = new ArrayList<>();
    private static final int MAX_HISTORY = 50;

    public EventStats(ServerEvents plugin, EventsRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.useDatabase = repository != null;
        this.dataFile = new File(plugin.getDataFolder(), "stats.yml");
        load();
    }

    public final void load() {
        playerStats.clear();
        recentEvents.clear();

        if (useDatabase) {
            // Load from database
            Map<UUID, EventsRepository.PlayerStats> dbStats = repository.getAllPlayerStats();
            for (Map.Entry<UUID, EventsRepository.PlayerStats> entry : dbStats.entrySet()) {
                EventsRepository.PlayerStats s = entry.getValue();
                playerStats.put(
                        entry.getKey(),
                        new PlayerStats(
                                s.uuid,
                                s.name,
                                s.wins,
                                s.participations,
                                s.coinsEarned,
                                s.keysEarned));
            }

            List<EventsRepository.EventRecord> dbEvents = repository.getRecentEvents(MAX_HISTORY);
            for (EventsRepository.EventRecord r : dbEvents) {
                ServerEvent.EventType eventType = ServerEvent.EventType.fromString(r.eventType);
                recentEvents.add(
                        new EventRecord(eventType, r.winnerUuid, r.winnerName, r.timestamp));
            }
        } else {
            // Load from file
            if (!dataFile.exists()) {
                try {
                    dataFile.getParentFile().mkdirs();
                    dataFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not create stats.yml: " + e.getMessage());
                }
            }

            dataConfig = YamlConfiguration.loadConfiguration(dataFile);

            ConfigurationSection playersSection = dataConfig.getConfigurationSection("players");
            if (playersSection != null) {
                for (String uuidStr : playersSection.getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        ConfigurationSection playerSection =
                                playersSection.getConfigurationSection(uuidStr);
                        if (playerSection != null) {
                            PlayerStats stats =
                                    new PlayerStats(
                                            uuid,
                                            playerSection.getString("name", "Unknown"),
                                            playerSection.getInt("wins", 0),
                                            playerSection.getInt("participations", 0),
                                            playerSection.getLong("coins_earned", 0),
                                            playerSection.getInt("keys_earned", 0));
                            playerStats.put(uuid, stats);
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            List<Map<?, ?>> historyList = dataConfig.getMapList("history");
            for (Map<?, ?> entry : historyList) {
                try {
                    String eventType = (String) entry.get("type");
                    String winnerUuid = (String) entry.get("winner_uuid");
                    String winnerName = (String) entry.get("winner_name");
                    long timestamp = ((Number) entry.get("timestamp")).longValue();

                    recentEvents.add(
                            new EventRecord(
                                    ServerEvent.EventType.fromString(eventType),
                                    winnerUuid != null ? UUID.fromString(winnerUuid) : null,
                                    winnerName,
                                    timestamp));
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void save() {
        if (useDatabase) {
            // Database saves happen immediately on updates
            return;
        }

        // Save to file
        for (Map.Entry<UUID, PlayerStats> entry : playerStats.entrySet()) {
            String path = "players." + entry.getKey().toString();
            PlayerStats stats = entry.getValue();
            dataConfig.set(path + ".name", stats.name());
            dataConfig.set(path + ".wins", stats.wins());
            dataConfig.set(path + ".participations", stats.participations());
            dataConfig.set(path + ".coins_earned", stats.coinsEarned());
            dataConfig.set(path + ".keys_earned", stats.keysEarned());
        }

        List<Map<String, Object>> historyList = new ArrayList<>();
        for (EventRecord record : recentEvents) {
            Map<String, Object> entry = new HashMap<>();
            entry.put(
                    "type",
                    record.eventType() != null ? record.eventType().getConfigKey() : "unknown");
            entry.put(
                    "winner_uuid",
                    record.winnerUuid() != null ? record.winnerUuid().toString() : null);
            entry.put("winner_name", record.winnerName());
            entry.put("timestamp", record.timestamp());
            historyList.add(entry);
        }
        dataConfig.set("history", historyList);

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save stats.yml: " + e.getMessage());
        }
    }

    /** Record a player winning an event. */
    public void recordWin(
            Player player, ServerEvent.EventType eventType, int coinsEarned, boolean keyEarned) {
        recordWin(player, eventType, coinsEarned, keyEarned, 0);
    }

    public void recordWin(
            Player player,
            ServerEvent.EventType eventType,
            int coinsEarned,
            boolean keyEarned,
            int participantCount) {
        UUID uuid = player.getUniqueId();
        PlayerStats existing =
                playerStats.getOrDefault(uuid, new PlayerStats(uuid, player.getName(), 0, 0, 0, 0));

        PlayerStats updated =
                new PlayerStats(
                        uuid,
                        player.getName(),
                        existing.wins() + 1,
                        existing.participations() + 1,
                        existing.coinsEarned() + coinsEarned,
                        existing.keysEarned() + (keyEarned ? 1 : 0));
        playerStats.put(uuid, updated);

        // Add to history
        recentEvents.add(
                0,
                new EventRecord(eventType, uuid, player.getName(), Instant.now().getEpochSecond()));

        // Trim history
        while (recentEvents.size() > MAX_HISTORY) {
            recentEvents.remove(recentEvents.size() - 1);
        }

        if (useDatabase) {
            // Save to database async
            plugin.getServer()
                    .getScheduler()
                    .runTaskAsynchronously(
                            plugin,
                            () -> {
                                repository.savePlayerStats(
                                        uuid,
                                        updated.name(),
                                        updated.wins(),
                                        updated.participations(),
                                        updated.coinsEarned(),
                                        updated.keysEarned());
                                repository.recordEvent(
                                        eventType.getConfigKey(),
                                        uuid,
                                        player.getName(),
                                        participantCount);
                            });
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
        }
    }

    /** Record a player participating in an event (without winning). */
    public void recordParticipation(Player player, int coinsEarned, boolean keyEarned) {
        UUID uuid = player.getUniqueId();
        PlayerStats existing =
                playerStats.getOrDefault(uuid, new PlayerStats(uuid, player.getName(), 0, 0, 0, 0));

        PlayerStats updated =
                new PlayerStats(
                        uuid,
                        player.getName(),
                        existing.wins(),
                        existing.participations() + 1,
                        existing.coinsEarned() + coinsEarned,
                        existing.keysEarned() + (keyEarned ? 1 : 0));
        playerStats.put(uuid, updated);

        if (useDatabase) {
            plugin.getServer()
                    .getScheduler()
                    .runTaskAsynchronously(
                            plugin,
                            () -> {
                                repository.savePlayerStats(
                                        uuid,
                                        updated.name(),
                                        updated.wins(),
                                        updated.participations(),
                                        updated.coinsEarned(),
                                        updated.keysEarned());
                            });
        }
    }

    /** Record an event that ended with no winner. */
    public void recordNoWinner(ServerEvent.EventType eventType) {
        recordNoWinner(eventType, 0);
    }

    public void recordNoWinner(ServerEvent.EventType eventType, int participantCount) {
        recentEvents.add(0, new EventRecord(eventType, null, null, Instant.now().getEpochSecond()));

        while (recentEvents.size() > MAX_HISTORY) {
            recentEvents.remove(recentEvents.size() - 1);
        }

        if (useDatabase) {
            plugin.getServer()
                    .getScheduler()
                    .runTaskAsynchronously(
                            plugin,
                            () -> {
                                repository.recordEvent(
                                        eventType.getConfigKey(), null, null, participantCount);
                            });
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
        }
    }

    /** Get top players by wins. Always uses in-memory cache to avoid blocking main thread. */
    public List<PlayerStats> getTopWinners(int limit) {
        // Always use in-memory cache (populated at load and updated on win)
        // This avoids sync DB queries on the main thread
        return playerStats.values().stream()
                .sorted(Comparator.comparingInt(PlayerStats::wins).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /** Get recent event history. */
    public List<EventRecord> getRecentEvents(int limit) {
        return recentEvents.stream().limit(limit).collect(Collectors.toList());
    }

    /** Get stats for a specific player. */
    public PlayerStats getPlayerStats(UUID uuid) {
        return playerStats.get(uuid);
    }

    /** Player statistics record. */
    public record PlayerStats(
            UUID uuid,
            String name,
            int wins,
            int participations,
            long coinsEarned,
            int keysEarned) {}

    /** Event history record. */
    public record EventRecord(
            ServerEvent.EventType eventType, UUID winnerUuid, String winnerName, long timestamp) {}
}
