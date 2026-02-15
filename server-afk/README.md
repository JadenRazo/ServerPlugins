# ServerAFK Plugin

Sophisticated AFK zone system for Paper/Spigot servers with configurable rewards, anti-exploit detection, and comprehensive player tracking.

## Table of Contents

- [Overview](#overview)
- [Quick Start Guide](#quick-start-guide)
- [Commands Reference](#commands-reference)
- [Permissions](#permissions)
- [Configuration Guide](#configuration-guide)
- [Reward Configuration](#reward-configuration)
- [Y-Level Configuration](#y-level-configuration)
- [Integration](#integration)
- [Troubleshooting](#troubleshooting)
- [Advanced Features](#advanced-features)

---

## Overview

### Features

**Core Functionality:**
- Create custom AFK zones with defined boundaries
- Configurable reward intervals and types
- Reward chance percentage control (0-100%)
- Y-level configuration for vertical zone boundaries
- Rank-based reward multipliers (LuckPerms integration)

**Advanced Systems:**
- Anti-exploit pattern detection with suspicion tracking
- Player verification system to prevent automation
- Combat detection (CombatLogX integration)
- Comprehensive statistics and leaderboards
- Session history and activity logging
- DecentHolograms integration for zone displays

**Reward Types:**
- **CURRENCY** - Vault economy integration
- **ITEM** - Direct item rewards
- **COMMAND** - Execute console commands
- **XP** - Experience points

### System Requirements

- **Server**: Paper 1.21.4+ or Spigot 1.21.4+
- **Java**: 21+
- **Required Dependencies**:
  - Vault (for economy rewards)
  - LuckPerms (for permission groups and rank multipliers)
- **Optional Dependencies**:
  - DecentHolograms (for zone holograms)
  - PlaceholderAPI (for placeholder support)
  - CombatLogX (for combat detection)

### Database Support

- **H2** - Embedded database (default, no setup required)
- **MariaDB/MySQL** - External database for multi-server setups

---

## Quick Start Guide

### Method 1: Position Selection (Traditional)

1. Stand at the first corner of your desired zone
   ```
   /wa p1
   ```

2. Stand at the opposite corner
   ```
   /wa p2
   ```

3. Open the admin GUI
   ```
   /wa
   ```

4. Click "Create Zone" and configure:
   - Zone name
   - Time interval (seconds between rewards)
   - Add rewards (click on reward types)

5. (Optional) Add a hologram to the zone:
   - Stand in the zone
   - Run `/wa holo`

### Method 2: Quick Create (Recommended)

Create a zone at your current location with a single command:

```
/wa quickcreate <name> [radius] [height]
```

**Examples:**

```bash
# 21x21x21 zone (default)
/wa quickcreate "Mining Zone"

# 31x31x31 zone (15 block radius)
/wa quickcreate "Farming Area" 15

# 31x21x31 zone (15 radius, 10 height = ±10 blocks vertically)
/wa quickcreate "Fishing Spot" 15 10
```

**How it works:**
- `radius` creates a square zone extending `radius` blocks in all horizontal directions
- `height` creates vertical bounds of ±`height` blocks from your Y position
- Final size is `(radius*2+1)` × `(height*2+1)` × `(radius*2+1)`
- Example: radius=10, height=5 creates a 21×11×21 block zone

---

## Commands Reference

### Admin Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/wa` | Open admin GUI | `server.afk.admin` |
| `/wa p1` | Set first corner position | `server.afk.admin` |
| `/wa p2` | Set second corner position | `server.afk.admin` |
| `/wa quickcreate <name> [radius] [height]` | Create zone at current location | `server.afk.admin` |
| `/wa holo` | Add hologram to zone at current location | `server.afk.admin` |
| `/wa reload` | Reload plugin configuration | `server.afk.admin` |
| `/wa stats <player>` | View player statistics | `server.afk.admin` |

### Player Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/afk` | Toggle manual AFK status | `server.afk.use` |
| `/afk stats` | View your own statistics | `server.afk.use` |
| `/afk top` | View AFK leaderboard | `server.afk.use` |

### Tab Completion

All commands support tab completion for:
- Player names
- Zone names
- Numeric values (radius/height)

---

## Permissions

### Admin Permissions

```yaml
server.afk.admin:
  description: Full admin access to AFK zones
  default: op
  children:
    - server.afk.admin.create
    - server.afk.admin.delete
    - server.afk.admin.edit
    - server.afk.admin.hologram
    - server.afk.admin.reload
    - server.afk.admin.stats
```

### Player Permissions

```yaml
server.afk.use:
  description: Use AFK zones and commands
  default: true

server.afk.bypass:
  description: Bypass AFK zone restrictions
  default: false

server.afk.bypass.combat:
  description: Earn rewards while in combat
  default: false
```

### Recommended Setup

For most servers:

```yaml
groups:
  default:
    permissions:
      - server.afk.use

  moderator:
    permissions:
      - server.afk.admin.stats

  admin:
    permissions:
      - server.afk.admin
```

---

## Configuration Guide

### Database Setup

#### H2 (Default)

No configuration needed! Data stored in `plugins/ServerAFK/afkzones.db`.

```yaml
database:
  type: H2
  file: afkzones
```

#### MariaDB/MySQL

For multi-server setups:

```yaml
database:
  type: MARIADB
  host: 127.0.0.1
  port: 3306
  name: minecraft
  username: minecraft_user
  password: "your_password_here"
```

**Important:** The plugin automatically creates all required tables on startup.

### Rank Multipliers Setup

Reward multipliers based on LuckPerms groups:

```yaml
rank-multipliers:
  default: 1.0        # No bonus
  server: 1.25        # +25% rewards
  serverplus: 1.5     # +50% rewards
  servermaster: 2.0   # 2x rewards
  servergoat: 3.0     # 3x rewards
```

**How it works:**
- Plugin checks player's primary group in LuckPerms
- Multiplier applies to CURRENCY rewards only
- Example: 100 coins × 2.0 multiplier = 200 coins
- Falls back to `default` if group not found

### Anti-Exploit Configuration

```yaml
anti-exploit:
  enabled: true
  analysis-interval-seconds: 120     # Check every 2 minutes
  min-movement-threshold: 5.0        # Minimum blocks moved
  max-repeated-patterns: 5           # Max identical movement patterns
  suspicion-threshold: 3             # Points before flagging
  reset-suspicion-after-minutes: 30  # Reset clean players
```

**Tuning tips:**
- Increase `min-movement-threshold` for more lenient detection
- Increase `suspicion-threshold` to reduce false positives
- Decrease `max-repeated-patterns` for stricter detection

### Verification System Tuning

```yaml
verification:
  enabled: true
  interval-seconds: 900              # Verify every 15 minutes
  trigger-level: 2                   # Suspicion level to trigger
  timeout-seconds: 120               # 2 minutes to respond
  penalty-disable-minutes: 30        # Penalty for failed verification
```

**How it works:**
- System sends math problems to players
- Players must respond in chat within timeout
- Failed verifications increase suspicion
- Too many failures result in temporary zone ban

### Message Customization

All messages use MiniMessage format. Edit in `config.yml`:

```yaml
messages:
  zone-enter: "<green>Entered AFK zone: <gold>{zone}"
  zone-exit: "<gray>Left AFK zone"
  reward-received: "<green>✓ Received reward! <gold>+{amount}"
  combat-blocked: "<red>Cannot earn rewards while in combat!"
  verification-prompt: "<yellow>⚠ Verification required! Solve: {problem}"
```

**Color Codes:**
- `<red>`, `<green>`, `<blue>`, `<yellow>`, `<gold>`, etc.
- `<bold>`, `<italic>`, `<underline>`
- `<gradient:#FF0000:#00FF00>Text</gradient>`
- Hex colors: `<#FF5733>Text`

---

## Reward Configuration

### Reward Types Overview

#### 1. CURRENCY Rewards

Give money via Vault economy:

```yaml
# In GUI: Click "Add Reward" > "Currency"
Type: CURRENCY
Amount: 100
Chance: 100%
```

**GUI Configuration:**
1. Click reward to edit amount
2. Shift+Click to edit chance percentage
3. Supports decimals (e.g., 0.50 for $0.50)

**With Rank Multipliers:**
- Default player: 100 coins
- Server rank (1.25x): 125 coins
- ServerGoat rank (3.0x): 300 coins

#### 2. ITEM Rewards

Give items to player inventory:

```yaml
# In GUI: Click "Add Reward" > "Item"
Type: ITEM
Item: Hold item in hand when creating
Chance: 50%
```

**How to configure:**
1. Hold the item you want to give in your main hand
2. Click "Add Reward" > "Item" in the GUI
3. Item is serialized with NBT data (enchantments, lore, etc.)
4. Shift+Click to edit chance percentage

**Important:** Inventory space is checked before giving items. If full, reward is skipped (not lost).

#### 3. COMMAND Rewards

Execute console commands:

```yaml
# In GUI: Click "Add Reward" > "Command"
Type: COMMAND
Command: "give %player% diamond 1"
Chance: 10%
```

**Available Placeholders:**
- `%player%` - Player's username
- `%uuid%` - Player's UUID
- `%zone%` - Zone name

**Examples:**

```bash
# Give permission temporarily
lp user %player% permission settemp some.perm true 1h

# Custom command from another plugin
customreward give %player% rare_crate

# Broadcast message
broadcast %player% found a rare reward!

# Multiple commands (separate with semicolons in database)
give %player% diamond 1; tell %player% Lucky!
```

#### 4. XP Rewards

Give experience points:

```yaml
# In GUI: Click "Add Reward" > "XP"
Type: XP
Amount: 50
Chance: 100%
```

**Note:** Experience is given as XP points, not levels. Use online XP calculators to convert levels to points.

### Chance Percentage System

Control reward probability with the chance percentage field:

**Configuration via GUI:**
1. Click on any reward in the zone editor
2. Shift+Click the reward
3. Type the chance percentage (0-100)
4. Press Enter

**How it works:**
- Each reward is rolled independently every interval
- 100% = guaranteed every time
- 50% = 50/50 chance
- 1% = 1 in 100 chance
- 0% = disabled (never gives)

**Example Setups:**

**Guaranteed Base + Rare Jackpot:**
```
Reward 1: 100 coins (100% chance)
Reward 2: 5000 coins (5% chance)
Result: Always get 100, occasionally get 5000 bonus
```

**Item Lottery:**
```
Reward 1: Common Crate (70% chance)
Reward 2: Uncommon Crate (25% chance)
Reward 3: Rare Crate (5% chance)
Result: One reward per interval, weighted by chance
```

**Multiple Guaranteed Rewards:**
```
Reward 1: 50 coins (100% chance)
Reward 2: 10 XP (100% chance)
Reward 3: Bonus command (20% chance)
Result: Always get coins+XP, sometimes get bonus
```

---

## Y-Level Configuration

### Understanding Y-Levels

AFK zones have vertical boundaries defined by minimum and maximum Y coordinates.

**Zone Boundaries:**
- **minY** - Lowest Y level included in zone
- **maxY** - Highest Y level included in zone
- **Inclusive** - Both minY and maxY blocks are part of the zone

**Example:**
```
Zone from Y=60 to Y=80:
- Standing at Y=60: IN ZONE ✓
- Standing at Y=70: IN ZONE ✓
- Standing at Y=80: IN ZONE ✓
- Standing at Y=59: OUT OF ZONE ✗
- Standing at Y=81: OUT OF ZONE ✗
```

### Setting Y-Levels

#### Method 1: Position Selection

Y-levels are automatically captured from your position:

```bash
# Stand at Y=50 (bottom corner)
/wa p1

# Stand at Y=80 (top corner)
/wa p2

# Zone created with minY=50, maxY=80 (31 blocks tall)
```

**Tip:** You don't need to be at exact corners - the plugin automatically sorts min/max values.

#### Method 2: Quick Create Command

Use the `height` parameter:

```bash
# Standing at Y=64, create ±10 blocks vertically
/wa quickcreate "Farm" 15 10

# Result: Zone from Y=54 to Y=74 (21 blocks tall)
```

**Height Calculation:**
- `height` parameter sets ±height from your Y position
- Total vertical size = `height × 2 + 1`
- Examples:
  - height=5 → 11 blocks tall (±5 from center)
  - height=10 → 21 blocks tall (±10 from center)
  - height=20 → 41 blocks tall (±20 from center)

### Y-Axis Forgiveness

Allow players to jump without leaving the zone:

```yaml
defaults:
  y-axis-forgiveness: 3  # Blocks
```

**How it works:**
- Player enters zone at Y=70 (within minY=60, maxY=80)
- Player jumps to Y=83 (3 blocks above maxY)
- Still considered in zone due to forgiveness
- Prevents accidental zone exits from jumping

**Recommended Values:**
- **3 blocks** - Default, handles normal jumping
- **5 blocks** - For areas with uneven terrain
- **0 blocks** - Strict mode, no forgiveness

### Common Y-Level Scenarios

#### Underground Mining Zone

```bash
# Create zone at Y=-50 to Y=-30 (20 blocks tall)
/wa p1  # At Y=-50
/wa p2  # At Y=-30
```

#### Skyblock Platform

```bash
# Create zone at Y=100 to Y=105 (5 blocks tall)
/wa quickcreate "Skyblock" 20 2
# Creates 41x5x41 platform zone
```

#### Multi-Floor Building

```bash
# Ground floor: Y=64 to Y=70
/wa quickcreate "Ground Floor" 10 3

# Second floor: Y=74 to Y=80
/wa quickcreate "Second Floor" 10 3
```

#### Ocean Floor Zone

```bash
# Create zone at ocean floor (Y=40 to Y=50)
/wa p1  # At Y=40 (ocean floor)
/wa p2  # At Y=50 (mid-water)
```

### Viewing Zone Y-Levels

Open the zone editor GUI (`/wa`) and view zone information:

```
Zone Boundaries:
Min: -52, -23, 134
Max: -12, -13, 174
Size: 41 × 11 × 41  (X × Y × Z)
```

The Y component (11 in example) shows the vertical height of the zone.

---

## Integration

### DecentHolograms Integration

**Setup:**

1. Install DecentHolograms plugin
2. Create AFK zone
3. Stand in the zone
4. Run `/wa holo`

**Hologram displays:**
- Zone name
- Reward interval
- Active player count
- Custom formatting from config

**Configuration:**

```yaml
holograms:
  update-interval: 60  # Update every 60 seconds
  lines:
    - "&e&l{zone_name}"
    - "&7━━━━━━━━━━━━━━━━"
    - "&fRewards every &e{interval}s"
    - "&7━━━━━━━━━━━━━━━━"
    - "&aPlayers: &f{count}"
```

**Available Placeholders:**
- `{zone_name}` - Name of the zone
- `{interval}` - Reward interval in seconds
- `{count}` - Number of active players

**Managing Holograms:**
- Use `/dh` commands to edit/delete holograms
- Holograms persist through restarts
- Update automatically when zone settings change

### PlaceholderAPI Integration

**Installation:**

1. Install PlaceholderAPI
2. Restart server
3. Placeholders automatically registered

**Available Placeholders:**

**Player Statistics:**
```
%server_afk_total_time%          - Total AFK time (formatted)
%server_afk_total_rewards%       - Total rewards received
%server_afk_total_currency%      - Total currency earned
%server_afk_total_xp%            - Total XP earned
%server_afk_sessions%            - Total sessions completed
%server_afk_longest_session%     - Longest session time
%server_afk_current_streak%      - Current daily streak
%server_afk_favorite_zone%       - Most used zone name
```

**Session Information:**
```
%server_afk_is_afk%              - true/false if player is AFK
%server_afk_current_time%        - Current session time
%server_afk_current_zone%        - Current zone name
%server_afk_session_rewards%     - Rewards this session
%server_afk_session_currency%    - Currency this session
```

**Leaderboard:**
```
%server_afk_top_time_<rank>%     - Top player by time (rank 1-10)
%server_afk_top_rewards_<rank>%  - Top player by rewards
```

**Usage Examples:**

TAB plugin scoreboard:
```yaml
scoreboard:
  lines:
    - "&eAFK Stats:"
    - "&fTime: &a%server_afk_total_time%"
    - "&fRewards: &a%server_afk_total_rewards%"
```

EssentialsX MOTD:
```
Welcome {PLAYER}!
AFK Time: %server_afk_total_time%
Current Streak: %server_afk_current_streak% days
```

### CombatLogX Integration

**Purpose:** Prevent AFK farming while in PvP combat

**How it works:**
1. Player enters combat (CombatLogX detection)
2. AFK rewards are paused
3. Combat ends (configurable timer)
4. AFK rewards resume

**Configuration:**

```yaml
combat-detection:
  enabled: true
  check-interval: 5  # Check combat status every 5 seconds
```

**Soft Dependency:** Plugin works without CombatLogX, just skips combat checks.

**Bypass Permission:**
```yaml
server.afk.bypass.combat: true
```

Players with this permission can earn rewards in combat (useful for staff testing).

---

## Troubleshooting

### Common Issues

#### 1. Rewards Not Working

**Symptoms:** Players in zone but not receiving rewards

**Checklist:**
- [ ] Check reward interval: `/wa` > Edit Zone > View interval setting
- [ ] Verify player is actually in zone: Check Y-level boundaries
- [ ] Check reward chance: Shift+Click rewards to see percentages
- [ ] View console for errors: Look for economy/permission errors
- [ ] Test with different reward types: Try CURRENCY vs ITEM

**Solutions:**

```bash
# Check if Vault is loaded
/plugins

# Check player's balance (test economy)
/balance <player>

# Check zone boundaries
/wa
# Click zone > View Min/Max coords
```

#### 2. Database Problems

**H2 Database Corruption:**

```bash
# Stop server
# Backup current database
cp plugins/ServerAFK/afkzones.db plugins/ServerAFK/afkzones.db.backup

# Delete corrupted database (will recreate)
rm plugins/ServerAFK/afkzones.db

# Start server (fresh database created)
```

**MariaDB Connection Issues:**

```yaml
# Verify credentials in config.yml
database:
  type: MARIADB
  host: 127.0.0.1  # Check this is correct
  port: 3306       # Default MySQL port
  name: minecraft  # Database must exist
  username: user   # User must have permissions
  password: pass   # Check for special characters
```

**Test Connection:**
```bash
mysql -u username -p -h host -P port database_name
```

#### 3. Hologram Not Showing

**Checklist:**
- [ ] DecentHolograms installed and running: `/plugins`
- [ ] Standing IN the zone when running `/wa holo`
- [ ] Hologram update interval not too long
- [ ] No world guard flags blocking holograms

**Manual Hologram Creation:**

```bash
# List all holograms
/dh list

# Create hologram at your location
/dh create test_holo

# Add lines
/dh line add test_holo &eTest Line

# Delete if needed
/dh delete test_holo
```

#### 4. Anti-Exploit False Positives

**Symptoms:** Legitimate players flagged as suspicious

**Solutions:**

```yaml
# Increase suspicion threshold (default: 3)
anti-exploit:
  suspicion-threshold: 5  # More lenient

# Increase movement threshold (default: 5.0)
  min-movement-threshold: 8.0  # Require more movement

# Decrease pattern sensitivity (default: 5)
  max-repeated-patterns: 8  # Allow more repetition
```

**Reset Player Suspicion:**

```bash
# Via database (H2)
java -jar h2*.jar
# Connect to plugins/ServerAFK/afkzones.db
# Run: UPDATE player_sessions SET suspicion_level = 0;
```

#### 5. Y-Level Issues

**Problem:** Players entering zone but immediately leaving

**Cause:** Y-level boundaries too strict or incorrect

**Solutions:**

```yaml
# Increase y-axis-forgiveness
defaults:
  y-axis-forgiveness: 5  # Up from 3
```

**Check Zone Bounds:**
```bash
/wa
# Click zone
# Note Min Y and Max Y
# Compare with player's Y coordinate (F3)
```

**Recreate Zone with Correct Y-Levels:**
```bash
# Delete old zone
/wa
# Click zone > Delete

# Stand at correct Y-level
/wa quickcreate "Zone Name" 15 10
```

---

## Advanced Features

### Anti-Exploit Pattern Detection

**How it works:**

The system tracks player behavior to detect AFK farming bots:

1. **Movement Tracking**
   - Records last 20 locations
   - Calculates average movement distance
   - Detects repeated identical patterns

2. **Suspicious Behaviors**
   - Standing perfectly still (< 5 blocks movement over 2 minutes)
   - Repeated exact movement patterns (5+ identical loops)
   - Camera not moving (pitch/yaw unchanged)
   - Suspicious timing (exactly 60s intervals)

3. **Suspicion Levels**
   - **Level 1** - Minor concern, logged
   - **Level 2** - Verification prompt triggered
   - **Level 3+** - Flagged for admin review

4. **Actions Taken**
   - Send verification prompt (math problem)
   - Notify admins if threshold exceeded
   - Log all suspicious activity
   - Optionally auto-kick

**Configuration:**

```yaml
anti-exploit:
  enabled: true
  analysis-interval-seconds: 120  # Check every 2 min
  min-movement-threshold: 5.0     # Min blocks moved
  max-repeated-patterns: 5        # Max identical patterns
  suspicion-threshold: 3          # Flag level
  reset-suspicion-after-minutes: 30  # Reset timer
  notify-admins: true             # Alert staff
  auto-kick-level: 5              # Auto-kick at level 5
```

**Admin Notifications:**

Staff with `server.afk.admin` receive alerts:
```
[ServerAFK] Player123 flagged suspicious (Level 3)
Reason: Repeated movement pattern detected
```

### Verification System

**Purpose:** Confirm human players are present

**How it works:**

1. Player reaches suspicion trigger level (default: 2)
2. Math problem sent in chat: "Solve: 7 + 4 = ?"
3. Player has 120 seconds to respond
4. Correct answer: Suspicion reset, rewards continue
5. Wrong/no answer: Suspicion increased, penalties applied

**Penalties:**

```yaml
verification:
  penalty-disable-minutes: 30  # Ban from zone
```

Failed verifications:
- **1st fail** - Warning message
- **2nd fail** - 30 min zone ban
- **3rd fail** - Admin notification + extended ban

**Bypass:**

```yaml
permissions:
  server.afk.bypass.verification: true
```

Staff/VIP ranks can bypass verification (useful for AFKing legitimately).

### Statistics Tracking

**Data Tracked:**

Per-player statistics:
- Total AFK time (seconds)
- Total rewards received
- Total currency earned
- Total XP earned
- Sessions completed
- Longest session
- Current/best daily streak
- Favorite zone
- First/last AFK time

**Viewing Stats:**

```bash
# Your own stats
/afk stats

# Other players (admin)
/wa stats PlayerName
```

**Leaderboards:**

```bash
# Top 10 by total time
/afk top
```

**Database Access:**

Statistics stored in `player_stats` table:

```sql
-- Top 10 by time
SELECT * FROM player_stats ORDER BY total_afk_time_seconds DESC LIMIT 10;

-- Top earners
SELECT * FROM player_stats ORDER BY total_currency_earned DESC LIMIT 10;
```

### Session History

**Purpose:** Track all AFK sessions for auditing

**Data Stored:**
- Session start/end times
- Zone used
- Total time in session
- Rewards earned
- Currency earned
- Suspicion events
- Verification results

**Database:**

```sql
-- View player's recent sessions
SELECT * FROM afk_sessions
WHERE player_uuid = 'uuid-here'
ORDER BY started_at DESC
LIMIT 10;

-- View suspicious sessions
SELECT * FROM afk_sessions
WHERE suspicion_level >= 3
ORDER BY started_at DESC;
```

**Activity Log:**

Detailed event log in `activity_log` table:
- Movement events
- Reward events
- Verification attempts
- Suspicion changes

**Retention:**

Consider periodic cleanup for performance:

```sql
-- Delete sessions older than 90 days
DELETE FROM afk_sessions WHERE started_at < DATE_SUB(NOW(), INTERVAL 90 DAY);

-- Keep activity log smaller
DELETE FROM activity_log WHERE timestamp < DATE_SUB(NOW(), INTERVAL 30 DAY);
```

---

## Support

For issues, feature requests, or questions:

1. Check this documentation first
2. Review [Troubleshooting](#troubleshooting) section
3. Check console logs for error messages
4. Contact server administrators

## License

Part of the ServerPluginSuite - All rights reserved.
