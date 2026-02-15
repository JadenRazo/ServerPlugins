package net.serverplugins.core.listeners;

import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.core.ServerCore;
import net.serverplugins.core.data.PlayerDataManager;
import net.serverplugins.core.features.PerPlayerFeature;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Handles player join events for per-player feature system. Shows hints to first-time joiners about
 * available per-player features.
 */
public class JoinListener implements Listener {

    private final ServerCore plugin;
    private final PlayerDataManager dataManager;

    public JoinListener(ServerCore plugin, PlayerDataManager dataManager) {
        this.plugin = plugin;
        this.dataManager = dataManager;
    }

    private static final String EARLY_ACCESS_MESSAGE =
            "<gradient:#FFD700:#FFA500>Early Access</gradient> <gray>- Bugs may occur! Create a ticket in Discord to report issues.";

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Send early access actionbar notification with a short delay
        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            if (player.isOnline()) {
                                TextUtil.sendActionBar(player, EARLY_ACCESS_MESSAGE);
                            }
                        },
                        40L); // 2 second delay

        // Load player data (initializes defaults if first join)
        PlayerDataManager.PlayerData data = dataManager.loadPlayerData(player.getUniqueId());

        // Initialize feature preferences from global defaults if first join
        if (data.isFirstJoin()) {
            initializePlayerDefaults(player, data);
            dataManager.markJoined(player.getUniqueId());

            // Send welcome message with feature hints
            if (plugin.getConfig()
                    .getBoolean("settings.per-player-toggles.show-first-join-hint", true)) {
                sendFirstJoinHint(player);
            }
        }

        // Broadcast welcome message to all players (every join)
        broadcastWelcome(player);
    }

    /** Initialize player's feature preferences from global defaults */
    private void initializePlayerDefaults(Player player, PlayerDataManager.PlayerData data) {
        plugin.getFeatures()
                .forEach(
                        (key, feature) -> {
                            if (feature instanceof PerPlayerFeature) {
                                // Set initial state to match global enabled state
                                data.setFeatureEnabled(key, feature.isEnabled());
                            }
                        });
    }

    /** Send helpful hint about per-player toggles to new players */
    private void sendFirstJoinHint(Player player) {
        // Delay hint to avoid overwhelming player with join messages
        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            if (!player.isOnline()) return;

                            TextUtil.send(player, "");
                            TextUtil.send(
                                    player,
                                    "<gradient:#FFD700:#FFA500><bold>Welcome to ServerPlugins!</bold></gradient>");
                            TextUtil.send(player, "");
                            TextUtil.send(
                                    player,
                                    "<gray>Did you know? You can customize your experience!");
                            TextUtil.send(
                                    player,
                                    "<yellow>Use <white>/servercore toggle <feature> <yellow>to enable/disable features.");
                            TextUtil.send(player, "");
                            TextUtil.send(player, "<green>Available per-player features:");

                            // List all per-player features
                            plugin.getFeatures()
                                    .forEach(
                                            (key, feature) -> {
                                                if (feature instanceof PerPlayerFeature) {
                                                    boolean enabled = feature.isEnabled();
                                                    String status =
                                                            enabled ? "<green>ON" : "<red>OFF";
                                                    TextUtil.send(
                                                            player,
                                                            "  <gray>• <white>"
                                                                    + key
                                                                    + " "
                                                                    + status);
                                                }
                                            });

                            TextUtil.send(player, "");
                            TextUtil.send(
                                    player,
                                    "<gray>Type <white>/servercore list <gray>to see all features.");
                            TextUtil.send(player, "");
                        },
                        100L); // 5 second delay
    }

    /** Broadcast welcome message to all players when someone joins */
    private void broadcastWelcome(Player player) {
        if (!plugin.getConfig().getBoolean("settings.welcome-broadcast.enabled", false)) {
            return;
        }

        String message =
                plugin.getConfig()
                        .getString(
                                "settings.welcome-broadcast.message",
                                "<yellow>Welcome <gold>{player}<yellow> to the server!");

        // Replace {player} placeholder with player name
        message = message.replace("{player}", player.getName());

        // Broadcast to all online players using TextUtil
        for (Player onlinePlayer : plugin.getServer().getOnlinePlayers()) {
            TextUtil.send(onlinePlayer, message);
        }
    }
}
