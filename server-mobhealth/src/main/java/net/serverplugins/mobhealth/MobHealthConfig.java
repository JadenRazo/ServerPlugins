package net.serverplugins.mobhealth;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.serverplugins.api.messages.PluginMessenger;
import org.bukkit.entity.EntityType;

public class MobHealthConfig {

    private final ServerMobHealth plugin;
    private PluginMessenger messenger;

    private String displayFormat;
    private int displayDuration;
    private double yOffset;
    private String colorHigh;
    private String colorMedium;
    private String colorLow;
    private String heartSymbol;
    private boolean playerDamageOnly;
    private Set<EntityType> excludedEntities;

    public MobHealthConfig(ServerMobHealth plugin) {
        this.plugin = plugin;
        reload();
    }

    public final void reload() {
        displayFormat = plugin.getConfig().getString("display-format", "{name} {health}");
        displayDuration = plugin.getConfig().getInt("display-duration", 5);
        yOffset = plugin.getConfig().getDouble("y-offset", 0.3);
        colorHigh = plugin.getConfig().getString("colors.high", "<green>");
        colorMedium = plugin.getConfig().getString("colors.medium", "<yellow>");
        colorLow = plugin.getConfig().getString("colors.low", "<red>");
        heartSymbol = plugin.getConfig().getString("heart-symbol", "\u2764");
        playerDamageOnly = plugin.getConfig().getBoolean("player-damage-only", true);

        excludedEntities = new HashSet<>();
        for (String name : plugin.getConfig().getStringList("excluded-entities")) {
            try {
                excludedEntities.add(EntityType.valueOf(name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Unknown entity type in excluded-entities: " + name);
            }
        }

        messenger =
                new PluginMessenger(
                        plugin.getConfig(),
                        "messages",
                        "<gradient:#e74c3c:#ff6b6b>[MobHealth]</gradient> ");
    }

    public String getHealthColor(double healthPercent) {
        if (healthPercent > 0.66) {
            return colorHigh;
        } else if (healthPercent > 0.33) {
            return colorMedium;
        } else {
            return colorLow;
        }
    }

    public boolean isExcluded(EntityType type) {
        return excludedEntities.contains(type);
    }

    public String getDisplayFormat() {
        return displayFormat;
    }

    public int getDisplayDuration() {
        return displayDuration;
    }

    public double getYOffset() {
        return yOffset;
    }

    public String getHeartSymbol() {
        return heartSymbol;
    }

    public boolean isPlayerDamageOnly() {
        return playerDamageOnly;
    }

    public PluginMessenger getMessenger() {
        return messenger;
    }
}
