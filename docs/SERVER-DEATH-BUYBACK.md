# ServerDeathBuyback

**Version:** 1.0-SNAPSHOT
**Type:** Death Recovery System
**Main Class:** `net.serverplugins.deathbuyback.ServerDeathBuyback`

## Overview

ServerDeathBuyback allows players to recover their items from death by purchasing them back. Features include multiple buyback slots (1-3), dynamic pricing based on item value, combat protection to prevent abuse, and cross-server synchronization via Redis.

## Key Features

### Multi-Slot System
- **1 Slot**: Default for all players
- **2 Slots**: VIP rank (permission: `deathbuyback.slots.2`)
- **3 Slots**: MVP+ rank (permission: `deathbuyback.slots.3`)

### Dynamic Pricing
Item prices calculated based on:
- SellGUI sell values
- Enchantment values
- Item rarity
- Configurable multipliers

### Combat Protection
CombatLogX integration prevents buyback during combat to prevent abuse.

### Cross-Server Sync
Redis integration via server-bridge syncs death inventories across servers.

### Admin Tools
- View player death inventories
- Clear death slots
- Reload configuration

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/buyback` | `deathbuyback.use` | Open buyback menu |
| `/deathbuyback` | `deathbuyback.use` | Alias for buyback |
| `/deaths` | `deathbuyback.use` | Alias for buyback |
| `/db` | `deathbuyback.use` | Alias for buyback |
| `/recover` | `deathbuyback.use` | Alias for buyback |
| `/buybackadmin reload` | `deathbuyback.admin` | Reload config |
| `/buybackadmin clear <player>` | `deathbuyback.admin` | Clear player deaths |
| `/buybackadmin info <player>` | `deathbuyback.admin` | View player deaths |

## Permissions

### Player Permissions
- `deathbuyback.use` - Access buyback menu
- `deathbuyback.slots.1` - 1 death slot (default)
- `deathbuyback.slots.2` - 2 death slots (VIP)
- `deathbuyback.slots.3` - 3 death slots (MVP+)
- `deathbuyback.bypass.combat` - Bypass combat restriction

### Admin Permissions
- `deathbuyback.admin` - All admin commands
- `deathbuyback.admin.reload` - Reload configuration
- `deathbuyback.admin.clear` - Clear player deaths
- `deathbuyback.admin.info` - View player info

## Configuration

### config.yml
```yaml
# Death Slots
slots:
  default: 1
  vip: 2
  mvp: 3

# Pricing
pricing:
  enabled: true
  base-multiplier: 1.5  # 150% of sell value
  enchantment-multiplier: 2.0  # 200% for enchanted items
  min-price: 100.0
  max-price: 100000.0
  free-if-no-value: false  # Charge min price for items with no sell value

# Item Value Calculation
value-calculation:
  use-sellgui: true  # Use SellGUI prices if available
  use-essentials: true  # Fall back to Essentials worth.yml
  custom-values:  # Custom item values
    NETHERITE_SWORD: 5000.0
    ELYTRA: 10000.0
    TOTEM_OF_UNDYING: 8000.0

  enchantment-values:
    SHARPNESS: 500
    PROTECTION: 500
    MENDING: 2000
    UNBREAKING: 1000

# Death Storage
storage:
  max-age: 604800  # 7 days in seconds
  auto-cleanup: true
  cleanup-interval: 86400  # 24 hours

# Combat Protection
combat-protection:
  enabled: true
  message: "&cYou cannot buy back items while in combat!"
  use-combatlogx: true

# Cross-Server Sync
redis:
  enabled: true
  channel: "deathbuyback"
  sync-on-death: true
  sync-on-buyback: true

# GUI Settings
gui:
  title: "&6&lDeath Recovery"
  size: 54  # Max 3 deaths * 18 rows each
  death-slot-display:
    material: SKELETON_SKULL
    name: "&cDeath #{number}"
    lore:
      - "&7Died: {time_ago}"
      - "&7Location: {world} ({x}, {y}, {z})"
      - "&7Items: {item_count}"
      - "&7Cost: ${cost}"
      - ""
      - "&eClick to view items"
      - "&aShift-Click to buy back all"

  item-display:
    show-enchantments: true
    show-durability: true
    lore:
      - "&7Price: ${price}"
      - ""
      - "&eClick to buy back"

  buyback-all-button:
    material: EMERALD
    name: "&aBuy Back All"
    lore:
      - "&7Total Cost: ${total}"
      - ""
      - "&eClick to purchase"

  close-button:
    material: BARRIER
    name: "&cClose"

# Messages
messages:
  prefix: "&7[&6DeathBuyback&7]&r "
  death-recorded: "&7Your items have been saved! Use &e/buyback &7to recover them."
  buyback-success: "&aBought back {item} for ${price}!"
  buyback-all-success: "&aBought back all items for ${total}!"
  insufficient-funds: "&cInsufficient funds! Need ${amount}"
  no-deaths: "&cYou have no deaths to recover from!"
  slots-full: "&cYour oldest death has been removed to make room!"
  death-expired: "&cThis death has expired and cannot be recovered."
  combat-denied: "&cYou cannot buy back items while in combat!"
```

## Player Data Storage

Death inventories stored in `plugins/ServerDeathBuyback/playerdata/<uuid>.yml`:

```yaml
deaths:
  1:
    timestamp: 1704067200000
    world: "world"
    x: 100.5
    y: 64.0
    z: -200.3
    cause: "ENTITY_ATTACK"
    killer: "Zombie"
    items:
      - ==: org.bukkit.inventory.ItemStack
        type: DIAMOND_SWORD
        amount: 1
        meta:
          enchants:
            SHARPNESS: 5
      - ==: org.bukkit.inventory.ItemStack
        type: GOLDEN_APPLE
        amount: 16
    total-value: 5000.0

  2:
    timestamp: 1704070800000
    # ... second death
```

## Implementation Details

### Package Structure
```
net.serverplugins.deathbuyback/
├── ServerDeathBuyback.java        # Main plugin class
├── DeathBuybackConfig.java        # Configuration wrapper
├── commands/
│   ├── BuybackCommand.java        # Main buyback command
│   └── BuybackAdminCommand.java   # Admin commands
├── managers/
│   ├── DeathManager.java          # Death storage management
│   ├── PricingManager.java        # Item price calculation
│   └── SlotManager.java           # Slot permission management
├── listeners/
│   ├── DeathListener.java         # Player death handler
│   └── CombatListener.java        # Combat protection
├── gui/
│   ├── BuybackMainGui.java        # Main buyback menu
│   └── DeathInventoryGui.java     # Individual death view
├── redis/
│   └── RedisSync.java             # Cross-server sync
└── models/
    └── DeathRecord.java           # Death data model
```

### Death Recording
```java
@EventHandler(priority = EventPriority.MONITOR)
public void onDeath(PlayerDeathEvent event) {
    Player player = event.getPlayer();

    // Create death record
    DeathRecord death = new DeathRecord(
        System.currentTimeMillis(),
        player.getLocation(),
        player.getLastDamageCause().getCause(),
        player.getKiller() != null ? player.getKiller().getName() : null,
        new ArrayList<>(event.getDrops())
    );

    // Calculate total value
    double totalValue = 0;
    for (ItemStack item : death.getItems()) {
        totalValue += pricingManager.calculatePrice(item);
    }
    death.setTotalValue(totalValue);

    // Store death
    deathManager.addDeath(player.getUniqueId(), death);

    // Sync to Redis if enabled
    if (config.isRedisEnabled()) {
        redisSync.publishDeath(player.getUniqueId(), death);
    }

    // Notify player
    player.sendMessage(config.getMessage("death-recorded"));
}
```

### Price Calculation
```java
public class PricingManager {
    public double calculatePrice(ItemStack item) {
        double basePrice = getBasePrice(item);

        // Apply enchantment multiplier
        if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) {
            basePrice *= config.getEnchantmentMultiplier();

            // Add per-enchantment values
            for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
                double enchantValue = config.getEnchantmentValue(entry.getKey());
                basePrice += enchantValue * entry.getValue();
            }
        }

        // Apply base multiplier
        basePrice *= config.getBaseMultiplier();

        // Clamp to min/max
        return Math.max(config.getMinPrice(),
                   Math.min(config.getMaxPrice(), basePrice));
    }

    private double getBasePrice(ItemStack item) {
        // Try SellGUI first
        if (sellGuiHook != null) {
            double price = sellGuiHook.getPrice(item);
            if (price > 0) return price;
        }

        // Try Essentials
        if (essentialsHook != null) {
            double price = essentialsHook.getWorth(item.getType());
            if (price > 0) return price;
        }

        // Try custom values
        if (config.hasCustomValue(item.getType())) {
            return config.getCustomValue(item.getType());
        }

        // Default
        return config.isFreePriceIfNoValue() ? 0 : config.getMinPrice();
    }
}
```

### Slot Management
```java
public class SlotManager {
    public int getMaxSlots(Player player) {
        if (player.hasPermission("deathbuyback.slots.3")) return 3;
        if (player.hasPermission("deathbuyback.slots.2")) return 2;
        if (player.hasPermission("deathbuyback.slots.1")) return 1;
        return config.getDefaultSlots();
    }

    public void addDeath(UUID uuid, DeathRecord death) {
        List<DeathRecord> deaths = getDeaths(uuid);
        deaths.add(0, death);  // Add to front

        // Remove oldest if over limit
        int maxSlots = getMaxSlots(Bukkit.getPlayer(uuid));
        while (deaths.size() > maxSlots) {
            deaths.remove(deaths.size() - 1);
        }

        saveDeaths(uuid, deaths);
    }
}
```

### GUI Implementation
```java
public class BuybackMainGui {
    public void open(Player player) {
        List<DeathRecord> deaths = deathManager.getDeaths(player.getUniqueId());

        if (deaths.isEmpty()) {
            player.sendMessage(config.getMessage("no-deaths"));
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 54,
            ChatColor.translateAlternateColorCodes('&', config.getGuiTitle()));

        // Display each death
        int slot = 0;
        for (int i = 0; i < deaths.size(); i++) {
            DeathRecord death = deaths.get(i);

            // Death skull item
            ItemStack skull = new ItemStack(Material.SKELETON_SKULL);
            ItemMeta meta = skull.getItemMeta();
            meta.setDisplayName("§cDeath #" + (i + 1));
            meta.setLore(Arrays.asList(
                "§7Died: " + formatTimeAgo(death.getTimestamp()),
                "§7Location: " + formatLocation(death.getLocation()),
                "§7Items: " + death.getItems().size(),
                "§7Cost: $" + death.getTotalValue(),
                "",
                "§eClick to view items",
                "§aShift-Click to buy back all"
            ));
            skull.setItemMeta(meta);

            gui.setItem(slot++, skull);
        }

        player.openInventory(gui);
    }
}
```

### Combat Protection
```java
@EventHandler
public void onBuybackAttempt(InventoryClickEvent event) {
    if (!config.isCombatProtectionEnabled()) return;

    Player player = (Player) event.getWhoClicked();

    if (combatLogX != null && combatLogX.isInCombat(player)) {
        if (!player.hasPermission("deathbuyback.bypass.combat")) {
            player.sendMessage(config.getMessage("combat-denied"));
            event.setCancelled(true);
            player.closeInventory();
        }
    }
}
```

### Redis Synchronization
```java
public class RedisSync {
    public void publishDeath(UUID uuid, DeathRecord death) {
        JSONObject data = new JSONObject();
        data.put("uuid", uuid.toString());
        data.put("death", serializeDeath(death));

        redis.publish("deathbuyback:death", data.toString());
    }

    public void subscribeDeath() {
        redis.subscribe("deathbuyback:death", message -> {
            JSONObject data = new JSONObject(message);
            UUID uuid = UUID.fromString(data.getString("uuid"));
            DeathRecord death = deserializeDeath(data.getJSONObject("death"));

            deathManager.addDeath(uuid, death);
        });
    }
}
```

## Dependencies

### Hard Dependencies
- ServerAPI
- Vault

### Soft Dependencies
- LuckPerms (permissions)
- PlaceholderAPI (placeholders)
- SellGUI (item pricing)
- Essentials (fallback pricing)
- CombatLogX (combat protection)
- server-bridge (Redis sync)

## Integration Examples

### SellGUI Integration
```java
if (sellGuiPlugin != null) {
    double price = SellGUI.getAPI().getPrice(item);
}
```

### CombatLogX Integration
```java
if (combatLogXPlugin != null) {
    ICombatLogX api = (ICombatLogX) combatLogXPlugin;
    boolean inCombat = api.getCombatManager().isInCombat(player);
}
```

## Best Practices

1. **Pricing**: Balance prices to make buyback valuable but not overpowered
2. **Slots**: Use slot limits as VIP perks
3. **Combat**: Keep combat protection enabled to prevent abuse
4. **Cleanup**: Regular cleanup of old deaths saves storage
5. **Redis**: Enable Redis sync for multi-server setups

## Performance Considerations

- Death records stored per-player, not globally
- Auto-cleanup removes old deaths automatically
- Redis sync is async to prevent lag
- Price calculations cached where possible

## Common Issues

### Items Not Saved on Death
Check that death event priority is MONITOR and keep-inventory is false.

### Prices Too High/Low
Adjust multipliers in config.yml and verify SellGUI/Essentials integration.

### Combat Protection Not Working
Ensure CombatLogX is installed and enabled in config.
