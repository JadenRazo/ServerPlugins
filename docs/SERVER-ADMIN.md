# ServerAdmin

**Version:** 1.0-SNAPSHOT
**Type:** Staff Administration Tools
**Main Class:** `net.serverplugins.admin.ServerAdmin`

## Overview

ServerAdmin provides comprehensive staff tools including vanish modes, spectator tools, X-ray detection, freeze system, staff chat, alt detection, punishment GUI, player reset tools, and server control commands.

## Key Features

### Vanish System
- **Staff Vanish**: Hidden from normal players, visible to other staff
- **Full Vanish**: Completely hidden, even from staff
- Configurable join/quit messages
- Tab list hiding
- Silent chest opening

### Spectator Tools
- **Spectate**: Gamemode spectator with target tracking
- **POV**: View from player's perspective
- **Freecam**: Spectator mode without target

### X-Ray Detection
- Mining pattern analysis
- Suspicious ore discovery alerts
- Configurable detection thresholds
- Alert staff in real-time

### Freeze System
- Freeze players for inspection
- Prevent movement, combat, commands
- Inventory freezing
- Unfreeze command

### Staff Chat
- Private staff communication channel
- Toggle on/off
- Cross-server (with server-bridge)
- Formatted messages

### Alt Detection
- Check for alternate accounts
- IP-based detection
- History tracking
- Alert on suspicious alts

### Additional Tools
- Night vision toggle
- X-ray vision (admin tool)
- Disguise system
- Impersonation

### Punishment GUI
- GUI-based punishment system
- Escalation presets (warn → kick → tempban → ban)
- Quick-select reasons
- History integration

### Player Reset
- Reset claims
- Reset economy
- Reset playtime
- Reset rank
- Full player wipe

### Server Control
- Shutdown with countdown
- Restart with countdown
- Configurable warnings

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/vanish [mode]` | `serveradmin.vanish` | Toggle vanish |
| `/spectate <player>` | `serveradmin.spectate` | Spectate player |
| `/pov <player>` | `serveradmin.pov` | View player POV |
| `/freecam` | `serveradmin.freecam` | Freecam mode |
| `/xrayalerts` | `serveradmin.xray.alerts` | Toggle X-ray alerts |
| `/xraycheck <player>` | `serveradmin.xray.check` | Check player for X-ray |
| `/freeze <player>` | `serveradmin.freeze` | Freeze player |
| `/unfreeze <player>` | `serveradmin.freeze` | Unfreeze player |
| `/sc <message>` | `serveradmin.staffchat` | Staff chat |
| `/sctoggle` | `serveradmin.staffchat` | Toggle staff chat |
| `/alts <player>` | `serveradmin.alts` | Check for alts |
| `/nightvision` | `serveradmin.nightvision` | Toggle night vision |
| `/xray` | `serveradmin.xray.vision` | Toggle X-ray vision |
| `/punish <player>` | `serveradmin.punish` | Open punishment GUI |
| `/unpunish <player>` | `serveradmin.unpunish` | Remove punishment |
| `/history <player>` | `serveradmin.history` | View player history |
| `/reset <player> [type]` | `serveradmin.reset` | Reset player data |
| `/shutdown [seconds]` | `serveradmin.shutdown` | Shutdown server |
| `/restart [seconds]` | `serveradmin.restart` | Restart server |
| `/impersonate <player> <msg>` | `serveradmin.impersonate` | Send as player |
| `/disguise <name>` | `serveradmin.disguise` | Change display name |

## Configuration

### config.yml
```yaml
# Vanish Settings
vanish:
  staff-mode:
    hide-from-players: true
    hide-from-staff: false
    silent-chest: true
    no-pickup: true
    fake-quit: true
    fake-join-message: "&e{player} left the game"

  full-mode:
    hide-from-players: true
    hide-from-staff: true
    silent-chest: true
    no-pickup: true
    fake-quit: true

# X-Ray Detection
xray-detection:
  enabled: true
  threshold: 50  # % of valuable ores vs stone
  check-radius: 5  # Blocks around player
  alert-staff: true
  log-to-file: true

# Freeze System
freeze:
  prevent-movement: true
  prevent-combat: true
  prevent-commands: true
  prevent-inventory: false
  message: "&cYou have been frozen by staff!"

# Staff Chat
staff-chat:
  format: "&7[&cStaff&7] &r{player}&7: {message}"
  cross-server: true  # Requires server-bridge

# Alt Detection
alt-detection:
  enabled: true
  check-on-join: true
  alert-threshold: 2  # Accounts on same IP
  whitelist-ips: []

# Punishment Presets
punishment-presets:
  hacking:
    - warn
    - tempban 7d
    - ban

  toxicity:
    - warn
    - warn
    - mute 1d
    - tempban 3d
    - ban

# Player Reset
reset:
  confirm-required: true
  log-to-file: true

# Server Control
server-control:
  shutdown-countdown: 30  # Seconds
  restart-countdown: 30
  warnings: [30, 20, 10, 5, 3, 2, 1]
```

## Database Schema

```sql
CREATE TABLE IF NOT EXISTS server_admin_punishments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL,
    staff_uuid VARCHAR(36) NOT NULL,
    type ENUM('WARN', 'KICK', 'TEMPBAN', 'BAN', 'MUTE'),
    reason TEXT,
    duration VARCHAR(32),
    preset VARCHAR(64),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS server_admin_xray_stats (
    uuid VARCHAR(36) PRIMARY KEY,
    total_checks INT DEFAULT 0,
    suspicious_count INT DEFAULT 0,
    last_check TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS server_admin_alts (
    ip VARCHAR(45) NOT NULL,
    uuid VARCHAR(36) NOT NULL,
    username VARCHAR(16) NOT NULL,
    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ip, uuid),
    INDEX (uuid)
);
```

## Dependencies

- ServerAPI
- ProtocolLib
- NBTAPI (soft)
- server-bridge (soft for cross-server staff chat)
