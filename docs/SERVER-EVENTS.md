# ServerEvents

**Version:** 1.0-SNAPSHOT
**Type:** Server Events & Mini-Games
**Main Class:** `net.serverplugins.events.ServerEvents`

## Overview

ServerEvents provides a system for triggering server-wide events and mini-games with automated random triggering, participant tracking, and reward distribution.

## Key Features

### Event System
- Trigger server-wide events
- Configurable event types
- Participant tracking
- Reward distribution

### Random Events
- Automated random event triggering
- Weighted probability
- Cooldown system
- Time-based scheduling

### Event GUI
- Management interface
- Start/stop controls
- Participant list
- Reward configuration

### Multi-World Support
- Multiverse-Core integration
- Per-world events
- Cross-world events

### Reward System
- Vault economy rewards
- Item rewards
- Command execution
- Top participant bonuses

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/event trigger <event>` | `serverevents.trigger` | Trigger event |
| `/event random` | `serverevents.random` | Trigger random event |
| `/event stop` | `serverevents.stop` | Stop active event |
| `/event reload` | `serverevents.reload` | Reload config |
| `/event gui` | `serverevents.gui` | Open event GUI |
| `/events` | `serverevents.gui` | Alias |
| `/we` | `serverevents.gui` | Alias |

## Configuration

### config.yml
```yaml
# Event Types
events:
  koth:
    name: "&6King of the Hill"
    duration: 600  # 10 minutes
    world: "world"
    location:
      x: 0
      y: 100
      z: 0
      radius: 10
    rewards:
      1st: "eco give {player} 10000"
      2nd: "eco give {player} 5000"
      3rd: "eco give {player} 2500"

  boss-fight:
    name: "&cBoss Fight"
    duration: 1200  # 20 minutes
    spawn-mob: "WITHER"
    rewards:
      participation: "eco give {player} 1000"
      top-damage: "eco give {player} 10000"

  scavenger-hunt:
    name: "&eScavenger Hunt"
    duration: 900  # 15 minutes
    items-to-find: 10
    rewards:
      completion: "eco give {player} 5000"

# Random Events
random-events:
  enabled: true
  interval: 3600  # 1 hour
  weights:
    koth: 40
    boss-fight: 30
    scavenger-hunt: 30

# Messages
messages:
  prefix: "&7[&6Events&7]&r "
  event-started: "&a{event} has started!"
  event-ended: "&c{event} has ended!"
  participant-joined: "&a{player} joined the event!"
```

## Database Schema

```sql
CREATE TABLE IF NOT EXISTS server_events (
    id INT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(64) NOT NULL,
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP NULL,
    participants INT DEFAULT 0,
    winner VARCHAR(36)
);

CREATE TABLE IF NOT EXISTS server_event_participants (
    event_id INT NOT NULL,
    uuid VARCHAR(36) NOT NULL,
    score DECIMAL(20, 2) DEFAULT 0,
    rewards_claimed BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (event_id) REFERENCES server_events(id),
    PRIMARY KEY (event_id, uuid)
);
```

## Dependencies

- ServerAPI
- Vault
- server-bridge (soft)
- ProtocolLib (soft)
- packetevents (soft)
- Multiverse-Core (soft)
