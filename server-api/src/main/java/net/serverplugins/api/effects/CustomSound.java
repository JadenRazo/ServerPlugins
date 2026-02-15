package net.serverplugins.api.effects;

import javax.annotation.Nullable;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Record representing a sound with volume and pitch. Sound can be null to represent silence. */
public record CustomSound(@Nullable Sound sound, float volume, float pitch) {

    /** A silent (no-op) sound constant. */
    public static final CustomSound NONE = new CustomSound(null, 0, 0);

    /** Create a sound with default volume and pitch. */
    public static CustomSound of(Sound sound) {
        return new CustomSound(sound, 1.0f, 1.0f);
    }

    /** Create a sound with custom volume and default pitch. */
    public static CustomSound of(Sound sound, float volume) {
        return new CustomSound(sound, volume, 1.0f);
    }

    /** Parse a sound from string format: "SOUND_NAME [volume] [pitch]" */
    public static CustomSound parse(String value) {
        if (value == null || value.isEmpty() || value.equalsIgnoreCase("none")) {
            return NONE;
        }

        String[] parts = value.split(" ");
        try {
            Sound sound = Sound.valueOf(parts[0].toUpperCase());
            float volume = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
            return new CustomSound(sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }

    /** Play this sound to a player at their location. */
    public void playSound(Player player) {
        if (player == null || sound == null) return;
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    /** Check if this is a silent sound. */
    public boolean isSilent() {
        return sound == null;
    }

    @Override
    public String toString() {
        if (sound == null) return "NONE";
        return String.format("%s %.2f %.2f", sound.name(), volume, pitch);
    }
}
