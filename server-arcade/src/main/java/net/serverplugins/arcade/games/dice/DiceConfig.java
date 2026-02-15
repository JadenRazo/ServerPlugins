package net.serverplugins.arcade.games.dice;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Configuration wrapper for the dice roll game. */
public class DiceConfig {

    private final YamlConfiguration config;
    private final Map<Integer, Double> overMultipliers = new HashMap<>();
    private final Map<Integer, Double> underMultipliers = new HashMap<>();
    private final Map<Integer, String> dieFaces = new HashMap<>();

    private int minBet;
    private int maxBet;
    private double exactMultiplier;
    private double oddMultiplier;
    private double evenMultiplier;
    private int animationDuration;
    private int rollFrames;
    private int frameDelay;

    public DiceConfig(YamlConfiguration config) {
        this.config = config;
        loadConfig();
    }

    private void loadConfig() {
        minBet = config.getInt("min_bet", 100);
        maxBet = config.getInt("max_bet", 100000);

        // Load multipliers
        ConfigurationSection multipliers = config.getConfigurationSection("multipliers");
        if (multipliers != null) {
            // Over multipliers
            ConfigurationSection over = multipliers.getConfigurationSection("over");
            if (over != null) {
                for (String key : over.getKeys(false)) {
                    overMultipliers.put(Integer.parseInt(key), over.getDouble(key));
                }
            }

            // Under multipliers
            ConfigurationSection under = multipliers.getConfigurationSection("under");
            if (under != null) {
                for (String key : under.getKeys(false)) {
                    underMultipliers.put(Integer.parseInt(key), under.getDouble(key));
                }
            }

            exactMultiplier = multipliers.getDouble("exact", 5.4);
            oddMultiplier = multipliers.getDouble("odd", 1.8);
            evenMultiplier = multipliers.getDouble("even", 1.8);
        }

        // Load animation settings
        animationDuration = config.getInt("animation_duration", 1000);
        rollFrames = config.getInt("roll_frames", 5);
        frameDelay = config.getInt("frame_delay", 200);

        // Load die faces
        ConfigurationSection faces = config.getConfigurationSection("die_faces");
        if (faces != null) {
            for (String key : faces.getKeys(false)) {
                dieFaces.put(Integer.parseInt(key), faces.getString(key));
            }
        }
    }

    public int getMinBet() {
        return minBet;
    }

    public int getMaxBet() {
        return maxBet;
    }

    public double getOverMultiplier(int threshold) {
        return overMultipliers.getOrDefault(threshold, 1.0);
    }

    public double getUnderMultiplier(int threshold) {
        return underMultipliers.getOrDefault(threshold, 1.0);
    }

    public double getExactMultiplier() {
        return exactMultiplier;
    }

    public double getOddMultiplier() {
        return oddMultiplier;
    }

    public double getEvenMultiplier() {
        return evenMultiplier;
    }

    public int getAnimationDuration() {
        return animationDuration;
    }

    public int getRollFrames() {
        return rollFrames;
    }

    public int getFrameDelay() {
        return frameDelay;
    }

    public String getDieFace(int number) {
        return dieFaces.getOrDefault(number, String.valueOf(number));
    }

    public String getMessage(String key) {
        return config.getString("messages." + key, "").replace("&", "§");
    }
}
