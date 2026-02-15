# ServerBounty

**Version:** 1.0-SNAPSHOT
**Type:** Player Bounty System
**Main Class:** `net.serverplugins.bounty.ServerBounty`

## Overview

ServerBounty allows players to place bounties on other players, creating a bounty hunting system. Features include trophy heads, tax system, cooldowns, combat integration, and cross-server synchronization.

## Key Features

### Bounty System
- Place bounties on players
- Claim rewards for kills
- Bounty stacking (multiple bounties on one player)
- Leaderboards (most wanted)

### Trophy Heads
- Receive player head on bounty kill
- Collectible trophies
- Custom lore showing bounty amount

### Tax System
- Configurable tax on bounty placement
- Prevent spam bounties
- Server economy sink

### Cooldown System
- Prevent bounty spam
- Per-player cooldowns
- Configurable durations

### Combat Integration
- CombatLogX integration
- Only count kills in valid combat
- Prevent combat logging abuse

### Cross-Server Sync
- Redis integration via server-bridge
- Bounties sync across servers
- Claims sync in real-time

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/bounty [player] [amount]` | `serverbounty.place` | Place bounty |
| `/bounties` | `serverbounty.list` | List active bounties |
| `/wanted` | `serverbounty.list` | Alias |
| `/hunt` | `serverbounty.list` | Alias |
| `/hitlist` | `serverbounty.list` | Alias |

## Configuration

### config.yml
```yaml
# Bounty Settings
bounties:
  min-amount: 1000.0
  max-amount: 1000000.0
  tax: 0.10  # 10% tax on placement
  allow-self-bounty: false
  expire-after: 604800  # 7 days in seconds

# Trophy Heads
trophy-heads:
  enabled: true
  give-on-claim: true
  custom-texture: true  # Use player skin
  lore:
    - "&7Bounty: ${amount}"
    - "&7Claimed by {hunter}"
    - "&7Date: {date}"

# Cooldowns
cooldowns:
  place-bounty: 300  # 5 minutes
  claim-bounty: 60  # 1 minute

# Combat
combat:
  require-combat: false  # Must be in combat to claim
  combatlogx-integration: true
  pvp-world-only: false  # Only in PvP worlds

# Leaderboards
leaderboards:
  enabled: true
  top-count: 10
  update-interval: 300  # 5 minutes

# Cross-Server
cross-server:
  enabled: true  # Requires server-bridge
  sync-placements: true
  sync-claims: true

# Messages
messages:
  prefix: "&7[&cBounty&7]&r "
  bounty-placed: "&aPlaced ${amount} bounty on {player}! (Tax: ${tax})"
  bounty-claimed: "&aYou claimed ${amount} bounty on {player}!"
  bounty-notification: "&c{player} placed a ${amount} bounty on you!"
  bounty-expired: "&cBounty on {player} has expired."
  no-bounties: "&cNo active bounties!"
  on-cooldown: "&cPlease wait {time} before placing another bounty."
  insufficient-funds: "&cInsufficient funds! Need ${amount} + ${tax} tax."
```

## Database Schema

```sql
CREATE TABLE IF NOT EXISTS server_bounties (
    id INT AUTO_INCREMENT PRIMARY KEY,
    target_uuid VARCHAR(36) NOT NULL,
    placer_uuid VARCHAR(36) NOT NULL,
    amount DECIMAL(20, 2) NOT NULL,
    placed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    claimed BOOLEAN DEFAULT FALSE,
    claimed_by VARCHAR(36),
    claimed_at TIMESTAMP NULL,
    INDEX (target_uuid, claimed),
    INDEX (claimed, expires_at)
);

CREATE TABLE IF NOT EXISTS server_bounty_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    target_uuid VARCHAR(36) NOT NULL,
    placer_uuid VARCHAR(36) NOT NULL,
    claimer_uuid VARCHAR(36),
    amount DECIMAL(20, 2) NOT NULL,
    placed_at TIMESTAMP NOT NULL,
    claimed_at TIMESTAMP,
    expired BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS server_bounty_cooldowns (
    uuid VARCHAR(36) PRIMARY KEY,
    last_placement TIMESTAMP NULL,
    last_claim TIMESTAMP NULL
);

CREATE TABLE IF NOT EXISTS server_bounty_stats (
    uuid VARCHAR(36) PRIMARY KEY,
    bounties_placed INT DEFAULT 0,
    bounties_claimed INT DEFAULT 0,
    total_paid DECIMAL(20, 2) DEFAULT 0,
    total_earned DECIMAL(20, 2) DEFAULT 0
);
```

## Dependencies

- ServerAPI
- CombatLogX (soft)
- Vault
- server-bridge (soft for cross-server)
