package net.serverplugins.keys.models;

import java.sql.Timestamp;
import java.util.UUID;

public class UnclaimedKey {
    private final int id;
    private final UUID uuid;
    private final String username;
    private final KeyType keyType;
    private final String keyName;
    private final int amount;
    private final String source;
    private final Timestamp createdAt;

    public UnclaimedKey(
            int id,
            UUID uuid,
            String username,
            KeyType keyType,
            String keyName,
            int amount,
            String source,
            Timestamp createdAt) {
        this.id = id;
        this.uuid = uuid;
        this.username = username;
        this.keyType = keyType;
        this.keyName = keyName;
        this.amount = amount;
        this.source = source;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
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

    public int getAmount() {
        return amount;
    }

    public String getSource() {
        return source;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }
}
