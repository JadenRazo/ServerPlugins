package net.serverplugins.admin.disguise;

import java.util.UUID;

public class DisguiseSession {

    private final UUID playerId;
    private final String originalDisplayName;
    private final String originalPlayerListName;
    private final String disguisedAsName;
    private final UUID disguisedAsUuid;
    private int actionBarTaskId = -1;

    public DisguiseSession(
            UUID playerId,
            String originalDisplayName,
            String originalPlayerListName,
            String disguisedAsName,
            UUID disguisedAsUuid) {
        this.playerId = playerId;
        this.originalDisplayName = originalDisplayName;
        this.originalPlayerListName = originalPlayerListName;
        this.disguisedAsName = disguisedAsName;
        this.disguisedAsUuid = disguisedAsUuid;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getOriginalDisplayName() {
        return originalDisplayName;
    }

    public String getOriginalPlayerListName() {
        return originalPlayerListName;
    }

    public String getDisguisedAsName() {
        return disguisedAsName;
    }

    public UUID getDisguisedAsUuid() {
        return disguisedAsUuid;
    }

    public int getActionBarTaskId() {
        return actionBarTaskId;
    }

    public void setActionBarTaskId(int actionBarTaskId) {
        this.actionBarTaskId = actionBarTaskId;
    }

    public boolean hasActionBarTask() {
        return actionBarTaskId != -1;
    }
}
