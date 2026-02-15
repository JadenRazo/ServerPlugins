package net.serverplugins.api.broadcast;

import java.util.HashMap;
import java.util.Map;
import net.serverplugins.api.effects.CustomSound;
import net.serverplugins.api.handlers.PlaceholderHandler;
import net.serverplugins.api.messages.Message;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Represents a broadcast message template with all metadata. Can be rendered for specific players
 * with custom placeholders.
 */
public class BroadcastMessage {
    private final String key;
    private String content;
    private final BroadcastType type;
    private final BroadcastFilter filter;
    private CustomSound sound;
    private Plugin plugin; // Plugin for scheduling tasks

    // Title-specific
    private String subtitle;
    private int fadeIn = 10;
    private int stay = 70;
    private int fadeOut = 20;

    // Boss bar-specific
    private BarColor barColor = BarColor.PURPLE;
    private BarStyle barStyle = BarStyle.SOLID;
    private double progress = 1.0;

    // Click/hover actions (for chat messages)
    private ClickAction clickAction;
    private String clickValue;
    private String hoverText;

    public BroadcastMessage(String key, String content, BroadcastType type) {
        this.key = key;
        this.content = content;
        this.type = type;
        this.filter = new BroadcastFilter();
        this.sound = CustomSound.NONE;
    }

    /**
     * Render this message for a specific player with custom placeholders.
     *
     * @param player The player context (null for console broadcasts)
     * @param placeholders Custom placeholders to apply
     * @return The rendered Message
     */
    public Message render(Player player, Placeholder... placeholders) {
        String rendered = content;

        // Apply custom placeholders first (curly brace format)
        for (Placeholder placeholder : placeholders) {
            rendered = placeholder.apply(rendered);
        }

        // Then apply PAPI placeholders (percent format)
        if (player != null) {
            rendered = PlaceholderHandler.parse(player, rendered);
        }

        return Message.fromText(rendered);
    }

    /** Render subtitle with placeholders. */
    public Message renderSubtitle(Player player, Placeholder... placeholders) {
        if (subtitle == null) return Message.empty();

        String rendered = subtitle;
        for (Placeholder placeholder : placeholders) {
            rendered = placeholder.apply(rendered);
        }
        if (player != null) {
            rendered = PlaceholderHandler.parse(player, rendered);
        }
        return Message.fromText(rendered);
    }

    /** Render hover text with placeholders. */
    public String renderHoverText(Player player, Placeholder... placeholders) {
        if (hoverText == null) return null;

        String rendered = hoverText;
        for (Placeholder placeholder : placeholders) {
            rendered = placeholder.apply(rendered);
        }
        if (player != null) {
            rendered = PlaceholderHandler.parse(player, rendered);
        }
        return rendered;
    }

    /**
     * Send this broadcast to all eligible players.
     *
     * @param placeholders Custom placeholders
     */
    public void send(Placeholder... placeholders) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!filter.test(player)) continue;

            sendToPlayer(player, placeholders);
        }
    }

    /**
     * Send this broadcast to a specific player.
     *
     * @param player The player
     * @param placeholders Custom placeholders
     */
    public void sendToPlayer(Player player, Placeholder... placeholders) {
        Message message = render(player, placeholders);

        switch (type) {
            case CHAT -> message.sendMessage(player);
            case ACTION_BAR -> message.sendActionbar(player);
            case TITLE -> {
                Message sub = renderSubtitle(player, placeholders);
                player.sendTitle(message.toString(), sub.toString(), fadeIn, stay, fadeOut);
            }
            case BOSS_BAR -> {
                // Create temporary boss bar
                BossBar bar = Bukkit.createBossBar(message.toString(), barColor, barStyle);
                bar.setProgress(progress);
                bar.addPlayer(player);

                // Remove after duration (only if we have a plugin for scheduling)
                if (plugin != null) {
                    Bukkit.getScheduler()
                            .runTaskLater(plugin, () -> bar.removePlayer(player), stay);
                } else {
                    // Fallback: remove after 5 seconds if no plugin available
                    Bukkit.getScheduler()
                            .runTaskLater(
                                    Bukkit.getPluginManager().getPlugins()[0],
                                    () -> bar.removePlayer(player),
                                    100L);
                }
            }
        }

        // Play sound
        if (!sound.isSilent()) {
            sound.playSound(player);
        }
    }

    // Getters and setters
    public String getKey() {
        return key;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public BroadcastType getType() {
        return type;
    }

    public BroadcastFilter getFilter() {
        return filter;
    }

    public CustomSound getSound() {
        return sound;
    }

    public void setSound(CustomSound sound) {
        this.sound = sound;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public int getFadeIn() {
        return fadeIn;
    }

    public void setFadeIn(int fadeIn) {
        this.fadeIn = fadeIn;
    }

    public int getStay() {
        return stay;
    }

    public void setStay(int stay) {
        this.stay = stay;
    }

    public int getFadeOut() {
        return fadeOut;
    }

    public void setFadeOut(int fadeOut) {
        this.fadeOut = fadeOut;
    }

    public BarColor getBarColor() {
        return barColor;
    }

    public void setBarColor(BarColor barColor) {
        this.barColor = barColor;
    }

    public BarStyle getBarStyle() {
        return barStyle;
    }

    public void setBarStyle(BarStyle barStyle) {
        this.barStyle = barStyle;
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = Math.max(0.0, Math.min(1.0, progress));
    }

    public ClickAction getClickAction() {
        return clickAction;
    }

    public void setClickAction(ClickAction clickAction) {
        this.clickAction = clickAction;
    }

    public String getClickValue() {
        return clickValue;
    }

    public void setClickValue(String clickValue) {
        this.clickValue = clickValue;
    }

    public String getHoverText() {
        return hoverText;
    }

    public void setHoverText(String hoverText) {
        this.hoverText = hoverText;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public void setPlugin(Plugin plugin) {
        this.plugin = plugin;
    }

    /** Enum for click actions on chat messages. */
    public enum ClickAction {
        RUN_COMMAND,
        SUGGEST_COMMAND,
        OPEN_URL,
        COPY_TO_CLIPBOARD
    }

    /** Serialize to a map for Redis transmission. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("key", key);
        map.put("content", content);
        map.put("type", type.name());

        if (!sound.isSilent()) {
            map.put("sound", sound.toString());
        }

        if (type == BroadcastType.TITLE && subtitle != null) {
            map.put("subtitle", subtitle);
            map.put("fadeIn", fadeIn);
            map.put("stay", stay);
            map.put("fadeOut", fadeOut);
        }

        if (type == BroadcastType.BOSS_BAR) {
            map.put("barColor", barColor.name());
            map.put("barStyle", barStyle.name());
            map.put("progress", progress);
        }

        return map;
    }

    /** Deserialize from a map (for Redis transmission). */
    public static BroadcastMessage fromMap(Map<String, Object> map) {
        String key = (String) map.get("key");
        String content = (String) map.get("content");
        BroadcastType type = BroadcastType.valueOf((String) map.get("type"));

        BroadcastMessage msg = new BroadcastMessage(key, content, type);

        if (map.containsKey("sound")) {
            msg.setSound(CustomSound.parse((String) map.get("sound")));
        }

        if (type == BroadcastType.TITLE) {
            if (map.containsKey("subtitle")) {
                msg.setSubtitle((String) map.get("subtitle"));
            }
            if (map.containsKey("fadeIn")) {
                msg.setFadeIn(((Number) map.get("fadeIn")).intValue());
            }
            if (map.containsKey("stay")) {
                msg.setStay(((Number) map.get("stay")).intValue());
            }
            if (map.containsKey("fadeOut")) {
                msg.setFadeOut(((Number) map.get("fadeOut")).intValue());
            }
        }

        if (type == BroadcastType.BOSS_BAR) {
            if (map.containsKey("barColor")) {
                msg.setBarColor(BarColor.valueOf((String) map.get("barColor")));
            }
            if (map.containsKey("barStyle")) {
                msg.setBarStyle(BarStyle.valueOf((String) map.get("barStyle")));
            }
            if (map.containsKey("progress")) {
                msg.setProgress(((Number) map.get("progress")).doubleValue());
            }
        }

        return msg;
    }
}
