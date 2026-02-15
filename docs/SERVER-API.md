# ServerAPI

**Version:** 1.0-SNAPSHOT
**Type:** Shared Library / Foundation Plugin
**Main Class:** `net.serverplugins.api.ServerAPI`

## Overview

ServerAPI is the foundational library for the entire ServerPlugins plugin ecosystem. It provides shared infrastructure including database abstraction, economy integration, permission management, GUI systems, messaging utilities, and more. All other plugins depend on this module.

## Key Features

### Database Abstraction Layer
- **Multi-Database Support**: H2, SQLite, and MariaDB
- **Connection Pooling**: HikariCP for efficient connection management
- **Async Operations**: Non-blocking database queries
- **Schema Management**: Automatic schema creation from SQL files

### Economy Provider
- **Vault Integration**: Standardized economy operations
- **Thread-Safe**: Safe concurrent money operations
- **Permission Checks**: Integrated permission validation

### Permission Provider
- **LuckPerms Integration**: Modern permission management
- **Group Management**: Add/remove player groups
- **Permission Queries**: Check player permissions

### GUI System
- **Inventory Management**: Create interactive chest GUIs
- **ProtocolLib Integration**: Hide player inventory during GUI interaction
- **Click Handlers**: Event-driven GUI interactions
- **Pagination Support**: Multi-page GUI support

### Message System
- **MiniMessage Support**: Modern text formatting with gradients, hover, click events
- **Legacy Color Codes**: Support for &-style color codes
- **Placeholder Support**: PlaceholderAPI integration
- **Broadcast System**: Server-wide announcements with filters

### ResourcePack Icon System
- **Custom Menu Titles**: Use resource pack icons in inventory titles
- **Icon Registry**: Centralized icon management
- **Unicode Support**: Custom unicode characters for menu headers

### Server Transfer Commands
- **Multi-Platform**: BungeeCord and Velocity support
- **Transfer Commands**: Move players between servers
- **Admin Controls**: Transfer specific players or all online players

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/server <server> [player\|all]` | `serverapi.server` | Transfer to another server |
| `/hub` | `serverapi.server` | Return to hub server |
| `/lobby` | `serverapi.server` | Return to lobby server |
| `/transfer <server> <player\|all>` | `serverapi.transfer` | Admin transfer command |

## Configuration

### config.yml
```yaml
database:
  type: mariadb  # h2, sqlite, or mariadb
  host: localhost
  port: 3306
  database: serverplugins
  username: root
  password: password
  pool-size: 10

messages:
  prefix: "&7[&6ServerPlugins&7]&r "

resource-pack:
  enabled: true
  icons:
    menu: "\uE000"
    warning: "\uE001"
```

## Database

### Supported Types
1. **H2**: Embedded, file-based (development)
2. **SQLite**: Simple file-based (small servers)
3. **MariaDB**: Production-grade (recommended for live servers)

### Configuration Parsers
- `LocationConfigParser`: Serialize/deserialize Bukkit Locations
- `ItemStackConfigParser`: Serialize/deserialize ItemStacks
- `MessageConfigParser`: Parse MiniMessage and legacy formats
- `CustomSoundConfigParser`: Parse custom sound configurations

## Implementation Details

### Package Structure
```
net.serverplugins.api/
├── ServerAPI.java               # Main plugin class
├── database/
│   ├── Database.java            # Database interface
│   ├── H2Database.java          # H2 implementation
│   ├── SQLiteDatabase.java      # SQLite implementation
│   └── MariaDBDatabase.java     # MariaDB implementation
├── economy/
│   └── EconomyProvider.java    # Vault economy wrapper
├── permissions/
│   └── PermissionProvider.java # LuckPerms wrapper
├── gui/
│   ├── GuiManager.java          # GUI management
│   └── PacketUtils.java         # ProtocolLib packet manipulation
├── message/
│   ├── MessageManager.java      # Message formatting
│   └── BroadcastManager.java    # Broadcast system
├── config/
│   ├── ConfigManager.java       # Configuration management
│   └── parsers/                 # Config value parsers
└── utils/
    ├── ResourcePackIcons.java   # Icon system
    └── ItemBuilder.java         # ItemStack builder utility
```

### Database Interface
```java
public interface Database {
    CompletableFuture<Void> connect();
    CompletableFuture<Void> disconnect();
    CompletableFuture<Void> executeUpdate(String sql, Object... params);
    <T> CompletableFuture<T> executeQuery(String sql, ResultSetHandler<T> handler, Object... params);
    CompletableFuture<Void> executeStatements(String sql);
    boolean isConnected();
}
```

### GUI Manager Example
```java
GuiManager guiManager = plugin.getGuiManager();
Inventory gui = guiManager.createGui(player, 54, "My Custom Menu");

guiManager.setItem(gui, 0, new ItemStack(Material.DIAMOND), (p, click) -> {
    p.sendMessage("You clicked a diamond!");
    p.closeInventory();
});

guiManager.openGui(player, gui);
```

### Economy Provider Example
```java
EconomyProvider economy = plugin.getEconomyProvider();

// Check balance
double balance = economy.getBalance(player);

// Deposit money
economy.depositPlayer(player, 100.0);

// Withdraw with permission check
if (economy.has(player, 50.0)) {
    economy.withdrawPlayer(player, 50.0);
}
```

### Message System Example
```java
MessageManager msg = plugin.getMessageManager();

// Send MiniMessage format
msg.sendMessage(player, "<gradient:red:blue>Rainbow Text</gradient>");

// Send with placeholders
msg.sendMessage(player, "Hello %player_name%!", player);

// Broadcast to server
msg.broadcast("&aServer event started!");
```

## Dependencies

### Hard Dependencies
- Paper/Spigot API 1.21.4
- Vault (economy)

### Soft Dependencies
- LuckPerms (permissions)
- ProtocolLib (GUI features)
- PlaceholderAPI (placeholders)

## Integration Guide

### Adding ServerAPI to Your Plugin

**pom.xml**
```xml
<dependency>
    <groupId>net.serverplugins</groupId>
    <artifactId>server-api</artifactId>
    <version>1.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

**plugin.yml**
```yaml
depend: [ServerAPI]
```

### Accessing ServerAPI Instance
```java
public class MyPlugin extends JavaPlugin {
    private ServerAPI api;

    @Override
    public void onEnable() {
        Plugin plugin = getServer().getPluginManager().getPlugin("ServerAPI");
        if (plugin instanceof ServerAPI) {
            this.api = (ServerAPI) plugin;
        }
    }
}
```

## Best Practices

1. **Database Operations**: Always use async operations for database queries
2. **GUI Cleanup**: Properly close GUIs to prevent memory leaks
3. **Error Handling**: Wrap database operations in try-catch blocks
4. **Configuration**: Validate config values on load
5. **Resource Management**: Close database connections on plugin disable

## Thread Safety

- Database operations are thread-safe via HikariCP
- GUI operations must be called on main thread
- Economy operations are synchronized
- Message broadcasts are thread-safe

## Performance Considerations

- Database connection pooling minimizes overhead
- LRU caches reduce duplicate queries
- Async operations prevent server lag
- ProtocolLib packet manipulation is optimized
