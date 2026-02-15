# ServerBackpacks

A flexible backpack system for ServerPlugins with 6-tier progression matching Dream's structure.

## Features

- **6-Tier Progression System**: From basic 9-slot backpacks (Tier I) to massive 54-slot backpacks (Tier VI)
- **Persistent Storage**: Backpack contents are saved to the item using PDC (PersistentDataContainer)
- **Auto-Save System**: Contents automatically saved periodically and on server shutdown
- **WorldGuard Integration**: Respects region protections for backpack usage
- **Blacklist System**: Prevents storing specific items (shulker boxes by default)
- **Nest Prevention**: Prevents backpacks from being stored inside other backpacks
- **Configurable Permissions**: Each tier can have its own permission node
- **Custom Model Data**: Support for resource pack custom models

## Backpack Tiers

| Tier | Size | Display Name | Custom Model Data | Permission |
|------|------|--------------|-------------------|------------|
| I    | 9    | Gray Backpack Tier I | 100 | `serverbackpacks.tier1` |
| II   | 18   | Green Backpack Tier II | 101 | `serverbackpacks.tier2` |
| III  | 27   | Blue Backpack Tier III | 102 | `serverbackpacks.tier3` |
| IV   | 36   | Yellow Backpack Tier IV | 105 | `serverbackpacks.tier4` |
| V    | 45   | Gold Backpack Tier V | 104 | `serverbackpacks.tier5` |
| VI   | 54   | Light Purple Backpack Tier VI | 103 | `serverbackpacks.tier6` |

## Commands

- `/givebackpack <player> <tier>` - Give a backpack to a player
  - Example: `/givebackpack PlayerName tier1`
  - Permission: `serverbackpacks.admin`

## Configuration

The plugin uses a flexible configuration system that allows custom backpack types. The default configuration includes the 6-tier progression system:

```yaml
backpacks:
  tier1:
    display-name: "<gray>Backpack <white>Tier I"
    material: LEATHER_HORSE_ARMOR
    size: 9
    permission: "serverbackpacks.tier1"
    custom-model-data: 100
  # ... (tier2 through tier6)
```

### Settings

- `prevent-nesting`: Prevents backpacks from being stored inside other backpacks
- `open-sound`: Sound played when opening a backpack
- `close-sound`: Sound played when closing a backpack

### Blacklist

Items that cannot be stored in backpacks. All shulker box variants are automatically blacklisted.

## Usage

1. **Getting a Backpack**: Use `/givebackpack <player> <tier>` to give a backpack to a player
2. **Opening a Backpack**: Right-click while holding the backpack
3. **Storing Items**: Simply click items into the backpack inventory
4. **Auto-Save**: Contents are automatically saved when you close the backpack or periodically

## API

The BackpackManager provides methods for:
- Creating backpacks: `createBackpack(BackpackType)`
- Checking if an item is a backpack: `isBackpack(ItemStack)`
- Getting backpack tier: `getBackpackTier(ItemStack)`
- Upgrading backpacks: `upgradeBackpack(Player, ItemStack)` (for future use)

### BackpackTier Enum

The `BackpackTier` enum provides helper methods for:
- Getting tier information (ID, Roman numeral, size, CMD)
- Checking if a tier can be upgraded
- Looking up tiers by ID, number, or custom model data

## Permissions

- `serverbackpacks.admin` - Allows using admin commands
- `serverbackpacks.tier1` - Required to use Tier I backpacks
- `serverbackpacks.tier2` - Required to use Tier II backpacks
- `serverbackpacks.tier3` - Required to use Tier III backpacks
- `serverbackpacks.tier4` - Required to use Tier IV backpacks
- `serverbackpacks.tier5` - Required to use Tier V backpacks
- `serverbackpacks.tier6` - Required to use Tier VI backpacks

## Dependencies

- **ServerAPI**: Provides utilities for text parsing, item building, and more
- **WorldGuard** (optional): For region protection integration
- **bStats**: Anonymous metrics collection

## Building

```bash
mvn clean package
```

The compiled JAR will be in `target/server-backpacks-1.0.0.jar`.

## Future Enhancements

- Upgrade command for converting backpacks to higher tiers
- Economy integration for upgrade costs
- Crafting recipes for each tier
- GUI for backpack management
- Multiple backpack support per player
