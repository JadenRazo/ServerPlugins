# ServerCommands

**Version:** 1.0-SNAPSHOT
**Type:** Essential Command Suite
**Main Class:** `net.serverplugins.commands.ServerCommands`

## Overview

ServerCommands provides a comprehensive suite of essential server commands including home/warp management, teleportation, moderation tools, admin utilities, and dynamic custom commands. It features GUI interfaces, database-backed punishment history, and extensive configuration options.

## Key Features

### Home System
- Set multiple homes with custom names and icons
- Home descriptions and metadata
- GUI interface with pagination
- Permission-based home limits
- Delete confirmation system

### Warp System
- Server-wide warp points
- Admin-only warp management
- GUI warp browser
- Permission-based warp access

### Teleportation
- TPA (teleport ask) system with accept/deny
- Back to last location
- Spawn teleport
- Request expiration system

### Moderation System
- Kick, ban, tempban with reasons
- Mute system with duration
- Warning system with threshold
- Comprehensive punishment history with GUI
- MariaDB-backed punishment database

### Admin Tools
- Gamemode switching (survival, creative, adventure, spectator)
- Fly mode toggle
- Heal, feed, god mode
- Speed adjustment (walk/fly)
- Inventory management (clear, repair)

### Inventory Commands
- Invsee - View/edit player inventories
- Enderchest - View/edit ender chests
- Clear - Clear inventory/specific slots
- Repair - Repair items in hand or all

### World Control
- Time adjustment (day, night, custom)
- Weather control (clear, rain, thunder)

### Player Information
- Playtime tracking with leaderboards
- Last seen information
- Player history lookup

### Economy
- Balance checking
- Money giving/taking
- Economy statistics

### Dynamic Commands
- Config-driven custom commands
- GUI support for custom commands
- Execute arbitrary command sequences
- Permission-based access control

## Commands

### Home Commands
| Command | Permission | Description |
|---------|-----------|-------------|
| `/home [name]` | `servercommands.home` | Teleport to home |
| `/sethome <name>` | `servercommands.sethome` | Set a home |
| `/delhome <name>` | `servercommands.delhome` | Delete a home |
| `/homes` | `servercommands.homes` | View homes GUI |
| `/renamehome <old> <new>` | `servercommands.renamehome` | Rename home |
| `/sethomedesc <name> <desc>` | `servercommands.sethomedesc` | Set home description |

### Warp Commands
| Command | Permission | Description |
|---------|-----------|-------------|
| `/warp [name]` | `servercommands.warp` | Teleport to warp |
| `/setwarp <name>` | `servercommands.setwarp` | Create warp |
| `/delwarp <name>` | `servercommands.delwarp` | Delete warp |
| `/warps` | `servercommands.warps` | View warps GUI |

### Teleport Commands
| Command | Permission | Description |
|---------|-----------|-------------|
| `/spawn` | `servercommands.spawn` | Teleport to spawn |
| `/setspawn` | `servercommands.setspawn` | Set spawn location |
| `/back` | `servercommands.back` | Return to last location |
| `/tpa <player>` | `servercommands.tpa` | Request teleport to player |
| `/tpahere <player>` | `servercommands.tpahere` | Request player teleport to you |
| `/tpaccept` | `servercommands.tpaccept` | Accept TPA request |
| `/tpdeny` | `servercommands.tpdeny` | Deny TPA request |

### Moderation Commands
| Command | Permission | Description |
|---------|-----------|-------------|
| `/kick <player> [reason]` | `servercommands.kick` | Kick player |
| `/ban <player> [reason]` | `servercommands.ban` | Ban player |
| `/tempban <player> <duration> [reason]` | `servercommands.tempban` | Temporary ban |
| `/unban <player>` | `servercommands.unban` | Unban player |
| `/mute <player> <duration> [reason]` | `servercommands.mute` | Mute player |
| `/unmute <player>` | `servercommands.unmute` | Unmute player |
| `/warn <player> [reason]` | `servercommands.warn` | Warn player |
| `/history <player>` | `servercommands.history` | View punishment history |
| `/staffhistory [player]` | `servercommands.staffhistory` | View staff actions |

### Admin Commands
| Command | Permission | Description |
|---------|-----------|-------------|
| `/gamemode <mode> [player]` | `servercommands.gamemode` | Change gamemode |
| `/gm <0\|1\|2\|3> [player]` | `servercommands.gamemode` | Gamemode shortcut |
| `/fly [player]` | `servercommands.fly` | Toggle flight |
| `/heal [player]` | `servercommands.heal` | Heal player |
| `/feed [player]` | `servercommands.feed` | Feed player |
| `/god [player]` | `servercommands.god` | Toggle god mode |
| `/speed <1-10> [player]` | `servercommands.speed` | Set movement speed |

### Inventory Commands
| Command | Permission | Description |
|---------|-----------|-------------|
| `/invsee <player>` | `servercommands.invsee` | View player inventory |
| `/enderchest <player>` | `servercommands.enderchest` | View ender chest |
| `/clear [player]` | `servercommands.clear` | Clear inventory |
| `/repair [all]` | `servercommands.repair` | Repair items |

### World Commands
| Command | Permission | Description |
|---------|-----------|-------------|
| `/time <day\|night\|<ticks>>` | `servercommands.time` | Set time |
| `/weather <clear\|rain\|thunder>` | `servercommands.weather` | Set weather |

### Info Commands
| Command | Permission | Description |
|---------|-----------|-------------|
| `/playtime [player]` | `servercommands.playtime` | View playtime |
| `/seen <player>` | `servercommands.seen` | Check last seen |

### Utility Commands
| Command | Permission | Description |
|---------|-----------|-------------|
| `/links` | `servercommands.links` | View server links |
| `/rules` | `servercommands.rules` | View server rules |
| `/vote` | `servercommands.vote` | View vote links |
| `/rtpmenu` | `servercommands.rtpmenu` | Random teleport menu |
| `/adminmenu` | `servercommands.adminmenu` | Admin control panel |

## Configuration

### config.yml
```yaml
# Home System
homes:
  max-homes: 5  # Default for players without permissions
  teleport-delay: 0  # Seconds
  allow-rename: true
  allow-descriptions: true

# Warp System
warps:
  teleport-delay: 0
  require-permission-per-warp: false  # servercommands.warp.<name>

# TPA System
tpa:
  request-timeout: 60  # Seconds
  teleport-delay: 3
  cooldown: 30  # Seconds between requests

# Spawn
spawn:
  teleport-delay: 0
  respawn-at-spawn: true

# Back Command
back:
  enabled: true
  save-on-death: true
  save-on-teleport: true

# Moderation
moderation:
  ban:
    broadcast: true
    message: "&c{player} has been banned by {staff} for: {reason}"
  kick:
    broadcast: false
  mute:
    prevent-commands: true
    allowed-commands:
      - "/msg"
      - "/helpop"
  warnings:
    threshold: 3  # Auto-action at 3 warnings
    threshold-action: "tempban {player} 1d Too many warnings"

# Admin Tools
admin:
  gamemode:
    require-permission-per-mode: false
  speed:
    max: 10
    default: 1

# Dynamic Commands
dynamic-commands:
  links:
    enabled: true
    permission: "servercommands.links"
    type: "gui"  # or "message" or "execute"
    gui:
      title: "&6&lServer Links"
      size: 27
      items:
        0:
          material: GLOBE
          name: "&aWebsite"
          lore:
            - "&7Click to open website"
          command: "openurl https://example.com"

# Messages
messages:
  prefix: "&7[&6ServerPlugins&7]&r "
  home-set: "&aHome '{name}' set!"
  home-deleted: "&cHome '{name}' deleted!"
  warp-not-found: "&cWarp not found!"
  tpa-sent: "&aTeleport request sent to {player}"
  tpa-expired: "&cTeleport request expired"
  player-banned: "&c{player} has been banned"
  no-permission: "&cYou don't have permission!"
```

### permissions.yml
```yaml
# Home limits per rank
permissions:
  default:
    servercommands.homes.max: 3
  vip:
    servercommands.homes.max: 5
  mvp:
    servercommands.homes.max: 10
  admin:
    servercommands.homes.max: 50
```

## Database Schema

### server_homes
```sql
CREATE TABLE IF NOT EXISTS server_homes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL,
    name VARCHAR(32) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    icon VARCHAR(64) DEFAULT 'RED_BED',
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY (uuid, name),
    INDEX (uuid)
);
```

### server_warps
```sql
CREATE TABLE IF NOT EXISTS server_warps (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE,
    world VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### server_mutes
```sql
CREATE TABLE IF NOT EXISTS server_mutes (
    uuid VARCHAR(36) PRIMARY KEY,
    reason TEXT,
    muted_by VARCHAR(36) NOT NULL,
    muted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    permanent BOOLEAN DEFAULT FALSE
);
```

### server_playtime
```sql
CREATE TABLE IF NOT EXISTS server_playtime (
    uuid VARCHAR(36) PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    total_seconds BIGINT DEFAULT 0,
    last_join TIMESTAMP NULL,
    INDEX (total_seconds DESC)  -- For leaderboards
);
```

### server_player_data
```sql
CREATE TABLE IF NOT EXISTS server_player_data (
    uuid VARCHAR(36) PRIMARY KEY,
    warnings INT DEFAULT 0,
    god_mode BOOLEAN DEFAULT FALSE,
    fly_mode BOOLEAN DEFAULT FALSE,
    last_location TEXT,  -- Serialized location
    guide_seen BOOLEAN DEFAULT FALSE
);
```

### Punishment History Tables
```sql
CREATE TABLE IF NOT EXISTS punishment_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL,
    staff_uuid VARCHAR(36) NOT NULL,
    type ENUM('WARN', 'KICK', 'BAN', 'TEMPBAN', 'MUTE', 'UNMUTE', 'UNBAN') NOT NULL,
    reason TEXT,
    duration VARCHAR(32),  -- e.g., "1d", "30m"
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX (uuid),
    INDEX (staff_uuid),
    INDEX (timestamp DESC)
);
```

## GUI Implementations

### HomesGui
- Displays all player homes with icons
- Pagination for 9+ homes
- Click to teleport
- Right-click to manage (rename, delete, set description)
- Home limit display

### InvseeGui
- Live view of player inventory
- Editable for admins
- Armor slots visible
- Offhand slot
- Real-time updates

### EnderchestGui
- Live view of player ender chest
- Editable for admins
- Synced with player's actual ender chest

### PunishmentHistoryGui
- Chronological punishment list
- Filter by type (ban, mute, warn, kick)
- Pagination support
- Staff member attribution
- Hover for full details

### AdminMenuCommand
Comprehensive admin panel with buttons for:
- Player management (kick, ban, freeze)
- Server control (restart, reload)
- World management (time, weather)
- Teleportation tools
- Inventory inspection

## Managers

### PlayerDataManager
- Manages player persistent data (warnings, modes, locations)
- Async file I/O for player configs
- In-memory caching with auto-save

### WarpManager
- Server warp creation/deletion
- Permission checks per warp
- Database persistence

### TpaManager
- Teleport request handling
- Request expiration (60 seconds)
- Cooldown management
- Concurrent request support

### MuteManager
- Active mute tracking
- Duration-based mutes
- Permanent mute support
- Chat event integration

### BanManager
- Ban/unban operations
- Temporary ban support with duration
- Kick on join for banned players

### PunishmentHistoryManager
- Complete audit trail of all punishments
- Staff action tracking
- Query by player or staff member
- Export capabilities

### DynamicCommandManager
- Load custom commands from config
- GUI-based or message-based
- Execute command sequences
- Permission-based access

## Implementation Details

### Package Structure
```
net.serverplugins.commands/
├── ServerCommands.java          # Main plugin class
├── CommandsConfig.java          # Configuration wrapper
├── commands/
│   ├── home/
│   │   ├── HomeCommand.java
│   │   ├── SetHomeCommand.java
│   │   ├── DelHomeCommand.java
│   │   ├── HomesCommand.java
│   │   └── RenameHomeCommand.java
│   ├── warp/
│   │   ├── WarpCommand.java
│   │   ├── SetWarpCommand.java
│   │   └── DelWarpCommand.java
│   ├── teleport/
│   │   ├── SpawnCommand.java
│   │   ├── BackCommand.java
│   │   ├── TpaCommand.java
│   │   └── TpAcceptCommand.java
│   ├── moderation/
│   │   ├── KickCommand.java
│   │   ├── BanCommand.java
│   │   ├── MuteCommand.java
│   │   └── WarnCommand.java
│   └── admin/
│       ├── GamemodeCommand.java
│       ├── FlyCommand.java
│       ├── HealCommand.java
│       └── AdminMenuCommand.java
├── managers/
│   ├── PlayerDataManager.java
│   ├── WarpManager.java
│   ├── TpaManager.java
│   ├── MuteManager.java
│   ├── BanManager.java
│   ├── PunishmentHistoryManager.java
│   └── DynamicCommandManager.java
├── gui/
│   ├── HomesGui.java
│   ├── HomeDeleteConfirmGui.java
│   ├── InvseeGui.java
│   ├── EnderchestGui.java
│   └── PunishmentHistoryGui.java
└── listeners/
    ├── ChatListener.java         # Mute enforcement
    ├── JoinListener.java         # Ban checks
    └── QuitListener.java         # Playtime tracking
```

## Dependencies

### Hard Dependencies
- ServerAPI
- Vault

### Soft Dependencies
- ServerAdmin (staff history integration)
- server-bridge (cross-server sync)
- Multiverse-Core (multi-world support)

## Integration Examples

### Setting Spawn from Another Plugin
```java
ServerCommands commands = (ServerCommands) Bukkit.getPluginManager().getPlugin("ServerCommands");
commands.setSpawnLocation(location);
```

### Checking if Player is Muted
```java
MuteManager muteManager = commands.getMuteManager();
if (muteManager.isMuted(player.getUniqueId())) {
    player.sendMessage("You are muted!");
    return;
}
```

### Adding Punishment History Entry
```java
PunishmentHistoryManager history = commands.getPunishmentHistoryManager();
history.addRecord(
    targetUUID,
    staffUUID,
    PunishmentType.WARN,
    "Inappropriate language",
    null  // No duration for warnings
);
```

## Best Practices

1. **Home Limits**: Set appropriate home limits per rank using permissions
2. **TPA Delays**: Use teleport delays to prevent combat abuse
3. **Punishment Reasons**: Always provide clear reasons for moderation actions
4. **Backup Data**: Regularly backup punishment history database
5. **Dynamic Commands**: Use dynamic commands for frequently updated content (links, rules)

## Performance Considerations

- Database queries are async to prevent lag
- Player data cached in memory
- TPA requests expire automatically
- GUI pagination prevents large inventory loads
- Punishment history indexed by UUID and timestamp
