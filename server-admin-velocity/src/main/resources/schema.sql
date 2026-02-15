-- ServerAdmin Velocity Database Schema
-- These tables are shared with server-admin (Bukkit) and store network-wide punishment data

-- Punishment records table
CREATE TABLE IF NOT EXISTS server_punishments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    target_uuid VARCHAR(36) NOT NULL,
    target_name VARCHAR(16),
    staff_uuid VARCHAR(36),
    staff_name VARCHAR(16),
    type VARCHAR(20) NOT NULL,
    category VARCHAR(30),
    reason_id VARCHAR(50),
    offense_number INT,
    reason VARCHAR(255),
    duration_ms BIGINT,
    issued_at BIGINT NOT NULL,
    expires_at BIGINT,
    active BOOLEAN DEFAULT TRUE,
    pardoned_by_uuid VARCHAR(36),
    pardoned_by_name VARCHAR(16),
    pardoned_at BIGINT,
    pardon_reason VARCHAR(255),
    source_server VARCHAR(30) DEFAULT NULL,
    INDEX idx_punishments_target (target_uuid),
    INDEX idx_punishments_active (active),
    INDEX idx_punishments_type (type),
    INDEX idx_punishments_expires (expires_at)
);

-- Player IP tracking table for alt detection
CREATE TABLE IF NOT EXISTS server_player_ips (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL,
    ip_hash VARCHAR(64) NOT NULL,
    username VARCHAR(16),
    first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_ip_player UNIQUE (uuid, ip_hash),
    INDEX idx_player_ips_uuid (uuid),
    INDEX idx_player_ips_hash (ip_hash)
);

-- Category-based offense tracking table (used by Bukkit side)
CREATE TABLE IF NOT EXISTS server_player_offenses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    category VARCHAR(30) NOT NULL,
    offense_count INT DEFAULT 0,
    last_offense_at BIGINT,
    CONSTRAINT unique_player_category UNIQUE (player_uuid, category),
    INDEX idx_offenses_player (player_uuid)
);

-- Reason-based offense tracking table (used by Bukkit side)
CREATE TABLE IF NOT EXISTS server_player_reason_offenses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    reason_id VARCHAR(50) NOT NULL,
    offense_count INT DEFAULT 0,
    last_offense_at BIGINT,
    CONSTRAINT unique_player_reason UNIQUE (player_uuid, reason_id),
    INDEX idx_reason_offenses_player (player_uuid)
);

-- Player data reset log table (used by Bukkit side)
CREATE TABLE IF NOT EXISTS server_reset_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    target_uuid VARCHAR(36) NOT NULL,
    target_name VARCHAR(16),
    staff_uuid VARCHAR(36),
    staff_name VARCHAR(16),
    reset_type VARCHAR(30) NOT NULL,
    details TEXT,
    reset_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_reset_log_target (target_uuid)
);
