package net.serverplugins.keys.models;

import java.sql.Timestamp;
import java.util.UUID;

public class KeyStats {
    private final UUID uuid;
    private final String username;
    private final KeyType keyType;
    private final String keyName;
    private int totalReceived;
    private int totalUsed;
    private Timestamp lastUpdated;

    public KeyStats(
            UUID uuid,
            String username,
            KeyType keyType,
            String keyName,
            int totalReceived,
            int totalUsed,
            Timestamp lastUpdated) {
        this.uuid = uuid;
        this.username = username;
        this.keyType = keyType;
        this.keyName = keyName;
        this.totalReceived = totalReceived;
        this.totalUsed = totalUsed;
        this.lastUpdated = lastUpdated;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    public KeyType getKeyType() {
        return keyType;
    }

    public String getKeyName() {
        return keyName;
    }

    public int getTotalReceived() {
        return totalReceived;
    }

    public int getTotalUsed() {
        return totalUsed;
    }

    public Timestamp getLastUpdated() {
        return lastUpdated;
    }

    public void addReceived(int amount) {
        this.totalReceived += amount;
    }

    public void addUsed(int amount) {
        this.totalUsed += amount;
    }
}
