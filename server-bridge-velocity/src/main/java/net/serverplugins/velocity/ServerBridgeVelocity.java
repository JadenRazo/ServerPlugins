package net.serverplugins.velocity;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import net.serverplugins.velocity.messaging.VelocityPlaceholder;
import net.serverplugins.velocity.messaging.VelocityTextUtil;
import org.slf4j.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Plugin(
        id = "server-bridge-velocity",
        name = "ServerBridge Velocity",
        version = "1.0.0",
        description = "Network stats bridge for ServerPlugins",
        authors = {"ServerPlugins"})
public class ServerBridgeVelocity {

    public static final String CHANNEL_PLAYER_JOIN = "server:player:join";
    public static final String CHANNEL_PLAYER_QUIT = "server:player:quit";
    public static final String CHANNEL_PLAYER_SWITCH = "server:player:switch";
    public static final String CHANNEL_NETWORK_STATS = "server:network:stats";

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Gson gson = new Gson();

    private VelocityBridgeConfig config;
    private JedisPool jedisPool;

    @Inject
    public ServerBridgeVelocity(
            ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        loadConfig();
        connectRedis();
        startStatsTask();
        logger.info("ServerBridge Velocity initialized successfully!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (jedisPool != null && !jedisPool.isClosed()) {
            // Send final stats with 0 players
            publishNetworkStats();
            jedisPool.close();
        }
        logger.info("ServerBridge Velocity shutdown.");
    }

    private void loadConfig() {
        try {
            config = new VelocityBridgeConfig(dataDirectory, logger);
            config.load();
        } catch (IOException e) {
            logger.error("Failed to load configuration: {}", e.getMessage());
        }
    }

    private void connectRedis() {
        if (config == null) {
            logger.error("Cannot connect to Redis - configuration not loaded");
            return;
        }

        if (!config.isRedisEnabled()) {
            logger.warn("Redis is disabled in configuration - network stats will not be published");
            return;
        }

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(5);
        poolConfig.setMaxIdle(2);
        poolConfig.setMinIdle(1);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(false);

        String host = config.getRedisHost();
        int port = config.getRedisPort();

        int timeout = 5000;
        if (config.hasRedisPassword()) {
            jedisPool = new JedisPool(poolConfig, host, port, timeout, config.getRedisPassword());
        } else {
            jedisPool = new JedisPool(poolConfig, host, port, timeout);
        }

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.ping();
            logger.info("Successfully connected to Redis at {}:{}", host, port);
        } catch (Exception e) {
            logger.error("Failed to connect to Redis at {}:{} - {}", host, port, e.getMessage());
        }
    }

    private void startStatsTask() {
        // Publish network stats every 30 seconds
        server.getScheduler()
                .buildTask(this, this::publishNetworkStats)
                .repeat(30, TimeUnit.SECONDS)
                .schedule();
    }

    private void publishNetworkStats() {
        if (jedisPool == null || jedisPool.isClosed()) return;

        try (Jedis jedis = jedisPool.getResource()) {
            JsonObject stats = new JsonObject();
            stats.addProperty("type", "NETWORK_STATS");
            stats.addProperty("timestamp", System.currentTimeMillis());

            // Total players on network
            int totalPlayers = server.getPlayerCount();
            stats.addProperty("totalPlayers", totalPlayers);

            // Per-server breakdown
            JsonObject servers = new JsonObject();
            for (RegisteredServer rs : server.getAllServers()) {
                String serverName = rs.getServerInfo().getName();
                int playerCount = rs.getPlayersConnected().size();
                servers.addProperty(serverName, playerCount);
            }
            stats.add("servers", servers);

            jedis.publish(CHANNEL_NETWORK_STATS, gson.toJson(stats));
        } catch (Exception e) {
            logger.warn("Failed to publish network stats: {}", e.getMessage());
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String currentServer = event.getServer().getServerInfo().getName();
        String previousServer =
                event.getPreviousServer().map(s -> s.getServerInfo().getName()).orElse(null);

        if (previousServer == null) {
            // Player just joined the network
            publishPlayerJoin(player, currentServer);
            sendJoinMessage(player, currentServer);
        } else {
            // Player switched servers
            publishPlayerSwitch(player, previousServer, currentServer);
            sendSwitchMessage(player, previousServer, currentServer);
        }

        // Also publish updated stats
        server.getScheduler()
                .buildTask(this, this::publishNetworkStats)
                .delay(500, TimeUnit.MILLISECONDS)
                .schedule();
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        String lastServer =
                player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("unknown");

        publishPlayerQuit(player, lastServer);
        sendQuitMessage(player, lastServer);

        // Publish updated stats after a short delay
        server.getScheduler()
                .buildTask(this, this::publishNetworkStats)
                .delay(500, TimeUnit.MILLISECONDS)
                .schedule();
    }

    private void publishPlayerJoin(Player player, String server) {
        if (jedisPool == null || jedisPool.isClosed()) return;

        try (Jedis jedis = jedisPool.getResource()) {
            JsonObject json = new JsonObject();
            json.addProperty("type", "PLAYER_JOIN");
            json.addProperty("player", player.getUsername());
            json.addProperty("uuid", player.getUniqueId().toString());
            json.addProperty("server", server);
            json.addProperty("timestamp", System.currentTimeMillis());

            jedis.publish(CHANNEL_PLAYER_JOIN, gson.toJson(json));
            logger.info("{} joined the network on server '{}'", player.getUsername(), server);
        } catch (Exception e) {
            logger.warn(
                    "Failed to publish player join event for {}: {}",
                    player.getUsername(),
                    e.getMessage());
        }
    }

    private void publishPlayerQuit(Player player, String lastServer) {
        if (jedisPool == null || jedisPool.isClosed()) return;

        try (Jedis jedis = jedisPool.getResource()) {
            JsonObject json = new JsonObject();
            json.addProperty("type", "PLAYER_QUIT");
            json.addProperty("player", player.getUsername());
            json.addProperty("uuid", player.getUniqueId().toString());
            json.addProperty("server", lastServer);
            json.addProperty("timestamp", System.currentTimeMillis());

            jedis.publish(CHANNEL_PLAYER_QUIT, gson.toJson(json));
            logger.info(
                    "{} left the network (last server: '{}')", player.getUsername(), lastServer);
        } catch (Exception e) {
            logger.warn(
                    "Failed to publish player quit event for {}: {}",
                    player.getUsername(),
                    e.getMessage());
        }
    }

    private void publishPlayerSwitch(Player player, String from, String to) {
        if (jedisPool == null || jedisPool.isClosed()) return;

        try (Jedis jedis = jedisPool.getResource()) {
            JsonObject json = new JsonObject();
            json.addProperty("type", "PLAYER_SWITCH");
            json.addProperty("player", player.getUsername());
            json.addProperty("uuid", player.getUniqueId().toString());
            json.addProperty("from", from);
            json.addProperty("to", to);
            json.addProperty("timestamp", System.currentTimeMillis());

            jedis.publish(CHANNEL_PLAYER_SWITCH, gson.toJson(json));
            logger.info("{} switched servers: '{}' -> '{}'", player.getUsername(), from, to);
        } catch (Exception e) {
            logger.warn(
                    "Failed to publish player switch event for {}: {}",
                    player.getUsername(),
                    e.getMessage());
        }
    }

    /** Sends a formatted join message to the player. */
    private void sendJoinMessage(Player player, String server) {
        if (!config.isShowConnectionMessages()) return;

        String message =
                VelocityPlaceholder.replaceAll(
                        config.getJoinMessage(),
                        VelocityPlaceholder.of("player", player.getUsername()),
                        VelocityPlaceholder.of("server", server),
                        VelocityPlaceholder.of("network", config.getNetworkName()));

        VelocityTextUtil.send(player, message);
    }

    /** Sends a formatted quit message to the player. */
    private void sendQuitMessage(Player player, String lastServer) {
        if (!config.isShowConnectionMessages()) return;

        String message =
                VelocityPlaceholder.replaceAll(
                        config.getQuitMessage(),
                        VelocityPlaceholder.of("player", player.getUsername()),
                        VelocityPlaceholder.of("server", lastServer),
                        VelocityPlaceholder.of("network", config.getNetworkName()));

        VelocityTextUtil.send(player, message);
    }

    /** Sends a formatted server switch message to the player. */
    private void sendSwitchMessage(Player player, String from, String to) {
        if (!config.isShowConnectionMessages()) return;

        String message =
                VelocityPlaceholder.replaceAll(
                        config.getSwitchMessage(),
                        VelocityPlaceholder.of("player", player.getUsername()),
                        VelocityPlaceholder.of("from", from),
                        VelocityPlaceholder.of("to", to),
                        VelocityPlaceholder.of("network", config.getNetworkName()));

        VelocityTextUtil.send(player, message);
    }
}
