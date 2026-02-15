-- ServerBounty Database Schema (MariaDB/MySQL Compatible)

-- Active bounties on players
CREATE TABLE IF NOT EXISTS server_bounties (
    id INT AUTO_INCREMENT PRIMARY KEY,
    target_uuid VARCHAR(36) NOT NULL,
    target_name VARCHAR(16) NOT NULL,
    total_amount DOUBLE NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_target (target_uuid),
    INDEX idx_amount (total_amount DESC)
);

-- Individual bounty contributions
CREATE TABLE IF NOT EXISTS server_bounty_contributions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    bounty_id INT NOT NULL,
    placer_uuid VARCHAR(36) NOT NULL,
    placer_name VARCHAR(16) NOT NULL,
    amount DOUBLE NOT NULL,
    tax_paid DOUBLE DEFAULT 0,
    placed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_bounty (bounty_id),
    INDEX idx_placer (placer_uuid)
);

-- Claimed bounties history
CREATE TABLE IF NOT EXISTS server_bounty_claims (
    id INT AUTO_INCREMENT PRIMARY KEY,
    killer_uuid VARCHAR(36) NOT NULL,
    killer_name VARCHAR(16) NOT NULL,
    victim_uuid VARCHAR(36) NOT NULL,
    victim_name VARCHAR(16) NOT NULL,
    amount_claimed DOUBLE NOT NULL,
    kill_world VARCHAR(64),
    kill_x DOUBLE,
    kill_y DOUBLE,
    kill_z DOUBLE,
    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_killer (killer_uuid),
    INDEX idx_victim (victim_uuid),
    INDEX idx_claimed_at (claimed_at)
);

-- Trophy heads awaiting pickup
CREATE TABLE IF NOT EXISTS server_bounty_heads (
    id INT AUTO_INCREMENT PRIMARY KEY,
    owner_uuid VARCHAR(36) NOT NULL,
    victim_uuid VARCHAR(36) NOT NULL,
    victim_name VARCHAR(16) NOT NULL,
    bounty_amount DOUBLE NOT NULL,
    head_data TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    claimed BOOLEAN DEFAULT FALSE,
    claimed_at TIMESTAMP DEFAULT NULL,
    INDEX idx_owner (owner_uuid),
    INDEX idx_expires (expires_at),
    INDEX idx_unclaimed (owner_uuid, claimed)
);
