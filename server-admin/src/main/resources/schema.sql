-- ServerAdmin Database Schema (H2 and MariaDB/MySQL Compatible)

-- Player IP tracking for alt detection
CREATE TABLE IF NOT EXISTS server_player_ips (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL,
    ip_hash VARCHAR(64) NOT NULL,
    username VARCHAR(16),
    first_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_ip_player UNIQUE (uuid, ip_hash)
);

CREATE INDEX IF NOT EXISTS idx_player_ips_ip ON server_player_ips (ip_hash);
CREATE INDEX IF NOT EXISTS idx_player_ips_uuid ON server_player_ips (uuid);

-- Unified punishment records
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
    pardon_reason VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_punishments_target ON server_punishments (target_uuid);
CREATE INDEX IF NOT EXISTS idx_punishments_active ON server_punishments (active, type);
CREATE INDEX IF NOT EXISTS idx_punishments_expires ON server_punishments (expires_at);
CREATE INDEX IF NOT EXISTS idx_punishments_reason ON server_punishments (reason_id);

-- Player offense counts per category for progressive escalation (legacy)
CREATE TABLE IF NOT EXISTS server_player_offenses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    category VARCHAR(30) NOT NULL,
    offense_count INT DEFAULT 0,
    last_offense_at BIGINT,
    CONSTRAINT unique_player_category UNIQUE (player_uuid, category)
);

CREATE INDEX IF NOT EXISTS idx_player_offenses_player ON server_player_offenses (player_uuid);

-- Player offense counts per reason for progressive escalation
CREATE TABLE IF NOT EXISTS server_player_reason_offenses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    reason_id VARCHAR(50) NOT NULL,
    offense_count INT DEFAULT 0,
    last_offense_at BIGINT,
    CONSTRAINT unique_player_reason UNIQUE (player_uuid, reason_id)
);

CREATE INDEX IF NOT EXISTS idx_player_reason_offenses_player ON server_player_reason_offenses (player_uuid);

-- Player data reset audit log
CREATE TABLE IF NOT EXISTS server_reset_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    target_uuid VARCHAR(36) NOT NULL,
    target_name VARCHAR(16),
    staff_uuid VARCHAR(36),
    staff_name VARCHAR(16),
    reset_type VARCHAR(30) NOT NULL,
    details TEXT,
    reset_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_reset_log_target ON server_reset_log (target_uuid);
CREATE INDEX IF NOT EXISTS idx_reset_log_reset_at ON server_reset_log (reset_at);
