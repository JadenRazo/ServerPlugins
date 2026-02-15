# ServerFilter

**Version:** 1.0-SNAPSHOT
**Type:** Chat Filtering System
**Main Class:** `net.serverplugins.filter.ServerFilter`

## Overview

ServerFilter provides personalized chat filtering with per-player filter levels, anti-spam, anti-caps, anti-advertising, and staff notifications for violations.

## Key Features

### Personalized Filtering
- **Strict**: Block most profanity and variations
- **Moderate**: Block common profanity
- **Relaxed**: Block only severe profanity
- **Minimal**: Nearly no filtering

### Anti-Spam
- Message repetition detection
- Configurable thresholds
- Auto-mute for spam

### Anti-Caps
- Excessive caps detection
- Auto-convert to lowercase
- Warning system

### Anti-Advertising
- IP address blocking
- Domain filtering
- Whitelist support

### Staff Notifications
- Alert staff to violations
- Configurable alert types
- Chat log integration

### Filter Bypass
- Permission-based bypass
- Staff override
- Whitelisted words

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/swearfilter [level]` | `serverfilter.use` | Set filter level |
| `/swearfilter status` | `serverfilter.use` | View current level |
| `/swearfilter reload` | `serverfilter.reload` | Reload config |
| `/filter` | `serverfilter.use` | Alias |
| `/chatfilter` | `serverfilter.use` | Alias |
| `/cf` | `serverfilter.use` | Alias |
| `/filteradmin reload` | `serverfilter.admin` | Admin reload |
| `/filteradmin stats` | `serverfilter.admin` | View statistics |
| `/filteradmin test <msg>` | `serverfilter.admin` | Test filter |

## Permissions

- `serverfilter.use` - Change filter level
- `serverfilter.bypass` - Bypass all filtering
- `serverfilter.reload` - Reload configuration
- `serverfilter.admin` - Admin commands

## Configuration

### config.yml
```yaml
# Filter Levels
levels:
  strict:
    name: "&cStrict"
    block-variations: true
    block-leet-speak: true
    sensitivity: high

  moderate:
    name: "&6Moderate"
    block-variations: true
    block-leet-speak: false
    sensitivity: medium

  relaxed:
    name: "&eRelaxed"
    block-variations: false
    block-leet-speak: false
    sensitivity: low

  minimal:
    name: "&aMinimal"
    block-variations: false
    block-leet-speak: false
    sensitivity: very-low

# Default Settings
default-level: moderate
allow-change: true
bypass-permission: "serverfilter.bypass"

# Word Lists
blocked-words:
  strict:
    - "word1"
    - "word2"
  moderate:
    - "word1"
  relaxed:
    - "severe1"
  minimal:
    - "extreme1"

whitelisted-words:
  - "assassin"  # Contains "ass" but is legitimate

# Anti-Spam
anti-spam:
  enabled: true
  identical-messages: 3  # Max repeated messages
  similar-threshold: 0.8  # 80% similarity
  time-window: 10  # Seconds
  action: "mute"  # mute, kick, or warn
  duration: 300  # 5 minutes

# Anti-Caps
anti-caps:
  enabled: true
  max-percentage: 70  # % of message in caps
  min-length: 5  # Min message length to check
  action: "convert"  # convert, block, or warn

# Anti-Advertising
anti-advertising:
  enabled: true
  block-ips: true
  block-domains: true
  whitelist:
    - "example.com"
    - "discord.gg/serverplugins"
  action: "block"  # block, warn, or mute

# Staff Notifications
staff-notifications:
  enabled: true
  alert-on-block: true
  alert-on-spam: true
  alert-on-advertising: true
  format: "&c[FILTER] &7{player}: {message}"
  permission: "serverfilter.alerts"

# Messages
messages:
  prefix: "&7[&6Filter&7]&r "
  message-blocked: "&cYour message was blocked by the filter."
  level-changed: "&aFilter level set to {level}"
  spam-detected: "&cPlease don't spam!"
  caps-warning: "&cPlease don't use excessive caps."
  advertising-blocked: "&cAdvertising is not allowed!"
```

## Database Schema

```sql
CREATE TABLE IF NOT EXISTS server_filter_settings (
    uuid VARCHAR(36) PRIMARY KEY,
    level VARCHAR(16) DEFAULT 'moderate'
);

CREATE TABLE IF NOT EXISTS server_filter_violations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL,
    type VARCHAR(32) NOT NULL,
    message TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX (uuid, timestamp DESC)
);
```

## Dependencies

- ServerAPI
- PlaceholderAPI (soft)
