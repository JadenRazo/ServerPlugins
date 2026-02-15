package net.serverplugins.enchants.models;

import java.util.Map;
import org.bukkit.Material;

public class Essence {

    private final String name;
    private final Material material;
    private final String color;
    private final Map<String, Integer> properties;

    public Essence(String name, Material material, String color, Map<String, Integer> properties) {
        this.name = name;
        this.material = material;
        this.color = color;
        this.properties = properties;
    }

    public String getName() {
        return name;
    }

    public Material getMaterial() {
        return material;
    }

    public String getColor() {
        return color;
    }

    public Map<String, Integer> getProperties() {
        return properties;
    }

    public int getProperty(String key) {
        return properties.getOrDefault(key, 0);
    }
}
