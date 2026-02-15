package net.serverplugins.npcs.models;

import org.bukkit.Location;

public class Npc {

    private final String id;
    private final String name;
    private final String displayName;
    private final String dialogId;
    private final Location location;

    public Npc(String id, String name, String displayName, String dialogId, Location location) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.dialogId = dialogId;
        this.location = location;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDialogId() {
        return dialogId;
    }

    public Location getLocation() {
        return location;
    }

    public static class Builder {
        private String id;
        private String name;
        private String displayName;
        private String dialogId;
        private Location location;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder dialogId(String dialogId) {
            this.dialogId = dialogId;
            return this;
        }

        public Builder location(Location location) {
            this.location = location;
            return this;
        }

        public Npc build() {
            return new Npc(id, name, displayName, dialogId, location);
        }
    }
}
