# ServerBridge-Velocity

**Version:** 1.0-SNAPSHOT
**Type:** Velocity Proxy Plugin
**Platform:** Velocity (NOT Bukkit/Paper)

## Overview

ServerBridge-Velocity is the Velocity proxy counterpart to ServerBridge, enabling cross-server communication, player transfers, and Redis messaging at the proxy level.

## Important Note

**This plugin runs on the Velocity proxy server, NOT on Paper/Spigot game servers.** It coordinates with the Paper-side ServerBridge plugin for full network functionality.

## Key Features

### Proxy-Level Integration
- Player connection handling
- Server switching coordination
- Global player list

### Redis Messaging
- Pub/sub coordination
- Cross-server chat relay
- Event broadcasting

### Player Transfer
- Seamless server switching
- Transfer commands
- Connection balancing

### Network Status
- Server status tracking
- Player distribution
- Online/offline detection

## Configuration

### config.toml
```toml
[redis]
enabled = true
host = "localhost"
port = 6379
password = ""
database = 0

[messaging]
channels = ["server:chat", "server:player", "server:broadcast"]

[servers]
lobby = "lobby"
survival = "smp"
creative = "creative"
```

## Dependencies

- Velocity API
- Redis (Jedis client)

## Installation

1. Place JAR in Velocity `plugins/` folder
2. Start Velocity to generate config
3. Configure Redis connection
4. Restart Velocity
