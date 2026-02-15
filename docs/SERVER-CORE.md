# ServerCore

**Version:** 1.0-SNAPSHOT
**Type:** Core Utility Plugin
**Main Class:** `net.serverplugins.core.ServerCore`

## Overview

ServerCore provides essential quality-of-life features and utilities for the ServerPlugins server. It includes toggleable features like auto-totem, double doors, silk spawners, emojis, custom WorldGuard flags, and more. Features can be individually toggled per-player or globally.

## Key Features

### Auto-Totem
Automatically moves totems of undying from your inventory to your offhand when needed.

**Config:**
```yaml
auto-totem:
  enabled: true
  default-enabled: true  # Default state for new players
```

### Double Door
Automatically opens connected double doors together when you open one door.

**Config:**
```yaml
double-door:
  enabled: true
  default-enabled: true
```

### Drop to Inventory
Items dropped in the world go directly to your inventory instead of spawning as entities.

**Config:**
```yaml
drop-to-inventory:
  enabled: true
  default-enabled: false
```

### Editable Signs
Right-click signs to edit them without breaking.

**Config:**
```yaml
editable-signs:
  enabled: true
  permission: servercore.editsign
```

### Hat System
Wear any item on your head as a hat.

**Commands:**
- `/hat` - Wear held item as hat

**Config:**
```yaml
hat:
  enabled: true
  blacklist:
    - AIR
    - BARRIER
```

### Anvil Colors
Use color codes (&a, &b, etc.) when renaming items in anvils.

**Config:**
```yaml
anvil-colors:
  enabled: true
  allow-formatting: true  # Allow &l, &o, etc.
```

### Emoji System
Use custom emojis in chat with permission-based access.

**Examples:**
- `:smile:` → 😊
- `:heart:` → ❤️
- `:fire:` → 🔥

**Config:**
```yaml
emojis:
  enabled: true
  emojis:
    smile: "😊"
    heart: "❤️"
    fire: "🔥"
  permissions:
    smile: servercore.emoji.smile
    heart: servercore.emoji.heart
```

### Silk Spawners
Mine spawners with silk touch and place them elsewhere.

**Config:**
```yaml
silk-spawners:
  enabled: true
  require-permission: false
  drop-chance: 100.0  # Percentage
  silk-touch-required: true
```

### Spawner Enhancement
Enhanced spawner mechanics with custom spawn rates and conditions.

**Config:**
```yaml
spawner-enhancement:
  enabled: true
  max-nearby-entities: 6
  spawn-delay: 200
  spawn-count: 4
```

### WorldGuard Custom Flags
Custom flags for advanced region protection.

**Flags:**
- `random-tp-flag` - Allow/deny random teleports in region
- `disable-elytra` - Disable elytra flight in region

**Config:**
```yaml
worldguard-flags:
  enabled: true
  flags:
    - random-tp-flag
    - disable-elytra
```

### Custom Plugin List
Customizes `/plugins` command output to show only approved plugins or hide them entirely.

**Config:**
```yaml
plugin-list:
  enabled: true
  mode: whitelist  # whitelist, blacklist, or hide
  whitelist:
    - ServerCore
    - ServerAPI
  blacklist: []
  message: "&cYou cannot view plugins!"
```

### Resource Pack
Automatically applies server resource pack on join.

**Config:**
```yaml
resource-pack:
  enabled: true
  url: "https://example.com/pack.zip"
  hash: "sha1-hash-here"
  prompt: "Would you like to download the server resource pack?"
  force: false  # Kick if declined
```

### Platform-Specific Commands
Execute different commands for Java vs Bedrock players.

**Command:**
```
/javabedrock <java_command> \\ <bedrock_command>
```

**Example:**
```
/javabedrock give @p diamond 1 \\ give @p emerald 1
```

### Custom Inventory Titles
Custom titles for ender chests and bookshelves using ProtocolLib.

**Config:**
```yaml
custom-inventory:
  enabled: true
  enderchest-title: "&5&lEnder Chest"
  bookshelf-title: "&6&lBookshelf"
```

### Auto Complete
Enhanced tab completion for commands.

**Config:**
```yaml
auto-complete:
  enabled: true
  suggest-player-names: true
```

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/servercore toggle <feature>` | `servercore.toggle` | Toggle features on/off |
| `/wc toggle <feature>` | `servercore.toggle` | Alias for servercore |
| `/hat` | `servercore.hat` | Wear item as hat |
| `/dgive <player> <item> [nbt]` | `servercore.dgive` | Advanced item giving |
| `/javabedrock <java> \\ <bedrock>` | `servercore.javabedrock` | Platform-specific commands |
| `/battlepass reset <player>` | `servercore.battlepass.reset` | Reset battlepass progress |
| `/restart [seconds]` | `servercore.restart` | Server restart countdown |
| `/givespawner <player> <type> [amt]` | `servercore.givespawner` | Give spawner items |

## Permissions

### Feature Toggles
- `servercore.toggle` - Access to toggle command
- `servercore.toggle.<feature>` - Toggle specific features

### Features
- `servercore.autototem` - Use auto-totem
- `servercore.hat` - Use /hat command
- `servercore.editsign` - Edit signs by right-clicking
- `servercore.emoji.<emoji>` - Use specific emoji
- `servercore.silkspawner` - Mine spawners with silk touch

### Admin Commands
- `servercore.dgive` - Advanced item giving
- `servercore.javabedrock` - Platform-specific commands
- `servercore.restart` - Server restart command
- `servercore.givespawner` - Give spawners

## Configuration

### Main Config (config.yml)
```yaml
# Feature toggles
auto-totem:
  enabled: true
  default-enabled: true

double-door:
  enabled: true
  default-enabled: true

drop-to-inventory:
  enabled: true
  default-enabled: false

editable-signs:
  enabled: true
  permission: servercore.editsign

hat:
  enabled: true
  blacklist:
    - AIR
    - BARRIER

anvil-colors:
  enabled: true
  allow-formatting: true

emojis:
  enabled: true
  emojis:
    smile: "😊"
    heart: "❤️"
    fire: "🔥"
    star: "⭐"
    check: "✅"
  permissions:
    smile: servercore.emoji.smile
    heart: servercore.emoji.heart

silk-spawners:
  enabled: true
  require-permission: false
  drop-chance: 100.0
  silk-touch-required: true

spawner-enhancement:
  enabled: true
  max-nearby-entities: 6
  spawn-delay: 200
  spawn-count: 4

worldguard-flags:
  enabled: true
  flags:
    - random-tp-flag
    - disable-elytra

plugin-list:
  enabled: true
  mode: hide
  message: "&cYou cannot view plugins!"

resource-pack:
  enabled: true
  url: "https://cdn.example.com/resourcepack.zip"
  hash: ""
  prompt: "Download ServerPlugins resource pack?"
  force: false

custom-inventory:
  enabled: true
  enderchest-title: "&5&lEnder Chest"
  bookshelf-title: "&6&lBookshelf"

auto-complete:
  enabled: true
  suggest-player-names: true
```

### Messages (messages.yml)
```yaml
auto-totem-enabled: "&aAuto-Totem enabled!"
auto-totem-disabled: "&cAuto-Totem disabled!"
hat-success: "&aYou are now wearing a hat!"
hat-empty-hand: "&cYou must hold an item to wear as a hat!"
feature-toggled: "&aFeature {feature} is now {state}!"
```

## Player Data Management

Player settings are stored in `plugins/ServerCore/playerdata/<uuid>.yml`:

```yaml
auto-totem: true
double-door: true
drop-to-inventory: false
```

**Manager:** `PlayerDataManager`
- Async file I/O
- Auto-save on quit
- In-memory caching

## Event Listeners

### CoreListener
Handles all core feature events:
- `PlayerInteractEvent` - Hat, editable signs, double doors
- `PlayerDropItemEvent` - Drop to inventory
- `EntityDamageEvent` - Auto-totem
- `BlockBreakEvent` - Silk spawners
- `AsyncChatEvent` - Emoji replacement
- `InventoryClickEvent` - Anvil colors

### JoinListener
Handles player join events:
- Resource pack application
- Player data loading
- Welcome messages

## PlaceholderAPI Expansion

**Class:** `CorePlaceholderExpansion`

**Placeholders:**
- `%servercore_autototem%` - Auto-totem status (enabled/disabled)
- `%servercore_doubledoor%` - Double door status
- `%servercore_droptoinventory%` - Drop to inventory status

## WorldGuard Integration

### Custom Flags

**RandomTpFlag:**
```java
public class RandomTpFlag extends StateFlag {
    public RandomTpFlag() {
        super("random-tp-flag", true);
    }
}
```

**Usage:**
```
/rg flag <region> random-tp-flag allow|deny
```

**DisableElytraFlag:**
```java
public class DisableElytraFlag extends StateFlag {
    public DisableElytraFlag() {
        super("disable-elytra", false);
    }
}
```

**Usage:**
```
/rg flag <region> disable-elytra allow|deny
```

## Implementation Details

### Package Structure
```
net.serverplugins.core/
├── ServerCore.java              # Main plugin class
├── CoreConfig.java              # Configuration wrapper
├── commands/
│   ├── ServerCoreCommand.java   # Toggle command
│   ├── HatCommand.java          # Hat command
│   ├── DGiveCommand.java        # Advanced give command
│   ├── JavaBedrockCommand.java  # Platform-specific commands
│   └── RestartCommand.java      # Server restart
├── listeners/
│   ├── CoreListener.java        # Main feature listener
│   └── JoinListener.java        # Join event handler
├── managers/
│   └── PlayerDataManager.java   # Player settings management
└── placeholders/
    └── CorePlaceholderExpansion.java
```

### Auto-Totem Logic
```java
@EventHandler
public void onDamage(EntityDamageEvent event) {
    if (!(event.getEntity() instanceof Player player)) return;

    if (!playerData.hasAutoTotemEnabled(player)) return;

    if (player.getHealth() - event.getFinalDamage() <= 0) {
        // Find totem in inventory
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
                player.getInventory().setItemInOffHand(item);
                player.getInventory().setItem(i, null);
                break;
            }
        }
    }
}
```

## Dependencies

### Hard Dependencies
- ServerAPI
- Vault

### Soft Dependencies
- WorldGuard (custom flags)
- PlaceholderAPI (placeholders)
- ProtocolLib (custom inventory titles)

## Performance Notes

- Player data loaded async on join
- Feature checks cached in memory
- Minimal event processing overhead
- WorldGuard flag checks only when needed

## Best Practices

1. Use `/wc toggle` to enable/disable features per player
2. Configure defaults in config.yml for server-wide settings
3. Use permissions to control access to specific emojis
4. Test resource pack thoroughly before forcing
5. Monitor spawner enhancement settings to prevent lag
