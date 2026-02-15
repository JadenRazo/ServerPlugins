package net.serverplugins.api.configuration.parsers;

import net.serverplugins.api.effects.CustomSound;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;

/** Parser for CustomSound objects. Format: "SOUND_NAME [volume] [pitch]" or section format. */
public class CustomSoundParser extends Parser<CustomSound> {

    private static final CustomSoundParser INSTANCE = new CustomSoundParser();

    private CustomSoundParser() {}

    public static CustomSoundParser getInstance() {
        return INSTANCE;
    }

    @Override
    public CustomSound loadFromConfig(ConfigurationSection config, String path) {
        if (config == null) return CustomSound.NONE;

        // Try string format first
        String value = path != null ? config.getString(path) : null;
        if (value != null && !value.isEmpty()) {
            return parseFromString(value);
        }

        // Try section format
        ConfigurationSection section = path != null ? config.getConfigurationSection(path) : config;
        if (section != null && section.contains("sound")) {
            return loadFromSection(section);
        }

        return CustomSound.NONE;
    }

    /** Parse from string format: "SOUND_NAME [volume] [pitch]" */
    public CustomSound parseFromString(String value) {
        return CustomSound.parse(value);
    }

    /** Load from section format with sound, volume, pitch keys. */
    private CustomSound loadFromSection(ConfigurationSection section) {
        String soundName = section.getString("sound", "");
        if (soundName.isEmpty() || soundName.equalsIgnoreCase("none")) {
            return CustomSound.NONE;
        }

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            float volume = (float) section.getDouble("volume", 1.0);
            float pitch = (float) section.getDouble("pitch", 1.0);
            return new CustomSound(sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            warning("Invalid sound: " + soundName);
            return CustomSound.NONE;
        }
    }

    @Override
    public void saveToConfig(ConfigurationSection config, String path, CustomSound sound) {
        if (config == null) return;

        if (sound == null || sound.isSilent()) {
            config.set(path, "NONE");
        } else {
            config.set(path, sound.toString());
        }
    }
}
