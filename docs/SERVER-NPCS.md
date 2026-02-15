# ServerNpcs

**Version:** 1.0-SNAPSHOT
**Type:** NPC & Dialog System
**Main Class:** `net.serverplugins.npcs.ServerNpcs`

## Overview

ServerNpcs provides NPC management and interactive dialog trees with support for FancyNpcs and Citizens plugins, plus Geyser/Floodgate compatibility for Bedrock players.

## Key Features

### NPC Management
- Create and manage NPCs
- Skin customization
- Location management
- Action bindings

### Dialog System
- Interactive conversation trees
- Multiple choice dialogs
- Command execution
- Conditional branching

### Plugin Integration
- FancyNpcs support
- Citizens support
- Auto-detection
- Unified API

### Bedrock Support
- Geyser compatibility
- Floodgate integration
- Touch-friendly dialogs

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/npc reload` | `servernpcs.admin` | Reload NPCs |
| `/npc list` | `servernpcs.admin` | List NPCs |
| `/npc info <id>` | `servernpcs.admin` | NPC info |
| `/npc talk <id>` | `servernpcs.use` | Talk to NPC |
| `/dialog <id>` | `servernpcs.dialog` | Open dialog |

## Configuration

### config.yml
```yaml
# NPC Settings
npcs:
  plugin: "auto"  # auto, fancynpcs, citizens

# Dialogs
dialogs:
  shop-keeper:
    greeting: "&6Welcome to the shop!"
    options:
      1:
        text: "&aBuy items"
        command: "shop open"
      2:
        text: "&eSell items"
        command: "shop sell"
      3:
        text: "&cLeave"
        action: "close"
```

## Dependencies

- ServerAPI
- FancyNpcs (soft)
- Citizens (soft)
- Geyser-Spigot (soft)
- floodgate (soft)
