package net.serverplugins.claim.managers;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.api.database.Database;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.DustEffect;
import net.serverplugins.claim.models.PlayerRewardsData;
import net.serverplugins.claim.models.ProfileColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

public class RewardsManager {

    private final ServerClaim plugin;
    private final Database database;
    private final Map<UUID, PlayerRewardsData> cache = new ConcurrentHashMap<>();

    public RewardsManager(ServerClaim plugin) {
        this.plugin = plugin;
        this.database = plugin.getDatabase();
    }

    public CompletableFuture<PlayerRewardsData> getOrCreatePlayerRewards(UUID uuid) {
        return CompletableFuture.supplyAsync(
                () -> {
                    PlayerRewardsData cached = cache.get(uuid);
                    if (cached != null) {
                        return cached;
                    }

                    PlayerRewardsData data = loadFromDatabase(uuid);
                    if (data == null) {
                        data = new PlayerRewardsData(uuid);
                        saveToDatabase(data);
                    }
                    cache.put(uuid, data);
                    return data;
                });
    }

    /**
     * Get player rewards - cache-first, loads synchronously if not cached. Use
     * getOrCreatePlayerRewards() for async loading with callback.
     */
    public PlayerRewardsData getPlayerRewardsSync(UUID uuid) {
        PlayerRewardsData cached = cache.get(uuid);
        if (cached != null) {
            return cached;
        }

        // Not in cache - load synchronously from database
        PlayerRewardsData data = loadFromDatabase(uuid);
        if (data == null) {
            // No DB record exists, create default
            data = new PlayerRewardsData(uuid);
            // Save default to database async
            PlayerRewardsData finalData = data;
            CompletableFuture.runAsync(() -> saveToDatabase(finalData));
        }

        // Cache the data and return
        cache.put(uuid, data);
        return data;
    }

    private PlayerRewardsData loadFromDatabase(UUID uuid) {
        return database.query(
                "SELECT * FROM server_player_rewards WHERE uuid = ?",
                rs -> {
                    if (rs.next()) {
                        boolean staticMode = false;
                        try {
                            staticMode = rs.getBoolean("static_particle_mode");
                        } catch (Exception ignored) {
                            // Column may not exist in older databases
                        }
                        return new PlayerRewardsData(
                                uuid,
                                rs.getBoolean("particles_enabled"),
                                staticMode,
                                DustEffect.fromString(rs.getString("selected_dust_effect")),
                                ProfileColor.fromString(rs.getString("selected_profile_color")));
                    }
                    return null;
                },
                uuid.toString());
    }

    private void saveToDatabase(PlayerRewardsData data) {
        database.execute(
                "INSERT INTO server_player_rewards (uuid, particles_enabled, static_particle_mode, selected_dust_effect, selected_profile_color, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP) "
                        + "ON DUPLICATE KEY UPDATE particles_enabled = VALUES(particles_enabled), "
                        + "static_particle_mode = VALUES(static_particle_mode), "
                        + "selected_dust_effect = VALUES(selected_dust_effect), selected_profile_color = VALUES(selected_profile_color), "
                        + "updated_at = CURRENT_TIMESTAMP",
                data.getUuid().toString(),
                data.isParticlesEnabled(),
                data.isStaticParticleMode(),
                data.getSelectedDustEffect() != null ? data.getSelectedDustEffect().name() : null,
                data.getSelectedProfileColor() != null
                        ? data.getSelectedProfileColor().name()
                        : null);
    }

    public void savePlayerRewards(PlayerRewardsData data) {
        cache.put(data.getUuid(), data);
        CompletableFuture.runAsync(() -> saveToDatabase(data));
    }

    public boolean areParticlesEnabled(UUID uuid) {
        PlayerRewardsData data = cache.get(uuid);
        if (data != null) {
            return data.isParticlesEnabled();
        }
        // Default to enabled if not in cache
        return true;
    }

    public void setParticlesEnabled(UUID uuid, boolean enabled) {
        PlayerRewardsData data = getPlayerRewardsSync(uuid);
        data.setParticlesEnabled(enabled);
        savePlayerRewards(data);
    }

    public boolean isStaticParticleMode(UUID uuid) {
        PlayerRewardsData data = cache.get(uuid);
        if (data != null) {
            return data.isStaticParticleMode();
        }
        return true; // Default to static mode (no blinking) for better performance
    }

    public void setStaticParticleMode(UUID uuid, boolean staticMode) {
        PlayerRewardsData data = getPlayerRewardsSync(uuid);
        data.setStaticParticleMode(staticMode);
        savePlayerRewards(data);
    }

    public void setDustEffect(UUID uuid, DustEffect effect) {
        PlayerRewardsData data = getPlayerRewardsSync(uuid);
        data.setSelectedDustEffect(effect);
        savePlayerRewards(data);
    }

    public void setProfileColor(UUID uuid, ProfileColor color) {
        PlayerRewardsData data = getPlayerRewardsSync(uuid);
        data.setSelectedProfileColor(color);
        savePlayerRewards(data);
    }

    public DustEffect getSelectedDustEffect(UUID uuid) {
        PlayerRewardsData data = cache.get(uuid);
        if (data != null) {
            return data.getSelectedDustEffect(); // Can be NULL = no global setting
        }
        return null; // No data means no global setting
    }

    public ProfileColor getSelectedProfileColor(UUID uuid) {
        PlayerRewardsData data = cache.get(uuid);
        if (data != null) {
            return data.getSelectedProfileColor(); // Can be NULL = no global setting
        }
        return null; // No data means no global setting
    }

    public long getPlayerPlaytimeMinutes(UUID uuid) {
        // Use vanilla Minecraft's PLAY_ONE_MINUTE statistic (measured in ticks)
        // This matches what the scoreboard displays for consistency
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
            // PLAY_ONE_MINUTE is measured in ticks (20 ticks = 1 second, 1200 ticks = 1 minute)
            int ticks = offlinePlayer.getStatistic(Statistic.PLAY_ONE_MINUTE);
            return ticks / 1200; // Convert ticks to minutes
        }
        return 0;
    }

    public boolean hasUnlockedDustEffect(UUID uuid, DustEffect effect) {
        long playtime = getPlayerPlaytimeMinutes(uuid);
        return effect.isUnlockedFor(playtime);
    }

    public boolean hasUnlockedProfileColor(UUID uuid, ProfileColor color) {
        long playtime = getPlayerPlaytimeMinutes(uuid);
        return color.isUnlockedFor(playtime);
    }

    public void invalidateCache(UUID uuid) {
        cache.remove(uuid);
    }

    public void clearCache() {
        cache.clear();
    }

    public void preloadPlayer(Player player) {
        getOrCreatePlayerRewards(player.getUniqueId());
    }

    public void unloadPlayer(UUID uuid) {
        PlayerRewardsData data = cache.remove(uuid);
        if (data != null) {
            // Save to database before unloading
            CompletableFuture.runAsync(() -> saveToDatabase(data));
        }
    }
}
