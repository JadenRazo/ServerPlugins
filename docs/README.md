# ServerPlugins Plugin Documentation

This directory contains comprehensive documentation for all ServerPlugins plugins. Each plugin has its own dedicated documentation file with implementation details, configuration options, and usage examples.

## Plugin Index

### Core Infrastructure
- **[ServerAPI](SERVER-API.md)** - Foundational library providing database abstraction, economy, permissions, GUI systems, and utilities for all other plugins
- **[ServerCore](SERVER-CORE.md)** - Essential quality-of-life features including auto-totem, silk spawners, emojis, custom WorldGuard flags, and more

### Essential Commands & Features
- **[ServerCommands](SERVER-COMMANDS.md)** - Comprehensive command suite with homes, warps, teleportation, moderation, admin tools, and dynamic custom commands
- **[ServerBackpacks](SERVER-BACKPACKS.md)** - 6-tier portable storage system with auto-refill and crafting upgrades
- **[ServerDeathBuyback](SERVER-DEATH-BUYBACK.md)** - Death recovery system allowing players to buy back items from deaths

### Advanced Systems
- **[ServerClaim](SERVER-CLAIM.md)** - Comprehensive chunk claiming with profiles, banks, upkeep, levels, nations, warfare, and warps
- **[ServerBridge](SERVER-BRIDGE.md)** - Discord-Minecraft bridge with linking, daily rewards, cross-platform chat, and Redis messaging
- **[ServerDiscord](SERVER-DISCORD.md)** - Discord bot integration with server status, player stats, and moderation commands

### Administration & Moderation
- **[ServerAdmin](SERVER-ADMIN.md)** - Staff tools including vanish, spectator mode, X-ray detection, freeze system, and punishment GUI
- **[ServerFilter](SERVER-FILTER.md)** - Personalized chat filtering with anti-spam, anti-caps, and anti-advertising

### Entertainment & Games
- **[ServerArcade](SERVER-ARCADE.md)** - Casino system with slots, blackjack, crash, coinflip, jackpot, and dice games
- **[ServerParkour](SERVER-PARKOUR.md)** - Infinite procedurally generated parkour with statistics and leaderboards
- **[ServerEvents](SERVER-EVENTS.md)** - Server-wide events and mini-games system

### Economy & Rewards
- **[ServerAFK](SERVER-AFK.md)** - AFK zone rewards system with hologram integration
- **[ServerBounty](SERVER-BOUNTY.md)** - Player bounty system with trophy heads and cross-server sync
- **[ServerKeys](SERVER-KEYS.md)** - Crate key management with mass distribution and statistics

### Map Integration
- **[ServerDynmap](SERVER-DYNMAP.md)** - Dynmap integration for displaying claims on web map
- **[ServerBlueMap](SERVER-BLUEMAP.md)** - BlueMap integration with POI management

### Utilities
- **[ServerStats](SERVER-STATS.md)** - HTTP REST API for server statistics
- **[ServerNpcs](SERVER-NPCS.md)** - NPC and dialog system with Bedrock support

### Proxy
- **[ServerBridge-Velocity](SERVER-BRIDGE-VELOCITY.md)** - Velocity proxy plugin for cross-server communication

## Architecture Overview

### Dependency Hierarchy

All plugins depend on **ServerAPI** as their foundation. The dependency structure is:

```
ServerAPI (foundation)
    ├── ServerCore
    ├── ServerCommands
    ├── ServerClaim
    │   ├── ServerDynmap
    │   └── ServerBlueMap
    ├── ServerBackpacks
    ├── ServerDeathBuyback
    ├── ServerBridge
    ├── ServerDiscord
    ├── ServerAdmin
    ├── ServerAFK
    ├── ServerArcade
    ├── ServerBounty
    ├── ServerKeys
    ├── ServerStats
    ├── ServerEvents
    ├── ServerParkour
    ├── ServerFilter
    └── ServerNpcs
```

### Common Patterns

1. **Configuration**: Each plugin has a dedicated config wrapper class
2. **Managers**: Business logic isolated from commands/listeners
3. **Repositories**: Database access layer (when applicable)
4. **GUI System**: Shared GUI framework from ServerAPI
5. **Async Operations**: Database I/O on async threads
6. **Caching**: LRU caches for frequently accessed data

### External Dependencies

- **Vault**: Economy abstraction (used by most plugins)
- **LuckPerms**: Permission provider
- **PlaceholderAPI**: Placeholder expansion
- **ProtocolLib**: Packet manipulation for GUI features
- **WorldGuard**: Region protection and custom flags
- **CombatLogX**: PvP combat detection
- **DecentHolograms**: Holographic displays
- **Redis**: Cross-server communication (via ServerBridge)

## Building Plugins

```bash
# Build all plugins
cd /serverplugins/plugins-src && mvn clean package

# Build specific plugin with dependencies
mvn clean package -pl server-claim -am -DskipTests

# Build only ServerAPI
mvn clean package -pl server-api
```

## Server Configuration

- **Minecraft Version**: 1.21.8 (Purpur fork)
- **Java Version**: 21
- **Worlds**: `playworld` (main), `spawn` (hub)
- **Database**: MariaDB at `localhost:1025`
- **Redis**: Used for cross-server messaging

## Getting Help

For issues or questions:
1. Check the specific plugin's documentation file
2. Review the CLAUDE.md file in `/serverplugins/` for project-specific guidance
3. Check plugin.yml for command and permission details
4. Examine schema.sql (if present) for database structure

## Contributing

When making changes to plugins:
1. Read the relevant plugin documentation first
2. Follow existing code patterns and architecture
3. Test thoroughly on the dev server before production
4. Update documentation if adding new features
5. Follow the build and deployment procedures in CLAUDE.md
