# ServerBackpacks

**Version:** 1.0-SNAPSHOT
**Type:** Portable Storage System
**Main Class:** `net.serverplugins.backpacks.ServerBackpacks`

## Overview

ServerBackpacks provides a 6-tier portable storage system where backpacks are physical items in your inventory. Features include auto-refill, crafting recipes for upgrades, WorldGuard region integration, and persistent storage.

## Key Features

### 6-Tier System
1. **Small** - 9 slots (1 row)
2. **Medium** - 18 slots (2 rows)
3. **Large** - 27 slots (3 rows)
4. **Extra Large** - 36 slots (4 rows)
5. **Huge** - 45 slots (5 rows)
6. **Massive** - 54 slots (6 rows - full chest)

### Auto-Refill
Automatically refills consumed items from your backpack when you run out in your main inventory.

**Example:** Eating the last steak in your hotbar automatically pulls another from your backpack.

### Crafting System
Upgrade backpacks through crafting recipes:
- Small + materials → Medium
- Medium + materials → Large
- etc.

### WorldGuard Integration
Custom flag `backpack-access` to control backpack usage in regions:
```
/rg flag <region> backpack-access allow|deny
```

### Persistent Storage
- Per-player backpack data saved to disk
- Auto-save every 3 seconds
- Survives server restarts

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/backpack [type]` | `serverbackpacks.use` | Open backpack |
| `/bp [type]` | `serverbackpacks.use` | Alias for backpack |
| `/bag [type]` | `serverbackpacks.use` | Alias for backpack |
| `/pack [type]` | `serverbackpacks.use` | Alias for backpack |
| `/storage [type]` | `serverbackpacks.use` | Alias for backpack |
| `/givebackpack <player> <type>` | `serverbackpacks.give` | Give backpack item |
| `/upgradebackpack` | `serverbackpacks.upgrade` | Upgrade backpack tier |
| `/upgradebp` | `serverbackpacks.upgrade` | Alias |
| `/bpupgrade` | `serverbackpacks.upgrade` | Alias |

## Permissions

### Usage
- `serverbackpacks.use` - Open backpacks
- `serverbackpacks.use.<type>` - Use specific tier (small, medium, large, xl, huge, massive)

### Administration
- `serverbackpacks.give` - Give backpack items to players
- `serverbackpacks.upgrade` - Upgrade backpacks via command

### Auto-Refill
- `serverbackpacks.autorefill` - Enable auto-refill feature
- `serverbackpacks.autorefill.<type>` - Auto-refill from specific tier

## Configuration

### config.yml
```yaml
# Backpack Settings
backpacks:
  small:
    enabled: true
    slots: 9
    name: "&6Small Backpack"
    lore:
      - "&79 slots of portable storage"
      - "&7Right-click to open"
    material: LEATHER
    custom-model-data: 1001
    crafting-enabled: true

  medium:
    enabled: true
    slots: 18
    name: "&6Medium Backpack"
    lore:
      - "&718 slots of portable storage"
    material: LEATHER
    custom-model-data: 1002
    crafting-enabled: true

  large:
    enabled: true
    slots: 27
    name: "&6Large Backpack"
    lore:
      - "&727 slots of portable storage"
    material: LEATHER
    custom-model-data: 1003
    crafting-enabled: true

  xl:
    enabled: true
    slots: 36
    name: "&6Extra Large Backpack"
    lore:
      - "&736 slots of portable storage"
    material: LEATHER
    custom-model-data: 1004
    crafting-enabled: true

  huge:
    enabled: true
    slots: 45
    name: "&6Huge Backpack"
    lore:
      - "&745 slots of portable storage"
    material: LEATHER
    custom-model-data: 1005
    crafting-enabled: true

  massive:
    enabled: true
    slots: 54
    name: "&6Massive Backpack"
    lore:
      - "&754 slots of portable storage"
    material: LEATHER
    custom-model-data: 1006
    crafting-enabled: false  # Can't upgrade further

# Auto-Refill
auto-refill:
  enabled: true
  delay: 1  # Ticks between refills (prevent spam)
  notify: true
  message: "&7[&6Backpack&7] Refilled {item}"
  blacklist:
    - TOTEM_OF_UNDYING  # Don't auto-refill totems
    - ELYTRA

# Auto-Save
auto-save:
  enabled: true
  interval: 60  # Seconds (3 seconds = 60 ticks)

# WorldGuard Integration
worldguard:
  enabled: true
  default-flag-state: allow  # allow or deny

# Messages
messages:
  prefix: "&7[&6Backpack&7]&r "
  opened: "&aOpened {type} backpack"
  no-backpack: "&cYou don't have a {type} backpack!"
  upgraded: "&aBackpack upgraded to {type}!"
  cannot-upgrade: "&cThis backpack cannot be upgraded further!"
  region-denied: "&cBackpacks are disabled in this region!"
```

### recipes.yml (Optional)
```yaml
# Custom crafting recipes for backpack upgrades
recipes:
  medium:
    ingredients:
      - LEATHER:4
      - STRING:2
      - IRON_INGOT:1
    shape:
      - "LLL"
      - "LSL"
      - "LLL"
    # L = Leather, S = Small Backpack

  large:
    ingredients:
      - LEATHER:8
      - STRING:4
      - GOLD_INGOT:1
```

## Player Data Storage

Backpack contents stored in `plugins/ServerBackpacks/playerdata/<uuid>.yml`:

```yaml
backpacks:
  small:
    '0':
      ==: org.bukkit.inventory.ItemStack
      type: DIAMOND
      amount: 64
    '1':
      ==: org.bukkit.inventory.ItemStack
      type: EMERALD
      amount: 32
  medium:
    '0':
      ==: org.bukkit.inventory.ItemStack
      type: COAL
      amount: 64
```

## Implementation Details

### Package Structure
```
net.serverplugins.backpacks/
├── ServerBackpacks.java           # Main plugin class
├── BackpacksConfig.java           # Configuration wrapper
├── commands/
│   ├── BackpackCommand.java       # Open backpack
│   ├── GiveBackpackCommand.java   # Admin give command
│   └── UpgradeBackpackCommand.java # Upgrade command
├── managers/
│   ├── BackpackManager.java       # Backpack inventory management
│   └── RecipeManager.java         # Crafting recipe registration
├── listeners/
│   ├── BackpackListener.java      # Right-click to open
│   └── AutoRefillListener.java    # Auto-refill system
├── tasks/
│   └── AutoSaveTask.java          # Periodic saving
├── worldguard/
│   └── BackpackFlagHandler.java   # WorldGuard flag
└── models/
    └── BackpackType.java          # Backpack tier enum
```

### BackpackManager
```java
public class BackpackManager {
    private Map<UUID, Map<BackpackType, Inventory>> cache = new HashMap<>();

    public Inventory getBackpack(UUID uuid, BackpackType type) {
        return cache.computeIfAbsent(uuid, k -> new HashMap<>())
                   .computeIfAbsent(type, t -> createBackpack(uuid, t));
    }

    public void saveBackpack(UUID uuid, BackpackType type) {
        Inventory inv = cache.get(uuid).get(type);
        if (inv != null) {
            saveToFile(uuid, type, inv);
        }
    }

    public void saveAll() {
        cache.forEach((uuid, backpacks) ->
            backpacks.forEach((type, inv) ->
                saveToFile(uuid, type, inv)
            )
        );
    }
}
```

### Auto-Refill Logic
```java
@EventHandler
public void onItemConsume(PlayerItemConsumeEvent event) {
    Player player = event.getPlayer();
    ItemStack consumed = event.getItem();

    if (!player.hasPermission("serverbackpacks.autorefill")) return;

    // Check if this was the last of the item
    int count = 0;
    for (ItemStack item : player.getInventory().getContents()) {
        if (item != null && item.isSimilar(consumed)) {
            count += item.getAmount();
        }
    }

    if (count <= 1) {
        // Search backpacks for refill
        for (BackpackType type : BackpackType.values()) {
            Inventory backpack = manager.getBackpack(player.getUniqueId(), type);
            for (int i = 0; i < backpack.getSize(); i++) {
                ItemStack item = backpack.getItem(i);
                if (item != null && item.isSimilar(consumed)) {
                    // Refill!
                    ItemStack refill = item.clone();
                    refill.setAmount(1);
                    player.getInventory().addItem(refill);

                    item.setAmount(item.getAmount() - 1);
                    if (item.getAmount() <= 0) {
                        backpack.setItem(i, null);
                    }

                    player.sendMessage("§7[§6Backpack§7] Refilled " + item.getType().name());
                    return;
                }
            }
        }
    }
}
```

### Backpack Item Creation
```java
public ItemStack createBackpackItem(BackpackType type) {
    Material material = config.getMaterial(type);
    int customModelData = config.getCustomModelData(type);

    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();

    meta.setDisplayName(config.getName(type));
    meta.setLore(config.getLore(type));
    meta.setCustomModelData(customModelData);

    // Add NBT tag to identify as backpack
    meta.getPersistentDataContainer().set(
        new NamespacedKey(plugin, "backpack_type"),
        PersistentDataType.STRING,
        type.name()
    );

    item.setItemMeta(meta);
    return item;
}
```

## Crafting Recipes

### Recipe Registration
```java
public class RecipeManager {
    public void registerRecipes() {
        // Medium Backpack Recipe
        ShapedRecipe medium = new ShapedRecipe(
            new NamespacedKey(plugin, "medium_backpack"),
            createBackpackItem(BackpackType.MEDIUM)
        );
        medium.shape("LLL", "LSL", "LLL");
        medium.setIngredient('L', Material.LEATHER);
        medium.setIngredient('S', createBackpackItem(BackpackType.SMALL));

        Bukkit.addRecipe(medium);

        // Repeat for all tiers...
    }
}
```

## WorldGuard Integration

### Custom Flag
```java
public class BackpackFlagHandler {
    public static final StateFlag BACKPACK_ACCESS =
        new StateFlag("backpack-access", true);

    public boolean canUseBackpack(Player player, Location location) {
        if (worldGuard == null) return true;

        RegionContainer container = WorldGuard.getInstance()
            .getPlatform()
            .getRegionContainer();
        RegionQuery query = container.createQuery();

        return query.testState(
            BukkitAdapter.adapt(location),
            BukkitAdapter.adapt(player),
            BACKPACK_ACCESS
        );
    }
}
```

## Dependencies

### Hard Dependencies
- ServerAPI

### Soft Dependencies
- WorldGuard (region flag integration)

## Best Practices

1. **Resource Pack**: Use custom model data for unique backpack appearances
2. **Auto-Save**: Keep interval reasonable (3-5 seconds)
3. **Permissions**: Use tier-specific permissions for VIP ranks
4. **Refill Blacklist**: Add valuable/dangerous items to prevent abuse
5. **Region Control**: Use WorldGuard flag in PvP areas

## Performance Considerations

- In-memory caching prevents repeated file I/O
- Auto-save runs async to prevent lag
- Backpack inventories only created when needed
- File I/O batched during save operations

## Common Issues

### Backpack Items Disappear
Ensure auto-save is enabled and interval isn't too long.

### Refill Not Working
Check permission `serverbackpacks.autorefill` and blacklist configuration.

### Can't Upgrade
Verify crafting-enabled is true for target tier and recipe is registered.
