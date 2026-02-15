package net.serverplugins.afk.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.afk.ServerAFK;
import net.serverplugins.afk.models.AfkZone;
import net.serverplugins.afk.models.ZoneReward;
import net.serverplugins.afk.repository.AfkRepository;
import org.bukkit.Location;

public class ZoneManager {

    private final ServerAFK plugin;
    private final AfkRepository repository;
    private final ConcurrentHashMap<Integer, AfkZone> zoneCache;

    public ZoneManager(ServerAFK plugin, AfkRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.zoneCache = new ConcurrentHashMap<>();
        loadZones();
    }

    public final void loadZones() {
        zoneCache.clear();
        List<AfkZone> zones = repository.getAllZones();
        for (AfkZone zone : zones) {
            zoneCache.put(zone.getId(), zone);
        }
        plugin.getLogger().info("Loaded " + zones.size() + " AFK zones");
    }

    public List<AfkZone> getAllZones() {
        return new ArrayList<>(zoneCache.values());
    }

    public List<AfkZone> getEnabledZones() {
        return zoneCache.values().stream().filter(AfkZone::isEnabled).toList();
    }

    public Optional<AfkZone> getZoneById(int id) {
        return Optional.ofNullable(zoneCache.get(id));
    }

    public Optional<AfkZone> getZoneByName(String name) {
        return zoneCache.values().stream()
                .filter(z -> z.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    public Optional<AfkZone> getZoneAt(Location location) {
        return getEnabledZones().stream().filter(z -> z.contains(location)).findFirst();
    }

    public AfkZone createZone(String name, Location corner1, Location corner2) {
        if (corner1.getWorld() == null) return null;

        AfkZone zone =
                new AfkZone(
                        name,
                        corner1.getWorld().getName(),
                        corner1.getBlockX(),
                        corner1.getBlockY(),
                        corner1.getBlockZ(),
                        corner2.getBlockX(),
                        corner2.getBlockY(),
                        corner2.getBlockZ());

        zone.setTimeIntervalSeconds(plugin.getAfkConfig().getDefaultTimeInterval());

        zone = repository.createZone(zone);
        zoneCache.put(zone.getId(), zone);

        return zone;
    }

    public void updateZone(AfkZone zone) {
        repository.updateZone(zone);
        zoneCache.put(zone.getId(), zone);
    }

    public void deleteZone(AfkZone zone) {
        // Delete hologram if it exists
        if (zone.hasHologram() && plugin.getHologramManager() != null) {
            plugin.getHologramManager().deleteHologram(zone);
        }

        // End all active sessions in this zone
        plugin.getPlayerTracker()
                .getPlayersInZone(zone.getId())
                .forEach(
                        playerId -> {
                            plugin.getPlayerTracker().endSession(playerId);
                        });

        // Delete from database (will cascade delete rewards)
        repository.deleteZone(zone.getId());
        zoneCache.remove(zone.getId());
    }

    public boolean zoneExists(String name) {
        return getZoneByName(name).isPresent();
    }

    // Reward management

    public ZoneReward addReward(AfkZone zone, ZoneReward reward) {
        reward.setZoneId(zone.getId());
        reward = repository.createReward(reward);
        zone.addReward(reward);
        return reward;
    }

    public void updateReward(ZoneReward reward) {
        repository.updateReward(reward);
    }

    public void removeReward(AfkZone zone, ZoneReward reward) {
        repository.deleteReward(reward.getId());
        zone.removeReward(reward);
    }

    public void reload() {
        loadZones();
    }
}
