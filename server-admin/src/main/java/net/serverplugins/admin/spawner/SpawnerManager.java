package net.serverplugins.admin.spawner;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.TileState;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public class SpawnerManager {

    // Key to mark spawners that were placed by players (not natural)
    public static NamespacedKey PLAYER_PLACED_KEY;

    private final ServerAdmin plugin;
    private final Map<UUID, SpawnerTracker> trackers;
    private final Set<UUID> alertReceivers;

    public SpawnerManager(ServerAdmin plugin) {
        this.plugin = plugin;
        this.trackers = new ConcurrentHashMap<>();
        this.alertReceivers = ConcurrentHashMap.newKeySet();

        // Initialize the key for tracking player-placed spawners
        PLAYER_PLACED_KEY = new NamespacedKey(plugin, "player_placed");

        // Start cleanup task
        Bukkit.getScheduler()
                .runTaskTimerAsynchronously(plugin, this::cleanupOldEvents, 20L * 60, 20L * 60);
    }

    public void recordSpawnerBreak(Player player, Block block) {
        Material type = block.getType();
        if (type != Material.SPAWNER && type != Material.TRIAL_SPAWNER) {
            return;
        }

        // Check if this spawner was placed by a player (not natural)
        if (isPlayerPlaced(block)) {
            return; // Don't track player-placed spawners
        }

        UUID uuid = player.getUniqueId();
        SpawnerTracker tracker = trackers.computeIfAbsent(uuid, SpawnerTracker::new);

        // Get the mob type from the spawner
        EntityType mobType = EntityType.PIG; // Default
        if (block.getState() instanceof CreatureSpawner spawner) {
            mobType = spawner.getSpawnedType();
        }

        Location location = block.getLocation();
        long now = System.currentTimeMillis();

        tracker.recordSpawnerBreak(mobType, location, now);

        // Check for alert
        int count = tracker.getRecentCount();
        int threshold = plugin.getAdminConfig().getSpawnerAlertThreshold();

        if (count >= threshold) {
            sendAlert(player, mobType, location, count);
        }
    }

    /** Check if a spawner block was placed by a player (not naturally generated) */
    public boolean isPlayerPlaced(Block block) {
        if (!(block.getState() instanceof TileState tileState)) {
            return false;
        }
        return tileState
                .getPersistentDataContainer()
                .has(PLAYER_PLACED_KEY, PersistentDataType.BYTE);
    }

    /** Mark a spawner as player-placed when placed by a player */
    public void markAsPlayerPlaced(Block block) {
        if (!(block.getState() instanceof TileState tileState)) {
            return;
        }
        tileState
                .getPersistentDataContainer()
                .set(PLAYER_PLACED_KEY, PersistentDataType.BYTE, (byte) 1);
        tileState.update();
    }

    private void sendAlert(Player player, EntityType mobType, Location location, int count) {
        int timeWindow = plugin.getAdminConfig().getSpawnerTimeWindow();
        String timeStr = formatTime(timeWindow);

        String locStr =
                String.format(
                        "%s %d, %d, %d",
                        location.getWorld().getName(),
                        location.getBlockX(),
                        location.getBlockY(),
                        location.getBlockZ());

        String mobName = formatMobType(mobType);

        String message =
                plugin.getAdminConfig()
                        .getSpawnerAlertMsg()
                        .replace("%player%", player.getName())
                        .replace("%type%", mobName)
                        .replace("%location%", locStr)
                        .replace("%count%", String.valueOf(count))
                        .replace("%time%", timeStr);

        for (UUID receiverId : alertReceivers) {
            Player receiver = Bukkit.getPlayer(receiverId);
            if (receiver != null && receiver.isOnline()) {
                TextUtil.send(receiver, message);
            }
        }

        // Also log to console
        plugin.getLogger()
                .warning(
                        "[Spawner] "
                                + player.getName()
                                + " found "
                                + mobName
                                + " spawner at "
                                + locStr
                                + " ("
                                + count
                                + " in "
                                + timeStr
                                + ")");
    }

    private String formatMobType(EntityType type) {
        if (type == null) return "unknown";
        return type.name().toLowerCase().replace("_", " ");
    }

    private String formatTime(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m";
        } else {
            return (seconds / 3600) + "h";
        }
    }

    private void cleanupOldEvents() {
        long cutoff =
                System.currentTimeMillis()
                        - (plugin.getAdminConfig().getSpawnerTimeWindow() * 1000L);
        for (SpawnerTracker tracker : trackers.values()) {
            tracker.pruneOldEvents(cutoff);
        }

        // Remove empty trackers
        trackers.entrySet().removeIf(entry -> entry.getValue().getRecentCount() == 0);
    }

    public void toggleAlerts(Player player) {
        UUID uuid = player.getUniqueId();
        if (alertReceivers.contains(uuid)) {
            alertReceivers.remove(uuid);
        } else {
            alertReceivers.add(uuid);
        }
    }

    public boolean hasAlertsEnabled(Player player) {
        return alertReceivers.contains(player.getUniqueId());
    }

    public SpawnerTracker getTracker(UUID playerId) {
        return trackers.get(playerId);
    }

    public Set<UUID> getAlertReceivers() {
        return alertReceivers;
    }
}
