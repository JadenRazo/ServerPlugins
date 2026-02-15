# ServerParkour

**Version:** 1.0-SNAPSHOT
**Type:** Infinite Parkour System
**Main Class:** `net.serverplugins.parkour.ServerParkour`

## Overview

ServerParkour provides a procedurally generated infinite parkour course with statistics tracking, leaderboards, and hologram integration.

## Key Features

### Infinite Parkour
- Procedurally generated course
- Increasing difficulty
- Checkpoint system
- Fall detection

### Statistics
- Track jumps completed
- Track falls
- Personal records
- Time tracking

### Leaderboards
- Top performers
- Most jumps
- Fastest times
- Hologram display

### Hologram Integration
- DecentHolograms support
- Real-time leaderboard
- Player stats display

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/parkour start` | `serverparkour.start` | Start parkour |
| `/parkour stop` | `serverparkour.stop` | Stop parkour |
| `/parkour stats` | `serverparkour.stats` | View your stats |
| `/parkour top` | `serverparkour.top` | View leaderboard |
| `/pk` | `serverparkour.start` | Alias |
| `/jump` | `serverparkour.start` | Alias |
| `/parkouradmin reload` | `serverparkour.admin` | Reload config |
| `/parkouradmin setnpc` | `serverparkour.admin` | Set NPC location |
| `/parkouradmin removenpc` | `serverparkour.admin` | Remove NPC |

## Configuration

### config.yml
```yaml
# Parkour Settings
parkour:
  start-location:
    world: "world"
    x: 0
    y: 100
    z: 0

  difficulty:
    starting: 1
    increment: 0.1  # Per jump
    max: 10

  rewards:
    per-jump: 10.0
    per-checkpoint: 100.0
    record-bonus: 1000.0

# Leaderboard
leaderboard:
  enabled: true
  top-count: 10
  hologram:
    enabled: true
    location:
      world: "world"
      x: 0
      y: 105
      z: 0
    lines:
      - "&6&lParkour Leaderboard"
      - "&e1. {player1} - {score1}"
      - "&e2. {player2} - {score2}"
      - "&e3. {player3} - {score3}"

# Messages
messages:
  prefix: "&7[&6Parkour&7]&r "
  started: "&aStarted parkour!"
  checkpoint: "&aCheckpoint {number} reached!"
  fell: "&cYou fell! Jumps: {jumps}"
  new-record: "&6&lNEW RECORD! {jumps} jumps!"
```

## Dependencies

- ServerAPI
- DecentHolograms (soft)
- PlaceholderAPI (soft)
