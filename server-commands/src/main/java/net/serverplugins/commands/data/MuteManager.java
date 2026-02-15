package net.serverplugins.commands.data;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.repository.CommandsRepository;
import org.bukkit.configuration.file.YamlConfiguration;

public class MuteManager {

    private final ServerCommands plugin;
    private final CommandsRepository repository;
    private final boolean useDatabase;
    private final File mutesFile;
    private YamlConfiguration mutesConfig;
    private final Map<UUID, MuteData> mutedPlayers = new ConcurrentHashMap<>();

    public MuteManager(ServerCommands plugin, CommandsRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.useDatabase = repository != null;
        this.mutesFile = new File(plugin.getDataFolder(), "mutes.yml");

        if (!useDatabase) {
            if (!mutesFile.exists()) {
                try {
                    mutesFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            this.mutesConfig = YamlConfiguration.loadConfiguration(mutesFile);
        }

        loadMutes();
    }

    public void reload() {
        if (useDatabase) {
            loadMutes();
        } else {
            this.mutesConfig = YamlConfiguration.loadConfiguration(mutesFile);
            loadMutes();
        }
    }

    private void loadMutes() {
        mutedPlayers.clear();

        if (useDatabase) {
            Map<UUID, CommandsRepository.MuteData> dbMutes = repository.getAllMutes();
            for (Map.Entry<UUID, CommandsRepository.MuteData> entry : dbMutes.entrySet()) {
                CommandsRepository.MuteData dbData = entry.getValue();
                if (dbData.isExpired()) {
                    repository.deleteMute(entry.getKey());
                } else {
                    String mutedByName =
                            dbData.mutedBy != null ? dbData.mutedBy.toString() : "Console";
                    long expiration = dbData.expiresAt != null ? dbData.expiresAt : -1;
                    mutedPlayers.put(
                            entry.getKey(),
                            new MuteData(
                                    entry.getKey(),
                                    expiration,
                                    dbData.reason != null ? dbData.reason : "No reason provided",
                                    mutedByName));
                }
            }
        } else {
            for (String key : mutesConfig.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    long expiration = mutesConfig.getLong(key + ".expiration");
                    String reason = mutesConfig.getString(key + ".reason", "No reason provided");
                    String mutedBy = mutesConfig.getString(key + ".mutedBy", "Console");

                    if (expiration == -1 || expiration > System.currentTimeMillis()) {
                        mutedPlayers.put(uuid, new MuteData(uuid, expiration, reason, mutedBy));
                    } else {
                        mutesConfig.set(key, null);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
            save();
        }
    }

    public void mute(UUID uuid, long duration, String reason, String mutedBy) {
        mute(uuid, duration, reason, mutedBy, null);
    }

    public void mute(UUID uuid, long duration, String reason, String mutedBy, UUID mutedByUuid) {
        long expiration = duration == -1 ? -1 : System.currentTimeMillis() + duration;
        MuteData data = new MuteData(uuid, expiration, reason, mutedBy);
        mutedPlayers.put(uuid, data);

        if (useDatabase) {
            Long expiresAt = duration == -1 ? null : expiration;
            // Save async to avoid blocking the main thread
            CompletableFuture.runAsync(
                    () -> repository.saveMute(uuid, mutedByUuid, reason, expiresAt));
        } else {
            String key = uuid.toString();
            mutesConfig.set(key + ".expiration", expiration);
            mutesConfig.set(key + ".reason", reason);
            mutesConfig.set(key + ".mutedBy", mutedBy);
            save();
        }
    }

    public void unmute(UUID uuid) {
        mutedPlayers.remove(uuid);

        if (useDatabase) {
            // Delete async to avoid blocking the main thread
            CompletableFuture.runAsync(() -> repository.deleteMute(uuid));
        } else {
            mutesConfig.set(uuid.toString(), null);
            save();
        }
    }

    public boolean isMuted(UUID uuid) {
        MuteData data = mutedPlayers.get(uuid);
        if (data == null) return false;

        if (data.getExpiration() != -1 && data.getExpiration() <= System.currentTimeMillis()) {
            unmute(uuid);
            return false;
        }

        return true;
    }

    public MuteData getMuteData(UUID uuid) {
        return mutedPlayers.get(uuid);
    }

    private void save() {
        if (!useDatabase) {
            try {
                mutesConfig.save(mutesFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static class MuteData {
        private final UUID uuid;
        private final long expiration;
        private final String reason;
        private final String mutedBy;

        public MuteData(UUID uuid, long expiration, String reason, String mutedBy) {
            this.uuid = uuid;
            this.expiration = expiration;
            this.reason = reason;
            this.mutedBy = mutedBy;
        }

        public UUID getUuid() {
            return uuid;
        }

        public long getExpiration() {
            return expiration;
        }

        public String getReason() {
            return reason;
        }

        public String getMutedBy() {
            return mutedBy;
        }

        public boolean isPermanent() {
            return expiration == -1;
        }

        public String getRemainingTime() {
            if (expiration == -1) return "Permanent";
            long remaining = expiration - System.currentTimeMillis();
            if (remaining <= 0) return "Expired";

            long seconds = remaining / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            long days = hours / 24;

            if (days > 0) return days + "d " + (hours % 24) + "h";
            if (hours > 0) return hours + "h " + (minutes % 60) + "m";
            if (minutes > 0) return minutes + "m " + (seconds % 60) + "s";
            return seconds + "s";
        }
    }
}
