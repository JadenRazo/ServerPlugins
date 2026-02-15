package net.serverplugins.api.broadcast;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import net.serverplugins.api.effects.CustomSound;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;

/** Parses broadcast messages from YAML configuration. */
public class BroadcastParser {
    private final Logger logger;

    public BroadcastParser(Logger logger) {
        this.logger = logger;
    }

    /**
     * Parse all broadcasts from a config section.
     *
     * @param section The "broadcasts:" section
     * @return Map of message key -> BroadcastMessage
     */
    public Map<String, BroadcastMessage> parseAll(ConfigurationSection section) {
        Map<String, BroadcastMessage> messages = new HashMap<>();

        if (section == null) {
            return messages;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection msgSection = section.getConfigurationSection(key);
            if (msgSection == null) {
                logger.warning("Invalid broadcast config for key: " + key);
                continue;
            }

            try {
                BroadcastMessage message = parse(key, msgSection);
                messages.put(key, message);
            } catch (Exception e) {
                logger.warning("Failed to parse broadcast '" + key + "': " + e.getMessage());
            }
        }

        return messages;
    }

    /** Parse a single broadcast message. */
    private BroadcastMessage parse(String key, ConfigurationSection section) {
        // Required fields
        String content = section.getString("message");
        if (content == null) {
            throw new IllegalArgumentException("Missing 'message' field");
        }

        BroadcastType type = parseBroadcastType(section.getString("type", "CHAT"));
        BroadcastMessage message = new BroadcastMessage(key, content, type);

        // Sound
        if (section.contains("sound")) {
            message.setSound(parseSound(section.getConfigurationSection("sound")));
        }

        // Filter
        if (section.contains("filter")) {
            parseFilter(section.getConfigurationSection("filter"), message.getFilter());
        }

        // Type-specific fields
        switch (type) {
            case TITLE -> parseTitleFields(section, message);
            case BOSS_BAR -> parseBossBarFields(section, message);
            case CHAT -> parseChatFields(section, message);
        }

        return message;
    }

    /** Parse broadcast type with fallback. */
    private BroadcastType parseBroadcastType(String value) {
        try {
            return BroadcastType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown broadcast type '" + value + "', defaulting to CHAT");
            return BroadcastType.CHAT;
        }
    }

    /** Parse sound configuration. */
    private CustomSound parseSound(ConfigurationSection section) {
        if (section == null) return CustomSound.NONE;

        String soundName = section.getString("type");
        if (soundName == null) return CustomSound.NONE;

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            float volume = (float) section.getDouble("volume", 1.0);
            float pitch = (float) section.getDouble("pitch", 1.0);
            return new CustomSound(sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            logger.warning("Unknown sound: " + soundName);
            return CustomSound.NONE;
        }
    }

    /** Parse filter configuration. */
    private void parseFilter(ConfigurationSection section, BroadcastFilter filter) {
        if (section == null) return;

        if (section.contains("permission")) {
            filter.permission(section.getString("permission"));
        }

        if (section.contains("world")) {
            Object worldValue = section.get("world");
            if (worldValue instanceof String) {
                filter.world((String) worldValue);
            } else if (worldValue instanceof Iterable) {
                for (Object world : (Iterable<?>) worldValue) {
                    filter.world(world.toString());
                }
            }
        }

        // Radius requires location, which must be set programmatically
    }

    /** Parse title-specific fields. */
    private void parseTitleFields(ConfigurationSection section, BroadcastMessage message) {
        if (section.contains("subtitle")) {
            message.setSubtitle(section.getString("subtitle"));
        }
        if (section.contains("fade-in")) {
            message.setFadeIn(section.getInt("fade-in", 10));
        }
        if (section.contains("stay")) {
            message.setStay(section.getInt("stay", 70));
        }
        if (section.contains("fade-out")) {
            message.setFadeOut(section.getInt("fade-out", 20));
        }
    }

    /** Parse boss bar-specific fields. */
    private void parseBossBarFields(ConfigurationSection section, BroadcastMessage message) {
        ConfigurationSection barSection = section.getConfigurationSection("boss-bar");
        if (barSection == null) return;

        if (barSection.contains("color")) {
            try {
                message.setBarColor(BarColor.valueOf(barSection.getString("color").toUpperCase()));
            } catch (IllegalArgumentException e) {
                logger.warning("Unknown boss bar color: " + barSection.getString("color"));
            }
        }

        if (barSection.contains("style")) {
            try {
                message.setBarStyle(BarStyle.valueOf(barSection.getString("style").toUpperCase()));
            } catch (IllegalArgumentException e) {
                logger.warning("Unknown boss bar style: " + barSection.getString("style"));
            }
        }

        if (barSection.contains("progress")) {
            message.setProgress(barSection.getDouble("progress", 1.0));
        }
    }

    /** Parse chat-specific fields (click/hover actions). */
    private void parseChatFields(ConfigurationSection section, BroadcastMessage message) {
        if (section.contains("click")) {
            ConfigurationSection clickSection = section.getConfigurationSection("click");
            if (clickSection != null) {
                String action = clickSection.getString("action");
                if (action != null) {
                    try {
                        message.setClickAction(
                                BroadcastMessage.ClickAction.valueOf(action.toUpperCase()));
                        message.setClickValue(clickSection.getString("value"));
                    } catch (IllegalArgumentException e) {
                        logger.warning("Unknown click action: " + action);
                    }
                }

                if (clickSection.contains("hover")) {
                    message.setHoverText(clickSection.getString("hover"));
                }
            }
        }
    }
}
