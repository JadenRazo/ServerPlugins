# ServerPlugins Resource Pack Integration Guide

Complete guide for using custom Unicode characters from the ServerPlugins resource pack in your plugins.

## Table of Contents

- [Overview](#overview)
- [Getting Started](#getting-started)
- [NPC Portraits](#npc-portraits)
- [Menu Backgrounds](#menu-backgrounds)
- [Icons and UI Elements](#icons-and-ui-elements)
- [Emojis](#emojis)
- [Ranks and Chat Tags](#ranks-and-chat-tags)
- [Usage Examples](#usage-examples)
- [Best Practices](#best-practices)

## Overview

The ServerPlugins resource pack maps custom Unicode characters to images, allowing plugins to display:

- **NPC Portraits** (90x90px) - Large character portraits for dialogs
- **Menu Backgrounds** (256px) - Full-screen GUI backgrounds
- **Icons** (7-13px) - Small UI elements and decorations
- **Emojis** (8px) - Chat emojis for player communication
- **Ranks** (9px) - Player rank badges
- **Chat Tags** (9px) - Colored chat prefix tags

All mappings are available in `net.serverplugins.api.ui.ResourcePackIcons`.

## Getting Started

### Dependency Setup

Add server-api to your plugin's `pom.xml`:

```xml
<dependency>
    <groupId>net.serverplugins</groupId>
    <artifactId>server-api</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

### Basic Usage

```java
import net.serverplugins.api.ui.ResourcePackIcons;

// Get an NPC icon
String sylviaIcon = ResourcePackIcons.NPCs.SYLVIA;

// Get an emoji
String fireEmoji = ResourcePackIcons.Emojis.FIRE;

// Get UI spacing
String space = ResourcePackIcons.UI.SPACE;
```

## NPC Portraits

### Available NPCs (90px height, ascent 25)

| NPC | Unicode | Character | Constant |
|-----|---------|-----------|----------|
| Sylvia | U+D001 | 퀁 | `ResourcePackIcons.NPCs.SYLVIA` |
| August | U+D002 | 퀂 | `ResourcePackIcons.NPCs.AUGUST` |
| Cassian | U+D003 | 퀃 | `ResourcePackIcons.NPCs.CASSIAN` |
| Gandalf | U+D004 | 퀄 | `ResourcePackIcons.NPCs.GANDALF` |
| Marcus | U+D005 | 퀅 | `ResourcePackIcons.NPCs.MARCUS` |
| Mark | U+D006 | 퀆 | `ResourcePackIcons.NPCs.MARK` |
| Noah | U+D007 | 퀇 | `ResourcePackIcons.NPCs.NOAH` |
| Petra | U+D008 | 퀈 | `ResourcePackIcons.NPCs.PETRA` |
| Thyra | U+D009 | 퀉 | `ResourcePackIcons.NPCs.THYRA` |

### NPC Dialog Format

NPC portraits are designed to appear on the left side of dialog text. Use the custom space character (⻔) for proper alignment:

```java
import static net.serverplugins.api.ui.ResourcePackIcons.*;

String[] dialogLines = {
    "&f",
    NPCs.SYLVIA + UI.SPACE + UI.SPACE + "&d&l━━━━━━━━━━━━━━━━━━━━━━",
    NPCs.SYLVIA + UI.SPACE + UI.SPACE,
    NPCs.SYLVIA + UI.SPACE + UI.SPACE + "&d&lNPC SYLVIA",
    NPCs.SYLVIA + UI.SPACE + UI.SPACE + "&7HELLO, " + playerName + "!",
    NPCs.SYLVIA + UI.SPACE + UI.SPACE + "&7I'M YOUR GUIDE TO SERVERPLUGINS.",
    NPCs.SYLVIA + UI.SPACE + UI.SPACE,
    UI.SPACE + UI.SPACE + "&c&l! &cHOVER AND CLICK THE OPTIONS",
    "&f"
};
```

### Helper Methods

Use built-in helper methods for easier formatting:

```java
// Get NPC icon by name
String icon = ResourcePackIcons.getNpcIcon("sylvia");

// Create a formatted dialog line
String line = ResourcePackIcons.createNpcDialogLine("sylvia", "&7Hello, player!");
// Result: 퀁⻔⻔&7Hello, player!

// Create a separator line
String separator = ResourcePackIcons.createNpcSeparator("sylvia", "&d", 22);
// Result: 퀁⻔⻔&d&l━━━━━━━━━━━━━━━━━━━━━━
```

## Menu Backgrounds

### Available Menus (256px height)

Menu backgrounds fill the entire screen and are typically used in custom inventory GUIs.

#### Auction Menus

```java
import static net.serverplugins.api.ui.ResourcePackIcons.Menus.Auction.*;

String auctionHouse = HOUSE;    // Main auction house menu
String browser = BROWSER;        // Auction browser menu
```

#### Casino Menus

```java
import static net.serverplugins.api.ui.ResourcePackIcons.Menus.Casino.*;

String blackjackMenu = BLACKJACK_MENU;   // Blackjack menu
String blackjackTable = BLACKJACK_TABLE; // Blackjack game table
String crashMenu = CRASH_MENU;           // Crash menu
String crashGame = CRASH_GAME;           // Crash game
String lottery = LOTTERY;                // Lottery menu
String rouletteMenu = ROULETTE_MENU;     // Roulette menu
String rouletteGame = ROULETTE_GAME;     // Roulette game
String slotsMenu = SLOTS_MENU;           // Slots menu
String slotsGame = SLOTS_GAME;           // Slots game
```

#### Shop Menus

```java
import static net.serverplugins.api.ui.ResourcePackIcons.Menus.Shop.*;

String shopMain = MAIN;          // Main shop menu
String shopBlocks = BLOCKS;      // Blocks category
String shopDecoration = DECORATION;  // Decoration category
String shopFarming = FARMING;    // Farming category
String shopFood = FOOD;          // Food category
String shopMisc = MISC;          // Miscellaneous category
String shopRedstone = REDSTONE;  // Redstone category
String shopTools = TOOLS;        // Tools category
```

#### Other Menus

```java
import static net.serverplugins.api.ui.ResourcePackIcons.Menus.*;

// Warps
String warpsMenu = Warps.MAIN;

// Crates
String cratesMenu = Crates.MAIN;
String cratesClaim = Crates.CLAIM;

// Battle Pass
String battlePassMenu = BattlePass.MAIN;

// Survival (main hub)
String survivalMenu = Survival.MAIN;
```

### Menu Implementation Example

```java
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import net.serverplugins.api.ui.ResourcePackIcons;

public class ShopGUI {

    public Inventory createShopMenu() {
        // Create inventory with menu background as title
        Inventory inv = Bukkit.createInventory(null, 54,
            ResourcePackIcons.Menus.Shop.MAIN);

        // Add items to the inventory
        // ...

        return inv;
    }
}
```

## Icons and UI Elements

### Core UI Elements

```java
import static net.serverplugins.api.ui.ResourcePackIcons.UI.*;

// Spacing
String space = SPACE;              // ⻔ - Custom space character

// Welcome screen
String welcome1 = WELCOME_1;       // 섑
String welcome2 = WELCOME_2;       // 섒
String welcome3 = WELCOME_3;       // 쀁

// Currency & Items
String coin = COIN;                //  - Coin icon
String chest = CHEST;              //  - Chest icon

// Boss bar
String bossbar = BOSSBAR;          // お - Boss bar graphic
```

### Negative Spaces (Precise Positioning)

Use negative spaces to overlap text or shift elements:

```java
import static net.serverplugins.api.ui.ResourcePackIcons.UI.*;

String text = SPACE_MINUS_8 + "Shifted text";  // Shift 8 pixels left

// Available negative spaces:
SPACE_MINUS_1    // -1 pixel
SPACE_MINUS_2    // -2 pixels
SPACE_MINUS_4    // -4 pixels
SPACE_MINUS_8    // -8 pixels
SPACE_MINUS_16   // -16 pixels
SPACE_MINUS_32   // -32 pixels
SPACE_MINUS_64   // -64 pixels
SPACE_MINUS_128  // -128 pixels

// Positive spaces:
SPACE_PLUS_1     // +1 pixel
SPACE_PLUS_2     // +2 pixels
SPACE_PLUS_4     // +4 pixels
SPACE_PLUS_8     // +8 pixels
```

### Small Icons

```java
import static net.serverplugins.api.ui.ResourcePackIcons.Icons.*;

String compass = COMPASS;      // Compass icon
String dice = DICE;            // Dice icon
String earth = EARTH;          // Earth/world icon
String heart = HEART;          // Heart icon
String pickaxe = PICKAXE;      // Pickaxe icon
String ping = PING;            // Ping/connection icon
String sword = SWORD;          // Sword icon
String arcade = ARCADE;        // Arcade machine icon
```

## Emojis

### Available Emojis (8px height, ascent 8)

| Emoji | Unicode | Character | Constant |
|-------|---------|-----------|----------|
| Clown | U+2F14 | ⼔ | `Emojis.CLOWN` |
| Dead | U+2F15 | ⼕ | `Emojis.DEAD` |
| Eyes | U+2F16 | ⼖ | `Emojis.EYES` |
| Fire | U+2F17 | ⼗ | `Emojis.FIRE` |
| Grimace | U+2F18 | ⼘ | `Emojis.GRIMACE` |
| Gun | U+2F19 | ⼙ | `Emojis.GUN` |
| Happy | U+2F1A | ⼚ | `Emojis.HAPPY` |
| Heart | U+2F1B | ⼛ | `Emojis.HEART` |
| Laugh | U+2F1C | ⼜ | `Emojis.LAUGH` |
| Love | U+2F1D | ⼝ | `Emojis.LOVE` |
| Muscle | U+2F1E | ⼞ | `Emojis.MUSCLE` |
| Party | U+2F1F | ⼟ | `Emojis.PARTY` |
| Pleading | U+2F20 | ⼠ | `Emojis.PLEADING` |
| Skull | U+2F21 | ⼡ | `Emojis.SKULL` |
| Sunglasses | U+2F22 | ⼢ | `Emojis.SUNGLASSES` |
| Thumbs Up | U+2F23 | ⼣ | `Emojis.THUMBS_UP` |
| Weary | U+2F24 | ⼤ | `Emojis.WEARY` |

### Emoji Usage

```java
import static net.serverplugins.api.ui.ResourcePackIcons.*;

// Direct use
player.sendMessage("That's lit! " + Emojis.FIRE);

// Get emoji by name
String emoji = getEmoji("skull");
player.sendMessage("RIP " + emoji);

// In chat formatting
String chatMessage = "&7[&bPlayer&7] " + Emojis.LAUGH + " That was funny!";
```

## Ranks and Chat Tags

### Player Ranks (9px height, ascent 8)

```java
import static net.serverplugins.api.ui.ResourcePackIcons.Ranks.*;

String adventurer = ADVENTURER;   // Adventurer rank
String vip = VIP;                 // VIP rank
String mvp = MVP;                 // MVP rank
String pro = PRO;                 // Pro rank
String helper = HELPER;           // Helper rank
String mod = MOD;                 // Moderator rank
String admin = ADMIN;             // Admin rank
String developer = DEVELOPER;     // Developer rank
String owner = OWNER;             // Owner rank
String youtube = YOUTUBE;         // YouTube rank
String twitch = TWITCH;           // Twitch rank
String partner = PARTNER;         // Partner rank
```

### Chat Tags (9px height, ascent 8)

```java
import static net.serverplugins.api.ui.ResourcePackIcons.ChatTags.*;

String red = RED;         // Red chat tag
String green = GREEN;     // Green chat tag
String blue = BLUE;       // Blue chat tag
String yellow = YELLOW;   // Yellow chat tag
String purple = PURPLE;   // Purple chat tag
String orange = ORANGE;   // Orange chat tag
String pink = PINK;       // Pink chat tag
String white = WHITE;     // White chat tag
```

## Usage Examples

### Example 1: NPC Dialog System

```java
import net.serverplugins.api.ui.ResourcePackIcons;
import static net.serverplugins.api.ui.ResourcePackIcons.*;

public class NPCDialog {

    public void showSylviaDialog(Player player) {
        List<String> lines = Arrays.asList(
            "&f",
            createNpcSeparator("sylvia", "&d", 22),
            createNpcDialogLine("sylvia", ""),
            createNpcDialogLine("sylvia", "&d&lNPC SYLVIA"),
            createNpcDialogLine("sylvia", "&7HELLO, " + player.getName() + "!"),
            createNpcDialogLine("sylvia", "&7I'M YOUR GUIDE TO SERVERPLUGINS."),
            createNpcDialogLine("sylvia", "&7WHAT DO YOU NEED?"),
            createNpcDialogLine("sylvia", ""),
            UI.SPACE + UI.SPACE + "&c&l! &cHOVER AND CLICK THE OPTIONS",
            "&f"
        );

        lines.forEach(player::sendMessage);
    }
}
```

### Example 2: Custom Shop GUI

```java
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import static net.serverplugins.api.ui.ResourcePackIcons.Menus.Shop.*;

public class ShopManager {

    public Inventory createMainShop() {
        // Use shop menu background as inventory title
        Inventory inv = Bukkit.createInventory(null, 54, MAIN);

        // Add category items
        // ...

        return inv;
    }

    public Inventory createBlocksShop() {
        Inventory inv = Bukkit.createInventory(null, 54, BLOCKS);
        // Add block items
        // ...
        return inv;
    }
}
```

### Example 3: Chat with Emojis

```java
import static net.serverplugins.api.ui.ResourcePackIcons.*;

public class ChatFormatter {

    public String formatChat(Player player, String message) {
        // Replace :emoji: with actual emoji characters
        message = message.replace(":fire:", Emojis.FIRE);
        message = message.replace(":skull:", Emojis.SKULL);
        message = message.replace(":laugh:", Emojis.LAUGH);
        message = message.replace(":heart:", Emojis.HEART);

        // Add rank badge
        String rank = getRankBadge(player);

        return rank + " &7" + player.getName() + " &f" + message;
    }

    private String getRankBadge(Player player) {
        if (player.hasPermission("server.rank.owner")) {
            return Ranks.OWNER;
        } else if (player.hasPermission("server.rank.admin")) {
            return Ranks.ADMIN;
        } else if (player.hasPermission("server.rank.vip")) {
            return Ranks.VIP;
        }
        return Ranks.ADVENTURER;
    }
}
```

### Example 4: Boss Bar with Icon

```java
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import static net.serverplugins.api.ui.ResourcePackIcons.*;

public class CustomBossBar {

    public BossBar createEventBossBar(String title) {
        // Create boss bar with custom icon
        String barTitle = UI.BOSSBAR + " &6&l" + title;

        BossBar bar = Bukkit.createBossBar(
            barTitle,
            BarColor.YELLOW,
            BarStyle.SOLID
        );

        return bar;
    }
}
```

## Best Practices

### 1. Always Import Statically

For cleaner code, use static imports:

```java
import static net.serverplugins.api.ui.ResourcePackIcons.*;

// Good
String icon = NPCs.SYLVIA;

// Avoid
String icon = ResourcePackIcons.NPCs.SYLVIA;
```

### 2. Use Helper Methods

Leverage the built-in helper methods for common patterns:

```java
// Good - uses helper method
String line = createNpcDialogLine("sylvia", "&7Hello!");

// Avoid - manual formatting
String line = NPCs.SYLVIA + UI.SPACE + UI.SPACE + "&7Hello!";
```

### 3. Resource Pack Dependency

Always inform users that the ServerPlugins resource pack must be enabled:

```java
if (!hasResourcePackEnabled(player)) {
    player.sendMessage("&cPlease enable the ServerPlugins resource pack for the best experience!");
    player.sendMessage("&7Download: &bhttps://example.com/resourcepack");
}
```

### 4. Fallback for Non-Resource Pack Users

Provide text fallbacks for players without the resource pack:

```java
public String formatNpcName(Player player, String npcName) {
    if (hasResourcePackEnabled(player)) {
        return getNpcIcon(npcName) + UI.SPACE + UI.SPACE + "&d&l" + npcName.toUpperCase();
    }
    return "&d&l" + npcName.toUpperCase();
}
```

### 5. Test on Both Java and Bedrock

Custom Unicode characters work differently across platforms:

- **Java Edition**: Full support via resource packs
- **Bedrock Edition**: May require adjustments via Geyser/Floodgate

### 6. Performance Considerations

- Cache formatted strings instead of recreating them
- Use StringBuilder for complex multi-line text
- Avoid excessive Unicode characters in high-frequency messages

### 7. Consistent Spacing

Always use `UI.SPACE` for consistent spacing:

```java
// Good - consistent spacing
NPCs.SYLVIA + UI.SPACE + UI.SPACE + message

// Avoid - manual spacing
NPCs.SYLVIA + "  " + message
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Icons show as squares/boxes | Player doesn't have resource pack enabled |
| Icons show as Korean characters | Resource pack not loaded or out of date |
| Wrong icon displays | Check Unicode mapping is correct |
| Icons misaligned | Use `UI.SPACE` for proper spacing |
| Icons too big/small | Check ascent/height in resource pack JSON |

## Additional Resources

- **Resource Pack Download**: https://example.com/resourcepack
- **Server Plugin Format**: See server-core, server-api examples
- **NPC Dialog Examples**: See server-npcs plugin
- **Discord Support**: https://discord.gg/serverplugins

## Version History

- **v1.0.0** (2025-12-25): Initial release with 9 NPCs, menus, icons, emojis, ranks
