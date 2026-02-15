-- ServerCommands Database Schema (MariaDB/MySQL Compatible)

-- Player homes
CREATE TABLE IF NOT EXISTS server_homes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    uuid VARCHAR(36) NOT NULL,
    name VARCHAR(32) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT DEFAULT 0,
    pitch FLOAT DEFAULT 0,
    icon VARCHAR(64) DEFAULT 'RED_BED',
    description VARCHAR(256) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_home (uuid, name),
    INDEX idx_uuid (uuid)
);

-- Migration: Add icon and description columns if missing (for existing installations)
-- These will silently fail if columns already exist
ALTER TABLE server_homes ADD COLUMN IF NOT EXISTS icon VARCHAR(64) DEFAULT 'RED_BED';
ALTER TABLE server_homes ADD COLUMN IF NOT EXISTS description VARCHAR(256) DEFAULT NULL;

-- Server warps
CREATE TABLE IF NOT EXISTS server_warps (
    name VARCHAR(32) PRIMARY KEY,
    world VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT DEFAULT 0,
    pitch FLOAT DEFAULT 0,
    created_by VARCHAR(36),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Player mutes
CREATE TABLE IF NOT EXISTS server_mutes (
    uuid VARCHAR(36) PRIMARY KEY,
    muted_by VARCHAR(36),
    reason VARCHAR(256),
    expires_at TIMESTAMP NULL,
    muted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Player playtime tracking
CREATE TABLE IF NOT EXISTS server_playtime (
    uuid VARCHAR(36) PRIMARY KEY,
    username VARCHAR(16),
    total_seconds BIGINT DEFAULT 0,
    last_join TIMESTAMP NULL,
    last_quit TIMESTAMP NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Player data (warnings, modes, last location)
CREATE TABLE IF NOT EXISTS server_player_data (
    uuid VARCHAR(36) PRIMARY KEY,
    username VARCHAR(16),
    warnings INT DEFAULT 0,
    fly_enabled BOOLEAN DEFAULT FALSE,
    god_mode BOOLEAN DEFAULT FALSE,
    survival_guide_enabled BOOLEAN DEFAULT TRUE,
    last_world VARCHAR(64) NULL,
    last_x DOUBLE NULL,
    last_y DOUBLE NULL,
    last_z DOUBLE NULL,
    last_yaw FLOAT NULL,
    last_pitch FLOAT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Migration: Add survival_guide_enabled column if missing
ALTER TABLE server_player_data ADD COLUMN IF NOT EXISTS survival_guide_enabled BOOLEAN DEFAULT TRUE;
