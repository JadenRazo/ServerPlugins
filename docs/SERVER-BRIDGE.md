# ServerBridge

**Version:** 1.0-SNAPSHOT
**Type:** Discord-Minecraft Bridge & Cross-Server Communication
**Main Class:** `net.serverplugins.bridge.ServerBridge`

## Overview

ServerBridge provides Discord integration with Minecraft, cross-platform chat synchronization, daily rewards for linked accounts, and Redis-based cross-server communication. It serves as the communication hub for the entire server network.

## Key Features

### Discord Linking
- Link Minecraft accounts to Discord
- Verification code system
- Persistent linking across sessions
- Unlink functionality

### Daily Rewards
- Claim daily rewards for linked Discord accounts
- Configurable reward commands
- 24-hour cooldown system
- Streak bonuses (optional)

### Cross-Platform Chat
- Discord ↔ Minecraft chat sync
- Formatted messages with role colors
- Mention support (@username)
- Emoji conversion

### Redis Messaging
- Cross-server communication
- Pub/sub event system
- Player data sync
- Broadcast messages across network

### Changelog System
- In-game changelog management
- Discord webhook integration
- Version tracking
- Platform-specific changelogs (Java/Bedrock)

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/verifylink <code>` | None | Verify Discord link |
| `/verify <code>` | None | Alias for verifylink |
| `/link <code>` | None | Alias for verifylink |
| `/dclink <code>` | None | Alias for verifylink |
| `/discordunlink` | None | Unlink Discord account |
| `/discorddaily` | `serverbridge.daily` | Claim daily reward |
| `/ddaily` | `serverbridge.daily` | Alias |
| `/dd` | `serverbridge.daily` | Alias |
| `/daily` | `serverbridge.daily` | Alias |
| `/changelog <version> <platform> <category> <desc>` | `serverbridge.changelog` | Add changelog entry |

## Permissions

### Player Permissions
- `serverbridge.daily` - Claim daily rewards
- `serverbridge.chat` - Send messages to Discord

### Admin Permissions
- `serverbridge.changelog` - Manage changelogs
- `serverbridge.admin` - Admin commands

## Configuration

### config.yml
```yaml
# Discord Bot Settings
discord:
  token: "YOUR_BOT_TOKEN_HERE"
  guild-id: "123456789012345678"
  chat-channel-id: "123456789012345678"
  link-channel-id: "123456789012345678"
  changelog-webhook-url: "https://discord.com/api/webhooks/..."

  # Message Formatting
  formatting:
    minecraft-to-discord: "**{player}**: {message}"
    discord-to-minecraft: "&7[&9Discord&7] &b{user}&7: {message}"
    join-message: "**{player}** joined the server"
    quit-message: "**{player}** left the server"

  # Role Colors
  role-colors:
    "123456789": "&c"  # Admin role ID -> Red
    "987654321": "&6"  # Mod role ID -> Gold

# Discord Linking
linking:
  enabled: true
  code-length: 6
  code-expiry: 300  # 5 minutes in seconds
  rewards:
    enabled: true
    commands:
      - "lp user {player} permission set serverbridge.linked true"
      - "eco give {player} 5000"
  messages:
    link-success: "&aSuccessfully linked your Discord account!"
    link-failed: "&cInvalid or expired code!"
    already-linked: "&cYou are already linked!"
    unlink-success: "&aDiscord account unlinked!"

# Daily Rewards
daily-rewards:
  enabled: true
  require-linked: true
  cooldown: 86400  # 24 hours
  rewards:
    - "eco give {player} 1000"
    - "give {player} diamond 5"
  streak-bonus:
    enabled: true
    3: "eco give {player} 500"
    7: "eco give {player} 2000"
    30: "eco give {player} 10000"
  messages:
    claimed: "&aDaily reward claimed! Next reward in 24 hours."
    cooldown: "&cYou can claim your next reward in {time}."
    not-linked: "&cYou must link your Discord account to claim rewards!"

# Redis Configuration
redis:
  enabled: true
  host: "localhost"
  port: 6379
  password: ""
  database: 0
  ssl: false

  # Pub/Sub Channels
  channels:
    global-chat: "server:chat"
    player-sync: "server:player"
    broadcast: "server:broadcast"
    events: "server:events"

  # Connection Pool
  pool:
    max-total: 10
    max-idle: 5
    min-idle: 1

# Cross-Server Chat
cross-server-chat:
  enabled: true
  format: "&7[{server}] &r{player}&7: {message}"
  servers:
    lobby: "Lobby"
    survival: "SMP"
    creative: "Creative"

# Changelog System
changelog:
  enabled: true
  versions-stored: 10
  categories:
    - "Added"
    - "Changed"
    - "Fixed"
    - "Removed"
  platforms:
    - "Java"
    - "Bedrock"
    - "Both"

# Messages
messages:
  prefix: "&7[&9Bridge&7]&r "
```

### links.yml (Player Links Storage)
```yaml
# UUID -> Discord ID mapping
links:
  "550e8400-e29b-41d4-a716-446655440000": "123456789012345678"
  "550e8400-e29b-41d4-a716-446655440001": "987654321098765432"

# Pending verification codes
pending:
  "ABC123":
    uuid: "550e8400-e29b-41d4-a716-446655440002"
    discord-id: "111222333444555666"
    expires: 1704067800000
```

### daily-cooldowns.yml
```yaml
# UUID -> Last claim timestamp
cooldowns:
  "550e8400-e29b-41d4-a716-446655440000": 1704067200000

# Streak tracking
streaks:
  "550e8400-e29b-41d4-a716-446655440000":
    current: 7
    last-claim: 1704067200000
```

## Implementation Details

### Package Structure
```
net.serverplugins.bridge/
├── ServerBridge.java              # Main plugin class
├── BridgeConfig.java              # Configuration wrapper
├── commands/
│   ├── VerifyLinkCommand.java     # Discord linking
│   ├── DiscordUnlinkCommand.java  # Unlinking
│   ├── DiscordDailyCommand.java   # Daily rewards
│   └── ChangelogCommand.java      # Changelog management
├── discord/
│   ├── DiscordBot.java            # JDA bot instance
│   ├── listeners/
│   │   ├── MessageListener.java   # Chat sync
│   │   └── LinkListener.java      # Link commands
│   └── ChangelogWebhook.java      # Webhook sender
├── redis/
│   ├── RedisManager.java          # Redis connection
│   ├── RedisSubscriber.java       # Pub/Sub listener
│   └── RedisPublisher.java        # Message publishing
├── managers/
│   ├── LinkManager.java           # Discord linking
│   ├── DailyRewardManager.java    # Daily rewards
│   └── ChangelogManager.java      # Changelog tracking
└── models/
    ├── LinkRequest.java           # Verification code
    └── ChangelogEntry.java        # Changelog data
```

### Discord Bot Setup

**DiscordBot.java**
```java
public class DiscordBot {
    private JDA jda;

    public void start(String token) throws Exception {
        jda = JDABuilder.createDefault(token)
            .setActivity(Activity.playing("ServerPlugins"))
            .addEventListeners(new MessageListener(), new LinkListener())
            .build()
            .awaitReady();
    }

    public void sendMessage(String channelId, String message) {
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel != null) {
            channel.sendMessage(message).queue();
        }
    }
}
```

**MessageListener.java**
```java
public class MessageListener extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.getChannel().getId().equals(config.getChatChannelId())) return;

        String username = event.getAuthor().getName();
        String message = event.getMessage().getContentDisplay();

        // Format for Minecraft
        String formatted = config.getDiscordToMinecraftFormat()
            .replace("{user}", username)
            .replace("{message}", message);

        // Broadcast to all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', formatted));
        }

        // Publish to Redis for other servers
        redisPublisher.publish("chat", username, message);
    }
}
```

### Discord Linking System

**LinkManager.java**
```java
public class LinkManager {
    private Map<String, LinkRequest> pendingLinks = new HashMap<>();

    public String createLinkRequest(String discordId) {
        String code = generateCode(6);
        LinkRequest request = new LinkRequest(discordId, System.currentTimeMillis() + 300000);
        pendingLinks.put(code, request);
        return code;
    }

    public boolean verifyLink(Player player, String code) {
        LinkRequest request = pendingLinks.get(code);

        if (request == null || request.isExpired()) {
            return false;
        }

        // Save link
        saveLink(player.getUniqueId(), request.getDiscordId());
        pendingLinks.remove(code);

        // Execute reward commands
        for (String command : config.getLinkRewardCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                command.replace("{player}", player.getName()));
        }

        return true;
    }

    private String generateCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }
}
```

### Daily Rewards System

**DailyRewardManager.java**
```java
public class DailyRewardManager {
    public boolean claimReward(Player player) {
        UUID uuid = player.getUniqueId();

        // Check if linked
        if (config.requireLinked() && !linkManager.isLinked(uuid)) {
            player.sendMessage(config.getMessage("not-linked"));
            return false;
        }

        // Check cooldown
        long lastClaim = getLastClaim(uuid);
        long now = System.currentTimeMillis();
        long cooldown = config.getCooldown() * 1000;

        if (now - lastClaim < cooldown) {
            long remaining = (cooldown - (now - lastClaim)) / 1000;
            String time = formatTime(remaining);
            player.sendMessage(config.getMessage("cooldown").replace("{time}", time));
            return false;
        }

        // Update streak
        int streak = updateStreak(uuid, now);

        // Execute reward commands
        for (String command : config.getRewardCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                command.replace("{player}", player.getName()));
        }

        // Execute streak bonus
        if (config.isStreakBonusEnabled() && config.hasStreakBonus(streak)) {
            for (String command : config.getStreakBonusCommands(streak)) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    command.replace("{player}", player.getName()));
            }
        }

        // Update cooldown
        setLastClaim(uuid, now);

        player.sendMessage(config.getMessage("claimed"));
        return true;
    }

    private int updateStreak(UUID uuid, long now) {
        int current = getStreak(uuid);
        long lastClaim = getLastClaim(uuid);

        // Check if within 48 hours (grace period)
        if (now - lastClaim <= 172800000) {  // 48 hours
            current++;
        } else {
            current = 1;  // Reset streak
        }

        saveStreak(uuid, current);
        return current;
    }
}
```

### Redis Integration

**RedisManager.java**
```java
public class RedisManager {
    private JedisPool pool;

    public void connect() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(config.getMaxTotal());
        poolConfig.setMaxIdle(config.getMaxIdle());
        poolConfig.setMinIdle(config.getMinIdle());

        this.pool = new JedisPool(
            poolConfig,
            config.getHost(),
            config.getPort(),
            2000,
            config.getPassword(),
            config.getDatabase(),
            config.isSsl()
        );
    }

    public void publish(String channel, String message) {
        try (Jedis jedis = pool.getResource()) {
            jedis.publish(channel, message);
        }
    }

    public void subscribe(String channel, Consumer<String> handler) {
        new Thread(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String ch, String msg) {
                        if (ch.equals(channel)) {
                            handler.accept(msg);
                        }
                    }
                }, channel);
            }
        }).start();
    }
}
```

**Cross-Server Chat**
```java
// Publish to Redis
@EventHandler
public void onChat(AsyncChatEvent event) {
    Player player = event.getPlayer();
    String message = PlainTextComponentSerializer.plainText()
        .serialize(event.message());

    JSONObject data = new JSONObject();
    data.put("server", config.getServerName());
    data.put("player", player.getName());
    data.put("message", message);

    redisManager.publish("server:chat", data.toString());
}

// Subscribe to Redis
redisManager.subscribe("server:chat", msg -> {
    JSONObject data = new JSONObject(msg);
    String server = data.getString("server");

    // Don't echo our own messages
    if (server.equals(config.getServerName())) return;

    String player = data.getString("player");
    String message = data.getString("message");

    String formatted = config.getCrossServerFormat()
        .replace("{server}", server)
        .replace("{player}", player)
        .replace("{message}", message);

    // Broadcast to Minecraft
    Bukkit.broadcast(Component.text(formatted));
});
```

### Changelog System

**ChangelogManager.java**
```java
public void addEntry(String version, String platform, String category, String description) {
    ChangelogEntry entry = new ChangelogEntry(
        version, platform, category, description, System.currentTimeMillis()
    );

    saveEntry(entry);

    // Send to Discord webhook
    sendToWebhook(entry);
}

private void sendToWebhook(ChangelogEntry entry) {
    String webhookUrl = config.getChangelogWebhookUrl();

    JSONObject embed = new JSONObject();
    embed.put("title", "Changelog - " + entry.getVersion());
    embed.put("description", entry.getDescription());
    embed.put("color", getCategoryColor(entry.getCategory()));

    embed.put("fields", new JSONArray()
        .put(new JSONObject()
            .put("name", "Platform")
            .put("value", entry.getPlatform())
            .put("inline", true))
        .put(new JSONObject()
            .put("name", "Category")
            .put("value", entry.getCategory())
            .put("inline", true))
    );

    JSONObject payload = new JSONObject();
    payload.put("embeds", new JSONArray().put(embed));

    // Send HTTP POST to webhook
    sendWebhookRequest(webhookUrl, payload.toString());
}
```

## Dependencies

### Hard Dependencies
- ServerAPI
- Vault

### Soft Dependencies
- LuckPerms (permissions)
- Essentials (economy fallback)
- PlaceholderAPI (placeholders)
- TAB (tablist sync)

### External Libraries
- JDA (Discord bot)
- Jedis (Redis client)
- JSON (data serialization)

## Best Practices

1. **Bot Token**: Store Discord bot token securely, never commit to git
2. **Redis Password**: Use strong password for Redis in production
3. **Rate Limiting**: Implement rate limiting for Discord messages
4. **Error Handling**: Gracefully handle Discord/Redis disconnections
5. **Logging**: Log all link attempts and reward claims

## Performance Considerations

- Redis pub/sub is async and non-blocking
- Discord messages sent in async tasks
- Link codes expire automatically
- Connection pooling for Redis

## Common Issues

### Bot Not Responding
Check bot token, permissions, and intents in Discord Developer Portal.

### Redis Connection Failed
Verify Redis server is running and credentials are correct.

### Daily Rewards Not Working
Ensure linking is enabled and player is linked.

### Chat Not Syncing
Check Redis channels match across all servers.
