# ServerAFK

**Version:** 1.0-SNAPSHOT
**Type:** AFK Zone Rewards System
**Main Class:** `net.serverplugins.afk.ServerAFK`

## Overview

ServerAFK allows players to earn money and XP by spending time in designated AFK zones. Features include hologram displays, statistics tracking, leaderboards, and CombatLogX integration to prevent abuse.

## Key Features

### AFK Zones
- Define cuboid regions for AFK rewards
- Multiple zones with different reward rates
- Hologram displays at zone locations
- Per-zone permissions

### Reward System
- Money rewards (Vault economy)
- XP rewards
- Configurable reward rates
- Time-based scaling

### Statistics
- Track total AFK time per player
- Session tracking
- Leaderboards (top AFKers)
- Zone-specific stats

### Holograms
- DecentHolograms integration
- Display zone info
- Show current AFKers
- Reward rates display

### Anti-Abuse
- CombatLogX integration (no rewards in combat)
- Movement detection
- AFK kick prevention

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/serverafk p1` | `serverafk.admin` | Set zone position 1 |
| `/serverafk p2` | `serverafk.admin` | Set zone position 2 |
| `/wa p1` | `serverafk.admin` | Alias |
| `/afk` | `serverafk.stats` | View AFK status |
| `/afk stats` | `serverafk.stats` | View your stats |
| `/afk top` | `serverafk.stats` | View leaderboard |
| `/afk zones` | `serverafk.zones` | List all zones |
| `/afk session` | `serverafk.stats` | Current session info |

## Configuration

### config.yml
```yaml
# AFK Zones
zones:
  spawn-afk:
    name: "&6Spawn AFK Zone"
    world: "world"
    min-x: 100
    min-y: 64
    min-z: 100
    max-x: 150
    max-y: 80
    max-z: 150

    # Rewards
    money-per-minute: 10.0
    xp-per-minute: 5
    permission: "serverafk.zone.spawn"

    # Hologram
    hologram:
      enabled: true
      location:
        x: 125.5
        y: 70.0
        z: 125.5
      lines:
        - "&6&lAFK Zone"
        - "&7Earn &a$10&7/min"
        - "&7Earn &b5 XP&7/min"
        - "&7Players: {count}"

# Reward Settings
rewards:
  update-interval: 60  # Seconds
  require-permission: false
  announce-earnings: true

# Combat Protection
combat:
  enabled: true
  prevent-rewards: true
  message: "&cNo rewards while in combat!"

# Statistics
stats:
  track-sessions: true
  save-interval: 300  # 5 minutes
  top-players: 10

# Messages
messages:
  prefix: "&7[&6AFK&7]&r "
  entered-zone: "&aYou entered the AFK zone!"
  left-zone: "&cYou left the AFK zone!"
  reward-earned: "&7You earned &a${money} &7and &b{xp} XP"
```

## Database Schema

```sql
CREATE TABLE IF NOT EXISTS server_afk_stats (
    uuid VARCHAR(36) PRIMARY KEY,
    total_time BIGINT DEFAULT 0,
    total_money DECIMAL(20, 2) DEFAULT 0,
    total_xp INT DEFAULT 0,
    sessions INT DEFAULT 0,
    last_afk TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS server_afk_sessions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL,
    zone_name VARCHAR(64) NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NULL,
    duration BIGINT,
    money_earned DECIMAL(20, 2),
    xp_earned INT
);
```

## Dependencies

- ServerAPI
- Vault
- DecentHolograms
- CombatLogX (soft)
- PlaceholderAPI (soft)
