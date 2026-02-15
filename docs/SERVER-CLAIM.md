# ServerClaim

**Version:** 1.0-SNAPSHOT
**Type:** Advanced Chunk Claiming System
**Main Class:** `net.serverplugins.claim.ServerClaim`

## Overview

ServerClaim is a comprehensive chunk-based claiming system with advanced features including claim profiles, custom permission groups, claim banks, upkeep system, level progression, nations, warfare, warps, and extensive customization options. It provides a complete land protection and management solution for survival servers.

## Key Features

### Core Claiming
- **Chunk-Based**: Claims are based on 16x16 chunks
- **Exponential Pricing**: Dynamic pricing that scales with claims owned
- **Profile System**: Multiple permission presets per claim
- **Custom Groups**: Define groups beyond visitor/member/owner
- **Claim Merging**: Merge adjacent chunks into single claims
- **Visual Boundaries**: Particle effects to visualize claim borders

### Claim Bank System
- **Separate Accounts**: Each claim has its own bank account
- **Transaction History**: Complete audit trail of all transactions
- **Deposit/Withdraw**: Member and owner-level banking
- **Upkeep Payments**: Automatic upkeep deductions from bank

### Upkeep System
- **Regular Costs**: Configurable maintenance fees per chunk
- **Grace Period**: Time before claim deletion for non-payment
- **Auto-Renewal**: Automatic payment from claim bank
- **Notifications**: Warn owners of upcoming upkeep

### Level System
- **Claim XP**: Earn experience for claim activities
- **Level Benefits**: Unlock perks at higher levels
- **Rewards**: Custom rewards per level
- **Progression Tracking**: View claim level and progress

### Nation System
- **Alliances**: Form alliances between claim owners
- **Nation Chat**: Private chat channel for nation members
- **Shared Resources**: Optional resource sharing
- **Relations**: Set relationships with other nations

### War System
- **Declare War**: Nation vs nation warfare
- **Capture Mechanics**: Capture enemy chunks
- **Tributes**: Peace agreements with resource payments
- **War History**: Track all wars and outcomes

### Claim Warps
- **Public/Private Warps**: Set warp points in your claim
- **Warp Costs**: Charge visitors to use warps
- **Visit Browser**: Browse all public claim warps
- **Warp Icons**: Custom icons for warps in GUI

### Advanced Features
- **Templates**: Save and load claim configurations
- **Notifications**: In-game notification system
- **Statistics**: Player and server-wide claim stats
- **Audit Logging**: Complete activity logs per claim
- **Refund System**: Admin refund tool for chunk pricing

## Commands

### Main Commands
| Command | Permission | Description |
|---------|-----------|-------------|
| `/claim [here\|unclaim\|info\|help]` | `serverclaim.claim` | Main claim menu |
| `/c` | `serverclaim.claim` | Alias for /claim |
| `/land` | `serverclaim.claim` | Alias for /claim |

### Admin Commands
| Command | Permission | Description |
|---------|-----------|-------------|
| `/claimadmin list [player]` | `serverclaim.admin` | List all claims |
| `/claimadmin info <claimId>` | `serverclaim.admin` | View claim details |
| `/claimadmin delete <claimId>` | `serverclaim.admin` | Delete a claim |
| `/claimadmin deleteat` | `serverclaim.admin` | Delete claim at location |
| `/claimrefund preview` | `serverclaim.admin.refund` | Preview refund amounts |
| `/claimrefund execute` | `serverclaim.admin.refund` | Execute refund |

### Nation Commands
| Command | Permission | Description |
|---------|-----------|-------------|
| `/nation create <name>` | `serverclaim.nation.create` | Create nation |
| `/nation invite <player>` | `serverclaim.nation.invite` | Invite to nation |
| `/nation accept` | `serverclaim.nation.accept` | Accept invitation |
| `/nation leave` | `serverclaim.nation.leave` | Leave nation |
| `/nation disband` | `serverclaim.nation.disband` | Disband nation |
| `/nation info [nation]` | `serverclaim.nation.info` | View nation info |
| `/nc <message>` | `serverclaim.nation.chat` | Nation chat |

### War Commands
| Command | Permission | Description |
|---------|-----------|-------------|
| `/war declare <nation>` | `serverclaim.war.declare` | Declare war |
| `/war surrender` | `serverclaim.war.surrender` | Surrender |
| `/war tribute <nation> <amount>` | `serverclaim.war.tribute` | Send tribute |
| `/war info` | `serverclaim.war.info` | View active wars |

### Utility Commands
| Command | Permission | Description |
|---------|-----------|-------------|
| `/playerwarps` | `serverclaim.warps` | Browse claim warps |
| `/pw` | `serverclaim.warps` | Alias for playerwarps |
| `/visit` | `serverclaim.warps` | Alias for playerwarps |
| `/claimnotifications` | `serverclaim.notifications` | View notifications |
| `/notifs` | `serverclaim.notifications` | Alias |
| `/claimlog [claimId]` | `serverclaim.log` | View activity logs |
| `/claimstats [player]` | `serverclaim.stats` | View statistics |
| `/claimshop` | `serverclaim.shop` | Purchase chunks |

## Configuration

### config.yml
```yaml
# Chunk Pricing
pricing:
  type: exponential  # or flat
  base-price: 100.0
  exponent: 1.05  # Price increases 5% per claim owned
  max-price: 100000.0

# Upkeep System
upkeep:
  enabled: true
  cost-per-chunk: 50.0
  interval: 86400  # 24 hours in seconds
  grace-period: 604800  # 7 days before deletion
  notify-threshold: 259200  # Notify 3 days before deletion

# Claim Limits
limits:
  max-chunks-per-claim: 100
  max-claims-per-player: 10
  min-distance-between-claims: 0  # Chunks

# Level System
levels:
  enabled: true
  max-level: 100
  xp-per-level: 1000
  xp-sources:
    claim-chunk: 10
    member-join: 5
    daily-login: 1
  rewards:
    5:
      - "eco give {player} 1000"
      - "lp user {player} permission set serverclaim.bonus.tier1"
    10:
      - "eco give {player} 5000"

# Nation System
nations:
  enabled: true
  max-members: 50
  creation-cost: 10000.0
  max-allies: 5
  max-enemies: 10

# War System
war:
  enabled: true
  declaration-cost: 5000.0
  capture-cost: 1000.0  # Per chunk
  tribute-tax: 0.1  # 10% tax on tributes
  cooldown: 604800  # 1 week between wars

# Claim Warps
warps:
  enabled: true
  max-warps-per-claim: 3
  teleport-delay: 3  # Seconds
  cost-to-create: 500.0
  max-visit-cost: 1000.0

# Particle System
particles:
  enabled: true
  type: FLAME  # Particle type
  density: 10  # Particles per block
  height: 5  # Blocks high
  duration: 10  # Seconds
  auto-show-on-claim: true

# Claim Bank
bank:
  enabled: true
  starting-balance: 0.0
  transaction-history-limit: 100

# Templates
templates:
  enabled: true
  max-templates-per-player: 5

# Messages
messages:
  prefix: "&7[&6Claim&7]&r "
  claim-created: "&aChunk claimed for ${cost}!"
  claim-deleted: "&cClaim deleted!"
  insufficient-funds: "&cInsufficient funds! Need ${amount}"
  chunk-already-claimed: "&cThis chunk is already claimed!"
  not-in-claim: "&cYou are not in a claim!"
  no-permission: "&cYou don't have permission to do that here!"
```

### profiles.yml
```yaml
# Default claim profile templates
profiles:
  default:
    name: "&aDefault"
    icon: GRASS_BLOCK
    permissions:
      visitor:
        break-blocks: false
        place-blocks: false
        open-containers: false
        use-items: false
        damage-entities: false
      member:
        break-blocks: true
        place-blocks: true
        open-containers: true
        use-items: true
        damage-entities: true
      owner:
        break-blocks: true
        place-blocks: true
        open-containers: true
        use-items: true
        damage-entities: true
        manage-members: true
        manage-settings: true

  public:
    name: "&ePublic"
    icon: EMERALD_BLOCK
    permissions:
      visitor:
        break-blocks: false
        place-blocks: false
        open-containers: false
        use-items: true
        damage-entities: false

  private:
    name: "&cPrivate"
    icon: REDSTONE_BLOCK
    permissions:
      visitor:
        break-blocks: false
        place-blocks: false
        open-containers: false
        use-items: false
        damage-entities: false
        enter-claim: false
```

## Database Schema

### Core Tables

**server_claims**
```sql
CREATE TABLE IF NOT EXISTS server_claims (
    id INT AUTO_INCREMENT PRIMARY KEY,
    owner_uuid VARCHAR(36) NOT NULL,
    name VARCHAR(64) NOT NULL,
    description TEXT,
    icon VARCHAR(64) DEFAULT 'GRASS_BLOCK',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    level INT DEFAULT 1,
    xp BIGINT DEFAULT 0,
    INDEX (owner_uuid)
);
```

**server_chunks**
```sql
CREATE TABLE IF NOT EXISTS server_chunks (
    claim_id INT NOT NULL,
    world VARCHAR(64) NOT NULL,
    chunk_x INT NOT NULL,
    chunk_z INT NOT NULL,
    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (world, chunk_x, chunk_z),
    FOREIGN KEY (claim_id) REFERENCES server_claims(id) ON DELETE CASCADE,
    INDEX (claim_id)
);
```

**server_claim_profiles**
```sql
CREATE TABLE IF NOT EXISTS server_claim_profiles (
    id INT AUTO_INCREMENT PRIMARY KEY,
    claim_id INT NOT NULL,
    name VARCHAR(32) NOT NULL,
    icon VARCHAR(64) DEFAULT 'PAPER',
    permissions JSON NOT NULL,  -- Serialized permission map
    active BOOLEAN DEFAULT FALSE,
    FOREIGN KEY (claim_id) REFERENCES server_claims(id) ON DELETE CASCADE,
    INDEX (claim_id)
);
```

**server_claim_members**
```sql
CREATE TABLE IF NOT EXISTS server_claim_members (
    claim_id INT NOT NULL,
    uuid VARCHAR(36) NOT NULL,
    role VARCHAR(32) NOT NULL,  -- visitor, member, owner
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (claim_id, uuid),
    FOREIGN KEY (claim_id) REFERENCES server_claims(id) ON DELETE CASCADE,
    INDEX (uuid)
);
```

**server_claim_groups**
```sql
CREATE TABLE IF NOT EXISTS server_claim_groups (
    id INT AUTO_INCREMENT PRIMARY KEY,
    claim_id INT NOT NULL,
    name VARCHAR(32) NOT NULL,
    icon VARCHAR(64) DEFAULT 'PLAYER_HEAD',
    color VARCHAR(16) DEFAULT 'WHITE',
    permissions JSON NOT NULL,
    priority INT DEFAULT 0,
    FOREIGN KEY (claim_id) REFERENCES server_claims(id) ON DELETE CASCADE,
    UNIQUE KEY (claim_id, name)
);
```

### Bank Tables

**server_claim_banks**
```sql
CREATE TABLE IF NOT EXISTS server_claim_banks (
    claim_id INT PRIMARY KEY,
    balance DECIMAL(20, 2) DEFAULT 0.00,
    last_upkeep TIMESTAMP NULL,
    next_upkeep TIMESTAMP NULL,
    FOREIGN KEY (claim_id) REFERENCES server_claims(id) ON DELETE CASCADE
);
```

**server_bank_transactions**
```sql
CREATE TABLE IF NOT EXISTS server_bank_transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    claim_id INT NOT NULL,
    uuid VARCHAR(36),  -- NULL for system transactions
    type ENUM('DEPOSIT', 'WITHDRAW', 'UPKEEP', 'REFUND') NOT NULL,
    amount DECIMAL(20, 2) NOT NULL,
    balance_after DECIMAL(20, 2) NOT NULL,
    description TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (claim_id) REFERENCES server_claims(id) ON DELETE CASCADE,
    INDEX (claim_id, timestamp DESC)
);
```

### Nation Tables

**server_nations**
```sql
CREATE TABLE IF NOT EXISTS server_nations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE,
    leader_uuid VARCHAR(36) NOT NULL,
    description TEXT,
    color VARCHAR(16) DEFAULT 'WHITE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX (leader_uuid)
);
```

**server_nation_members**
```sql
CREATE TABLE IF NOT EXISTS server_nation_members (
    nation_id INT NOT NULL,
    uuid VARCHAR(36) NOT NULL,
    rank ENUM('LEADER', 'OFFICER', 'MEMBER') DEFAULT 'MEMBER',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (nation_id, uuid),
    FOREIGN KEY (nation_id) REFERENCES server_nations(id) ON DELETE CASCADE,
    INDEX (uuid)
);
```

**server_nation_relations**
```sql
CREATE TABLE IF NOT EXISTS server_nation_relations (
    nation_id INT NOT NULL,
    other_nation_id INT NOT NULL,
    relation ENUM('ALLY', 'ENEMY', 'NEUTRAL') NOT NULL,
    since TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (nation_id, other_nation_id),
    FOREIGN KEY (nation_id) REFERENCES server_nations(id) ON DELETE CASCADE,
    FOREIGN KEY (other_nation_id) REFERENCES server_nations(id) ON DELETE CASCADE
);
```

### War Tables

**server_wars**
```sql
CREATE TABLE IF NOT EXISTS server_wars (
    id INT AUTO_INCREMENT PRIMARY KEY,
    attacker_nation_id INT NOT NULL,
    defender_nation_id INT NOT NULL,
    status ENUM('ACTIVE', 'ENDED', 'SURRENDERED') DEFAULT 'ACTIVE',
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP NULL,
    winner_nation_id INT NULL,
    FOREIGN KEY (attacker_nation_id) REFERENCES server_nations(id) ON DELETE CASCADE,
    FOREIGN KEY (defender_nation_id) REFERENCES server_nations(id) ON DELETE CASCADE,
    INDEX (status)
);
```

**server_war_captures**
```sql
CREATE TABLE IF NOT EXISTS server_war_captures (
    id INT AUTO_INCREMENT PRIMARY KEY,
    war_id INT NOT NULL,
    claim_id INT NOT NULL,
    captured_by_uuid VARCHAR(36) NOT NULL,
    captured_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (war_id) REFERENCES server_wars(id) ON DELETE CASCADE,
    FOREIGN KEY (claim_id) REFERENCES server_claims(id) ON DELETE CASCADE
);
```

### Warp Tables

**server_claim_warps**
```sql
CREATE TABLE IF NOT EXISTS server_claim_warps (
    id INT AUTO_INCREMENT PRIMARY KEY,
    claim_id INT NOT NULL,
    name VARCHAR(32) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL,
    pitch FLOAT NOT NULL,
    icon VARCHAR(64) DEFAULT 'ENDER_PEARL',
    description TEXT,
    public BOOLEAN DEFAULT FALSE,
    cost DECIMAL(20, 2) DEFAULT 0.00,
    visits INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (claim_id) REFERENCES server_claims(id) ON DELETE CASCADE,
    UNIQUE KEY (claim_id, name),
    INDEX (public)
);
```

### Utility Tables

**server_claim_notifications**
```sql
CREATE TABLE IF NOT EXISTS server_claim_notifications (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL,
    claim_id INT,
    type VARCHAR(32) NOT NULL,
    message TEXT NOT NULL,
    read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (claim_id) REFERENCES server_claims(id) ON DELETE CASCADE,
    INDEX (uuid, read)
);
```

**server_claim_audit_log**
```sql
CREATE TABLE IF NOT EXISTS server_claim_audit_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    claim_id INT NOT NULL,
    uuid VARCHAR(36),
    action VARCHAR(64) NOT NULL,
    details TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (claim_id) REFERENCES server_claims(id) ON DELETE CASCADE,
    INDEX (claim_id, timestamp DESC)
);
```

**server_claim_templates**
```sql
CREATE TABLE IF NOT EXISTS server_claim_templates (
    id INT AUTO_INCREMENT PRIMARY KEY,
    owner_uuid VARCHAR(36) NOT NULL,
    name VARCHAR(32) NOT NULL,
    data JSON NOT NULL,  -- Serialized claim settings
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY (owner_uuid, name),
    INDEX (owner_uuid)
);
```

**server_claim_stats**
```sql
CREATE TABLE IF NOT EXISTS server_claim_stats (
    uuid VARCHAR(36) PRIMARY KEY,
    total_claims INT DEFAULT 0,
    total_chunks INT DEFAULT 0,
    total_spent DECIMAL(20, 2) DEFAULT 0.00,
    total_earned DECIMAL(20, 2) DEFAULT 0.00,
    last_claim TIMESTAMP NULL
);
```

## GUI System (30+ GUIs)

### Main Menu
- **ClaimMainGui**: Hub for all claim operations
- **ChunkManagementGui**: Claim/unclaim chunks
- **ClaimListGui**: View all your claims

### Profile Management
- **ProfileListGui**: View/switch profiles
- **ProfileEditGui**: Edit profile permissions
- **CreateProfileGui**: Create new profile

### Member Management
- **MemberListGui**: View all members
- **AddMemberGui**: Add new members
- **MemberPermissionsGui**: Edit member roles

### Bank Management
- **ClaimBankGui**: Main bank interface
- **BankTransactionHistoryGui**: View transactions
- **BankDepositGui**: Deposit funds
- **BankWithdrawGui**: Withdraw funds

### Nation Management
- **NationMenuGui**: Main nation interface
- **NationMembersGui**: View nation members
- **NationRelationsGui**: Manage relations
- **CreateNationGui**: Nation creation wizard

### War System
- **WarMenuGui**: Active wars display
- **DeclareWarGui**: Declare war interface
- **WarHistoryGui**: Past wars
- **TributeGui**: Send tribute

### Warp System
- **ClaimWarpsGui**: Manage claim warps
- **VisitListGui**: Browse public warps
- **CreateWarpGui**: Create new warp
- **WarpSettingsGui**: Edit warp settings

### Utilities
- **ColorPickerGui**: Choose colors for claims/nations
- **IconPickerGui**: Choose icons
- **ConfirmationGui**: Generic confirmation dialog
- **StatisticsGui**: View claim statistics
- **NotificationsGui**: View notifications

## Managers

### ClaimManager
- Core claiming logic
- LRU cache for frequently accessed claims
- Chunk ownership queries
- Claim creation/deletion

### ProfileManager
- Profile preset management
- Permission inheritance
- Active profile switching

### VisitationManager
- Warp point management
- Public warp browsing
- Teleportation handling

### ParticleManager
- Particle boundary effects
- Customizable particle types
- Performance optimization

### RewardsManager
- Level-based reward distribution
- Command execution on level up
- Configurable reward tiers

### BankManager
- Claim bank account management
- Transaction processing
- Balance tracking

### UpkeepManager
- Automated upkeep scheduling
- Grace period handling
- Notification system

### LevelManager
- XP tracking and level calculation
- Level up events
- Reward triggering

### NationManager
- Nation creation and management
- Member management
- Relation handling

### WarManager
- War declaration and tracking
- Capture mechanics
- Tribute processing

### NotificationManager
- Player notification queuing
- Read/unread tracking
- Notification expiration

### ClaimStatsManager
- Statistics aggregation
- Leaderboard generation
- Personal stat tracking

### ClaimMerger
- Adjacent chunk detection
- Automatic claim merging
- Split detection

## Pricing System

### ExponentialPricing
```java
public class ExponentialPricing {
    private final double basePrice;
    private final double exponent;
    private final double maxPrice;

    public double calculateChunkPrice(int chunksOwned) {
        double price = basePrice * Math.pow(exponent, chunksOwned);
        return Math.min(price, maxPrice);
    }

    public double calculateRefund(int chunksOwned) {
        // Refund 75% of purchase price
        return calculateChunkPrice(chunksOwned - 1) * 0.75;
    }
}
```

## Event System

### Custom Events
- `ClaimCreateEvent` - When a claim is created
- `ClaimDeleteEvent` - When a claim is deleted
- `ChunkClaimEvent` - When a chunk is claimed
- `ChunkUnclaimEvent` - When a chunk is unclaimed
- `ClaimMemberAddEvent` - When member is added
- `ClaimMemberRemoveEvent` - When member is removed
- `ClaimLevelUpEvent` - When claim levels up
- `NationCreateEvent` - When nation is created
- `WarDeclareEvent` - When war is declared
- `WarEndEvent` - When war ends

## Dependencies

### Hard Dependencies
- ServerAPI
- Vault

### Soft Dependencies
- LuckPerms (permission integration)
- ProtocolLib (GUI features)

## Best Practices

1. **Pricing**: Set exponential pricing carefully to balance economy
2. **Upkeep**: Configure upkeep to match server economy scale
3. **Limits**: Set reasonable chunk/claim limits per player
4. **Nations**: Monitor nation sizes to prevent mega-alliances
5. **War**: Balance war costs to prevent griefing
6. **Backups**: Regular database backups essential for claim data
7. **Performance**: Monitor particle density on large servers

## Performance Optimization

- LRU caching for frequently accessed claims
- Async database operations
- Batched chunk queries
- Indexed database tables
- Particle effect throttling
- GUI pagination for large lists

## Admin Tools

### Refund System
Preview and execute refunds for chunk pricing changes:
```
/claimrefund preview  # Shows what players would receive
/claimrefund execute  # Issues refunds
```

### Claim Deletion
Delete claims by ID or location:
```
/claimadmin delete <id>
/claimadmin deleteat  # Deletes claim at your location
```

### Claim Information
View detailed claim info:
```
/claimadmin info <id>
```
