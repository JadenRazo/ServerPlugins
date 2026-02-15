package net.serverplugins.core.features;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.serverplugins.core.ServerCore;
import net.serverplugins.core.data.PlayerDataManager;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class EmojisFeature extends Feature implements Listener, PerPlayerFeature {

    private final Map<String, EmojiData> emojis = new HashMap<>();
    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacyAmpersand();
    private static final String FEATURE_KEY = "emojis";

    public EmojisFeature(ServerCore plugin) {
        super(plugin);
        loadEmojis();
    }

    @Override
    public String getName() {
        return "Emojis";
    }

    @Override
    public String getDescription() {
        return "Replaces :emoji: patterns in chat with custom characters";
    }

    @Override
    public String getFeatureKey() {
        return FEATURE_KEY;
    }

    @Override
    public boolean isEnabledForPlayer(Player player) {
        if (!isEnabled()) return false;

        try {
            PlayerDataManager dataManager = plugin.getPlayerDataManager();
            PlayerDataManager.PlayerData data = dataManager.loadPlayerData(player.getUniqueId());

            // If player has a preference, use it; otherwise use global state
            if (data.hasFeaturePreference(FEATURE_KEY)) {
                return data.isFeatureEnabled(FEATURE_KEY);
            }
        } catch (IllegalStateException e) {
            // Player data not preloaded (e.g. after plugin reload) — fall back to global state
        }

        return isEnabled();
    }

    @Override
    protected void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadEmojis() {
        emojis.clear();
        ConfigurationSection section =
                plugin.getConfig().getConfigurationSection("settings.emojis.list");
        if (section == null) {
            plugin.getLogger().warning("No emojis configured in settings.emojis.list");
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection emojiSection = section.getConfigurationSection(key);
            if (emojiSection == null) continue;

            String trigger = emojiSection.getString("trigger", ":" + key + ":");
            String value = emojiSection.getString("value", "");

            if (!trigger.isEmpty() && !value.isEmpty()) {
                // Pre-compute the plain emoji character from the legacy-formatted value
                String plainEmoji =
                        PlainTextComponentSerializer.plainText()
                                .serialize(LEGACY.deserialize(value));
                emojis.put(key, new EmojiData(trigger, plainEmoji));
                plugin.getLogger()
                        .info("Loaded emoji: " + key + " (" + trigger + " -> " + plainEmoji + ")");
            }
        }

        plugin.getLogger().info("Loaded " + emojis.size() + " emojis");
    }

    // Run at HIGH so server-filter's ChatFilterListener (NORMAL) has already set its renderer
    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncChatEvent event) {
        if (!isEnabled() || event.isCancelled()) return;

        Player player = event.getPlayer();
        if (!isEnabledForPlayer(player)) return;

        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message());

        // Collect applicable emoji replacements
        List<TextReplacementConfig> replacements = new ArrayList<>();
        for (Map.Entry<String, EmojiData> entry : emojis.entrySet()) {
            String emojiName = entry.getKey();
            EmojiData data = entry.getValue();

            if (!plainMessage.contains(data.trigger)) continue;

            if (!player.hasPermission("servercore.emoji.*")
                    && !player.hasPermission("servercore.emoji." + emojiName)) {
                continue;
            }

            replacements.add(
                    TextReplacementConfig.builder()
                            .match(Pattern.compile(Pattern.quote(data.trigger)))
                            .replacement(data.character)
                            .build());
        }

        if (replacements.isEmpty()) return;

        // Wrap the existing renderer instead of modifying event.message().
        // This ensures the emoji character only exists in the rendered output,
        // not in the raw message (which can be duplicated by packet processors).
        ChatRenderer existingRenderer = event.renderer();
        event.renderer(
                (source, displayName, message, viewer) -> {
                    Component modified = message;
                    for (TextReplacementConfig config : replacements) {
                        modified = modified.replaceText(config);
                    }
                    return existingRenderer.render(source, displayName, modified, viewer);
                });
    }

    public void reload() {
        loadEmojis();
    }

    public Map<String, EmojiData> getEmojis() {
        return new HashMap<>(emojis);
    }

    // character holds the pre-computed plain emoji char (stripped of legacy color codes)
    public record EmojiData(String trigger, String character) {}
}
