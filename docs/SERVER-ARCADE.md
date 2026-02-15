# ServerArcade

**Version:** 1.0-SNAPSHOT
**Type:** Casino & Gambling System
**Main Class:** `net.serverplugins.arcade.ServerArcade`

## Overview

ServerArcade provides a comprehensive casino system with slot machines, blackjack, crash, coinflip, jackpot, and dice games. Features include physical arcade machines, hologram integration, gambling controls, and migration tools from other casino plugins.

## Key Features

### Casino Games

#### Slot Machines
- Traditional 3-reel slots
- Configurable symbols and payouts
- Progressive jackpot
- Animation effects

#### Blackjack
- Standard blackjack rules
- Dealer AI
- Split and double-down
- Configurable house rules

#### Crash
- Multiplier crash game
- Real-time graph
- Cash-out system
- Provably fair

#### Coinflip
- Player vs player betting
- Heads or tails
- Configurable odds
- Match-making system

#### Jackpot
- Lottery-style game
- Entry tickets
- Multiple prize tiers
- Automatic drawings

#### Dice
- Bet on outcomes (over/under/exact/odd/even)
- Configurable odds
- Multiple betting options
- History tracking

### Physical Arcade Machines
- Place machines in the world
- Hologram displays above machines
- Click to play
- Multi-machine support

### Gambling Controls
- Self-exclusion system
- Loss limits
- Session time limits
- Statistics tracking

### Migration Tool
- Import from DreamArcade
- Preserve player balances
- Convert game history

## Commands

| Command | Permission | Description |
|---------|-----------|-------------|
| `/arcade` | `serverarcade.use` | Open arcade menu |
| `/casino` | `serverarcade.use` | Alias |
| `/games` | `serverarcade.use` | Alias |
| `/gamble` | `serverarcade.use` | Alias |
| `/slots <bet>` | `serverarcade.slots` | Play slots |
| `/blackjack <bet>` | `serverarcade.blackjack` | Play blackjack |
| `/crash <bet>` | `serverarcade.crash` | Play crash |
| `/coinflip <bet>` | `serverarcade.coinflip` | Play coinflip |
| `/jackpot` | `serverarcade.jackpot` | Join jackpot |
| `/dice bet <amt> <type>` | `serverarcade.dice` | Bet on dice |
| `/gamble stats` | `serverarcade.stats` | View stats |
| `/gamble exclude` | `serverarcade.exclude` | Self-exclusion |
| `/arcademachine place <type>` | `serverarcade.machine.place` | Place machine |
| `/arcademachine remove` | `serverarcade.machine.remove` | Remove machine |
| `/arcademachine list` | `serverarcade.machine.list` | List machines |
| `/arcademigrate [path]` | `serverarcade.migrate` | Migration tool |

## Configuration

### config.yml
```yaml
# General Settings
arcade:
  enabled-games:
    - slots
    - blackjack
    - crash
    - coinflip
    - jackpot
    - dice

  house-edge: 0.05  # 5% house edge

# Slots
slots:
  min-bet: 10.0
  max-bet: 10000.0
  symbols:
    CHERRY: 0.30  # 30% chance
    LEMON: 0.25
    ORANGE: 0.20
    BELL: 0.15
    SEVEN: 0.08
    DIAMOND: 0.02

  payouts:
    CHERRY-CHERRY-CHERRY: 5.0  # 5x bet
    LEMON-LEMON-LEMON: 10.0
    ORANGE-ORANGE-ORANGE: 25.0
    BELL-BELL-BELL: 50.0
    SEVEN-SEVEN-SEVEN: 100.0
    DIAMOND-DIAMOND-DIAMOND: 1000.0

  jackpot:
    enabled: true
    contribution: 0.01  # 1% of each bet goes to jackpot
    starting-value: 10000.0

# Blackjack
blackjack:
  min-bet: 50.0
  max-bet: 50000.0
  payout: 2.0  # 2x bet for win
  blackjack-payout: 2.5  # 2.5x for natural blackjack
  allow-split: true
  allow-double-down: true
  dealer-hits-soft-17: false

# Crash
crash:
  min-bet: 10.0
  max-bet: 10000.0
  min-multiplier: 1.0
  max-multiplier: 100.0
  crash-chance: 0.01  # 1% chance per 0.1x

# Coinflip
coinflip:
  min-bet: 100.0
  max-bet: 100000.0
  house-edge: 0.02  # 2% fee
  timeout: 120  # Seconds to accept match

# Jackpot
jackpot:
  ticket-cost: 100.0
  draw-interval: 3600  # 1 hour
  max-tickets-per-player: 100
  prize-distribution:
    first: 0.5  # 50%
    second: 0.3  # 30%
    third: 0.2  # 20%

# Dice
dice:
  min-bet: 10.0
  max-bet: 10000.0
  payouts:
    over: 2.0  # Bet dice is over 3.5
    under: 2.0  # Bet dice is under 3.5
    exact: 6.0  # Bet exact number
    odd: 2.0
    even: 2.0

# Arcade Machines
machines:
  enabled: true
  holograms:
    enabled: true
    lines:
      - "&6&l{game}"
      - "&7Click to play"
      - "&7Min: ${min} Max: ${max}"

# Gambling Controls
controls:
  self-exclusion:
    enabled: true
    durations: [24, 168, 720]  # Hours (1d, 1w, 1m)

  loss-limits:
    enabled: true
    daily: 50000.0
    weekly: 200000.0
    monthly: 500000.0

  session-limits:
    enabled: true
    max-duration: 7200  # 2 hours
    warning-interval: 1800  # 30 min warnings

# Statistics
stats:
  track-games: true
  track-winnings: true
  leaderboards: true

# Messages
messages:
  prefix: "&7[&6Arcade&7]&r "
  slots-win: "&aYou won ${amount}!"
  slots-jackpot: "&6&l⭐ JACKPOT! You won ${amount}! ⭐"
  blackjack-win: "&aBlackjack! You won ${amount}!"
  crash-cashed-out: "&aCashed out at {multiplier}x for ${amount}!"
  coinflip-won: "&aYou won the coinflip! +${amount}"
  self-excluded: "&cYou are self-excluded from gambling."
  loss-limit-reached: "&cYou have reached your loss limit!"
```

## Database Schema

```sql
CREATE TABLE IF NOT EXISTS server_arcade_stats (
    uuid VARCHAR(36) PRIMARY KEY,
    games_played INT DEFAULT 0,
    total_wagered DECIMAL(20, 2) DEFAULT 0,
    total_won DECIMAL(20, 2) DEFAULT 0,
    total_lost DECIMAL(20, 2) DEFAULT 0,
    biggest_win DECIMAL(20, 2) DEFAULT 0,
    jackpots_won INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS server_arcade_game_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL,
    game VARCHAR(32) NOT NULL,
    bet DECIMAL(20, 2) NOT NULL,
    result VARCHAR(16) NOT NULL,  # WIN, LOSE, PUSH
    payout DECIMAL(20, 2) DEFAULT 0,
    details JSON,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX (uuid, timestamp DESC)
);

CREATE TABLE IF NOT EXISTS server_arcade_jackpot (
    id INT AUTO_INCREMENT PRIMARY KEY,
    amount DECIMAL(20, 2) NOT NULL,
    winner_uuid VARCHAR(36),
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS server_arcade_machines (
    id INT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    placed_by VARCHAR(36) NOT NULL,
    placed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY (world, x, y, z)
);

CREATE TABLE IF NOT EXISTS server_arcade_exclusions (
    uuid VARCHAR(36) PRIMARY KEY,
    excluded_until TIMESTAMP NOT NULL,
    reason VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS server_arcade_limits (
    uuid VARCHAR(36) PRIMARY KEY,
    daily_loss DECIMAL(20, 2) DEFAULT 0,
    weekly_loss DECIMAL(20, 2) DEFAULT 0,
    monthly_loss DECIMAL(20, 2) DEFAULT 0,
    last_reset_daily TIMESTAMP,
    last_reset_weekly TIMESTAMP,
    last_reset_monthly TIMESTAMP
);
```

## Dependencies

- ServerAPI
- Vault
- DecentHolograms
- PlaceholderAPI (soft)
