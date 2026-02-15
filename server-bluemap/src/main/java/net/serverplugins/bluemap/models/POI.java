package net.serverplugins.bluemap.models;

import java.sql.Timestamp;
import java.util.UUID;

public class POI {
    private int id;
    private String name;
    private String world;
    private int x;
    private int y;
    private int z;
    private String category;
    private String description;
    private UUID creatorUuid;
    private Timestamp createdAt;
    private boolean visible;

    public POI() {
        this.visible = true;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    public POI(
            int id,
            String name,
            String world,
            int x,
            int y,
            int z,
            String category,
            String description,
            UUID creatorUuid,
            Timestamp createdAt,
            boolean visible) {
        this.id = id;
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.category = category;
        this.description = description;
        this.creatorUuid = creatorUuid;
        this.createdAt = createdAt;
        this.visible = visible;
    }

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

    public String getWorld() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getZ() {
        return z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public UUID getCreatorUuid() {
        return creatorUuid;
    }

    public void setCreatorUuid(UUID creatorUuid) {
        this.creatorUuid = creatorUuid;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}
