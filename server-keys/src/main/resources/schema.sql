-- ServerKeys Database Schema

-- Player key statistics
CREATE TABLE IF NOT EXISTS server_key_stats (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL,
    username VARCHAR(16) NOT NULL,
    key_type VARCHAR(16) NOT NULL,
    key_name VARCHAR(32) NOT NULL,
    total_received INT DEFAULT 0,
    total_used INT DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_player_key (uuid, key_type, key_name),
    INDEX idx_uuid (uuid)
);

-- Key transaction history
CREATE TABLE IF NOT EXISTS server_key_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL,
    username VARCHAR(16) NOT NULL,
    key_type VARCHAR(16) NOT NULL,
    key_name VARCHAR(32) NOT NULL,
    amount INT NOT NULL,
    action VARCHAR(16) NOT NULL,
    source VARCHAR(64) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_uuid (uuid),
    INDEX idx_created (created_at DESC)
);

-- Unclaimed keys storage (for overflow when inventory is full)
CREATE TABLE IF NOT EXISTS server_key_unclaimed (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL,
    username VARCHAR(16) NOT NULL,
    key_type VARCHAR(16) NOT NULL,
    key_name VARCHAR(32) NOT NULL,
    amount INT NOT NULL,
    source VARCHAR(64) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    claimed BOOLEAN DEFAULT FALSE,
    claimed_at TIMESTAMP NULL,
    INDEX idx_unclaimed_uuid (uuid),
    INDEX idx_unclaimed_pending (uuid, claimed)
);
