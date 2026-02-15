package net.serverplugins.npcs.dialog;

public class Dialog {

    private final String id;
    private final String npcName;
    private final String displayName;
    private final DialogNode rootNode;

    public Dialog(String id, String npcName, String displayName, DialogNode rootNode) {
        this.id = id;
        this.npcName = npcName;
        this.displayName = displayName;
        this.rootNode = rootNode;
    }

    public String getId() {
        return id;
    }

    public String getNpcName() {
        return npcName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public DialogNode getRootNode() {
        return rootNode;
    }

    public static class Builder {
        private String id;
        private String npcName;
        private String displayName;
        private DialogNode rootNode;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder npcName(String npcName) {
            this.npcName = npcName;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder rootNode(DialogNode rootNode) {
            this.rootNode = rootNode;
            return this;
        }

        public Dialog build() {
            return new Dialog(id, npcName, displayName, rootNode);
        }
    }
}
