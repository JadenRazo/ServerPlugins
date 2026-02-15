package net.serverplugins.filter.data;

import java.util.UUID;

public class PlayerFilterData {

    private final UUID uuid;
    private final String playerName;
    private FilterLevel filterLevel;
    private long lastUpdated;

    public PlayerFilterData(UUID uuid, String playerName, FilterLevel filterLevel) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.filterLevel = filterLevel;
        this.lastUpdated = System.currentTimeMillis();
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public FilterLevel getFilterLevel() {
        return filterLevel;
    }

    public void setFilterLevel(FilterLevel filterLevel) {
        this.filterLevel = filterLevel;
        this.lastUpdated = System.currentTimeMillis();
    }

    public long getLastUpdated() {
        return lastUpdated;
    }
}
