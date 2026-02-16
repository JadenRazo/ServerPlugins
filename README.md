# Server Plugin Suite

A modular Minecraft plugin ecosystem for Paper 1.21.4+ servers. Built with Java 21 and Maven, this suite provides 24 interconnected plugins covering economy, claims, events, administration, and more.

## Architecture

![Architecture](docs/architecture.svg)

## Modules

| Module | Description |
|--------|-------------|
| `server-api` | Shared library: database abstraction (H2/SQLite/MariaDB), economy & permission providers, GUI system, messaging utilities |
| `server-core` | Quality-of-life features: auto-totem, silk spawners, emojis, custom WorldGuard flags |
| `server-admin` | Staff tools: vanish, spectator mode, X-ray detection, freeze, punishment GUI |
| `server-admin-velocity` | Velocity proxy admin tools with Redis-based cross-server communication |
| `server-commands` | Command suite: homes, warps, teleportation, moderation, admin menus |
| `server-claim` | Chunk claiming with exponential pricing, nations, warfare, banks, upkeep |
| `server-arcade` | Casino system: slots, blackjack, crash, coinflip, jackpot, dice, baccarat, roulette |
| `server-backpacks` | 6-tier portable storage with auto-refill and crafting upgrades |
| `server-death-buyback` | Death recovery system for buying back lost items |
| `server-events` | Server-wide events: drop parties, dragon fights, math challenges, spelling bees |
| `server-bridge` | Minecraft-side cross-server communication via Redis |
| `server-bridge-velocity` | Velocity proxy plugin for cross-server messaging |
| `server-resourcepack-velocity` | Velocity plugin for resource pack distribution |
| `server-afk` | AFK zone rewards with hologram integration |
| `server-bounty` | Player bounty system with trophy heads |
| `server-keys` | Crate key management with mass distribution and statistics |
| `server-filter` | Chat filtering: anti-spam, anti-caps, anti-advertising |
| `server-npcs` | NPC dialog system with Bedrock support |
| `server-parkour` | Procedurally generated infinite parkour with leaderboards |
| `server-stats` | HTTP REST API for server statistics |
| `server-bluemap` | BlueMap integration with POI management |
| `server-mobhealth` | Mob health display |
| `server-items` | Custom items API |
| `server-enchants` | Custom enchantments |

## Requirements

- **Java**: 21+
- **Server**: Paper 1.21.4+ (or any Paper fork like Purpur)
- **Build tool**: Maven 3.9+
- **Database**: H2 (default, zero-config), SQLite, or MariaDB/MySQL

## Building

```bash
# Build all plugins
mvn clean package -DskipTests

# Build a specific plugin with its dependencies
mvn clean package -pl server-claim -am -DskipTests

# Run tests
mvn clean test

# Check code formatting
mvn spotless:check

# Fix formatting
mvn spotless:apply
```

All built JARs are output to each module's `target/` directory.

## Installation

1. Build the project (see above)
2. Copy `server-api/target/ServerAPI.jar` to your server's `plugins/` folder (required by all other plugins)
3. Copy whichever plugin JARs you want to use
4. Start the server to generate default configs
5. Configure database settings in each plugin's `config.yml`

### Database Setup

Each plugin defaults to H2 (file-based, no external database needed). To use MariaDB:

1. Create a database and user
2. Update the `database` section in each plugin's `config.yml`:
   ```yaml
   database:
     type: mariadb
     host: localhost
     port: 3306
     name: my_database
     username: db_user
     password: your_password
   ```
3. Or use environment variables (supported by most plugins) - see `.env.example`

### External Dependencies

Some plugins require other Bukkit plugins to be installed:

- **Vault** - Economy abstraction (required by most plugins)
- **LuckPerms** - Permission provider
- **WorldGuard** - Region protection (optional, for claim integration)
- **PlaceholderAPI** - Placeholder expansion (optional)
- **ProtocolLib** - Packet manipulation (optional, for GUI features)

## Project Structure

```
server-<module>/
  src/main/java/net/serverplugins/<module>/
    Server<Module>.java      # Main plugin class
    <Module>Config.java      # Configuration wrapper
    commands/                # Command implementations
    listeners/               # Event handlers
    managers/                # Business logic
    models/                  # Data classes
    repository/              # Database access
    gui/                     # GUI implementations
  src/main/resources/
    plugin.yml               # Plugin metadata
    config.yml               # Default configuration
    schema.sql               # Database schema
```

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.
