package net.serverplugins.bridge.listeners;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.chat.Chat;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.bridge.ServerBridge;
import net.serverplugins.bridge.messaging.RedisClient;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;

public class PlayerJoinQuitListener implements Listener {

    private final ServerBridge plugin;
    private Chat vaultChat;
    private boolean hasPapi;
    private boolean hasTab;

    public PlayerJoinQuitListener(ServerBridge plugin) {
        this.plugin = plugin;
        setupVaultChat();
        this.hasPapi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        this.hasTab = Bukkit.getPluginManager().isPluginEnabled("TAB");

        if (hasTab) {
            plugin.getLogger()
                    .info("TAB integration enabled - will refresh player data on transfer");
        }
    }

    private void setupVaultChat() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        RegisteredServiceProvider<Chat> rsp =
                Bukkit.getServicesManager().getRegistration(Chat.class);
        if (rsp != null) {
            vaultChat = rsp.getProvider();
        }
    }

    /** Handle player joins - check for transfers and refresh TAB data. */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoinApplyDisplayName(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        var redis = plugin.getRedisClient();

        // Check if this is a server transfer
        boolean isTransfer =
                redis != null && redis.isConnected() && redis.isTransfer(player.getUniqueId());

        if (isTransfer) {
            // For transfers, we need to wait longer for LuckPerms to sync from database
            // Then force TAB to refresh the player's data
            Bukkit.getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                if (!player.isOnline()) return;

                                applyDisplayName(player);
                                refreshTabPlayer(player);

                                plugin.getLogger()
                                        .info(
                                                "Applied display name and refreshed TAB for transferred player: "
                                                        + player.getName());
                            },
                            40L); // 2 second delay for LuckPerms database sync
        } else {
            // For fresh joins, shorter delay is fine
            Bukkit.getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                if (!player.isOnline()) return;
                                applyDisplayName(player);
                            },
                            5L); // 0.25 second delay
        }
    }

    /** Publish join event at MONITOR priority (after all processing is done). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        var redis = plugin.getRedisClient();
        if (redis == null || !redis.isConnected()) {
            return;
        }

        Player player = event.getPlayer();
        String playerName = player.getName();
        String uuid = player.getUniqueId().toString();

        // Check if this is a transfer and log it
        RedisClient.TransferContext transfer = redis.getTransferContext(player.getUniqueId());
        if (transfer != null) {
            plugin.getLogger()
                    .info(
                            "Player "
                                    + playerName
                                    + " arrived from "
                                    + transfer.getFromServer()
                                    + " (transfer)");
        }

        redis.publishPlayerJoinAsync(playerName, uuid);
    }

    /**
     * Deliver any pending notifications from Discord (e.g. daily reward claims while offline).
     * Delayed by 60 ticks (3 seconds) so the message appears after join spam.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoinDeliverNotifications(PlayerJoinEvent event) {
        var redis = plugin.getRedisClient();
        if (redis == null || !redis.isConnected()) {
            return;
        }

        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();

        Bukkit.getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            if (!player.isOnline()) return;

                            Bukkit.getScheduler()
                                    .runTaskAsynchronously(
                                            plugin,
                                            () -> {
                                                List<String> pending =
                                                        redis.consumePendingNotifications(uuid);
                                                if (pending.isEmpty()) return;

                                                Bukkit.getScheduler()
                                                        .runTask(
                                                                plugin,
                                                                () -> {
                                                                    if (!player.isOnline()) return;
                                                                    for (String msg : pending) {
                                                                        TextUtil.send(player, msg);
                                                                    }
                                                                });
                                            });
                        },
                        60L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        var redis = plugin.getRedisClient();
        if (redis == null || !redis.isConnected()) {
            return;
        }

        String playerName = event.getPlayer().getName();
        String uuid = event.getPlayer().getUniqueId().toString();

        redis.publishPlayerQuitAsync(playerName, uuid);
    }

    /**
     * Refresh TAB plugin data for a player. Forces TAB to re-read all placeholders and update
     * scoreboard/tablist.
     */
    private void refreshTabPlayer(Player player) {
        if (!hasTab) {
            return;
        }

        try {
            // Use TAB API to refresh the player
            me.neznamy.tab.api.TabAPI tabApi = me.neznamy.tab.api.TabAPI.getInstance();
            if (tabApi != null) {
                me.neznamy.tab.api.TabPlayer tabPlayer = tabApi.getPlayer(player.getUniqueId());
                if (tabPlayer != null) {
                    // Force refresh of all features for this player
                    // The API doesn't expose a direct refresh method, so we use property updates

                    // Get scoreboard manager and refresh
                    var scoreboardManager = tabApi.getScoreboardManager();
                    if (scoreboardManager != null) {
                        // Toggle scoreboard off and on to force refresh
                        boolean wasVisible = scoreboardManager.hasScoreboardVisible(tabPlayer);
                        if (wasVisible) {
                            scoreboardManager.setScoreboardVisible(tabPlayer, false, false);
                            Bukkit.getScheduler()
                                    .runTaskLater(
                                            plugin,
                                            () -> {
                                                if (player.isOnline()) {
                                                    scoreboardManager.setScoreboardVisible(
                                                            tabPlayer, true, false);
                                                }
                                            },
                                            2L);
                        }
                    }

                    plugin.getLogger().fine("Refreshed TAB data for " + player.getName());
                }
            }
        } catch (Exception e) {
            // TAB API might not be available or compatible
            plugin.getLogger().fine("Could not refresh TAB via API: " + e.getMessage());

            // Fallback: dispatch tab reload command after a delay
            // This is heavy-handed but ensures refresh
            Bukkit.getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                if (player.isOnline()) {
                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tab reload");
                                    plugin.getLogger()
                                            .info("Dispatched TAB reload for player refresh");
                                }
                            },
                            10L);
        }
    }

    /**
     * Applies the player's display name from LuckPerms metadata. Only sets the nickname (no
     * prefix/suffix) since ServerFilter's chat format already handles vault_prefix and vault_suffix
     * separately. Including them here causes stale rank icons when a player's group changes while
     * online.
     */
    private void applyDisplayName(Player player) {
        String nickname = getNickname(player);

        if (nickname.isEmpty() || nickname.equals(player.getName())) {
            return; // No customization needed
        }

        try {
            // Convert legacy color codes to Component
            Component displayComponent =
                    LegacyComponentSerializer.legacyAmpersand().deserialize(nickname);

            // Apply to display name (used in chat and other places)
            player.displayName(displayComponent);

            // Apply to player list name (tab list) - TAB may override this
            player.playerListName(displayComponent);

        } catch (Exception e) {
            plugin.getLogger()
                    .warning(
                            "Failed to apply display name for "
                                    + player.getName()
                                    + ": "
                                    + e.getMessage());
        }
    }

    /** Gets the player's nickname from LuckPerms meta, falling back to player name. */
    private String getNickname(Player player) {
        if (hasPapi) {
            try {
                String nickname =
                        me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(
                                player, "%luckperms_meta_nickname%");
                if (nickname != null
                        && !nickname.isEmpty()
                        && !nickname.equals("%luckperms_meta_nickname%")
                        && !nickname.equalsIgnoreCase("null")) {
                    return nickname;
                }
            } catch (Exception ignored) {
            }
        }
        return player.getName();
    }

    private String getPlayerPrefix(Player player) {
        if (hasPapi) {
            try {
                String prefix =
                        me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(
                                player, "%vault_prefix%");
                if (prefix != null && !prefix.equals("%vault_prefix%") && !prefix.isEmpty()) {
                    return prefix;
                }
            } catch (Exception ignored) {
            }
        }

        if (vaultChat != null) {
            try {
                String prefix = vaultChat.getPlayerPrefix(player);
                return prefix != null ? prefix : "";
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private String getPlayerSuffix(Player player) {
        if (hasPapi) {
            try {
                String suffix =
                        me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(
                                player, "%vault_suffix%");
                if (suffix != null && !suffix.equals("%vault_suffix%") && !suffix.isEmpty()) {
                    return suffix;
                }
            } catch (Exception ignored) {
            }
        }

        if (vaultChat != null) {
            try {
                String suffix = vaultChat.getPlayerSuffix(player);
                return suffix != null ? suffix : "";
            } catch (Exception ignored) {
            }
        }
        return "";
    }
}
