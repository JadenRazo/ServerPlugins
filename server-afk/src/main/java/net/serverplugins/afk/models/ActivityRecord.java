package net.serverplugins.afk.models;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.time.LocalDateTime;
import java.util.UUID;
import org.bukkit.Location;

public class ActivityRecord {

    public enum ActivityType {
        MOVEMENT,
        CHAT,
        COMMAND,
        BLOCK_BREAK,
        BLOCK_PLACE,
        INTERACT,
        INVENTORY_CLICK,
        JUMP,
        SNEAK,
        SPRINT,
        DAMAGE_TAKEN,
        DAMAGE_DEALT
    }

    private static final Gson GSON = new Gson();

    private int id;
    private UUID playerUuid;
    private ActivityType activityType;
    private LocalDateTime timestamp;
    private int locationX;
    private int locationY;
    private int locationZ;
    private String world;
    private String metadata;

    public ActivityRecord(UUID playerUuid, ActivityType activityType, Location location) {
        this.playerUuid = playerUuid;
        this.activityType = activityType;
        this.timestamp = LocalDateTime.now();
        this.locationX = location.getBlockX();
        this.locationY = location.getBlockY();
        this.locationZ = location.getBlockZ();
        this.world = location.getWorld() != null ? location.getWorld().getName() : "unknown";
        this.metadata = null;
    }

    public ActivityRecord() {
        // Empty constructor for database mapping
    }

    // Utility methods for metadata

    public void setMetadataFromJson(JsonObject json) {
        this.metadata = GSON.toJson(json);
    }

    public JsonObject getMetadataAsJson() {
        if (metadata == null || metadata.isEmpty()) {
            return new JsonObject();
        }
        try {
            return GSON.fromJson(metadata, JsonObject.class);
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    public void addMetadata(String key, String value) {
        JsonObject json = getMetadataAsJson();
        json.addProperty(key, value);
        setMetadataFromJson(json);
    }

    public void addMetadata(String key, int value) {
        JsonObject json = getMetadataAsJson();
        json.addProperty(key, value);
        setMetadataFromJson(json);
    }

    public void addMetadata(String key, boolean value) {
        JsonObject json = getMetadataAsJson();
        json.addProperty(key, value);
        setMetadataFromJson(json);
    }

    public Location toLocation(org.bukkit.Server server) {
        org.bukkit.World bukkitWorld = server.getWorld(world);
        if (bukkitWorld == null) {
            return null;
        }
        return new Location(bukkitWorld, locationX, locationY, locationZ);
    }

    public double distanceTo(ActivityRecord other) {
        if (!this.world.equals(other.world)) {
            return Double.MAX_VALUE;
        }
        int dx = this.locationX - other.locationX;
        int dy = this.locationY - other.locationY;
        int dz = this.locationZ - other.locationZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // Getters and Setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public ActivityType getActivityType() {
        return activityType;
    }

    public void setActivityType(ActivityType activityType) {
        this.activityType = activityType;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getLocationX() {
        return locationX;
    }

    public void setLocationX(int locationX) {
        this.locationX = locationX;
    }

    public int getLocationY() {
        return locationY;
    }

    public void setLocationY(int locationY) {
        this.locationY = locationY;
    }

    public int getLocationZ() {
        return locationZ;
    }

    public void setLocationZ(int locationZ) {
        this.locationZ = locationZ;
    }

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
