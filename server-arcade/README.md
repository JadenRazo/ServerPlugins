# ServerArcade

![Java](https://img.shields.io/badge/Java-21-orange)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-green)
![Paper](https://img.shields.io/badge/Paper-Required-blue)
![License](https://img.shields.io/badge/License-Proprietary-red)

A comprehensive casino and gambling plugin for Paper 1.21.4+ featuring physical arcade machines, 8+ configurable games, REST API integration, and advanced player protection systems.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Installation](#installation)
- [Configuration](#configuration)
- [Database Setup](#database-setup)
- [API Documentation](#api-documentation)
- [Permissions](#permissions)
- [Commands](#commands)
- [Machine System](#machine-system)
- [Games](#games)
- [Troubleshooting](#troubleshooting)
- [Development](#development)
- [Security](#security)
- [Deployment](#deployment)

## Overview

ServerArcade transforms your Minecraft server into a fully-featured casino experience with:

- **8 Configurable Games**: Slots, Blackjack, Crash, Coinflip, Jackpot, Dice, and more
- **Physical Arcade Machines**: 3D interactive structures with custom models and animations
- **REST API**: Real-time leaderboards and statistics for website integration
- **Player Protection**: Self-exclusion system with configurable durations
- **Statistics Tracking**: Comprehensive player stats, win/loss tracking, and leaderboards
- **Discord Integration**: Webhook notifications for big wins
- **Economy Integration**: Full Vault economy support
- **Performance Optimized**: Caffeine caching with 5-minute TTL for high-traffic servers

## Features

### Casino Games
- **Slots** - 5-reel slot machine with custom symbols and multipliers
- **Blackjack** - Classic 21 with dealer AI and doubling down
- **Crash** - Multiplier-based betting with configurable house edge
- **Coinflip** - PvP coin flipping with automatic matching
- **Jackpot** - Community pot with timed rounds and animations
- **Dice** - Multi-bet types (over/under/exact/odd/even) with custom payouts
- **Quick Lottery** - Fast-paced lottery with instant results
- **Mega Jackpot** - High-stakes lottery with progressive pot

### Machine System
- **Personal Machines** - Smaller, player-placeable machines for claims
- **Casino Machines** - Large multi-block structures for dedicated casino buildings
- **Persistent Storage** - MariaDB-backed machine placement and ownership
- **Auto-ejection** - Configurable timeout to prevent AFK players (default: 5 minutes)
- **Custom Models** - Resource pack integration with custom_model_data

### Statistics & API
- **Real-time Stats** - Net profit, win rate, biggest wins, streaks
- **REST API** - JSON endpoints for website leaderboards
- **Caching** - Caffeine-powered cache reduces database load by 80%
- **Rate Limiting** - 5 requests/second per IP prevents abuse

### Player Protection
- **Self-Exclusion** - Players can exclude themselves for 1d/7d/30d/permanent
- **Confirmation System** - Two-step confirmation prevents accidental exclusion
- **Admin Override** - Admins can remove exclusions via database
- **Audit Logging** - All exclusions logged to file system

## Installation

### Requirements

- **Server**: Paper 1.21.4+ or Purpur 1.21.8
- **Java**: Java 21 (OpenJDK or Oracle)
- **Dependencies**:
  - ServerAPI (required) - Provides database layer and economy
  - Vault (required) - Economy integration
  - DecentHolograms (optional) - Legacy hologram cleanup
  - PlaceholderAPI (optional) - Placeholder expansion support
- **Database**: MariaDB 10.5+ or MySQL 8.0+
- **Resources**:
  - RAM: 512MB minimum, 1GB recommended for API enabled
  - CPU: 2 cores recommended for 50+ concurrent players

### Build Instructions

```bash
# Build ServerArcade with dependencies
cd /serverplugins/plugins-src
mvn clean package -pl server-arcade -am

# Output JAR location
# /serverplugins/plugins-src/server-arcade/target/server-arcade-1.0.0.jar
```

### Server Installation

1. Stop your server
2. Copy `server-arcade-1.0.0.jar` to your `plugins/` folder
3. Ensure `server-api-1.0.0.jar` is also in `plugins/`
4. Start the server to generate default configuration
5. Configure database credentials (see [Database Setup](#database-setup))
6. Restart the server

## Configuration

The main configuration file is located at `plugins/ServerArcade/config.yml`.

### Core Settings

```yaml
settings:
  min-bet: 100          # Minimum bet amount ($100)
  max-bet: 1000000      # Maximum bet amount ($1M)
  cooldown: 5           # Cooldown between bets (seconds)
```

### Game Configuration

Each game has a separate configuration file in `plugins/ServerArcade/games/`:

- `slots.yml` - 5-reel slot machine configuration
- `blackjack.yml` - Blackjack rules and payouts
- `crash.yml` - Crash multiplier settings
- `dice.yml` - Dice bet types and payouts
- `jackpot.yml` - Jackpot timing and animation
- `quick-lottery.yml` - Quick lottery settings
- `mega-jackpot.yml` - Mega jackpot settings

#### Slots Configuration Example

```yaml
SLOTS:
  title: "§8Slots Machine"
  bet_amounts: [100, 500, 1000, 5000, 10000]
  symbols:
    cherry:
      material: STICK
      custom_model_data: 10
      weight: 30              # Higher weight = more common
      multiplier: 2.0         # 2x bet on win
    jackpot:
      material: STICK
      custom_model_data: 15
      weight: 2               # Rare (2% chance)
      multiplier: 100.0       # 100x bet on win
      is_star: true           # Jackpot symbol
```

#### Crash Configuration Example

```yaml
crash:
  enabled: true
  min-multiplier: 1.0
  max-multiplier: 100.0
  house-edge: 0.05           # 5% house edge
  tick-rate: 2               # Update every 2 ticks
```

### Discord Integration

```yaml
discord:
  enabled: true
  webhook_url: "https://discord.com/api/webhooks/..."
  big_win_threshold:
    crash: 10000       # Announce crash wins over $10k
    lottery: 0         # Announce all lottery wins
    dice: 5000         # Announce dice wins over $5k
```

### Machine Settings

```yaml
machines:
  enabled: true
  seat_timeout_seconds: 300  # Auto-eject after 5 minutes
```

### Messages

All player-facing messages are customizable with MiniMessage formatting:

```yaml
messages:
  prefix: "<gradient:#ff6b6b:#feca57>[Arcade]</gradient> "
  bet-placed: "<green>Bet placed: <gold>${bet}</gold>"
  win: "<green>You won <gold>${amount}</gold>!"
  lose: "<red>You lost <gold>${amount}</gold>!"
  insufficient-funds: "<red>You need at least <gold>${amount}</gold> to play!"
```

## Database Setup

ServerArcade requires MariaDB/MySQL for machine persistence, statistics tracking, and the REST API.

### Environment Variables (Recommended)

For security, use environment variables instead of storing credentials in `config.yml`:

```bash
export DB_HOST="localhost"
export DB_PORT="1025"
export DB_NAME="my_database"
export DB_USERNAME="db_user"
export DB_PASSWORD="your_password_here"
```

### Configuration File (Alternative)

```yaml
database:
  host: "localhost"
  port: 3306
  database: "my_database"
  username: "db_user"
  password: "your_password_here"
```

**Warning**: If credentials are in `config.yml`, the plugin will log a warning recommending environment variables.

### Schema Tables

The plugin automatically creates these tables on startup:

| Table | Purpose |
|-------|---------|
| `server_arcade_machines` | Physical machine placement and persistence |
| `server_arcade_stats` | Player gambling statistics and leaderboards |
| `server_arcade_history` | Individual bet history and audit trail |
| `server_arcade_exclusions` | Self-exclusion records |
| `server_arcade_access` | Command access tiers (future feature) |
| `server_arcade_ownership` | Machine ownership and revenue tracking |

### Connection String Format

```
jdbc:mariadb://HOST:PORT/DATABASE?user=USERNAME&password=PASSWORD
```

### Troubleshooting Database Connection

If the plugin fails to connect:

1. **Check Firewall**: Ensure port 1025 (or your port) is open
2. **Verify Credentials**: Test connection with MySQL client:
   ```bash
   mysql -h localhost -P 3306 -u db_user -p
   ```
3. **Check User Permissions**: Ensure user has `ALL PRIVILEGES` on the database
4. **Whitelist IP**: Some hosts require whitelisting your server's IP address

### Fallback Mode

If database is not configured, the plugin will:
- Still function for command-based games (slots, blackjack, etc.)
- Disable machine persistence (machines won't survive restarts)
- Disable statistics tracking
- Disable REST API
- Log warning: `Database not available - machine persistence will use fallback`

## API Documentation

ServerArcade provides a REST API for real-time statistics and leaderboards on your website.

### Enabling the API

```yaml
api:
  enabled: true
  port: 8080
  require_auth: true
  key: "your-secret-api-key-here"
  allowed_origins:
    - "https://example.com"
    - "https://www.example.com"
```

**Security Note**: Use the `API_KEY` environment variable instead of storing the key in config.yml.

### Endpoints

#### GET /api/arcade/leaderboard/{type}

Get top players by category.

**Parameters**:
- `type` - Leaderboard type: `net_profit`, `crash_mult`, `biggest_win`, `total_wagered`
- `limit` (optional) - Number of results (default: 10, max: 100)

**Example Request**:
```bash
curl -H "X-API-Key: your-secret-key" \
  "http://localhost:8080/api/arcade/leaderboard/net_profit?limit=10"
```

**Example Response**:
```json
[
  {
    "rank": 1,
    "player_name": "Notch",
    "player_uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
    "value": 1500000,
    "label": "$1.5M"
  },
  {
    "rank": 2,
    "player_name": "Jeb_",
    "player_uuid": "853c80ef-3c37-49fd-aa49-938b674adae6",
    "value": 890000,
    "label": "$890.0K"
  }
]
```

#### GET /api/arcade/stats/{uuid}

Get detailed statistics for a specific player.

**Example Request**:
```bash
curl -H "X-API-Key: your-secret-key" \
  "http://localhost:8080/api/arcade/stats/069a79f4-44e9-4726-a5be-fca90e38aaf5"
```

**Example Response**:
```json
{
  "player_name": "Notch",
  "net_profit": 1500000,
  "total_wagered": 5000000,
  "total_won": 4500000,
  "total_lost": 3000000,
  "win_rate": "60.5%",
  "crash": {
    "total_bets": 150,
    "total_wins": 95,
    "biggest_win": 50000,
    "highest_mult": 27.5,
    "win_rate": "63.3%"
  },
  "lottery": {
    "total_bets": 50,
    "total_wins": 5,
    "biggest_win": 100000
  },
  "dice": {
    "total_bets": 200,
    "total_wins": 110,
    "biggest_win": 25000,
    "win_rate": "55.0%"
  },
  "streaks": {
    "current": 3,
    "best_win": 12,
    "worst_loss": -8
  }
}
```

#### GET /api/arcade/recent

Get recent big wins (over $10k).

**Parameters**:
- `limit` (optional) - Number of results (default: 20, max: 100)

**Example Request**:
```bash
curl -H "X-API-Key: your-secret-key" \
  "http://localhost:8080/api/arcade/recent?limit=20"
```

**Example Response**:
```json
[
  {
    "player_name": "Notch",
    "game_type": "crash",
    "bet": 5000,
    "payout": 137500,
    "multiplier": 27.5,
    "timestamp": 1706400000000
  }
]
```

### Authentication

All API endpoints require the `X-API-Key` header:

```bash
curl -H "X-API-Key: your-secret-key" \
  "http://localhost:8080/api/arcade/leaderboard/net_profit"
```

**401 Unauthorized** response if key is missing or invalid.

### CORS Configuration

Configure allowed origins to prevent cross-site requests:

```yaml
api:
  allowed_origins:
    - "https://example.com"
    - "https://www.example.com"
```

Use `["*"]` to allow all origins (not recommended for production).

### Rate Limiting

The API enforces 5 requests per second per IP address. Exceeding this limit returns:

**429 Rate Limit Exceeded**:
```json
{
  "error": "Rate limit exceeded - Maximum 5 requests per second"
}
```

### Performance Optimization

- **Caching**: Leaderboard results are cached for 5 minutes (configurable)
- **Cache Stats**: Cache reduces database queries by ~80% under normal load
- **Maximum Size**: Cache stores up to 100 different queries
- **Automatic Invalidation**: Cache entries expire after 5 minutes

## Permissions

### Player Permissions

| Permission | Description | Default |
|-----------|-------------|---------|
| `serverarcade.play` | Access to play arcade games | `true` |
| `serverarcade.place.personal` | Place personal (small) arcade machines | `false` |

### Admin Permissions

| Permission | Description | Default |
|-----------|-------------|---------|
| `serverarcade.admin` | Full admin access to arcade system | `op` |
| `serverarcade.machine.admin` | Place and manage all arcade machines | `op` |
| `serverarcade.machine.break.others` | Break other players' machines | `op` |
| `serverarcade.place.casino` | Place casino (large) arcade machines | `op` |

## Commands

### Player Commands

#### /arcade
Open the main arcade GUI menu.

**Aliases**: `/casino`, `/games`, `/gamble`, `/gambling`

```
/arcade
```

#### /slots <bet>
Play the slot machine game with the specified bet.

**Aliases**: `/slot`, `/slotmachine`

```
/slots 1000
```

#### /blackjack <bet>
Start a blackjack game with the specified bet.

**Aliases**: `/bj`, `/21`

```
/blackjack 5000
```

#### /crash <bet>
Play the crash game with the specified bet.

**Aliases**: `/crashgame`, `/rocket`

```
/crash 2500
```

#### /coinflip <bet> [player]
Challenge another player to a coinflip.

**Aliases**: `/cf`, `/flip`, `/cflip`

```
/coinflip 1000 Notch
```

#### /jackpot
Join the current jackpot round.

**Aliases**: `/jp`, `/pot`, `/lottery`

```
/jackpot
```

#### /dice bet <amount> <type> [number]
Roll the dice with various bet types.

**Aliases**: `/diceroll`, `/rolldice`

**Bet Types**:
- `over <number>` - Roll over specified number
- `under <number>` - Roll under specified number
- `exact <number>` - Roll exact number
- `odd` - Roll an odd number
- `even` - Roll an even number

```
/dice bet 1000 over 50
/dice bet 500 exact 6
/dice bet 2000 odd
```

#### /gamble stats [player]
View detailed gambling statistics for yourself or another player.

**Aliases**: `/gambling`, `/gamblinghelp`

```
/gamble stats
/gamble stats Notch
```

#### /gamble exclude <duration>
Self-exclude from gambling for a specified duration.

**Durations**: `1d`, `7d`, `30d`, `permanent`

```
/gamble exclude 7d
/gamble exclude permanent
```

#### /gamble exclude status
Check your current self-exclusion status.

```
/gamble exclude status
```

### Admin Commands

#### /arcademachine place <type>
Place an arcade machine at your target block.

**Permission**: `serverarcade.machine.admin`
**Aliases**: `/am`

**Machine Types**: `slots`, `blackjack`, `crash`, `jackpot`

```
/arcademachine place slots
```

#### /arcademachine remove
Remove the arcade machine you're looking at.

**Permission**: `serverarcade.machine.admin`

```
/arcademachine remove
```

#### /arcademachine list
List all arcade machines on the server.

**Permission**: `serverarcade.machine.admin`

```
/arcademachine list
```

#### /arcademachine reload
Reload configuration files.

**Permission**: `serverarcade.machine.admin`

```
/arcademachine reload
```

#### /arcademachine cleanup [radius]
Clean up orphaned machine entities within radius.

**Permission**: `serverarcade.machine.admin`

```
/arcademachine cleanup 50
```

#### /arcademigrate [path]
Migrate machines from DreamArcade plugin data.

**Permission**: `serverarcade.admin`

```
/arcademigrate /path/to/dreamarcade/data
```

#### /spawnseat
Spawn a debug slot machine seat (for testing).

**Permission**: `serverarcade.admin`

```
/spawnseat
```

#### /removeseat [radius]
Remove nearby slot machine seats.

**Permission**: `serverarcade.admin`

```
/removeseat 10
```

## Machine System

### Personal vs Casino Machines

**Personal Machines**:
- Smaller footprint (3x3 blocks typically)
- Can be placed by players with `serverarcade.place.personal` permission
- Suitable for player claims and private areas
- Examples: Personal slots, personal blackjack tables

**Casino Machines**:
- Large multi-block structures (5x5+ blocks)
- Require `serverarcade.place.casino` permission (admin only)
- Designed for dedicated casino buildings
- Examples: Casino slots, casino blackjack tables, jackpot machines

### Machine Placement

1. Look at the block where you want to place the machine
2. Run `/arcademachine place <type>`
3. Machine will spawn on top of the block, facing towards you
4. Machine structure is automatically saved to database

### Machine Interaction

- **Right-click** the machine to sit down and start playing
- **Shift + Right-click** to leave the machine
- **Auto-ejection** after 5 minutes of inactivity (configurable)

### Machine Ownership

- Machines remember who placed them (`placed_by` field)
- Only the owner or admins with `serverarcade.machine.break.others` can remove machines
- Future feature: Revenue tracking per machine

### Machine Persistence

Machines are stored in the `server_arcade_machines` table:

```sql
CREATE TABLE server_arcade_machines (
    id VARCHAR(36) PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    direction VARCHAR(16) NOT NULL,
    placed_by VARCHAR(36) NOT NULL,
    placed_at BIGINT NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    blocks_data TEXT
);
```

Machines automatically respawn on server restart.

## Games

### Slots

5-reel slot machine with customizable symbols and payouts.

**Configuration**: `plugins/ServerArcade/games/slots.yml`

- **Paylines**: Horizontal, diagonal, V-shape patterns
- **Symbols**: Cherry, Lemon, Orange, Bell, Seven, Star (jackpot)
- **Multipliers**: 2x to 100x bet amount
- **Weights**: Configurable probability per symbol

### Blackjack

Classic 21 card game against the dealer.

**Configuration**: `plugins/ServerArcade/games/blackjack.yml`

- **Rules**: Dealer stands on 17
- **Blackjack Payout**: 2.5x bet (configurable)
- **Win Payout**: 2.0x bet
- **Actions**: Hit, Stand, Double Down
- **Turn Timeout**: 30 seconds (configurable)

### Crash

Multiplier-based betting game.

**Configuration**: `plugins/ServerArcade/games/crash.yml`

- **Multiplier Range**: 1.0x to 100.0x
- **House Edge**: 5% (configurable)
- **Tick Rate**: Updates every 2 ticks
- **Cash Out**: Click to cash out at current multiplier
- **Crash**: Random crash point between min and max

### Coinflip

PvP coin flipping with automatic matching.

**Configuration**: `config.yml` under `coinflip`

- **Expiry**: 60 seconds to accept challenge
- **Sides**: Heads or Tails
- **Winner**: Takes all (minus optional house fee)

### Jackpot

Community pot with timed rounds.

**Configuration**: `plugins/ServerArcade/games/jackpot.yml`

- **Betting Duration**: 600 seconds (10 minutes)
- **Animation**: 120 seconds of spinning heads
- **Time Extension**: +5 seconds per bet
- **Winner Selection**: Weighted random based on bet amounts

### Dice

Roll the dice with multiple bet types.

**Configuration**: `plugins/ServerArcade/games/dice.yml`

- **Bet Types**: Over, Under, Exact, Odd, Even
- **Payouts**: Configurable per bet type
- **Dice Range**: 1-100 or 1-6 (configurable)

## Troubleshooting

### "Database not connected" Error

**Symptoms**: Plugin loads but logs `Database not available - machine persistence will use fallback`

**Solutions**:
1. Verify database credentials in config.yml or environment variables
2. Check firewall allows connection to database port
3. Test connection manually:
   ```bash
   mysql -h YOUR_HOST -P YOUR_PORT -u YOUR_USER -p
   ```
4. Check database user has proper permissions:
   ```sql
   GRANT ALL PRIVILEGES ON your_database.* TO 'your_user'@'your_server_ip';
   FLUSH PRIVILEGES;
   ```

### "API won't start" Error

**Symptoms**: API enabled in config but not accessible

**Solutions**:
1. Check port is not already in use:
   ```bash
   netstat -tuln | grep 8080
   ```
2. Verify `api.enabled: true` in config.yml
3. Check `api.require_auth` and ensure `api.key` is set
4. Test CORS configuration - ensure your domain is in `allowed_origins`
5. Check firewall allows inbound connections on API port

### "Machine persistence disabled" Warning

**Symptoms**: Machines disappear on server restart

**Solutions**:
1. Enable database connection (see Database Setup)
2. Check `server_arcade_machines` table exists
3. Verify table has correct schema (see schema.sql)

### Memory Issues

**Symptoms**: High memory usage, OutOfMemoryError

**Solutions**:
1. Tune cache settings in ArcadeStatsAPI.java:
   ```java
   // Reduce cache size
   .maximumSize(50) // Instead of 100
   .expireAfterWrite(3, TimeUnit.MINUTES) // Instead of 5
   ```
2. Increase JVM heap size:
   ```bash
   java -Xms2G -Xmx4G -jar paper.jar
   ```
3. Monitor cache hit rate in logs
4. Disable API if not needed

### Performance Problems

**Symptoms**: TPS drops, server lag during gambling

**Solutions**:
1. Enable database connection pooling (handled by ServerAPI)
2. Increase cache TTL to reduce database queries
3. Lower `tick-rate` in crash game configuration
4. Reduce `animation_duration` in jackpot configuration
5. Profile with Spark: `/spark profiler`

### "Insufficient funds" Despite Having Money

**Symptoms**: Players can't bet even with sufficient balance

**Solutions**:
1. Check Vault economy is installed and working:
   ```
   /version Vault
   ```
2. Verify ServerAPI is loaded before ServerArcade
3. Check economy provider is set in ServerAPI config
4. Test economy directly:
   ```
   /eco give PlayerName 1000
   /eco take PlayerName 100
   ```

### Machines Not Spawning

**Symptoms**: `/arcademachine place` runs but no machine appears

**Solutions**:
1. Check target block is not air
2. Verify space above block is clear (machines need 2-3 blocks height)
3. Check world is loaded and not disabled in WorldGuard
4. Look for errors in console
5. Try cleanup command: `/arcademachine cleanup 50`

## Development

### Building from Source

```bash
# Clone repository
git clone https://github.com/JadenRazo/SMP.git
cd SMP/serverplugins/plugins-src

# Build all modules
mvn clean package

# Build only ServerArcade (with dependencies)
mvn clean package -pl server-arcade -am

# Skip tests for faster builds
mvn clean package -pl server-arcade -am -DskipTests
```

### Project Structure

```
server-arcade/
├── src/main/java/net/serverplugins/arcade/
│   ├── ServerArcade.java          # Main plugin class
│   ├── ArcadeConfig.java          # Configuration wrapper
│   ├── commands/                  # Command handlers
│   ├── games/                     # Game implementations
│   │   ├── slots/                 # Slots game
│   │   ├── blackjack/             # Blackjack game
│   │   ├── crash/                 # Crash game
│   │   └── dice/                  # Dice game
│   ├── machines/                  # Physical machine system
│   ├── gui/                       # GUI implementations
│   ├── api/                       # REST API server
│   ├── statistics/                # Stats tracking
│   ├── protection/                # Self-exclusion system
│   └── integrations/              # Discord webhooks
└── src/main/resources/
    ├── plugin.yml                 # Plugin metadata
    ├── config.yml                 # Main configuration
    ├── schema.sql                 # Database schema
    └── games/                     # Game configurations
        ├── slots.yml
        ├── blackjack.yml
        └── ...
```

### Module Dependencies

ServerArcade depends on:
- **server-api** (provided scope) - Database layer, economy, GUI system
- **paper-api** (provided scope) - Paper server API
- **VaultAPI** (provided scope) - Economy integration
- **DecentHolograms** (provided scope, optional) - Legacy hologram cleanup
- **PlaceholderAPI** (provided scope, optional) - Placeholder expansion
- **Caffeine** (compile scope) - Performance caching

### Testing

```bash
# Run unit tests
mvn test -pl server-arcade

# Run specific test
mvn test -pl server-arcade -Dtest=SlotsOddsTest

# Generate test coverage report
mvn jacoco:report -pl server-arcade
```

### Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-new-game`
3. Make your changes
4. Test thoroughly on a development server
5. Commit changes: `git commit -am 'Add new game feature'`
6. Push to branch: `git push origin feature/my-new-game`
7. Submit a pull request

**Code Style**:
- Follow existing code conventions
- Use meaningful variable names
- Add JavaDoc comments for public methods
- Keep methods focused and single-purpose
- Handle errors gracefully with appropriate logging

## Security

### Environment Variables

Always use environment variables for sensitive credentials:

```bash
# Database credentials
export DB_HOST="your_host"
export DB_PORT="3306"
export DB_NAME="your_database"
export DB_USERNAME="your_username"
export DB_PASSWORD="your_password"

# API key
export API_KEY="your-secret-api-key-here"
```

**Never commit credentials to git**. The `.gitignore` excludes config files with credentials.

### API Authentication

The REST API uses header-based authentication:

```yaml
api:
  require_auth: true
  key: ""  # Leave empty, use API_KEY environment variable
```

Generate a strong random API key:
```bash
openssl rand -base64 32
```

### CORS Configuration

Restrict API access to trusted domains:

```yaml
api:
  allowed_origins:
    - "https://yourdomain.com"
    - "https://www.yourdomain.com"
```

**Never use `["*"]` in production** unless absolutely necessary.

### Rate Limiting

The API enforces 5 requests per second per IP. This is hardcoded and cannot be disabled.

### Self-Exclusion System

The self-exclusion system provides player protection:

1. Players can self-exclude for 1d/7d/30d/permanent
2. Two-step confirmation required (cannot be undone)
3. Only admins can remove exclusions via database
4. All exclusions are logged to audit trail

### Audit Logging

All sensitive operations are logged to `plugins/ServerArcade/audit.log`:
- Player exclusions
- Large wins/losses
- Machine placement/removal
- API access attempts

## Deployment

### Production Server Deployment

ServerArcade is deployed to the production server via SFTP.

**SFTP Connection**:
- Host: `gator.ultraservers.com:60002`
- User: `gmp567to.587fdbb4`

**Deploy Command**:
```bash
# Build the plugin
cd /serverplugins/plugins-src
mvn clean package -pl server-arcade -am

# Deploy via SFTP
lftp -u 'gmp567to.587fdbb4','A4Z}Gw7)w#}S' sftp://gator.ultraservers.com:60002 -e "set xfer:clobber on; put /serverplugins/plugins-src/server-arcade/target/server-arcade-1.0.0.jar -o plugins/server-arcade-1.0.0.jar; quit"
```

**Post-Deployment**:
1. Connect to server console
2. Run `/arcademachine reload` to reload configuration
3. Test API endpoint: `curl http://localhost:8080/api/arcade/leaderboard/net_profit`
4. Verify machines persist across `/reload`

### Environment Setup

For production deployment, set environment variables in server startup script:

```bash
#!/bin/bash
export DB_HOST="localhost"
export DB_PORT="1025"
export DB_NAME="my_database"
export DB_USERNAME="db_user"
export DB_PASSWORD="your_password_here"
export API_KEY="your_api_key_here"

java -Xms2G -Xmx4G -jar purpur.jar nogui
```

### Health Checks

Monitor plugin health with these checks:

```bash
# Check plugin loaded
/plugins | grep ServerArcade

# Check database connection
/arcademachine list

# Check API
curl -H "X-API-Key: your-key" http://localhost:8080/api/arcade/leaderboard/net_profit

# Check cache hit rate (in logs)
grep "Cache hit rate" plugins/ServerArcade/arcade.log
```

### Backup Recommendations

1. **Database**: Daily automated backups of MariaDB
2. **Configuration**: Backup `plugins/ServerArcade/` folder weekly
3. **Audit Logs**: Retain audit.log for 90 days minimum
4. **Machine Data**: Export machine data before major updates

---

For support, contact the ServerPlugins development team or create an issue in the repository.
