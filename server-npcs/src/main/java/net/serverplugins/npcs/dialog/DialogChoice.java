package net.serverplugins.npcs.dialog;

public class DialogChoice {

    private final String text;
    private final String command;
    private final String permission;
    private final int delay;
    private final boolean close;
    private final String nextNodeId;

    private DialogChoice(Builder builder) {
        this.text = builder.text;
        this.command = builder.command;
        this.permission = builder.permission;
        this.delay = builder.delay;
        this.close = builder.close;
        this.nextNodeId = builder.nextNodeId;
    }

    public String getText() {
        return text;
    }

    public String getCommand() {
        return command;
    }

    public String getPermission() {
        return permission;
    }

    public int getDelay() {
        return delay;
    }

    public boolean shouldClose() {
        return close;
    }

    public String getNextNodeId() {
        return nextNodeId;
    }

    public boolean hasPermission() {
        return permission != null && !permission.isEmpty();
    }

    public boolean hasCommand() {
        return command != null && !command.isEmpty();
    }

    public boolean hasNextNode() {
        return nextNodeId != null && !nextNodeId.isEmpty();
    }

    public static class Builder {
        private String text = "";
        private String command;
        private String permission;
        private int delay = 0;
        private boolean close = false;
        private String nextNodeId;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder command(String command) {
            this.command = command;
            return this;
        }

        public Builder permission(String permission) {
            this.permission = permission;
            return this;
        }

        public Builder delay(int delay) {
            this.delay = delay;
            return this;
        }

        public Builder close(boolean close) {
            this.close = close;
            return this;
        }

        public Builder nextNode(String nextNodeId) {
            this.nextNodeId = nextNodeId;
            return this;
        }

        public DialogChoice build() {
            return new DialogChoice(this);
        }
    }
}
