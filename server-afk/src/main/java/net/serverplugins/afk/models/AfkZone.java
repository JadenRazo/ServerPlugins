package net.serverplugins.afk.models;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class AfkZone {

    private int id;
    private String name;
    private String worldName;
    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;
    private int timeIntervalSeconds;
    private boolean enabled;
    private boolean useRankMultipliers;
    private String holoWorld;
    private Double holoX, holoY, holoZ;
    private List<ZoneReward> rewards;

    public AfkZone() {
        this.rewards = new ArrayList<>();
        this.timeIntervalSeconds = 300;
        this.enabled = true;
        this.useRankMultipliers = true;
    }

    public AfkZone(
            String name,
            String worldName,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ) {
        this();
        this.name = name;
        this.worldName = worldName;
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public boolean contains(Location loc) {
        if (loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(worldName)) return false;

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public int getWidth() {
        return maxX - minX + 1;
    }

    public int getHeight() {
        return maxY - minY + 1;
    }

    public int getDepth() {
        return maxZ - minZ + 1;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public int getMinX() {
        return minX;
    }

    public void setMinX(int minX) {
        this.minX = minX;
    }

    public int getMinY() {
        return minY;
    }

    public void setMinY(int minY) {
        this.minY = minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public void setMinZ(int minZ) {
        this.minZ = minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public void setMaxX(int maxX) {
        this.maxX = maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public void setMaxY(int maxY) {
        this.maxY = maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public void setMaxZ(int maxZ) {
        this.maxZ = maxZ;
    }

    public int getTimeIntervalSeconds() {
        return timeIntervalSeconds;
    }

    public void setTimeIntervalSeconds(int timeIntervalSeconds) {
        this.timeIntervalSeconds = timeIntervalSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<ZoneReward> getRewards() {
        return rewards;
    }

    public void setRewards(List<ZoneReward> rewards) {
        this.rewards = rewards;
    }

    public void addReward(ZoneReward reward) {
        this.rewards.add(reward);
    }

    public void removeReward(ZoneReward reward) {
        this.rewards.remove(reward);
    }

    // Rank multipliers
    public boolean usesRankMultipliers() {
        return useRankMultipliers;
    }

    public void setUseRankMultipliers(boolean use) {
        this.useRankMultipliers = use;
    }

    // Hologram location
    public String getHoloWorld() {
        return holoWorld;
    }

    public void setHoloWorld(String holoWorld) {
        this.holoWorld = holoWorld;
    }

    public Double getHoloX() {
        return holoX;
    }

    public void setHoloX(Double holoX) {
        this.holoX = holoX;
    }

    public Double getHoloY() {
        return holoY;
    }

    public void setHoloY(Double holoY) {
        this.holoY = holoY;
    }

    public Double getHoloZ() {
        return holoZ;
    }

    public void setHoloZ(Double holoZ) {
        this.holoZ = holoZ;
    }

    public Location getHologramLocation() {
        if (holoWorld == null || holoX == null || holoY == null || holoZ == null) {
            return null;
        }
        World world = Bukkit.getWorld(holoWorld);
        if (world == null) return null;
        return new Location(world, holoX, holoY, holoZ);
    }

    public void setHologramLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            this.holoWorld = null;
            this.holoX = null;
            this.holoY = null;
            this.holoZ = null;
            return;
        }
        this.holoWorld = loc.getWorld().getName();
        this.holoX = loc.getX();
        this.holoY = loc.getY();
        this.holoZ = loc.getZ();
    }

    public boolean hasHologram() {
        return holoWorld != null && holoX != null && holoY != null && holoZ != null;
    }
}
