package net.serverplugins.commands.listeners;

import java.lang.reflect.Method;
import java.util.UUID;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.data.PlayerDataManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

public class PlayerListener implements Listener {

    private final ServerCommands plugin;

    // Cached reflection handles for ServerBridge transfer detection
    private Object redisClient;
    private Method isTransferMethod;
    private Method consumeTransferMethod;
    private boolean bridgeChecked = false;

    public PlayerListener(ServerCommands plugin) {
        this.plugin = plugin;
    }

    /**
     * Attempts to get the ServerBridge RedisClient via reflection. This allows server-commands to
     * detect server transfers without a hard dependency.
     */
    private void setupBridgeIntegration() {
        if (bridgeChecked) return;
        bridgeChecked = true;

        try {
            Plugin bridgePlugin = Bukkit.getPluginManager().getPlugin("ServerBridge");
            if (bridgePlugin == null || !bridgePlugin.isEnabled()) {
                return;
            }

            // Get the RedisClient from ServerBridge
            Method getRedisClient = bridgePlugin.getClass().getMethod("getRedisClient");
            redisClient = getRedisClient.invoke(bridgePlugin);

            if (redisClient != null) {
                // Cache the methods we need
                isTransferMethod = redisClient.getClass().getMethod("isTransfer", UUID.class);
                consumeTransferMethod =
                        redisClient.getClass().getMethod("consumeTransferContext", UUID.class);
                plugin.getLogger().info("ServerBridge integration enabled for transfer detection");
            }
        } catch (Exception e) {
            plugin.getLogger().fine("ServerBridge integration not available: " + e.getMessage());
        }
    }

    /** Check if a player is transferring from another server. */
    private boolean isPlayerTransfer(UUID uuid) {
        setupBridgeIntegration();

        if (redisClient == null || isTransferMethod == null) {
            return false;
        }

        try {
            return (boolean) isTransferMethod.invoke(redisClient, uuid);
        } catch (Exception e) {
            return false;
        }
    }

    /** Consume the transfer context after processing. */
    private void consumeTransfer(UUID uuid) {
        if (redisClient == null || consumeTransferMethod == null) {
            return;
        }

        try {
            consumeTransferMethod.invoke(redisClient, uuid);
        } catch (Exception e) {
            plugin.getLogger()
                    .fine("Failed to consume transfer for " + uuid + ": " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        // Check if this is a server transfer BEFORE loading data
        boolean isTransfer = isPlayerTransfer(uuid);

        // Start session tracking IMMEDIATELY - this prevents race conditions
        // if the player quits before async data loading completes
        long sessionStart = System.currentTimeMillis();
        plugin.getPlayerDataManager().startSession(uuid, sessionStart);

        // Preload player data asynchronously to avoid blocking main thread
        plugin.getPlayerDataManager()
                .preloadPlayerDataAsync(uuid)
                .thenAccept(
                        data -> {
                            // Only reset session start for fresh joins, not transfers
                            // Transfers should preserve the session from the previous server
                            if (!isTransfer) {
                                data.setSessionStart(sessionStart);
                            } else {
                                // For transfers, if session start is 0 or very old, update it
                                // This handles edge cases where session data wasn't preserved
                                if (data.getSessionStart() <= 0) {
                                    data.setSessionStart(sessionStart);
                                } else {
                                    // Preserve the original session start from the transfer
                                    plugin.getPlayerDataManager()
                                            .startSession(uuid, data.getSessionStart());
                                }
                                // Consume the transfer context now that we've processed it
                                consumeTransfer(uuid);
                                plugin.getLogger()
                                        .info(
                                                "Player "
                                                        + event.getPlayer().getName()
                                                        + " transferred - preserving session");
                            }

                            // Apply fly status on main thread
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (event.getPlayer().isOnline()
                                                        && data.isFlyEnabled()
                                                        && event.getPlayer()
                                                                .hasPermission(
                                                                        "servercommands.fly")) {
                                                    event.getPlayer().setAllowFlight(true);
                                                    event.getPlayer().setFlying(true);
                                                }
                                            });
                        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        // Finalize the session using the session tracker
        // This works even if data hasn't finished loading yet
        long sessionTime = plugin.getPlayerDataManager().finalizeSession(uuid);

        // Get player data (may still be loading or placeholder)
        PlayerDataManager.PlayerData data = plugin.getPlayerDataManager().getPlayerData(uuid);

        // If data is still loading, wait for it using CompletableFuture
        // This prevents blocking the main thread with busy-wait polling
        if (data instanceof PlayerDataManager.PlaceholderPlayerData) {
            // Use the preload future to wait for data completion
            plugin.getPlayerDataManager()
                    .preloadPlayerDataAsync(uuid)
                    .orTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .whenCompleteAsync(
                            (loadedData, throwable) -> {
                                if (throwable != null) {
                                    plugin.getLogger()
                                            .warning(
                                                    "Failed to load player data on quit for "
                                                            + event.getPlayer().getName()
                                                            + ": "
                                                            + throwable.getMessage());
                                    // Still unload to clean up
                                    plugin.getPlayerDataManager().unloadPlayerData(uuid);
                                    return;
                                }

                                // Update and save the loaded data
                                if (loadedData != null && sessionTime > 0) {
                                    loadedData.addPlaytime(sessionTime);
                                    loadedData.setLastSeen(System.currentTimeMillis());
                                    plugin.getPlayerDataManager().savePlayerData(uuid);
                                }

                                // Unload the player data
                                plugin.getPlayerDataManager().unloadPlayerData(uuid);
                            },
                            Bukkit.getScheduler().getMainThreadExecutor(plugin));
        } else {
            // Data is already loaded, process immediately
            if (sessionTime > 0) {
                data.addPlaytime(sessionTime);
            }
            data.setLastSeen(System.currentTimeMillis());

            // Save and unload async
            Bukkit.getScheduler()
                    .runTaskAsynchronously(
                            plugin,
                            () -> {
                                plugin.getPlayerDataManager().savePlayerData(uuid);
                                plugin.getPlayerDataManager().unloadPlayerData(uuid);
                            });
        }

        // Cancel any active command queues for this player
        if (plugin.getDynamicCommandManager() != null) {
            plugin.getDynamicCommandManager().getCommandQueue().cancelQueue(uuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        PlayerDataManager.PlayerData data =
                plugin.getPlayerDataManager().getPlayerData(event.getEntity().getUniqueId());
        data.setLastLocation(event.getEntity().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND
                || event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return;
        }

        PlayerDataManager.PlayerData data =
                plugin.getPlayerDataManager().getPlayerData(event.getPlayer().getUniqueId());
        data.setLastLocation(event.getFrom());
    }
}
