-- ServerClaim Database Schema (MariaDB/MySQL Compatible)

-- Player claim data
CREATE TABLE IF NOT EXISTS server_player_claims (
    uuid VARCHAR(36) PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    total_chunks INT DEFAULT 2,
    purchased_chunks INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Claims (groups of chunks owned by a player)
CREATE TABLE IF NOT EXISTS server_claims (
    id INT AUTO_INCREMENT PRIMARY KEY,
    owner_uuid VARCHAR(36) NOT NULL,
    name VARCHAR(64) DEFAULT 'My Claim',
    world VARCHAR(64) NOT NULL,
    welcome_message VARCHAR(256) DEFAULT NULL,
    teleport_protected BOOLEAN DEFAULT FALSE,
    keep_inventory BOOLEAN DEFAULT FALSE,
    -- Per-claim chunk pool (added in v2.0)
    total_chunks INT DEFAULT 2,
    purchased_chunks INT DEFAULT 0,
    claim_order INT DEFAULT 1,
    -- Claim appearance (moved from profiles)
    color VARCHAR(16) DEFAULT 'WHITE',
    icon VARCHAR(64) DEFAULT NULL,
    particle_effect VARCHAR(32) DEFAULT 'DUST',
    -- Claim settings (moved from profile_settings)
    pvp_enabled BOOLEAN DEFAULT FALSE,
    fire_spread BOOLEAN DEFAULT FALSE,
    explosions BOOLEAN DEFAULT FALSE,
    hostile_spawns BOOLEAN DEFAULT TRUE,
    mob_griefing BOOLEAN DEFAULT FALSE,
    passive_spawns BOOLEAN DEFAULT TRUE,
    crop_trampling BOOLEAN DEFAULT FALSE,
    leaf_decay BOOLEAN DEFAULT TRUE,
    -- Group permissions (moved from profiles)
    group_permissions TEXT DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_owner (owner_uuid),
    INDEX idx_world (world),
    INDEX idx_claim_order (owner_uuid, claim_order)
);

-- Individual claimed chunks
CREATE TABLE IF NOT EXISTS server_chunks (
    id INT AUTO_INCREMENT PRIMARY KEY,
    claim_id INT NOT NULL,
    world VARCHAR(64) NOT NULL,
    chunk_x INT NOT NULL,
    chunk_z INT NOT NULL,
    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_chunk (world, chunk_x, chunk_z),
    INDEX idx_claim (claim_id)
);

-- Claim profiles (settings presets)
CREATE TABLE IF NOT EXISTS server_profiles (
    id INT AUTO_INCREMENT PRIMARY KEY,
    claim_id INT NOT NULL,
    name VARCHAR(32) NOT NULL,
    color VARCHAR(16) DEFAULT 'WHITE',
    icon VARCHAR(64) DEFAULT 'WHITE_STAINED_GLASS_PANE',
    particle_effect VARCHAR(32) DEFAULT 'DUST',
    dust_effect VARCHAR(32) DEFAULT NULL,
    is_active BOOLEAN DEFAULT FALSE,
    slot_index INT DEFAULT 0,
    group_permissions TEXT DEFAULT NULL,
    INDEX idx_claim (claim_id)
);

-- Profile settings
CREATE TABLE IF NOT EXISTS server_profile_settings (
    profile_id INT PRIMARY KEY,
    pvp_enabled BOOLEAN DEFAULT FALSE,
    fire_spread BOOLEAN DEFAULT FALSE,
    explosions BOOLEAN DEFAULT FALSE,
    hostile_spawns BOOLEAN DEFAULT TRUE,
    mob_griefing BOOLEAN DEFAULT FALSE,
    passive_spawns BOOLEAN DEFAULT TRUE,
    crop_trampling BOOLEAN DEFAULT FALSE
);

-- Claim members (players assigned to groups)
CREATE TABLE IF NOT EXISTS server_claim_members (
    id INT AUTO_INCREMENT PRIMARY KEY,
    claim_id INT NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    group_name VARCHAR(32) NOT NULL DEFAULT 'VISITOR',
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_member (claim_id, player_uuid),
    INDEX idx_claim (claim_id)
);

-- Legacy trusted players table (kept for migration)
CREATE TABLE IF NOT EXISTS server_trusted_players (
    id INT AUTO_INCREMENT PRIMARY KEY,
    claim_id INT NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    trusted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_trust (claim_id, player_uuid),
    INDEX idx_claim (claim_id)
);

-- Banned players from claims
CREATE TABLE IF NOT EXISTS server_banned_players (
    id INT AUTO_INCREMENT PRIMARY KEY,
    claim_id INT NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    banned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_ban (claim_id, player_uuid),
    INDEX idx_claim (claim_id)
);

-- Player rewards and particle settings
CREATE TABLE IF NOT EXISTS server_player_rewards (
    uuid VARCHAR(36) PRIMARY KEY,
    particles_enabled BOOLEAN DEFAULT TRUE,
    static_particle_mode BOOLEAN DEFAULT FALSE,
    selected_dust_effect VARCHAR(32) DEFAULT NULL,
    selected_profile_color VARCHAR(16) DEFAULT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Claim warp points and visitation settings
CREATE TABLE IF NOT EXISTS server_claim_warps (
    claim_id INT PRIMARY KEY,
    warp_x DOUBLE NOT NULL,
    warp_y DOUBLE NOT NULL,
    warp_z DOUBLE NOT NULL,
    warp_yaw FLOAT DEFAULT 0,
    warp_pitch FLOAT DEFAULT 0,
    visibility_mode VARCHAR(16) DEFAULT 'PRIVATE',
    visit_cost DOUBLE DEFAULT 0,
    description VARCHAR(256) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_visibility (visibility_mode)
);

-- Claim templates system for saving and applying claim configurations
CREATE TABLE IF NOT EXISTS server_claim_templates (
    id INT AUTO_INCREMENT PRIMARY KEY,
    owner_uuid VARCHAR(36) NOT NULL,
    template_name VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    pvp_enabled BOOLEAN DEFAULT FALSE,
    fire_spread BOOLEAN DEFAULT FALSE,
    mob_spawning BOOLEAN DEFAULT TRUE,
    explosion_damage BOOLEAN DEFAULT FALSE,
    piston_push BOOLEAN DEFAULT FALSE,
    fluid_flow BOOLEAN DEFAULT TRUE,
    leaf_decay BOOLEAN DEFAULT TRUE,
    crop_growth BOOLEAN DEFAULT TRUE,
    group_permissions TEXT,
    custom_groups_csv TEXT,
    times_used INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_template (owner_uuid, template_name),
    INDEX idx_owner (owner_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Allowlist for ALLOWLIST visibility mode
CREATE TABLE IF NOT EXISTS server_claim_warp_allowlist (
    id INT AUTO_INCREMENT PRIMARY KEY,
    claim_id INT NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_allowlist (claim_id, player_uuid),
    INDEX idx_claim (claim_id)
);

-- Blocklist for blocking specific users (applies even if PUBLIC)
CREATE TABLE IF NOT EXISTS server_claim_warp_blocklist (
    id INT AUTO_INCREMENT PRIMARY KEY,
    claim_id INT NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_blocklist (claim_id, player_uuid),
    INDEX idx_claim (claim_id)
);

-- ============================================================================
-- MIGRATION: Per-Claim Chunk Ownership (v2.0)
-- These statements add new columns to existing databases
-- ============================================================================

-- Add per-claim chunk pool columns
ALTER TABLE server_claims ADD COLUMN IF NOT EXISTS total_chunks INT DEFAULT 2;
ALTER TABLE server_claims ADD COLUMN IF NOT EXISTS purchased_chunks INT DEFAULT 0;
ALTER TABLE server_claims ADD COLUMN IF NOT EXISTS claim_order INT DEFAULT 1;

-- Add claim appearance columns (moved from profiles)
ALTER TABLE server_claims ADD COLUMN IF NOT EXISTS color VARCHAR(16) DEFAULT 'WHITE';
ALTER TABLE server_claims ADD COLUMN IF NOT EXISTS icon VARCHAR(64) DEFAULT NULL;
ALTER TABLE server_claims ADD COLUMN IF NOT EXISTS particle_effect VARCHAR(32) DEFAULT 'DUST';

-- Add claim settings columns (moved from profile_settings)
ALTER TABLE server_claims ADD COLUMN IF NOT EXISTS pvp_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE server_claims ADD COLUMN IF NOT EXISTS fire_spread BOOLEAN DEFAULT FALSE;
ALTER TABLE server_claims ADD COLUMN IF NOT EXISTS explosions BOOLEAN DEFAULT FALSE;
ALTER TABLE server_claims ADD COLUMN IF NOT EXISTS hostile_spawns BOOLEAN DEFAULT TRUE;
ALTER TABLE server_claims ADD COLUMN IF NOT EXISTS mob_griefing BOOLEAN DEFAULT FALSE;
ALTER TABLE server_claims ADD COLUMN IF NOT EXISTS passive_spawns BOOLEAN DEFAULT TRUE;
ALTER TABLE server_claims ADD COLUMN IF NOT EXISTS crop_trampling BOOLEAN DEFAULT FALSE;
ALTER TABLE server_claims ADD COLUMN IF NOT EXISTS leaf_decay BOOLEAN DEFAULT TRUE;

-- Add group permissions column
ALTER TABLE server_claims ADD COLUMN IF NOT EXISTS group_permissions TEXT DEFAULT NULL;

-- Add static particle mode column to player rewards
ALTER TABLE server_player_rewards ADD COLUMN IF NOT EXISTS static_particle_mode BOOLEAN DEFAULT FALSE;

-- Add per-claim particle toggle column
ALTER TABLE server_claims ADD COLUMN IF NOT EXISTS particle_enabled BOOLEAN DEFAULT TRUE;

-- ============================================================================
-- MIGRATION: Per-Profile Particle Settings (v6.0)
-- ============================================================================
-- Add per-profile particle customization columns
ALTER TABLE server_profiles ADD COLUMN IF NOT EXISTS selected_dust_effect VARCHAR(32) DEFAULT NULL;
ALTER TABLE server_profiles ADD COLUMN IF NOT EXISTS selected_profile_color VARCHAR(16) DEFAULT NULL;
ALTER TABLE server_profiles ADD COLUMN IF NOT EXISTS particles_enabled BOOLEAN DEFAULT TRUE;
ALTER TABLE server_profiles ADD COLUMN IF NOT EXISTS static_particle_mode BOOLEAN DEFAULT FALSE;

-- ============================================================================
-- MIGRATION: Custom Groups System (v2.1)
-- ============================================================================

-- Custom claim groups (replaces enum-based ClaimGroup)
-- Each claim has its own set of groups that can be renamed and customized
CREATE TABLE IF NOT EXISTS server_claim_groups (
    id INT AUTO_INCREMENT PRIMARY KEY,
    claim_id INT NOT NULL,
    name VARCHAR(32) NOT NULL,
    color_tag VARCHAR(16) DEFAULT '<white>',
    icon VARCHAR(64) DEFAULT 'WHITE_STAINED_GLASS_PANE',
    priority INT DEFAULT 10,
    permissions TEXT DEFAULT NULL,
    management_permissions TEXT DEFAULT NULL,
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_claim (claim_id),
    INDEX idx_priority (claim_id, priority DESC)
);

-- Add group_id column to members table for custom group support
ALTER TABLE server_claim_members ADD COLUMN IF NOT EXISTS group_id INT DEFAULT NULL;
ALTER TABLE server_claim_members ADD INDEX IF NOT EXISTS idx_group (group_id);

-- Chunk transfer history for auditing (optional)
CREATE TABLE IF NOT EXISTS server_chunk_transfers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    chunk_world VARCHAR(64) NOT NULL,
    chunk_x INT NOT NULL,
    chunk_z INT NOT NULL,
    from_claim_id INT,
    to_claim_id INT,
    from_owner_uuid VARCHAR(36),
    to_owner_uuid VARCHAR(36),
    transfer_type VARCHAR(16) DEFAULT 'OWNERSHIP',
    transferred_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chunk (chunk_world, chunk_x, chunk_z),
    INDEX idx_claims (from_claim_id, to_claim_id)
);

-- ============================================================================
-- PHASE 1: Land Banks & Upkeep System (v3.0)
-- ============================================================================

-- Land Bank for each claim
CREATE TABLE IF NOT EXISTS server_claim_banks (
    claim_id INT PRIMARY KEY,
    balance DECIMAL(15,2) DEFAULT 0.00,
    minimum_balance_warning DECIMAL(15,2) DEFAULT 100.00,
    last_upkeep_payment TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    next_upkeep_due TIMESTAMP DEFAULT NULL,
    grace_period_start TIMESTAMP DEFAULT NULL,
    INDEX idx_next_due (next_upkeep_due)
);

-- Bank transaction history
CREATE TABLE IF NOT EXISTS server_claim_bank_transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    claim_id INT NOT NULL,
    player_uuid VARCHAR(36) DEFAULT NULL,
    transaction_type ENUM('DEPOSIT', 'WITHDRAW', 'UPKEEP', 'TAX', 'REFUND', 'NATION_TAX') NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    balance_after DECIMAL(15,2) NOT NULL,
    description VARCHAR(256) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_claim (claim_id),
    INDEX idx_type (transaction_type),
    INDEX idx_created (created_at)
);

-- Upkeep tracking per claim
CREATE TABLE IF NOT EXISTS server_claim_upkeep (
    claim_id INT PRIMARY KEY,
    cost_per_chunk DECIMAL(10,2) DEFAULT 10.00,
    discount_percentage DECIMAL(5,2) DEFAULT 0.00,
    grace_days INT DEFAULT 7,
    auto_unclaim_enabled BOOLEAN DEFAULT TRUE,
    notifications_sent INT DEFAULT 0
);

-- Chunks removed due to unpaid upkeep (audit trail)
CREATE TABLE IF NOT EXISTS server_unclaimed_by_upkeep (
    id INT AUTO_INCREMENT PRIMARY KEY,
    claim_id INT NOT NULL,
    chunk_world VARCHAR(64) NOT NULL,
    chunk_x INT NOT NULL,
    chunk_z INT NOT NULL,
    unclaimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_claim (claim_id)
);

-- ============================================================================
-- PHASE 2: Claim Levels & XP System (v3.0)
-- ============================================================================

-- Claim level and XP
CREATE TABLE IF NOT EXISTS server_claim_levels (
    claim_id INT PRIMARY KEY,
    level INT DEFAULT 1,
    current_xp BIGINT DEFAULT 0,
    total_xp_earned BIGINT DEFAULT 0
);

-- XP history/events
CREATE TABLE IF NOT EXISTS server_claim_xp_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    claim_id INT NOT NULL,
    player_uuid VARCHAR(36),
    xp_amount INT NOT NULL,
    xp_source ENUM('PLAYTIME', 'BLOCKS_PLACED', 'BLOCKS_BROKEN', 'MEMBER_ADDED',
                   'UPKEEP_PAID', 'CHUNK_CLAIMED', 'ACHIEVEMENT', 'ADMIN_GRANT') NOT NULL,
    earned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_claim (claim_id),
    INDEX idx_source (xp_source)
);

-- Cached benefits per claim (updated on level up)
CREATE TABLE IF NOT EXISTS server_claim_benefits (
    claim_id INT PRIMARY KEY,
    max_member_slots INT DEFAULT 10,
    max_warp_slots INT DEFAULT 1,
    upkeep_discount_percent DECIMAL(5,2) DEFAULT 0.00,
    welcome_message_length INT DEFAULT 64,
    particle_tier INT DEFAULT 1,
    bonus_chunk_slots INT DEFAULT 0
);

-- Playtime tracking per player per claim
CREATE TABLE IF NOT EXISTS server_claim_playtime (
    id INT AUTO_INCREMENT PRIMARY KEY,
    claim_id INT NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    total_seconds BIGINT DEFAULT 0,
    last_xp_grant TIMESTAMP DEFAULT NULL,
    session_start TIMESTAMP DEFAULT NULL,
    UNIQUE KEY unique_claim_player (claim_id, player_uuid),
    INDEX idx_claim (claim_id)
);

-- ============================================================================
-- PHASE 3: Nations System (v3.0)
-- ============================================================================

-- Nations
CREATE TABLE IF NOT EXISTS server_nations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(32) NOT NULL UNIQUE,
    tag VARCHAR(5) NOT NULL UNIQUE,
    leader_uuid VARCHAR(36) NOT NULL,
    description VARCHAR(512) DEFAULT NULL,
    color VARCHAR(16) DEFAULT 'WHITE',
    founded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    total_chunks INT DEFAULT 0,
    member_count INT DEFAULT 0,
    level INT DEFAULT 1,
    tax_rate DOUBLE DEFAULT 0.0,
    INDEX idx_leader (leader_uuid),
    INDEX idx_name (name)
);

-- Nation bank
CREATE TABLE IF NOT EXISTS server_nation_banks (
    nation_id INT PRIMARY KEY,
    balance DECIMAL(15,2) DEFAULT 0.00,
    tax_rate DECIMAL(5,2) DEFAULT 0.00,
    last_tax_collection TIMESTAMP DEFAULT NULL
);

-- Nation members (claims that joined nations)
CREATE TABLE IF NOT EXISTS server_nation_members (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nation_id INT NOT NULL,
    claim_id INT NOT NULL UNIQUE,
    role ENUM('LEADER', 'OFFICER', 'MEMBER') DEFAULT 'MEMBER',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    contributed_amount DECIMAL(15,2) DEFAULT 0.00,
    INDEX idx_nation (nation_id),
    INDEX idx_role (role)
);

-- Nation invites (player-based for persistent invitations)
CREATE TABLE IF NOT EXISTS server_nation_invites (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nation_id INT NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    inviter_uuid VARCHAR(36) NOT NULL,
    invited_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP DEFAULT NULL,
    UNIQUE KEY unique_invite (nation_id, player_uuid),
    INDEX idx_player_uuid (player_uuid),
    INDEX idx_expires (expires_at)
);

-- Nation relations (diplomacy)
CREATE TABLE IF NOT EXISTS server_nation_relations (
    nation_id INT NOT NULL,
    target_nation_id INT NOT NULL,
    relation_type ENUM('NEUTRAL', 'ALLY', 'ENEMY', 'AT_WAR', 'TRUCE') DEFAULT 'NEUTRAL',
    established_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (nation_id, target_nation_id),
    INDEX idx_relation (relation_type)
);

-- Nation bank transactions
CREATE TABLE IF NOT EXISTS server_nation_bank_transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nation_id INT NOT NULL,
    player_uuid VARCHAR(36) DEFAULT NULL,
    claim_id INT DEFAULT NULL,
    transaction_type ENUM('DEPOSIT', 'WITHDRAW', 'TAX_COLLECTION', 'WAR_TRIBUTE', 'REFUND') NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    balance_after DECIMAL(15,2) NOT NULL,
    description VARCHAR(256) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_nation (nation_id)
);

-- ============================================================================
-- PHASE 4: War System (v3.0)
-- ============================================================================

-- Wars
CREATE TABLE IF NOT EXISTS server_wars (
    id INT AUTO_INCREMENT PRIMARY KEY,
    attacker_nation_id INT DEFAULT NULL,
    attacker_claim_id INT DEFAULT NULL,
    defender_nation_id INT DEFAULT NULL,
    defender_claim_id INT DEFAULT NULL,
    war_state ENUM('DECLARED', 'ACTIVE', 'CEASEFIRE', 'ENDED') DEFAULT 'DECLARED',
    declaration_reason VARCHAR(512) DEFAULT NULL,
    declared_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    active_at TIMESTAMP DEFAULT NULL,
    ended_at TIMESTAMP DEFAULT NULL,
    outcome ENUM('ATTACKER_WIN', 'DEFENDER_WIN', 'SURRENDER', 'TRUCE', 'TIMEOUT') DEFAULT NULL,
    INDEX idx_state (war_state),
    INDEX idx_attacker_nation (attacker_nation_id),
    INDEX idx_defender_nation (defender_nation_id)
);

-- War shields (immunity periods)
CREATE TABLE IF NOT EXISTS server_war_shields (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nation_id INT DEFAULT NULL,
    claim_id INT DEFAULT NULL,
    shield_end TIMESTAMP NOT NULL,
    reason VARCHAR(128) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_end (shield_end),
    INDEX idx_nation (nation_id),
    INDEX idx_claim (claim_id)
);

-- Territory capture progress during wars
CREATE TABLE IF NOT EXISTS server_war_captures (
    id INT AUTO_INCREMENT PRIMARY KEY,
    war_id INT NOT NULL,
    chunk_world VARCHAR(64) NOT NULL,
    chunk_x INT NOT NULL,
    chunk_z INT NOT NULL,
    capturing_nation_id INT DEFAULT NULL,
    capture_progress INT DEFAULT 0,
    last_progress_update TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    captured_at TIMESTAMP DEFAULT NULL,
    UNIQUE KEY unique_capture (war_id, chunk_world, chunk_x, chunk_z),
    INDEX idx_war (war_id)
);

-- War tribute/surrender offers
CREATE TABLE IF NOT EXISTS server_war_tributes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    war_id INT NOT NULL,
    offering_side ENUM('ATTACKER', 'DEFENDER') NOT NULL,
    tribute_type ENUM('SURRENDER', 'PEACE_OFFER', 'TRIBUTE_DEMAND') NOT NULL,
    money_amount DECIMAL(15,2) DEFAULT 0.00,
    message VARCHAR(512) DEFAULT NULL,
    response ENUM('PENDING', 'ACCEPTED', 'REJECTED') DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP DEFAULT NULL,
    INDEX idx_war (war_id),
    INDEX idx_response (response)
);

-- War events log (audit trail)
CREATE TABLE IF NOT EXISTS server_war_events (
    id INT AUTO_INCREMENT PRIMARY KEY,
    war_id INT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    player_uuid VARCHAR(36) DEFAULT NULL,
    details TEXT DEFAULT NULL,
    occurred_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_war (war_id),
    INDEX idx_type (event_type)
);

-- ============================================================================
-- PERFORMANCE INDEXES (Added for optimization)
-- ============================================================================

-- Additional index on server_wars for time-based queries
CREATE INDEX IF NOT EXISTS idx_war_declared_at ON server_wars(declared_at);

-- Index on server_chunks for claim-based queries (if not exists)
CREATE INDEX IF NOT EXISTS idx_chunk_claim_id ON server_chunks(claim_id);

-- Index on server_nations for owner-based queries
CREATE INDEX IF NOT EXISTS idx_nation_leader_uuid ON server_nations(leader_uuid);

-- Index on server_claim_bank_transactions for claim-based queries
CREATE INDEX IF NOT EXISTS idx_bank_transaction_claim ON server_claim_bank_transactions(claim_id);

-- ============================================================================
-- AUDIT LOG SYSTEM (Added for security and accountability)
-- ============================================================================

-- Comprehensive audit log for all claim operations
CREATE TABLE IF NOT EXISTS server_claim_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    claim_id INT,
    player_uuid VARCHAR(36),
    action_type ENUM(
        'PERMISSION_CHANGE',
        'ADMIN_COMMAND',
        'TRANSFER_OWNERSHIP',
        'DELETE_CLAIM',
        'ADD_MEMBER',
        'REMOVE_MEMBER',
        'BANK_DEPOSIT',
        'BANK_WITHDRAW',
        'SETTINGS_CHANGE',
        'CHUNK_ADD',
        'CHUNK_REMOVE'
    ) NULL,
    details TEXT,
    ip_address VARCHAR(45) DEFAULT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_claim (claim_id),
    INDEX idx_player (player_uuid),
    INDEX idx_action (action_type),
    INDEX idx_timestamp (timestamp)
);

-- ============================================================================
-- REFERENTIAL INTEGRITY: Foreign Key Constraints (v3.0 - Phase 4 Priority 3)
-- ============================================================================
-- These constraints enforce referential integrity at the database level and
-- automatically cascade deletes to prevent orphaned records.
--
-- IMPORTANT: For existing databases, you must clean orphaned data before
-- applying these constraints, or they will fail. To clean orphaned data:
--
--   DELETE FROM server_chunks WHERE claim_id NOT IN (SELECT id FROM server_claims);
--   DELETE FROM server_claim_banks WHERE claim_id NOT IN (SELECT id FROM server_claims);
--   DELETE FROM server_claim_groups WHERE claim_id NOT IN (SELECT id FROM server_claims);
--   DELETE FROM server_nation_members WHERE nation_id NOT IN (SELECT id FROM server_nations);
--   DELETE FROM server_wars WHERE attacker_nation_id NOT IN (SELECT id FROM server_nations);
--   DELETE FROM server_wars WHERE defender_nation_id NOT IN (SELECT id FROM server_nations);
--   DELETE FROM server_claim_bank_transactions WHERE claim_id NOT IN (SELECT id FROM server_claims);
--   DELETE FROM server_claim_warps WHERE claim_id NOT IN (SELECT id FROM server_claims);
--
-- Note: These constraints are compatible with MariaDB/MySQL and H2.
-- SQLite has limited ALTER TABLE support - constraints should be added to CREATE TABLE.
-- ============================================================================

-- 1. Chunks must belong to a valid claim
-- When a claim is deleted, all its chunks are automatically removed
ALTER TABLE server_chunks
DROP FOREIGN KEY IF EXISTS fk_chunk_claim;

ALTER TABLE server_chunks
ADD CONSTRAINT fk_chunk_claim
FOREIGN KEY (claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 2. Claim banks must belong to a valid claim
-- When a claim is deleted, its bank is automatically removed
ALTER TABLE server_claim_banks
DROP FOREIGN KEY IF EXISTS fk_bank_claim;

ALTER TABLE server_claim_banks
ADD CONSTRAINT fk_bank_claim
FOREIGN KEY (claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 3. Custom groups must belong to a valid claim
-- When a claim is deleted, all its custom groups are automatically removed
ALTER TABLE server_claim_groups
DROP FOREIGN KEY IF EXISTS fk_custom_group_claim;

ALTER TABLE server_claim_groups
ADD CONSTRAINT fk_custom_group_claim
FOREIGN KEY (claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 4. Nation members must belong to a valid nation
-- When a nation is deleted, all its members are automatically removed
ALTER TABLE server_nation_members
DROP FOREIGN KEY IF EXISTS fk_nation_member_nation;

ALTER TABLE server_nation_members
ADD CONSTRAINT fk_nation_member_nation
FOREIGN KEY (nation_id) REFERENCES server_nations(id)
ON DELETE CASCADE;

-- 5. Nation members must reference a valid claim
-- When a claim is deleted, its nation membership is automatically removed
ALTER TABLE server_nation_members
DROP FOREIGN KEY IF EXISTS fk_nation_member_claim;

ALTER TABLE server_nation_members
ADD CONSTRAINT fk_nation_member_claim
FOREIGN KEY (claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 6. Wars - attacker nation must be valid
-- When attacking nation is deleted, war is automatically cancelled
ALTER TABLE server_wars
DROP FOREIGN KEY IF EXISTS fk_war_attacker_nation;

ALTER TABLE server_wars
ADD CONSTRAINT fk_war_attacker_nation
FOREIGN KEY (attacker_nation_id) REFERENCES server_nations(id)
ON DELETE CASCADE;

-- 7. Wars - defender nation must be valid
-- When defending nation is deleted, war is automatically cancelled
ALTER TABLE server_wars
DROP FOREIGN KEY IF EXISTS fk_war_defender_nation;

ALTER TABLE server_wars
ADD CONSTRAINT fk_war_defender_nation
FOREIGN KEY (defender_nation_id) REFERENCES server_nations(id)
ON DELETE CASCADE;

-- 8. Wars - attacker claim must be valid (for claim vs claim wars)
-- When attacking claim is deleted, war is automatically cancelled
ALTER TABLE server_wars
DROP FOREIGN KEY IF EXISTS fk_war_attacker_claim;

ALTER TABLE server_wars
ADD CONSTRAINT fk_war_attacker_claim
FOREIGN KEY (attacker_claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 9. Wars - defender claim must be valid (for claim vs claim wars)
-- When defending claim is deleted, war is automatically cancelled
ALTER TABLE server_wars
DROP FOREIGN KEY IF EXISTS fk_war_defender_claim;

ALTER TABLE server_wars
ADD CONSTRAINT fk_war_defender_claim
FOREIGN KEY (defender_claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 10. Bank transactions must belong to a valid claim
-- When a claim is deleted, all its transaction history is automatically removed
ALTER TABLE server_claim_bank_transactions
DROP FOREIGN KEY IF EXISTS fk_transaction_claim;

ALTER TABLE server_claim_bank_transactions
ADD CONSTRAINT fk_transaction_claim
FOREIGN KEY (claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 11. Claim warps must belong to a valid claim
-- When a claim is deleted, its warp point is automatically removed
DELETE FROM server_claim_warps WHERE claim_id NOT IN (SELECT id FROM server_claims);

ALTER TABLE server_claim_warps
DROP FOREIGN KEY IF EXISTS fk_warp_claim;

ALTER TABLE server_claim_warps
ADD CONSTRAINT fk_warp_claim
FOREIGN KEY (claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 12. Claim warp allowlist must belong to a valid claim
-- When a claim is deleted, its warp allowlist is automatically removed
ALTER TABLE server_claim_warp_allowlist
DROP FOREIGN KEY IF EXISTS fk_warp_allowlist_claim;

ALTER TABLE server_claim_warp_allowlist
ADD CONSTRAINT fk_warp_allowlist_claim
FOREIGN KEY (claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 13. Claim warp blocklist must belong to a valid claim
-- When a claim is deleted, its warp blocklist is automatically removed
ALTER TABLE server_claim_warp_blocklist
DROP FOREIGN KEY IF EXISTS fk_warp_blocklist_claim;

ALTER TABLE server_claim_warp_blocklist
ADD CONSTRAINT fk_warp_blocklist_claim
FOREIGN KEY (claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 14. Claim members must belong to a valid claim
-- When a claim is deleted, all its members are automatically removed
ALTER TABLE server_claim_members
DROP FOREIGN KEY IF EXISTS fk_claim_member_claim;

ALTER TABLE server_claim_members
ADD CONSTRAINT fk_claim_member_claim
FOREIGN KEY (claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 15. Claim members must reference a valid custom group (if group_id is set)
-- When a group is deleted, member's group_id is set to NULL
ALTER TABLE server_claim_members
DROP FOREIGN KEY IF EXISTS fk_claim_member_group;

ALTER TABLE server_claim_members
ADD CONSTRAINT fk_claim_member_group
FOREIGN KEY (group_id) REFERENCES server_claim_groups(id)
ON DELETE SET NULL;

-- 16. Trusted players must belong to a valid claim (legacy)
-- When a claim is deleted, all its trusted players are automatically removed
DELETE FROM server_trusted_players WHERE claim_id NOT IN (SELECT id FROM server_claims);

ALTER TABLE server_trusted_players
DROP FOREIGN KEY IF EXISTS fk_trusted_claim;

ALTER TABLE server_trusted_players
ADD CONSTRAINT fk_trusted_claim
FOREIGN KEY (claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 17. Banned players must belong to a valid claim
-- When a claim is deleted, all its banned players are automatically removed
ALTER TABLE server_banned_players
DROP FOREIGN KEY IF EXISTS fk_banned_claim;

ALTER TABLE server_banned_players
ADD CONSTRAINT fk_banned_claim
FOREIGN KEY (claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 18. Claim profiles must belong to a valid claim
-- When a claim is deleted, all its profiles are automatically removed
DELETE FROM server_profiles WHERE claim_id NOT IN (SELECT id FROM server_claims);

ALTER TABLE server_profiles
DROP FOREIGN KEY IF EXISTS fk_profile_claim;

ALTER TABLE server_profiles
ADD CONSTRAINT fk_profile_claim
FOREIGN KEY (claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 19. Profile settings must belong to a valid profile
-- When a profile is deleted, its settings are automatically removed
ALTER TABLE server_profile_settings
DROP FOREIGN KEY IF EXISTS fk_settings_profile;

ALTER TABLE server_profile_settings
ADD CONSTRAINT fk_settings_profile
FOREIGN KEY (profile_id) REFERENCES server_profiles(id)
ON DELETE CASCADE;

-- 20. Claim upkeep must belong to a valid claim
-- When a claim is deleted, its upkeep configuration is automatically removed
ALTER TABLE server_claim_upkeep
DROP FOREIGN KEY IF EXISTS fk_upkeep_claim;

ALTER TABLE server_claim_upkeep
ADD CONSTRAINT fk_upkeep_claim
FOREIGN KEY (claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 21. Unclaimed by upkeep audit trail must reference claim
-- When a claim is deleted, its upkeep audit history is automatically removed
ALTER TABLE server_unclaimed_by_upkeep
DROP FOREIGN KEY IF EXISTS fk_unclaimed_claim;

ALTER TABLE server_unclaimed_by_upkeep
ADD CONSTRAINT fk_unclaimed_claim
FOREIGN KEY (claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 22. Claim levels must belong to a valid claim
-- When a claim is deleted, its level data is automatically removed
ALTER TABLE server_claim_levels
DROP FOREIGN KEY IF EXISTS fk_level_claim;

ALTER TABLE server_claim_levels
ADD CONSTRAINT fk_level_claim
FOREIGN KEY (claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 23. Claim XP history must belong to a valid claim
-- When a claim is deleted, all its XP history is automatically removed
ALTER TABLE server_claim_xp_history
DROP FOREIGN KEY IF EXISTS fk_xp_history_claim;

ALTER TABLE server_claim_xp_history
ADD CONSTRAINT fk_xp_history_claim
FOREIGN KEY (claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 24. Claim benefits must belong to a valid claim
-- When a claim is deleted, its benefit cache is automatically removed
ALTER TABLE server_claim_benefits
DROP FOREIGN KEY IF EXISTS fk_benefits_claim;

ALTER TABLE server_claim_benefits
ADD CONSTRAINT fk_benefits_claim
FOREIGN KEY (claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 25. Claim playtime must belong to a valid claim
-- When a claim is deleted, all playtime tracking is automatically removed
ALTER TABLE server_claim_playtime
DROP FOREIGN KEY IF EXISTS fk_playtime_claim;

ALTER TABLE server_claim_playtime
ADD CONSTRAINT fk_playtime_claim
FOREIGN KEY (claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 26. Nation bank must belong to a valid nation
-- When a nation is deleted, its bank is automatically removed
ALTER TABLE server_nation_banks
DROP FOREIGN KEY IF EXISTS fk_nation_bank_nation;

ALTER TABLE server_nation_banks
ADD CONSTRAINT fk_nation_bank_nation
FOREIGN KEY (nation_id) REFERENCES server_nations(id)
ON DELETE CASCADE;

-- 27. Nation invites must reference a valid nation
-- When a nation is deleted, all its invites are automatically removed
ALTER TABLE server_nation_invites
DROP FOREIGN KEY IF EXISTS fk_invite_nation;

ALTER TABLE server_nation_invites
ADD CONSTRAINT fk_invite_nation
FOREIGN KEY (nation_id) REFERENCES server_nations(id)
ON DELETE CASCADE;

-- 28. Nation invites are now player-based (no claim foreign key needed)
-- Invites persist across claim changes and are tied to the player

-- 29. Nation relations must reference valid nations (both sides)
-- When a nation is deleted, all its relations are automatically removed
ALTER TABLE server_nation_relations
DROP FOREIGN KEY IF EXISTS fk_relation_nation;

ALTER TABLE server_nation_relations
ADD CONSTRAINT fk_relation_nation
FOREIGN KEY (nation_id) REFERENCES server_nations(id)
ON DELETE CASCADE;

ALTER TABLE server_nation_relations
DROP FOREIGN KEY IF EXISTS fk_relation_target_nation;

ALTER TABLE server_nation_relations
ADD CONSTRAINT fk_relation_target_nation
FOREIGN KEY (target_nation_id) REFERENCES server_nations(id)
ON DELETE CASCADE;

-- 30. Nation bank transactions must belong to a valid nation
-- When a nation is deleted, all its transaction history is automatically removed
ALTER TABLE server_nation_bank_transactions
DROP FOREIGN KEY IF EXISTS fk_nation_transaction_nation;

ALTER TABLE server_nation_bank_transactions
ADD CONSTRAINT fk_nation_transaction_nation
FOREIGN KEY (nation_id) REFERENCES server_nations(id)
ON DELETE CASCADE;

-- 31. War shields must reference valid nation or claim
-- When a nation/claim is deleted, its war shields are automatically removed
ALTER TABLE server_war_shields
DROP FOREIGN KEY IF EXISTS fk_shield_nation;

ALTER TABLE server_war_shields
ADD CONSTRAINT fk_shield_nation
FOREIGN KEY (nation_id) REFERENCES server_nations(id)
ON DELETE CASCADE;

ALTER TABLE server_war_shields
DROP FOREIGN KEY IF EXISTS fk_shield_claim;

ALTER TABLE server_war_shields
ADD CONSTRAINT fk_shield_claim
FOREIGN KEY (claim_id) REFERENCES server_claims(id)
ON DELETE CASCADE;

-- 32. War captures must belong to a valid war
-- When a war is deleted, all capture progress is automatically removed
ALTER TABLE server_war_captures
DROP FOREIGN KEY IF EXISTS fk_capture_war;

ALTER TABLE server_war_captures
ADD CONSTRAINT fk_capture_war
FOREIGN KEY (war_id) REFERENCES server_wars(id)
ON DELETE CASCADE;

-- 33. War captures must reference a valid capturing nation
-- When the capturing nation is deleted, capture progress is automatically removed
ALTER TABLE server_war_captures
DROP FOREIGN KEY IF EXISTS fk_capture_nation;

ALTER TABLE server_war_captures
ADD CONSTRAINT fk_capture_nation
FOREIGN KEY (capturing_nation_id) REFERENCES server_nations(id)
ON DELETE CASCADE;

-- 34. War tributes must belong to a valid war
-- When a war is deleted, all tribute offers are automatically removed
ALTER TABLE server_war_tributes
DROP FOREIGN KEY IF EXISTS fk_tribute_war;

ALTER TABLE server_war_tributes
ADD CONSTRAINT fk_tribute_war
FOREIGN KEY (war_id) REFERENCES server_wars(id)
ON DELETE CASCADE;

-- 35. War events must belong to a valid war
-- When a war is deleted, all event logs are automatically removed
ALTER TABLE server_war_events
DROP FOREIGN KEY IF EXISTS fk_event_war;

ALTER TABLE server_war_events
ADD CONSTRAINT fk_event_war
FOREIGN KEY (war_id) REFERENCES server_wars(id)
ON DELETE CASCADE;

-- 36. Chunk transfers can optionally reference claims
-- When a claim is deleted, transfer history shows NULL for that claim
ALTER TABLE server_chunk_transfers
DROP FOREIGN KEY IF EXISTS fk_transfer_from_claim;

ALTER TABLE server_chunk_transfers
ADD CONSTRAINT fk_transfer_from_claim
FOREIGN KEY (from_claim_id) REFERENCES server_claims(id)
ON DELETE SET NULL;

ALTER TABLE server_chunk_transfers
DROP FOREIGN KEY IF EXISTS fk_transfer_to_claim;

ALTER TABLE server_chunk_transfers
ADD CONSTRAINT fk_transfer_to_claim
FOREIGN KEY (to_claim_id) REFERENCES server_claims(id)
ON DELETE SET NULL;

-- 37. Audit log can optionally reference a claim
-- When a claim is deleted, audit logs are preserved with NULL claim_id for historical record
ALTER TABLE server_claim_audit_log
DROP FOREIGN KEY IF EXISTS fk_audit_claim;

ALTER TABLE server_claim_audit_log
ADD CONSTRAINT fk_audit_claim
FOREIGN KEY (claim_id) REFERENCES server_claims(id)
ON DELETE SET NULL;

-- ============================================================================
-- END OF FOREIGN KEY CONSTRAINTS
-- ============================================================================

-- ============================================================================
-- PHASE 8: Bulk Operations & Notification System (v3.1)
-- ============================================================================

-- Player notifications for claims, nations, upkeep, wars, and transfers
CREATE TABLE IF NOT EXISTS server_player_notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    type ENUM('NATION_INVITE', 'UPKEEP_WARNING', 'WAR_DECLARED', 'TRANSFER_RECEIVED', 'MEMBER_REMOVED', 'PERMISSION_CHANGED') NOT NULL,
    title VARCHAR(100) NOT NULL,
    message TEXT NOT NULL,
    priority ENUM('LOW', 'NORMAL', 'HIGH', 'URGENT') DEFAULT 'NORMAL',
    related_claim_id INT NULL,
    related_nation_id INT NULL,
    action_button TEXT NULL,
    expires_at TIMESTAMP DEFAULT NULL,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_player_unread (player_uuid, read_at),
    INDEX idx_expires (expires_at),
    INDEX idx_type (type),
    INDEX idx_created (created_at),
    FOREIGN KEY (related_claim_id) REFERENCES server_claims(id) ON DELETE CASCADE,
    FOREIGN KEY (related_nation_id) REFERENCES server_nations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- PHASE 8: Activity Log Enhancement and Statistics Dashboard (v3.1)
-- ============================================================================

-- Enhance audit log with activity tracking
ALTER TABLE server_claim_audit_log
    MODIFY COLUMN action_type ENUM(
        'PERMISSION_CHANGE',
        'ADMIN_COMMAND',
        'TRANSFER_OWNERSHIP',
        'DELETE_CLAIM',
        'ADD_MEMBER',
        'REMOVE_MEMBER',
        'BANK_DEPOSIT',
        'BANK_WITHDRAW',
        'SETTINGS_CHANGE',
        'CHUNK_ADD',
        'CHUNK_REMOVE'
    ) NULL,
    ADD COLUMN IF NOT EXISTS activity_type ENUM(
        'CLAIM_ACCESS',
        'CHUNK_PURCHASE',
        'CHUNK_UNCLAIM',
        'MEMBER_ADDED',
        'GROUP_CREATED',
        'GROUP_MODIFIED',
        'BANK_DEPOSIT',
        'BANK_WITHDRAW',
        'WARP_TELEPORT'
    ) NULL AFTER action_type,
    ADD COLUMN IF NOT EXISTS amount DECIMAL(10,2) NULL,
    ADD COLUMN IF NOT EXISTS old_value TEXT NULL,
    ADD COLUMN IF NOT EXISTS new_value TEXT NULL;

-- Add indexes for activity log performance
CREATE INDEX IF NOT EXISTS idx_activity_type ON server_claim_audit_log(activity_type, timestamp);
CREATE INDEX IF NOT EXISTS idx_claim_recent ON server_claim_audit_log(claim_id, timestamp DESC);

-- Statistics cache table for server-wide metrics
CREATE TABLE IF NOT EXISTS server_claim_stats_cache (
    id INT AUTO_INCREMENT PRIMARY KEY,
    stat_type ENUM('SERVER_OVERVIEW', 'TOP_OWNERS', 'ACTIVITY_METRICS') NOT NULL,
    data_json TEXT NOT NULL,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_stat (stat_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- MIGRATION: Bug Fixes & Unlimited Chunks (v4.0)
-- ============================================================================
-- This migration addresses critical bugs and enables unlimited chunks per profile

-- Track migrations
CREATE TABLE IF NOT EXISTS server_claim_migrations (
    migration_id VARCHAR(255) PRIMARY KEY,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    description TEXT
);

-- Update existing claims to unlimited mode (optional - depends on config)
-- Uncomment the line below if you want to enable unlimited chunks for all existing profiles
-- UPDATE server_claims SET total_chunks = 999999 WHERE total_chunks < 999999;

-- Sync data integrity: fix purchasedChunks to match actual chunks
-- This is handled automatically by syncChunkCounts() in Java code when claims are loaded

-- Drop deprecated profile tables if they exist and are empty
-- WARNING: Only run this if you've fully migrated from profiles to claims system
-- DELETE FROM server_profile_settings WHERE profile_id NOT IN (SELECT id FROM server_profiles);
-- DELETE FROM server_profiles WHERE claim_id NOT IN (SELECT id FROM server_claims);

-- Record migration
INSERT IGNORE INTO server_claim_migrations (migration_id, description)
VALUES ('v4.0_bug_fixes_unlimited', 'Fixed profile counting, pricing, data sync, and enabled unlimited chunks per profile');

-- ============================================================================
-- GLOBAL CHUNK POOL SYSTEM (v5.0)
-- ============================================================================
-- Tracks player-level purchased chunks (persists across claim/profile deletions)
-- SEMANTIC CHANGE: server_claims.purchased_chunks now means "chunks allocated FROM global pool TO this profile"
-- NOT "chunks purchased FOR this profile" - actual purchased chunks are stored here
CREATE TABLE IF NOT EXISTS server_player_chunk_pool (
    player_uuid VARCHAR(36) PRIMARY KEY,
    purchased_chunks INT DEFAULT 0,              -- Total chunks purchased (global pool)
    total_spent DECIMAL(15,2) DEFAULT 0.00,     -- Total money spent on chunks
    last_purchase TIMESTAMP DEFAULT NULL,        -- Last purchase time
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_purchased (purchased_chunks DESC),
    INDEX idx_last_purchase (last_purchase)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================================
-- MIGRATION: Fix Global Color Priority System (v6.1)
-- ============================================================================
-- Change default values from 'WHITE' to NULL to enable proper fallback priority
-- NULL = no global setting, allows profiles without overrides to use claim defaults
-- 'WHITE' = explicitly set to white, overrides claim defaults

-- Update existing 'WHITE' values to NULL for proper global color fallback
-- This allows profiles without custom colors to use global colors correctly
ALTER TABLE server_player_rewards
    MODIFY COLUMN selected_dust_effect VARCHAR(32) DEFAULT NULL,
    MODIFY COLUMN selected_profile_color VARCHAR(16) DEFAULT NULL;

-- Optional: Uncomment to convert existing 'WHITE' entries to NULL
-- This enables the global color system for existing players
-- UPDATE server_player_rewards SET selected_dust_effect = NULL WHERE selected_dust_effect = 'WHITE';
-- UPDATE server_player_rewards SET selected_profile_color = NULL WHERE selected_profile_color = 'WHITE';

-- Record migration
INSERT IGNORE INTO server_claim_migrations (migration_id, description)
VALUES ('v6.1_global_color_priority', 'Fixed global color priority system by using NULL defaults instead of WHITE defaults');

-- ============================================================================
-- END OF SCHEMA
-- ============================================================================

