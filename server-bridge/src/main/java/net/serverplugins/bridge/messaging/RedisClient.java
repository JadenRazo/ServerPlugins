package net.serverplugins.bridge.messaging;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.serverplugins.api.utils.LegacyText;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.bridge.BridgeConfig;
import net.serverplugins.bridge.ServerBridge;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

public class RedisClient {

    public static final String CHANNEL_CHAT_MINECRAFT = "server:chat:minecraft";
    public static final String CHANNEL_CHAT_DISCORD = "server:chat:discord";
    public static final String CHANNEL_STATUS_REQUEST = "server:status:request";
    public static final String CHANNEL_STATUS_RESPONSE = "server:status:response";
    public static final String CHANNEL_ECONOMY_DEPOSIT = "server:economy:deposit";
    public static final String CHANNEL_ECONOMY_RESPONSE = "server:economy:response";
    public static final String CHANNEL_LINK_VERIFY = "server:link:verify";
    public static final String CHANNEL_LINK_COMPLETE = "server:link:complete";
    public static final String CHANNEL_PLAYER_JOIN = "server:player:join";
    public static final String CHANNEL_PLAYER_QUIT = "server:player:quit";
    public static final String CHANNEL_PLAYER_SWITCH = "server:player:switch";
    public static final String CHANNEL_CHANGELOG_ADD = "server:changelog:add";
    public static final String CHANNEL_CHANGELOG_RESPONSE = "server:changelog:response";
    public static final String CHANNEL_CONSOLE_COMMAND = "server:console:command";
    public static final String CHANNEL_CHAT_CROSSSERVER = "server:chat:crossserver";

    // Bounty system channels
    public static final String CHANNEL_BOUNTY_PLACED = "server:bounty:placed";
    public static final String CHANNEL_BOUNTY_CLAIMED = "server:bounty:claimed";

    // Death system channels
    public static final String CHANNEL_DEATH_STORED = "server:death:stored";
    public static final String CHANNEL_DEATH_PURCHASED = "server:death:purchased";

    // Moderation channel
    public static final String CHANNEL_MODERATION_LOG = "server:moderation:log";

    // Event channel
    public static final String CHANNEL_EVENT_ANNOUNCEMENT = "server:event:announcement";

    // Economy withdrawal channel
    public static final String CHANNEL_ECONOMY_WITHDRAW = "server:economy:withdraw";

    // Pending notification key prefix (per-player Redis LIST)
    public static final String KEY_PENDING_NOTIFICATIONS = "server:pending:notifications:";

    private final ServerBridge plugin;
    private final BridgeConfig config;
    private final Gson gson;
    private final MiniMessage miniMessage;

    private JedisPool jedisPool;
    private JedisPool
            subscriberPool; // Dedicated pool for subscriber to avoid blocking publish operations
    private Thread subscriberThread;
    private volatile boolean running = true;
    private volatile boolean connected = false;

    // Track players transferring between servers (UUID -> TransferContext)
    // Entries expire after 10 seconds to prevent memory leaks
    private final Map<UUID, TransferContext> pendingTransfers = new ConcurrentHashMap<>();

    public RedisClient(ServerBridge plugin, BridgeConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.gson = new Gson();
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void connect() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        // Increased pool size to handle concurrent async operations
        // Subscriber thread holds 1 connection permanently, so we need more for async tasks
        poolConfig.setMaxTotal(32); // Increased from 10 to handle high concurrency
        poolConfig.setMaxIdle(16); // Increased from 5 to maintain more idle connections
        poolConfig.setMinIdle(4); // Increased from 1 for better response time

        // Connection validation settings - avoid test on borrow to reduce latency
        poolConfig.setTestOnBorrow(false);
        poolConfig.setTestOnReturn(false);
        poolConfig.setTestWhileIdle(true);

        // Eviction settings to remove stale connections
        poolConfig.setTimeBetweenEvictionRunsMillis(30000); // Check every 30 seconds
        poolConfig.setMinEvictableIdleTimeMillis(60000); // Evict after 60 seconds idle
        poolConfig.setNumTestsPerEvictionRun(5); // Increased to test more connections

        // Block when exhausted instead of throwing exception
        poolConfig.setBlockWhenExhausted(true);
        poolConfig.setMaxWaitMillis(5000); // 5 seconds - allows for network hiccups without failing

        // Enable JMX for monitoring (optional but helpful for debugging)
        poolConfig.setJmxEnabled(true);
        poolConfig.setJmxNamePrefix("server-redis-pool");

        // Create a smaller dedicated pool for the subscriber (only needs 1-2 connections)
        JedisPoolConfig subscriberPoolConfig = new JedisPoolConfig();
        subscriberPoolConfig.setMaxTotal(2);
        subscriberPoolConfig.setMaxIdle(1);
        subscriberPoolConfig.setMinIdle(1);
        subscriberPoolConfig.setTestOnBorrow(true);
        subscriberPoolConfig.setBlockWhenExhausted(true);
        subscriberPoolConfig.setMaxWaitMillis(1000);
        subscriberPoolConfig.setJmxEnabled(true);
        subscriberPoolConfig.setJmxNamePrefix("server-redis-subscriber-pool");

        String password = config.getRedisPassword();
        int timeout = 5000; // 5 second timeout for operations
        if (password != null && !password.isEmpty()) {
            jedisPool =
                    new JedisPool(
                            poolConfig,
                            config.getRedisHost(),
                            config.getRedisPort(),
                            timeout,
                            password);
            subscriberPool =
                    new JedisPool(
                            subscriberPoolConfig,
                            config.getRedisHost(),
                            config.getRedisPort(),
                            timeout,
                            password);
        } else {
            jedisPool =
                    new JedisPool(
                            poolConfig, config.getRedisHost(), config.getRedisPort(), timeout);
            subscriberPool =
                    new JedisPool(
                            subscriberPoolConfig,
                            config.getRedisHost(),
                            config.getRedisPort(),
                            timeout);
        }

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.ping();
            connected = true;
            plugin.getLogger().info("Redis connection established.");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to Redis: " + e.getMessage());
            plugin.getLogger()
                    .warning(
                            "Redis features will be disabled. Chat bridge and cross-server features won't work.");
            connected = false;
            // Close both pools since we can't connect
            if (jedisPool != null && !jedisPool.isClosed()) {
                jedisPool.close();
                jedisPool = null;
            }
            if (subscriberPool != null && !subscriberPool.isClosed()) {
                subscriberPool.close();
                subscriberPool = null;
            }
            return;
        }

        startSubscriber();
    }

    public boolean isConnected() {
        return connected && jedisPool != null && !jedisPool.isClosed();
    }

    public void disconnect() {
        running = false;

        if (subscriberThread != null) {
            subscriberThread.interrupt();
        }

        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }

        if (subscriberPool != null && !subscriberPool.isClosed()) {
            subscriberPool.close();
        }

        plugin.getLogger().info("Redis connection closed.");
    }

    private void startSubscriber() {
        subscriberThread =
                new Thread(
                        () -> {
                            while (running) {
                                try (Jedis jedis = subscriberPool.getResource()) {
                                    plugin.getLogger()
                                            .info("Redis subscriber started and listening...");
                                    jedis.subscribe(
                                            new JedisPubSub() {
                                                @Override
                                                public void onMessage(
                                                        String channel, String message) {
                                                    handleMessage(channel, message);
                                                }
                                            },
                                            CHANNEL_CHAT_DISCORD,
                                            CHANNEL_STATUS_REQUEST,
                                            CHANNEL_ECONOMY_DEPOSIT,
                                            CHANNEL_LINK_VERIFY,
                                            CHANNEL_CONSOLE_COMMAND,
                                            CHANNEL_CHAT_CROSSSERVER,
                                            CHANNEL_PLAYER_SWITCH,
                                            CHANNEL_ECONOMY_WITHDRAW);
                                } catch (Exception e) {
                                    if (running) {
                                        plugin.getLogger()
                                                .warning(
                                                        "Redis subscriber error: "
                                                                + e.getMessage());
                                        try {
                                            Thread.sleep(5000);
                                        } catch (InterruptedException ie) {
                                            Thread.currentThread().interrupt();
                                            break;
                                        }
                                    }
                                }
                            }
                        },
                        "ServerBridge-Redis-Subscriber");
        subscriberThread.setDaemon(true);
        subscriberThread.start();
    }

    private void handleMessage(String channel, String message) {
        try {
            JsonObject json = gson.fromJson(message, JsonObject.class);

            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                switch (channel) {
                                    case CHANNEL_CHAT_DISCORD -> handleDiscordChat(json);
                                    case CHANNEL_STATUS_REQUEST -> handleStatusRequest(json);
                                    case CHANNEL_ECONOMY_DEPOSIT -> handleEconomyDeposit(json);
                                    case CHANNEL_CONSOLE_COMMAND -> handleConsoleCommand(json);
                                    case CHANNEL_CHAT_CROSSSERVER -> handleCrossServerChat(json);
                                    case CHANNEL_PLAYER_SWITCH -> handlePlayerSwitch(json);
                                    case CHANNEL_ECONOMY_WITHDRAW -> handleEconomyWithdraw(json);
                                }
                            });
        } catch (Exception e) {
            plugin.getLogger().warning("Error handling Redis message: " + e.getMessage());
        }
    }

    private void handleDiscordChat(JsonObject json) {
        String author = json.has("author") ? json.get("author").getAsString() : "Unknown";
        String content = json.has("message") ? json.get("message").getAsString() : "";

        String format =
                config.getChatBridgeFormat()
                        .replace("%player%", author)
                        .replace("%message%", content);

        var component = miniMessage.deserialize(format);
        Bukkit.getServer().sendMessage(component);
    }

    private void handleStatusRequest(JsonObject json) {
        String requestId = json.has("requestId") ? json.get("requestId").getAsString() : "";

        JsonObject response = new JsonObject();
        response.addProperty("type", "STATUS_RESPONSE");
        response.addProperty("requestId", requestId);
        response.addProperty("server", config.getServerName());
        response.addProperty("online", true);
        response.addProperty("playerCount", Bukkit.getOnlinePlayers().size());
        response.addProperty("maxPlayers", Bukkit.getMaxPlayers());

        try {
            double tps = Bukkit.getTPS()[0];
            response.addProperty("tps", Math.min(tps, 20.0));
        } catch (Exception e) {
            response.addProperty("tps", 20.0);
        }

        publishAsync(CHANNEL_STATUS_RESPONSE, response);
    }

    private void handleEconomyDeposit(JsonObject json) {
        String uuid = json.has("uuid") ? json.get("uuid").getAsString() : "";
        double amount = json.has("amount") ? json.get("amount").getAsDouble() : 0;
        String reason = json.has("reason") ? json.get("reason").getAsString() : "Discord Reward";
        String requestId = json.has("requestId") ? json.get("requestId").getAsString() : "";

        plugin.getLogger()
                .info(
                        "[Economy] Received deposit request from Redis: uuid="
                                + uuid
                                + ", amount="
                                + amount
                                + ", requestId="
                                + requestId);

        boolean success = plugin.getEconomyService().deposit(uuid, amount);

        plugin.getLogger()
                .info(
                        "[Economy] Deposit "
                                + (success ? "SUCCESS" : "FAILED")
                                + " for uuid="
                                + uuid
                                + ", amount="
                                + amount);

        if (success) {
            notifyDeposit(uuid, amount, reason);
        }

        JsonObject response = new JsonObject();
        response.addProperty("type", "ECONOMY_RESPONSE");
        response.addProperty("requestId", requestId);
        response.addProperty("uuid", uuid);
        response.addProperty("success", success);
        response.addProperty("amount", amount);

        publishAsync(CHANNEL_ECONOMY_RESPONSE, response);
        plugin.getLogger().info("[Economy] Published response to Redis for requestId=" + requestId);
    }

    private void handleEconomyWithdraw(JsonObject json) {
        String uuid = json.has("uuid") ? json.get("uuid").getAsString() : "";
        double amount = json.has("amount") ? json.get("amount").getAsDouble() : 0;
        String reason = json.has("reason") ? json.get("reason").getAsString() : "Discord Casino";
        String requestId = json.has("requestId") ? json.get("requestId").getAsString() : "";

        plugin.getLogger()
                .info(
                        "[Economy] Received withdraw request from Redis: uuid="
                                + uuid
                                + ", amount="
                                + amount
                                + ", requestId="
                                + requestId);

        boolean success = plugin.getEconomyService().withdraw(uuid, amount);

        plugin.getLogger()
                .info(
                        "[Economy] Withdraw "
                                + (success ? "SUCCESS" : "FAILED")
                                + " for uuid="
                                + uuid
                                + ", amount="
                                + amount);

        JsonObject response = new JsonObject();
        response.addProperty("type", "ECONOMY_RESPONSE");
        response.addProperty("requestId", requestId);
        response.addProperty("uuid", uuid);
        response.addProperty("success", success);
        response.addProperty("amount", amount);

        publishAsync(CHANNEL_ECONOMY_RESPONSE, response);
        plugin.getLogger()
                .info("[Economy] Published withdraw response to Redis for requestId=" + requestId);
    }

    private void handleConsoleCommand(JsonObject json) {
        String command = json.has("command") ? json.get("command").getAsString() : "";

        if (command.isEmpty()) {
            plugin.getLogger().warning("Received empty console command from Redis");
            return;
        }

        plugin.getLogger().info("Executing console command from Discord: " + command);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private void handleCrossServerChat(JsonObject json) {
        if (!config.isCrossServerChatEnabled()) {
            plugin.getLogger().fine("[CrossChat] Cross-server chat disabled, ignoring message");
            return;
        }

        String originServer =
                json.has("originServer") && !json.get("originServer").isJsonNull()
                        ? json.get("originServer").getAsString()
                        : "Unknown";
        String playerName =
                json.has("player") && !json.get("player").isJsonNull()
                        ? json.get("player").getAsString()
                        : "Unknown";
        String prefix =
                json.has("prefix") && !json.get("prefix").isJsonNull()
                        ? json.get("prefix").getAsString()
                        : "";
        String suffix =
                json.has("suffix") && !json.get("suffix").isJsonNull()
                        ? json.get("suffix").getAsString()
                        : "";
        String message =
                json.has("message") && !json.get("message").isJsonNull()
                        ? json.get("message").getAsString()
                        : "";

        plugin.getLogger()
                .info(
                        "[CrossChat] Received from "
                                + originServer
                                + ": "
                                + playerName
                                + " > "
                                + message);

        // Prevent message loops - ignore messages from this server
        if (originServer.equalsIgnoreCase(config.getServerName())) {
            plugin.getLogger().fine("[CrossChat] Ignoring own message from " + originServer);
            return;
        }

        // Capitalize server name for display (e.g., "smp" -> "SMP", "lobby" -> "Lobby")
        String displayServer = formatServerName(originServer);

        // Get format from config and apply placeholders
        String format =
                config.getCrossServerChatFormat()
                        .replace("%server%", displayServer)
                        .replace("%prefix%", prefix != null ? prefix : "")
                        .replace("%player%", playerName)
                        .replace("%suffix%", suffix != null ? suffix : "")
                        .replace("%message%", escapeUserContent(message));

        // Convert legacy color codes in prefix/suffix to section symbols, then parse with
        // MiniMessage
        format = LegacyText.colorize(format);

        try {
            var component = miniMessage.deserialize(format);
            Bukkit.getServer().sendMessage(component);
        } catch (Exception e) {
            // Fallback: if MiniMessage fails, try plain text
            plugin.getLogger()
                    .warning("Failed to parse cross-server chat format: " + e.getMessage());
            Bukkit.getServer()
                    .sendMessage(
                            net.kyori.adventure.text.Component.text(
                                    "[" + displayServer + "] " + playerName + ": " + message));
        }
    }

    /**
     * Formats server name for display. Short names (<=3 chars) are uppercased (smp -> SMP) Longer
     * names are title-cased (lobby -> Lobby)
     */
    private String formatServerName(String serverName) {
        if (serverName == null || serverName.isEmpty()) {
            return "Unknown";
        }
        if (serverName.length() <= 3) {
            return serverName.toUpperCase();
        }
        return serverName.substring(0, 1).toUpperCase() + serverName.substring(1).toLowerCase();
    }

    /** Escapes user content to prevent MiniMessage injection */
    private String escapeUserContent(String content) {
        if (content == null) return "";
        return content.replace("<", "\\<").replace(">", "\\>");
    }

    // === Deposit Notification System ===

    private void notifyDeposit(String uuidStr, double amount, String reason) {
        NumberFormat fmt = NumberFormat.getNumberInstance(Locale.US);
        fmt.setMinimumFractionDigits(0);
        fmt.setMaximumFractionDigits(2);
        String formatted = "$" + fmt.format(amount);

        String message =
                "<dark_gray>[<#7289DA>Discord<dark_gray>] <green>\u2713 <gray>You received <white>"
                        + formatted
                        + " <gray>("
                        + reason
                        + ")";

        Player player = Bukkit.getPlayer(UUID.fromString(uuidStr));
        if (player != null && player.isOnline()) {
            TextUtil.send(player, message);
        } else {
            queuePendingNotification(uuidStr, message);
        }
    }

    private void queuePendingNotification(String uuid, String message) {
        if (!isConnected()) return;

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try (Jedis jedis = jedisPool.getResource()) {
                                String key = KEY_PENDING_NOTIFICATIONS + uuid;
                                jedis.rpush(key, message);
                                jedis.expire(key, 604800); // 7-day TTL
                            } catch (Exception e) {
                                plugin.getLogger()
                                        .warning(
                                                "Failed to queue pending notification: "
                                                        + e.getMessage());
                            }
                        });
    }

    public List<String> consumePendingNotifications(String uuid) {
        if (!isConnected()) return Collections.emptyList();

        try (Jedis jedis = jedisPool.getResource()) {
            String key = KEY_PENDING_NOTIFICATIONS + uuid;
            List<String> messages = jedis.lrange(key, 0, -1);
            if (messages != null && !messages.isEmpty()) {
                jedis.del(key);
                return messages;
            }
        } catch (Exception e) {
            plugin.getLogger()
                    .warning("Failed to consume pending notifications: " + e.getMessage());
        }
        return Collections.emptyList();
    }

    public void publish(String channel, Object message) {
        if (!isConnected()) {
            return; // Silently skip if not connected
        }
        try (Jedis jedis = jedisPool.getResource()) {
            String json = message instanceof String ? (String) message : gson.toJson(message);
            jedis.publish(channel, json);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to publish to " + channel + ": " + e.getMessage());
        }
    }

    public void publishAsync(String channel, Object message) {
        if (!isConnected()) {
            return; // Silently skip if not connected
        }

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            long start = System.currentTimeMillis();
                            try (Jedis jedis = jedisPool.getResource()) {
                                long acquireTime = System.currentTimeMillis() - start;
                                if (acquireTime > 100) {
                                    plugin.getLogger()
                                            .warning(
                                                    "Redis pool latency: "
                                                            + acquireTime
                                                            + "ms to acquire connection");
                                }
                                String json =
                                        message instanceof String
                                                ? (String) message
                                                : gson.toJson(message);
                                jedis.publish(channel, json);
                            } catch (Exception e) {
                                plugin.getLogger()
                                        .warning(
                                                "Failed to publish to "
                                                        + channel
                                                        + " (async): "
                                                        + e.getMessage());
                            }
                        });
    }

    public void publishChat(String player, String uuid, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "MINECRAFT_CHAT");
        json.addProperty("server", config.getServerName());
        json.addProperty("player", player);
        json.addProperty("uuid", uuid);
        json.addProperty("message", message);
        json.addProperty("timestamp", System.currentTimeMillis());
        publish(CHANNEL_CHAT_MINECRAFT, json);
    }

    public void publishCrossServerChat(
            String player, String uuid, String prefix, String suffix, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "CROSSSERVER_CHAT");
        json.addProperty("originServer", config.getServerName());
        json.addProperty("player", player);
        json.addProperty("uuid", uuid);
        json.addProperty("prefix", prefix != null ? prefix : "");
        json.addProperty("suffix", suffix != null ? suffix : "");
        json.addProperty("message", message);
        json.addProperty("timestamp", System.currentTimeMillis());
        json.addProperty("messageId", java.util.UUID.randomUUID().toString());

        plugin.getLogger()
                .info(
                        "[CrossChat] Publishing from "
                                + config.getServerName()
                                + ": "
                                + player
                                + " > "
                                + message);
        publishAsync(CHANNEL_CHAT_CROSSSERVER, json);
    }

    public void publishPlayerJoin(String player, String uuid) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "PLAYER_JOIN");
        json.addProperty("server", config.getServerName());
        json.addProperty("player", player);
        json.addProperty("uuid", uuid);
        json.addProperty("timestamp", System.currentTimeMillis());
        publish(CHANNEL_PLAYER_JOIN, json);
    }

    public void publishPlayerJoinAsync(String player, String uuid) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "PLAYER_JOIN");
        json.addProperty("server", config.getServerName());
        json.addProperty("player", player);
        json.addProperty("uuid", uuid);
        json.addProperty("timestamp", System.currentTimeMillis());
        publishAsync(CHANNEL_PLAYER_JOIN, json);
    }

    public void publishPlayerQuit(String player, String uuid) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "PLAYER_QUIT");
        json.addProperty("server", config.getServerName());
        json.addProperty("player", player);
        json.addProperty("uuid", uuid);
        json.addProperty("timestamp", System.currentTimeMillis());
        publish(CHANNEL_PLAYER_QUIT, json);
    }

    public void publishPlayerQuitAsync(String player, String uuid) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "PLAYER_QUIT");
        json.addProperty("server", config.getServerName());
        json.addProperty("player", player);
        json.addProperty("uuid", uuid);
        json.addProperty("timestamp", System.currentTimeMillis());
        publishAsync(CHANNEL_PLAYER_QUIT, json);
    }

    public void publishLinkComplete(
            String requestId, long discordId, String uuid, String username, boolean success) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "LINK_COMPLETE");
        json.addProperty("requestId", requestId);
        json.addProperty("discordId", discordId);
        json.addProperty("uuid", uuid);
        json.addProperty("username", username);
        json.addProperty("success", success);
        json.addProperty("timestamp", System.currentTimeMillis());
        publish(CHANNEL_LINK_COMPLETE, json);
    }

    public void publishChangelogAdd(
            String requestId,
            String version,
            String platform,
            String category,
            String description,
            String playerName) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "CHANGELOG_ADD");
        json.addProperty("requestId", requestId);
        json.addProperty("version", version);
        json.addProperty("platform", platform);
        json.addProperty("category", category);
        json.addProperty("description", description);
        json.addProperty("playerName", playerName);
        json.addProperty("timestamp", System.currentTimeMillis());
        publish(CHANNEL_CHANGELOG_ADD, json);
    }

    public void publishChangelogAddAsync(
            String requestId,
            String version,
            String platform,
            String category,
            String description,
            String playerName) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "CHANGELOG_ADD");
        json.addProperty("requestId", requestId);
        json.addProperty("version", version);
        json.addProperty("platform", platform);
        json.addProperty("category", category);
        json.addProperty("description", description);
        json.addProperty("playerName", playerName);
        json.addProperty("timestamp", System.currentTimeMillis());
        publishAsync(CHANNEL_CHANGELOG_ADD, json);
    }

    // === Bounty System Publishing ===

    public void publishBountyPlaced(
            String placerName,
            String placerUuid,
            String targetName,
            String targetUuid,
            double amount,
            double total) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "BOUNTY_PLACED");
        json.addProperty("placerName", placerName);
        json.addProperty("placerUuid", placerUuid);
        json.addProperty("targetName", targetName);
        json.addProperty("targetUuid", targetUuid);
        json.addProperty("amount", amount);
        json.addProperty("total", total);
        json.addProperty("timestamp", System.currentTimeMillis());
        publishAsync(CHANNEL_BOUNTY_PLACED, json);
    }

    public void publishBountyClaimed(
            String killerName,
            String killerUuid,
            String victimName,
            String victimUuid,
            double payout) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "BOUNTY_CLAIMED");
        json.addProperty("killerName", killerName);
        json.addProperty("killerUuid", killerUuid);
        json.addProperty("victimName", victimName);
        json.addProperty("victimUuid", victimUuid);
        json.addProperty("payout", payout);
        json.addProperty("timestamp", System.currentTimeMillis());
        publishAsync(CHANNEL_BOUNTY_CLAIMED, json);
    }

    // === Death System Publishing ===

    public void publishDeathStored(
            String playerName, String playerUuid, int itemCount, double buybackCost) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "DEATH_STORED");
        json.addProperty("playerName", playerName);
        json.addProperty("playerUuid", playerUuid);
        json.addProperty("itemCount", itemCount);
        json.addProperty("buybackCost", buybackCost);
        json.addProperty("timestamp", System.currentTimeMillis());
        publishAsync(CHANNEL_DEATH_STORED, json);
    }

    public void publishDeathPurchased(String playerName, String playerUuid, double cost) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "DEATH_PURCHASED");
        json.addProperty("playerName", playerName);
        json.addProperty("playerUuid", playerUuid);
        json.addProperty("cost", cost);
        json.addProperty("timestamp", System.currentTimeMillis());
        publishAsync(CHANNEL_DEATH_PURCHASED, json);
    }

    // === Moderation Logging ===

    public void publishModerationLog(
            String targetName,
            String targetUuid,
            String staffName,
            String staffUuid,
            String punishmentType,
            String reason,
            String duration) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "MODERATION_LOG");
        json.addProperty("targetName", targetName);
        json.addProperty("targetUuid", targetUuid);
        json.addProperty("staffName", staffName);
        json.addProperty("staffUuid", staffUuid);
        json.addProperty("punishmentType", punishmentType);
        json.addProperty("reason", reason);
        if (duration != null) {
            json.addProperty("duration", duration);
        }
        json.addProperty("timestamp", System.currentTimeMillis());
        publishAsync(CHANNEL_MODERATION_LOG, json);
    }

    // === Event Announcements ===

    public void publishEventAnnouncement(String eventName, String eventType, String message) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "EVENT_ANNOUNCEMENT");
        json.addProperty("eventName", eventName);
        json.addProperty("eventType", eventType);
        json.addProperty("message", message);
        json.addProperty("timestamp", System.currentTimeMillis());
        publishAsync(CHANNEL_EVENT_ANNOUNCEMENT, json);
    }

    public Gson getGson() {
        return gson;
    }

    /**
     * Get the Jedis pool for advanced Redis operations.
     *
     * @return The JedisPool, or null if not connected
     */
    public JedisPool getJedisPool() {
        return jedisPool;
    }

    /**
     * Handles player switch events from Velocity proxy. When a player switches servers, we store
     * their transfer context so the destination server can detect it's a transfer (not a fresh
     * join).
     */
    private void handlePlayerSwitch(JsonObject json) {
        String uuidStr = json.has("uuid") ? json.get("uuid").getAsString() : null;
        String fromServer = json.has("from") ? json.get("from").getAsString() : null;
        String toServer = json.has("to") ? json.get("to").getAsString() : null;
        String playerName = json.has("player") ? json.get("player").getAsString() : null;
        long timestamp =
                json.has("timestamp")
                        ? json.get("timestamp").getAsLong()
                        : System.currentTimeMillis();

        if (uuidStr == null || toServer == null) {
            return;
        }

        // Only process if this server is the destination
        if (!toServer.equalsIgnoreCase(config.getServerName())) {
            return;
        }

        try {
            UUID uuid = UUID.fromString(uuidStr);
            TransferContext context =
                    new TransferContext(uuid, playerName, fromServer, toServer, timestamp);
            pendingTransfers.put(uuid, context);

            plugin.getLogger()
                    .info(
                            "Player "
                                    + playerName
                                    + " transferring from "
                                    + fromServer
                                    + " to "
                                    + toServer);

            // Clean up after 10 seconds if not consumed
            Bukkit.getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                TransferContext old = pendingTransfers.get(uuid);
                                if (old != null && old.timestamp == timestamp) {
                                    pendingTransfers.remove(uuid);
                                }
                            },
                            200L); // 10 seconds

        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid UUID in player switch event: " + uuidStr);
        }
    }

    /**
     * Check if a player is transferring from another server.
     *
     * @param uuid The player's UUID
     * @return TransferContext if this is a transfer, null if fresh join
     */
    public TransferContext getTransferContext(UUID uuid) {
        return pendingTransfers.get(uuid);
    }

    /**
     * Consume and remove the transfer context for a player. Call this after processing the
     * transfer.
     *
     * @param uuid The player's UUID
     * @return TransferContext if this was a transfer, null if fresh join
     */
    public TransferContext consumeTransferContext(UUID uuid) {
        return pendingTransfers.remove(uuid);
    }

    /**
     * Check if a player is transferring (without consuming the context).
     *
     * @param uuid The player's UUID
     * @return true if player is transferring from another server
     */
    public boolean isTransfer(UUID uuid) {
        return pendingTransfers.containsKey(uuid);
    }

    /** Context information about a player transferring between servers. */
    public static class TransferContext {
        private final UUID uuid;
        private final String playerName;
        private final String fromServer;
        private final String toServer;
        private final long timestamp;

        public TransferContext(
                UUID uuid, String playerName, String fromServer, String toServer, long timestamp) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.fromServer = fromServer;
            this.toServer = toServer;
            this.timestamp = timestamp;
        }

        public UUID getUuid() {
            return uuid;
        }

        public String getPlayerName() {
            return playerName;
        }

        public String getFromServer() {
            return fromServer;
        }

        public String getToServer() {
            return toServer;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
