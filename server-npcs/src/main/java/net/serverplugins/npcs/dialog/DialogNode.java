package net.serverplugins.npcs.dialog;

import java.util.ArrayList;
import java.util.List;

public class DialogNode {

    private final String nodeId;
    private final List<String> messages;
    private final List<DialogChoice> choices;

    public DialogNode(String nodeId, List<String> messages, List<DialogChoice> choices) {
        this.nodeId = nodeId;
        this.messages = messages != null ? messages : new ArrayList<>();
        this.choices = choices != null ? choices : new ArrayList<>();
    }

    public String getNodeId() {
        return nodeId;
    }

    public List<String> getMessages() {
        return new ArrayList<>(messages);
    }

    public List<DialogChoice> getChoices() {
        return new ArrayList<>(choices);
    }

    public boolean hasChoices() {
        return !choices.isEmpty();
    }

    public static class Builder {
        private String nodeId;
        private List<String> messages = new ArrayList<>();
        private List<DialogChoice> choices = new ArrayList<>();

        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public Builder addMessage(String message) {
            this.messages.add(message);
            return this;
        }

        public Builder messages(List<String> messages) {
            this.messages = new ArrayList<>(messages);
            return this;
        }

        public Builder addChoice(DialogChoice choice) {
            this.choices.add(choice);
            return this;
        }

        public Builder choices(List<DialogChoice> choices) {
            this.choices = new ArrayList<>(choices);
            return this;
        }

        public DialogNode build() {
            return new DialogNode(nodeId, messages, choices);
        }
    }
}
