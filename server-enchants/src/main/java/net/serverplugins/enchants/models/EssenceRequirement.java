package net.serverplugins.enchants.models;

import java.util.Map;

public class EssenceRequirement {

    private final Map<String, Integer> required;

    public EssenceRequirement(Map<String, Integer> required) {
        this.required = required;
    }

    public Map<String, Integer> getRequired() {
        return required;
    }

    public boolean isSatisfiedBy(Map<String, Integer> current) {
        for (Map.Entry<String, Integer> entry : required.entrySet()) {
            int currentVal = current.getOrDefault(entry.getKey(), 0);
            if (currentVal != entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    public int getRequiredValue(String property) {
        return required.getOrDefault(property, 0);
    }
}
