package net.serverplugins.api.broadcast;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.effects.CustomSound;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.plugin.Plugin;

/** Fluent builder for creating broadcast messages programmatically. */
public class BroadcastBuilder {
    private final String key;
    private String message;
    private BroadcastType type = BroadcastType.CHAT;
    private final BroadcastFilter filter = new BroadcastFilter();
    private CustomSound sound = CustomSound.NONE;
    private final List<Placeholder> placeholders = new ArrayList<>();
    private Plugin plugin; // For boss bar scheduling

    // Title fields
    private String subtitle;
    private int fadeIn = 10;
    private int stay = 70;
    private int fadeOut = 20;

    // Boss bar fields
    private BarColor barColor = BarColor.PURPLE;
    private BarStyle barStyle = BarStyle.SOLID;
    private double progress = 1.0;

    public BroadcastBuilder(String key) {
        this.key = key;
    }

    /** Set the message content. */
    public BroadcastBuilder message(String message) {
        this.message = message;
        return this;
    }

    /** Set the broadcast type. */
    public BroadcastBuilder type(BroadcastType type) {
        this.type = type;
        return this;
    }

    /** Add a custom placeholder. */
    public BroadcastBuilder placeholder(String key, String value) {
        this.placeholders.add(Placeholder.of(key, value));
        return this;
    }

    /** Add multiple placeholders. */
    public BroadcastBuilder placeholders(Placeholder... placeholders) {
        for (Placeholder p : placeholders) {
            this.placeholders.add(p);
        }
        return this;
    }

    /** Set a sound effect. */
    public BroadcastBuilder sound(Sound sound, float volume, float pitch) {
        this.sound = new CustomSound(sound, volume, pitch);
        return this;
    }

    /** Set a sound effect with default volume/pitch. */
    public BroadcastBuilder sound(Sound sound) {
        this.sound = CustomSound.of(sound);
        return this;
    }

    /** Filter by permission. */
    public BroadcastBuilder filterPermission(String permission) {
        this.filter.permission(permission);
        return this;
    }

    /** Filter by world(s). */
    public BroadcastBuilder filterWorld(String... worlds) {
        this.filter.world(worlds);
        return this;
    }

    /** Filter by world. */
    public BroadcastBuilder filterWorld(World world) {
        this.filter.world(world);
        return this;
    }

    /** Filter by radius around a location. */
    public BroadcastBuilder filterRadius(Location center, double radius) {
        this.filter.radius(center, radius);
        return this;
    }

    // Title-specific methods

    /** Set subtitle (for titles). */
    public BroadcastBuilder subtitle(String subtitle) {
        this.subtitle = subtitle;
        return this;
    }

    /** Set title timings (for titles). */
    public BroadcastBuilder titleTiming(int fadeIn, int stay, int fadeOut) {
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
        return this;
    }

    // Boss bar-specific methods

    /** Set boss bar color. */
    public BroadcastBuilder bossBarColor(BarColor color) {
        this.barColor = color;
        return this;
    }

    /** Set boss bar style. */
    public BroadcastBuilder bossBarStyle(BarStyle style) {
        this.barStyle = style;
        return this;
    }

    /** Set boss bar progress (0.0-1.0). */
    public BroadcastBuilder bossBarProgress(double progress) {
        this.progress = Math.max(0.0, Math.min(1.0, progress));
        return this;
    }

    /** Set the plugin for scheduling (required for boss bars). */
    public BroadcastBuilder plugin(Plugin plugin) {
        this.plugin = plugin;
        return this;
    }

    /** Build and send the broadcast. */
    public void send() {
        if (message == null) {
            throw new IllegalStateException("Message content is required");
        }

        BroadcastMessage msg = build();
        msg.send(placeholders.toArray(new Placeholder[0]));
    }

    /** Build the BroadcastMessage without sending. */
    public BroadcastMessage build() {
        if (message == null) {
            throw new IllegalStateException("Message content is required");
        }

        BroadcastMessage msg = new BroadcastMessage(key, message, type);
        msg.setSound(sound);
        msg.setPlugin(plugin);

        // Copy filter settings
        if (filter.getPermission() != null) {
            msg.getFilter().permission(filter.getPermission());
        }
        for (String world : filter.getWorlds()) {
            msg.getFilter().world(world);
        }
        if (filter.getRadius() > 0) {
            msg.getFilter().radius(filter.getCenter(), filter.getRadius());
        }

        // Title fields
        if (type == BroadcastType.TITLE) {
            msg.setSubtitle(subtitle);
            msg.setFadeIn(fadeIn);
            msg.setStay(stay);
            msg.setFadeOut(fadeOut);
        }

        // Boss bar fields
        if (type == BroadcastType.BOSS_BAR) {
            msg.setBarColor(barColor);
            msg.setBarStyle(barStyle);
            msg.setProgress(progress);
        }

        return msg;
    }
}
