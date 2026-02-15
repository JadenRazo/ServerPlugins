package net.serverplugins.admin.xray;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

public class XrayManager {

    private static final BlockFace[] ADJACENT_FACES = {
        BlockFace.UP, BlockFace.DOWN,
        BlockFace.NORTH, BlockFace.SOUTH,
        BlockFace.EAST, BlockFace.WEST
    };

    private final ServerAdmin plugin;
    private final Map<UUID, XrayTracker> trackers;
    private final Set<UUID> alertReceivers;

    public XrayManager(ServerAdmin plugin) {
        this.plugin = plugin;
        this.trackers = new ConcurrentHashMap<>();
        this.alertReceivers = ConcurrentHashMap.newKeySet();

        // Start cleanup task
        Bukkit.getScheduler()
                .runTaskTimerAsynchronously(plugin, this::cleanupOldEvents, 20L * 60, 20L * 60);
    }

    public void recordBlockBreak(Player player, Block block) {
        Material type = block.getType();
        Set<Material> tracked = plugin.getAdminConfig().getTrackedBlocks();
        Set<Material> stone = plugin.getAdminConfig().getStoneBlocks();

        if (!tracked.contains(type) && !stone.contains(type)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        XrayTracker tracker = trackers.computeIfAbsent(uuid, XrayTracker::new);

        boolean wasExposed = isBlockExposed(block);
        tracker.recordMine(type, wasExposed, System.currentTimeMillis());

        // Check for alert
        if (tracked.contains(type)) {
            int suspicion = tracker.calculateSuspicion();
            if (suspicion >= plugin.getAdminConfig().getAlertThreshold()) {
                sendAlert(player, type, suspicion, tracker);
            }
        }
    }

    private boolean isBlockExposed(Block block) {
        for (BlockFace face : ADJACENT_FACES) {
            Block adjacent = block.getRelative(face);
            if (adjacent.getType().isAir()
                    || adjacent.getType() == Material.CAVE_AIR
                    || adjacent.getType() == Material.WATER
                    || adjacent.getType() == Material.LAVA) {
                return true;
            }
        }
        return false;
    }

    private void sendAlert(Player player, Material block, int suspicion, XrayTracker tracker) {
        String level;
        if (suspicion >= 80) {
            level = "&4VERY HIGH";
        } else if (suspicion >= 60) {
            level = "&cHIGH";
        } else {
            level = "&eMODERATE";
        }

        String blockName = formatBlockName(block);
        int timeWindow = plugin.getAdminConfig().getTimeWindow();
        String timeStr = formatTime(timeWindow);

        String message =
                plugin.getAdminConfig()
                        .getXrayAlertMsg()
                        .replace("%player%", player.getName())
                        .replace("%count%", String.valueOf(tracker.getTotalOres()))
                        .replace("%block%", blockName)
                        .replace("%time%", timeStr)
                        .replace("%level%", level);

        for (UUID receiverId : alertReceivers) {
            Player receiver = Bukkit.getPlayer(receiverId);
            if (receiver != null && receiver.isOnline()) {
                TextUtil.send(receiver, message);
            }
        }

        // Also log to console
        plugin.getLogger()
                .warning(
                        "[XRay] "
                                + player.getName()
                                + " - Suspicion: "
                                + suspicion
                                + "% | Ores: "
                                + tracker.getTotalOres()
                                + " | Unexposed: "
                                + tracker.getUnexposedOres());
    }

    private String formatBlockName(Material material) {
        return switch (material) {
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> "diamonds";
            case ANCIENT_DEBRIS -> "ancient debris";
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> "emeralds";
            default -> material.name().toLowerCase().replace("_", " ");
        };
    }

    private String formatTime(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else {
            return (seconds / 60) + "m";
        }
    }

    private void cleanupOldEvents() {
        long cutoff =
                System.currentTimeMillis() - (plugin.getAdminConfig().getTimeWindow() * 1000L);
        for (XrayTracker tracker : trackers.values()) {
            tracker.pruneOldEvents(cutoff);
        }

        // Remove empty trackers
        trackers.entrySet().removeIf(entry -> entry.getValue().getRecentEventCount() == 0);
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

    public XrayTracker getTracker(UUID playerId) {
        return trackers.get(playerId);
    }

    public void clearTracker(UUID playerId) {
        trackers.remove(playerId);
    }
}
