# GUI Menu System Guide

Complete guide for creating custom GUI menus in ServerCommands using the `GUI_MENU` command type.

## Overview

The GUI Menu system allows you to create fully customizable inventory-based menus through YAML configuration. These menus support:

- Custom titles with MiniMessage formatting
- Configurable inventory sizes (9-54 slots)
- Sound effects (open, click, per-button)
- Multiple action types (commands, console commands, GUI navigation)
- Full PlaceholderAPI integration
- Custom items with lore, names, and custom model data

## Quick Start

### 1. Create a Dynamic Command

First, create a command that opens your GUI:

```yaml
dynamic-commands:
  mymenu:
    description: "Open my custom menu"
    type: GUI_MENU
    command: "my-gui-id"  # Links to gui-menus.my-gui-id
    permission: "myplugin.menu"
    sound: "UI_BUTTON_CLICK 0.5 1.0"
```

### 2. Define the GUI Menu

Then define the actual GUI structure:

```yaml
gui-menus:
  my-gui-id:
    title: '<gold><bold>My Menu</bold></gold>'
    size: 27
    open-sound:
      type: BLOCK_CHEST_OPEN
      volume: 1.0
      pitch: 1.0
    buttons:
      13:
        item:
          material: DIAMOND
          name: '<aqua>Click Me!</aqua>'
        action: 'command:spawn'
```

## Configuration Reference

### GUI Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `title` | String | "Menu" | GUI title (supports MiniMessage formatting) |
| `size` | Integer | 27 | Inventory size (must be multiple of 9, max 54) |
| `open-sound` | Section | none | Sound played when GUI opens |
| `click-sound` | Section | none | Default click sound for all buttons |
| `buttons` | Section | {} | Button definitions by slot number |

### Sound Configuration

```yaml
open-sound:
  type: BLOCK_CHEST_OPEN  # Bukkit Sound enum name
  volume: 1.0             # 0.0 to 1.0 (or higher)
  pitch: 1.0              # 0.5 to 2.0 recommended
```

Set `type: none` or omit the section for silent.

### Button Configuration

```yaml
buttons:
  <slot>:  # Slot number (0-53)
    item:
      material: DIAMOND           # Required: Bukkit Material name
      amount: 1                   # Optional: Item count (1-64)
      name: '<gold>Name</gold>'  # Optional: Display name
      lore:                       # Optional: Lore lines
        - '<gray>Line 1'
        - '<yellow>Line 2'
      custom-model-data: 12345    # Optional: Custom model data
      glint: false                # Optional: Enchantment glint
    action: 'command:spawn'       # Required: Action on click
    click-sound:                  # Optional: Override click sound
      type: UI_BUTTON_CLICK
      volume: 0.5
      pitch: 1.2
```

## Action Types

### `command:<command>`
Execute a command as the player.

```yaml
action: 'command:spawn'
action: 'command:warp lobby'
action: 'command:home'
```

### `console:<command>`
Execute a command as console. Use `{player}` placeholder for player name.

```yaml
action: 'console:give {player} diamond 1'
action: 'console:eco give {player} 100'
```

### `close`
Simply close the GUI.

```yaml
action: 'close'
```

### `open:<gui-id>`
Open another GUI (for sub-menus).

```yaml
action: 'open:settings-menu'
action: 'open:teleport-hub'
```

## Placeholders

All text fields support PlaceholderAPI placeholders:

```yaml
item:
  material: PLAYER_HEAD
  name: '<green>%player_name%</green>'
  lore:
    - '<yellow>Balance: <white>$%vault_eco_balance%'
    - '<yellow>Level: <white>%player_level%'
    - '<yellow>Playtime: <white>%statistic_time_played%'
```

## Complete Example: Server Hub Menu

```yaml
dynamic-commands:
  hub:
    description: "Open server hub menu"
    type: GUI_MENU
    command: "server-hub"
    sound: "BLOCK_CHEST_OPEN 0.8 1.0"

gui-menus:
  server-hub:
    title: '<gradient:#ff6b6b:#feca57>Server Hub</gradient>'
    size: 27
    open-sound:
      type: BLOCK_CHEST_OPEN
      volume: 1.0
      pitch: 1.0
    click-sound:
      type: UI_BUTTON_CLICK
      volume: 0.5
      pitch: 1.0
    buttons:
      # Spawn button
      11:
        item:
          material: GRASS_BLOCK
          name: '<green><bold>Spawn</bold></green>'
          lore:
            - '<gray>Teleport to spawn'
        action: 'command:spawn'
        click-sound:
          type: ENTITY_ENDERMAN_TELEPORT
          volume: 0.6
          pitch: 1.2

      # Player menu button
      13:
        item:
          material: PLAYER_HEAD
          name: '<aqua><bold>Player Menu</bold></aqua>'
          lore:
            - '<gray>Access player features'
        action: 'open:player-menu'

      # Shop button
      15:
        item:
          material: EMERALD
          name: '<gold><bold>Shop</bold></gold>'
          lore:
            - '<gray>Buy and sell items'
            - ''
            - '<yellow>Balance: <white>$%vault_eco_balance%'
        action: 'command:shop'

      # Close button
      22:
        item:
          material: BARRIER
          name: '<red><bold>Close</bold></red>'
        action: 'close'
```

## Multi-Page Menus

Create multi-page menus by linking GUIs together:

```yaml
gui-menus:
  main-menu:
    title: 'Main Menu (1/2)'
    size: 27
    buttons:
      # ... content buttons ...
      26:
        item:
          material: ARROW
          name: '<yellow>Next Page →'
        action: 'open:main-menu-2'

  main-menu-2:
    title: 'Main Menu (2/2)'
    size: 27
    buttons:
      # ... content buttons ...
      18:
        item:
          material: ARROW
          name: '<yellow>← Previous Page'
        action: 'open:main-menu'
```

## Best Practices

### 1. Use Consistent Layouts
Keep navigation buttons in the same positions across menus (e.g., close at slot 22, back at slot 18).

### 2. Provide Visual Feedback
Use different click sounds for different button types:
- Navigation: `BLOCK_WOODEN_BUTTON_CLICK_ON`
- Actions: `ENTITY_PLAYER_LEVELUP`
- Teleports: `ENTITY_ENDERMAN_TELEPORT`
- Errors: `ENTITY_VILLAGER_NO`

### 3. Add Helpful Lore
Include usage hints, requirements, or status information in item lore.

### 4. Validate Sizes
GUI sizes must be multiples of 9 (9, 18, 27, 36, 45, 54). Invalid sizes default to 27.

### 5. Test Placeholders
Not all PlaceholderAPI expansions may be installed. Test placeholder rendering before deploying.

### 6. Use Descriptive IDs
Use clear GUI IDs like `teleport-hub`, `player-settings`, `admin-tools` instead of `gui1`, `menu2`.

## Troubleshooting

### GUI doesn't open
- Check console for errors during plugin load
- Verify GUI ID matches between command and gui-menus section
- Ensure `type: GUI_MENU` is set correctly

### Items don't appear
- Verify slot numbers are valid (0-53 for size 54 GUI)
- Check material names against Bukkit Material enum
- Look for YAML syntax errors (indentation, quotes)

### Actions don't work
- Verify action format matches one of: `command:`, `console:`, `close`, `open:`
- Check if target commands exist and player has permission
- For `open:` actions, ensure target GUI exists

### Sounds don't play
- Verify sound type against Bukkit Sound enum
- Check if sound section syntax is correct
- Test with different sounds (some may not work in all versions)

## Advanced Features

### Conditional Buttons

Use PlaceholderAPI conditionals in lore to show different information:

```yaml
lore:
  - '%permission_has_fly% ? <green>✓ Fly Enabled : <red>✗ No Permission%'
  - '%vault_eco_balance% >= 1000 ? <green>You can afford this : <red>Not enough money%'
```

### Custom Model Data

Use custom textures from resource packs:

```yaml
item:
  material: PLAYER_HEAD
  custom-model-data: 42069
  name: '<gold>Custom Item'
```

### Dynamic Content

All placeholders are evaluated when the GUI opens, allowing for dynamic content:

```yaml
title: '<green>%player_name%''s Menu</green>'
item:
  lore:
    - '<yellow>Current time: <white>%server_time_HH:mm:ss%'
```

## API Usage (For Developers)

### Opening GUIs Programmatically

```java
GuiRegistry registry = GuiRegistry.getInstance();
ConfigurableGui gui = registry.createGui("my-gui-id", player);
if (gui != null) {
    gui.open(player);
}
```

### Registering GUIs Programmatically

```java
ConfigurationSection config = // ... your config section
GuiRegistry.getInstance().registerGui("custom-gui", config);
```

### Checking If GUI Exists

```java
if (GuiRegistry.getInstance().isRegistered("my-gui")) {
    // GUI exists
}
```

## Version Compatibility

- Requires: ServerAPI (for GUI framework)
- Requires: Paper/Spigot 1.20+
- Optional: PlaceholderAPI (for placeholder support)
- Compatible with: All modern resource packs and custom model data

## Examples in Config

See `config.yml` for complete working examples:
- `server-menu`: Main server navigation hub
- `player-menu`: Player-specific features and settings

## Support

For issues or questions:
1. Check this documentation
2. Verify YAML syntax at yamllint.com
3. Check server console for errors
4. Review example configurations in config.yml
