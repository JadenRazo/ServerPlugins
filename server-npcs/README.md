# ServerNpcs

NPC and dialog management system for ServerPlugins following the server plugin format.

## Features

- **YAML-based Dialog System**: Define NPC dialogs in clean, easy-to-edit YAML files
- **Cross-Platform Support**: Supports both Java Edition (GUI) and Bedrock Edition (Forms via Geyser)
- **Command Integration**: Dialog choices can execute commands with configurable delays
- **Permission-based Choices**: Control which choices players can see based on permissions
- **FancyNpcs Integration**: Seamlessly integrates with FancyNpcs for visual NPC rendering
- **Extensible Architecture**: Easy to add new NPCs and dialogs

## Installation

1. Build the plugin: `mvn clean package`
2. Copy `target/server-npcs-1.0.0.jar` to your server's `plugins/` folder
3. Ensure `ServerAPI.jar` is also in the plugins folder
4. Restart the server
5. Configure NPCs and dialogs in the generated `plugins/ServerNpcs/dialogs/` folder

## Configuration

### Main Config (`config.yml`)

```yaml
debug: false

dialog:
  gui-enabled: true          # Enable GUI for Java Edition
  form-enabled: true         # Enable Forms for Bedrock Edition
  timeout-seconds: 60        # Dialog session timeout
  dialogs-folder: "dialogs"  # Folder containing dialog files

integrations:
  fancynpcs: true           # Enable FancyNpcs integration
  citizens: false           # Enable Citizens integration
```

### Dialog Files

Dialog files are stored in `plugins/ServerNpcs/dialogs/` and use the following format:

```yaml
npc:
  name: "npc_id"
  display-name: "&6NPC Display Name"

dialog:
  greeting:
    message:
      - "&aHello, {player}!"
      - "&7How can I help you?"

    choices:
      1:
        text: "&aOption 1"
        command: "some_command"  # Command to execute (without /)
        permission: ""           # Optional permission check
        delay: 0                 # Delay in ticks (20 ticks = 1 second)
        close: true              # Close dialog after choice
        next-node: ""            # Optional: Go to another dialog node

      2:
        text: "&cExit"
        close: true
```

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/npc reload` | Reload plugin configuration | `servernpcs.admin` |
| `/npc list` | List all NPCs and dialogs | `servernpcs.admin` |
| `/npc info <id>` | Get information about an NPC | `servernpcs.admin` |
| `/npc talk <id>` | Open a dialog with an NPC | `servernpcs.use` |
| `/dialog <id>` | Open a specific dialog | `servernpcs.dialog` |

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `servernpcs.*` | All permissions | op |
| `servernpcs.use` | Use basic commands | true |
| `servernpcs.admin` | Use admin commands | op |
| `servernpcs.dialog` | Open dialogs | true |

## Integration with FancyNpcs

1. Create NPCs using FancyNpcs commands
2. Create corresponding dialog files in `plugins/ServerNpcs/dialogs/`
3. Use the same name for both the FancyNpc and the dialog file
4. Right-clicking the NPC will automatically show the dialog

## Migration from Skript

To migrate existing Skript-based NPC commands:

1. Create a dialog YAML file for each NPC
2. Convert Skript commands to dialog choices
3. Update FancyNpcs actions to use `/dialog <npc-name>` or integrate directly
4. Remove old Skript commands once tested

### Example Migration

**Old Skript:**
```skript
command /sksylvia:
    trigger:
        make player execute command "sylvia"
```

**New Dialog:**
Create `dialogs/sylvia.yml` with appropriate choices, then update FancyNpcs action to trigger the dialog.

## Directory Structure

```
server-npcs/
├── src/main/
│   ├── java/net/serverplugins/npcs/
│   │   ├── ServerNpcs.java          # Main plugin class
│   │   ├── NpcsConfig.java          # Configuration handler
│   │   ├── commands/                # Command implementations
│   │   ├── listeners/               # Event listeners
│   │   ├── managers/                # Dialog & NPC managers
│   │   ├── dialog/                  # Dialog model classes
│   │   └── models/                  # NPC data models
│   └── resources/
│       ├── plugin.yml               # Plugin metadata
│       ├── config.yml               # Main configuration
│       └── dialogs/                 # Dialog YAML files
│           ├── sylvia.yml
│           ├── gandalf.yml
│           └── ...
└── pom.xml
```

## Development

This plugin follows the ServerPlugins plugin format:

- **Package**: `net.serverplugins.npcs`
- **Main Class**: `ServerNpcs.java`
- **Dependencies**: `server-api`
- **Code Style**: Follows existing server plugin patterns

## Support

For issues or questions, contact the ServerPlugins development team.
