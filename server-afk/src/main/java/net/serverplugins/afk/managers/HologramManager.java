package net.serverplugins.afk.managers;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.afk.ServerAFK;
import net.serverplugins.afk.models.AfkZone;
import net.serverplugins.afk.models.ZoneReward;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

public class HologramManager {

    private final ServerAFK plugin;
    private final Map<Integer, Hologram> zoneHolograms;
    private BukkitTask updateTask;

    public HologramManager(ServerAFK plugin) {
        this.plugin = plugin;
        this.zoneHolograms = new ConcurrentHashMap<>();
    }

    public void start() {
        loadExistingHolograms();
        startUpdateTask();
    }

    public void stop() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        deleteAllHolograms();
    }

    private void loadExistingHolograms() {
        for (AfkZone zone : plugin.getZoneManager().getAllZones()) {
            if (zone.hasHologram()) {
                Location loc = zone.getHologramLocation();
                if (loc != null) {
                    createHologram(zone, loc);
                }
            }
        }
    }

    private void startUpdateTask() {
        int intervalSeconds = plugin.getAfkConfig().getHologramUpdateInterval();
        updateTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimer(
                                plugin,
                                this::updateAllHolograms,
                                20L * intervalSeconds,
                                20L * intervalSeconds);
    }

    public void createHologram(AfkZone zone, Location location) {
        deleteHologram(zone);

        String holoName = "serverafk_zone_" + zone.getId();
        List<String> lines = getHologramContent(zone);

        try {
            Hologram hologram = DHAPI.createHologram(holoName, location, lines);
            zoneHolograms.put(zone.getId(), hologram);

            zone.setHologramLocation(location);
            plugin.getZoneManager().updateZone(zone);
        } catch (Exception e) {
            plugin.getLogger()
                    .warning(
                            "Failed to create hologram for zone "
                                    + zone.getName()
                                    + ": "
                                    + e.getMessage());
        }
    }

    public void deleteHologram(AfkZone zone) {
        Hologram existing = zoneHolograms.remove(zone.getId());
        if (existing != null) {
            existing.delete();
        }

        String holoName = "serverafk_zone_" + zone.getId();
        Hologram byName = DHAPI.getHologram(holoName);
        if (byName != null) {
            byName.delete();
        }

        zone.setHologramLocation(null);
        plugin.getZoneManager().updateZone(zone);
    }

    public void deleteAllHolograms() {
        for (Hologram holo : zoneHolograms.values()) {
            try {
                holo.delete();
            } catch (Exception ignored) {
            }
        }
        zoneHolograms.clear();
    }

    public void updateAllHolograms() {
        for (AfkZone zone : plugin.getZoneManager().getAllZones()) {
            updateHologram(zone);
        }
    }

    public void updateHologram(AfkZone zone) {
        Hologram hologram = zoneHolograms.get(zone.getId());
        if (hologram == null) return;

        List<String> lines = getHologramContent(zone);
        try {
            DHAPI.setHologramLines(hologram, lines);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update hologram for zone " + zone.getName());
        }
    }

    private List<String> getHologramContent(AfkZone zone) {
        List<String> lines = new ArrayList<>();

        lines.add("&e&l" + zone.getName().toUpperCase());
        lines.add("&7━━━━━━━━━━━━━━━━");

        int intervalMinutes = zone.getTimeIntervalSeconds() / 60;
        String intervalStr =
                intervalMinutes > 0
                        ? intervalMinutes + " min"
                        : zone.getTimeIntervalSeconds() + "s";
        lines.add("&fRewards every &e" + intervalStr);

        lines.add("");
        lines.add("&6Rewards:");
        for (ZoneReward reward : zone.getRewards()) {
            if (reward.getType() == ZoneReward.RewardType.CURRENCY) {
                lines.add("&7• &f$" + (int) reward.getCurrencyAmount());
            } else {
                lines.add("&7• &fItem Reward");
            }
        }

        if (zone.usesRankMultipliers()) {
            Map<String, Double> multipliers = plugin.getAfkConfig().getAllMultipliers();
            boolean hasMultipliers = multipliers.values().stream().anyMatch(m -> m > 1.0);

            if (hasMultipliers) {
                lines.add("");
                lines.add("&d&lRank Bonuses:");
                for (Map.Entry<String, Double> entry : multipliers.entrySet()) {
                    if (entry.getValue() > 1.0) {
                        lines.add(
                                "&7• &f"
                                        + capitalize(entry.getKey())
                                        + ": &a"
                                        + entry.getValue()
                                        + "x");
                    }
                }
            }
        }

        lines.add("&7━━━━━━━━━━━━━━━━");

        int playerCount = plugin.getPlayerTracker().getPlayersInZone(zone.getId()).size();
        lines.add("&aPlayers: &f" + playerCount);

        return lines;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    public boolean hasHologram(AfkZone zone) {
        return zoneHolograms.containsKey(zone.getId());
    }
}
