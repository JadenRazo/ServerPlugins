package net.serverplugins.npcs.models;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NpcData {

    private final UUID npcUuid;
    private final Map<String, Object> data;

    public NpcData(UUID npcUuid) {
        this.npcUuid = npcUuid;
        this.data = new HashMap<>();
    }

    public UUID getNpcUuid() {
        return npcUuid;
    }

    public void set(String key, Object value) {
        data.put(key, value);
    }

    public Object get(String key) {
        return data.get(key);
    }

    public String getString(String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    public int getInt(String key, int defaultValue) {
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = data.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    public boolean has(String key) {
        return data.containsKey(key);
    }

    public void remove(String key) {
        data.remove(key);
    }

    public Map<String, Object> getAllData() {
        return new HashMap<>(data);
    }
}
