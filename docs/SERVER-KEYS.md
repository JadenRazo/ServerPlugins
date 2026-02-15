# ServerKeys

**Version:** 1.0-SNAPSHOT
**Type:** Crate Key Management
**Main Class:** `net.serverplugins.keys.ServerKeys`

## Overview

ServerKeys provides mass key distribution, statistics tracking, and history logging for crate keys. Supports ExcellentCrates and dungeon-style keys with comprehensive admin tools.

## Key Features

### Mass Distribution
- Give keys to all online players
- Support for ExcellentCrates
- Support for dungeon keys
- Configurable amount limits

### Statistics Tracking
- Track key ownership per player
- View key balances
- Crate-specific stats
- Leaderboards

### History Tracking
- Transaction logs
- Who gave what keys
- Distribution history
- Audit trail

### Integration
- ExcellentCrates support
- PlaceholderAPI integration
- Vault economy (optional costs)

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/keyall <crate\|dungeon> <key> [amt]` | `serverkeys.keyall` | Give keys to all |
| `/giveallkeys <crate\|dungeon> <key> [amt]` | `serverkeys.keyall` | Alias |
| `/keys [player]` | `serverkeys.view` | View key stats |
| `/mykeys` | `serverkeys.view` | View your keys |
| `/k` | `serverkeys.view` | Alias |
| `/key` | `serverkeys.view` | Alias |
| `/keyadmin give <player> <key> [amt]` | `serverkeys.admin` | Give keys |
| `/keyadmin history [player]` | `serverkeys.admin` | View history |
| `/keyadmin reload` | `serverkeys.admin` | Reload config |

## Configuration

### config.yml
```yaml
# Key Distribution
distribution:
  max-amount: 64  # Max keys per distribution
  cooldown: 60  # Seconds between /keyall
  announce: true
  message: "&aEveryone received {amount}x {key}!"

# Statistics
stats:
  track-ownership: true
  track-usage: true
  update-interval: 60  # Seconds

# History
history:
  enabled: true
  max-entries: 1000  # Per player
  log-to-file: true
  file-rotation: 7  # Days

# Integration
integration:
  excellentcrates: true
  dungeon-keys: true

# Messages
messages:
  prefix: "&7[&6Keys&7]&r "
  keys-given: "&aGave {amount}x {key} to {count} players!"
  no-players: "&cNo players online!"
  invalid-key: "&cInvalid key type!"
```

## Database Schema

```sql
CREATE TABLE IF NOT EXISTS server_key_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    giver_uuid VARCHAR(36),
    receiver_uuid VARCHAR(36) NOT NULL,
    key_type VARCHAR(64) NOT NULL,
    amount INT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX (receiver_uuid, timestamp DESC),
    INDEX (key_type)
);

CREATE TABLE IF NOT EXISTS server_key_balances (
    uuid VARCHAR(36) NOT NULL,
    key_type VARCHAR(64) NOT NULL,
    balance INT DEFAULT 0,
    PRIMARY KEY (uuid, key_type)
);
```

## Dependencies

- ServerAPI
- PlaceholderAPI (soft)
- ExcellentCrates (soft)
