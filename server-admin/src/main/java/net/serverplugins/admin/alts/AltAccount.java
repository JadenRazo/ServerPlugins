package net.serverplugins.admin.alts;

import java.util.UUID;

public class AltAccount {

    private final UUID uuid;
    private final String name;
    private final String ipHash;
    private final long firstSeen;
    private final long lastSeen;
    private final boolean online;
    private final boolean banned;

    public AltAccount(UUID uuid, String name, long lastSeen, boolean online, boolean banned) {
        this(uuid, name, null, 0, lastSeen, online, banned);
    }

    public AltAccount(
            UUID uuid,
            String name,
            String ipHash,
            long firstSeen,
            long lastSeen,
            boolean online,
            boolean banned) {
        this.uuid = uuid;
        this.name = name;
        this.ipHash = ipHash;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.online = online;
        this.banned = banned;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getIpHash() {
        return ipHash;
    }

    public long getFirstSeen() {
        return firstSeen;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public boolean isOnline() {
        return online;
    }

    public boolean isBanned() {
        return banned;
    }
}
