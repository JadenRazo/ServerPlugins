package net.serverplugins.velocity.resourcepack.listeners;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerResourcePackStatusEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.ResourcePackInfo;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.serverplugins.velocity.resourcepack.ResourcePackConfig;
import net.serverplugins.velocity.resourcepack.managers.PackVersionManager;
import org.slf4j.Logger;

/**
 * Listens for player login events and sends appropriate resource packs.
 *
 * <p>Handles protocol detection and pack offering immediately after authentication.
 */
public class ResourcePackListener {

    private final Object plugin;
    private final ProxyServer server;
    private final Logger logger;
    private final ResourcePackConfig config;
    private final PackVersionManager packManager;

    // Track players who have successfully loaded the resource pack
    private final Set<UUID> playersWithPack = ConcurrentHashMap.newKeySet();

    public ResourcePackListener(
            Object plugin,
            ProxyServer server,
            Logger logger,
            ResourcePackConfig config,
            PackVersionManager packManager) {
        this.plugin = plugin;
        this.server = server;
        this.logger = logger;
        this.config = config;
        this.packManager = packManager;
    }

    /**
     * Checks if a player is a Bedrock Edition player connecting through Geyser/Floodgate. Floodgate
     * players have UUIDs starting with 00000000-0000-0000-0009-
     *
     * @param player The player to check
     * @return true if the player is a Bedrock player
     */
    private boolean isBedrockPlayer(Player player) {
        String uuid = player.getUniqueId().toString();
        return uuid.startsWith("00000000-0000-0000-0009-");
    }

    /**
     * Blocks player from connecting to backend servers until resource pack is loaded. This ensures
     * they never see the world without the pack applied. Bedrock Edition players are allowed
     * through immediately.
     */
    @Subscribe(order = PostOrder.FIRST)
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Allow Bedrock players through immediately (they don't need Java packs)
        if (isBedrockPlayer(player)) {
            return;
        }

        // Allow connection if player has already loaded the pack
        if (playersWithPack.contains(playerId)) {
            return;
        }

        // Block connection until pack is loaded
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        logger.debug(
                "Blocked {} from connecting to {} - waiting for resource pack",
                player.getUsername(),
                event.getOriginalServer().getServerInfo().getName());
    }

    /**
     * Handles player login after authentication. Sends resource pack immediately based on protocol
     * version. Skips Bedrock Edition players (Geyser/Floodgate) as they cannot use Java packs.
     */
    @Subscribe(order = PostOrder.FIRST)
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check if this is a Bedrock Edition player
        if (isBedrockPlayer(player)) {
            logger.info(
                    "Player {} is a Bedrock Edition player - skipping Java resource pack",
                    player.getUsername());
            // Mark as having pack so they can connect immediately
            // They'll be allowed through in ServerPreConnectEvent
            playersWithPack.add(playerId);
            return;
        }

        // Get player's protocol version
        int protocol = player.getProtocolVersion().getProtocol();

        logger.info("Player {} authenticated using protocol {}", player.getUsername(), protocol);

        // Get appropriate resource pack for this protocol
        Optional<ResourcePackInfo> packInfo = packManager.getPackForProtocol(protocol);

        if (packInfo.isEmpty()) {
            logger.warn(
                    "No resource pack available for player {} (protocol {})",
                    player.getUsername(),
                    protocol);
            return;
        }

        // Send pack immediately after authentication
        ResourcePackInfo pack = packInfo.get();

        int delayTicks = config.getPackSendDelayTicks();
        if (delayTicks > 0) {
            // Use scheduled delivery if delay is configured
            long delayMillis = delayTicks * 50L;
            server.getScheduler()
                    .buildTask(plugin, () -> sendResourcePack(player, pack, protocol))
                    .delay(delayMillis, TimeUnit.MILLISECONDS)
                    .schedule();
            logger.debug(
                    "Scheduled resource pack delivery for {} in {}ms (protocol {})",
                    player.getUsername(),
                    delayMillis,
                    protocol);
        } else {
            // Send immediately
            sendResourcePack(player, pack, protocol);
        }
    }

    /** Sends the resource pack to the player. */
    private void sendResourcePack(Player player, ResourcePackInfo packInfo, int protocol) {
        try {
            player.sendResourcePackOffer(packInfo);
            logger.info(
                    "Sent resource pack to {} (protocol {}): {}",
                    player.getUsername(),
                    protocol,
                    packInfo.getUrl());
        } catch (Exception e) {
            logger.error(
                    "Failed to send resource pack to {}: {}", player.getUsername(), e.getMessage());
        }
    }

    /**
     * Handles player responses to resource pack offers. Tracks acceptance/decline and allows
     * connection once pack is loaded.
     */
    @Subscribe
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        PlayerResourcePackStatusEvent.Status status = event.getStatus();

        switch (status) {
            case SUCCESSFUL:
                logger.info(
                        "Player {} successfully loaded the resource pack", player.getUsername());

                // Mark player as having the pack and try to connect them
                playersWithPack.add(playerId);
                connectPlayerToBackend(player);
                break;

            case DECLINED:
                logger.warn("Player {} declined the resource pack", player.getUsername());
                if (config.isPackRequired()) {
                    player.disconnect(
                            Component.text(
                                    "You must accept the resource pack to play on this server!"));
                    logger.info(
                            "Kicked {} for declining required resource pack", player.getUsername());
                } else {
                    // Allow connection even without pack if not required
                    playersWithPack.add(playerId);
                    connectPlayerToBackend(player);
                }
                break;

            case FAILED_DOWNLOAD:
                logger.error(
                        "Player {} failed to download the resource pack", player.getUsername());
                player.disconnect(
                        Component.text(
                                "Failed to download resource pack. Please try again or contact an admin."));
                break;

            case ACCEPTED:
                logger.debug(
                        "Player {} accepted the resource pack (downloading...)",
                        player.getUsername());
                break;

            case DOWNLOADED:
                logger.debug(
                        "Player {} finished downloading the resource pack", player.getUsername());
                break;

            case INVALID_URL:
                logger.error("Player {} received invalid resource pack URL", player.getUsername());
                player.disconnect(
                        Component.text(
                                "Resource pack configuration error. Please contact an admin."));
                break;

            case FAILED_RELOAD:
                logger.error("Player {} failed to reload the resource pack", player.getUsername());
                break;

            case DISCARDED:
                logger.warn("Player {} discarded the resource pack", player.getUsername());
                break;

            default:
                logger.debug("Player {} resource pack status: {}", player.getUsername(), status);
                break;
        }
    }

    /** Attempts to connect the player to their initial backend server. */
    private void connectPlayerToBackend(Player player) {
        // Get the player's initial server (usually determined by Velocity's connection order)
        player.createConnectionRequest(
                        server.getServer("lobby")
                                .orElseGet(
                                        () ->
                                                server.getAllServers().stream()
                                                        .findFirst()
                                                        .orElse(null)))
                .connect()
                .thenAccept(
                        result -> {
                            if (result.isSuccessful()) {
                                logger.info(
                                        "Connected {} to backend server after resource pack loaded",
                                        player.getUsername());
                            } else {
                                logger.error(
                                        "Failed to connect {} to backend server: {}",
                                        player.getUsername(),
                                        result.getReasonComponent()
                                                .orElse(Component.text("Unknown error")));
                            }
                        });
    }

    /** Cleanup tracking when player disconnects. */
    @Subscribe
    public void onDisconnect(com.velocitypowered.api.event.connection.DisconnectEvent event) {
        playersWithPack.remove(event.getPlayer().getUniqueId());
    }
}
