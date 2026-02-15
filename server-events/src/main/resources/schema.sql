-- ServerEvents Database Schema (MariaDB/MySQL Compatible)

-- Player event statistics
CREATE TABLE IF NOT EXISTS server_event_stats (
    uuid VARCHAR(36) PRIMARY KEY,
    username VARCHAR(16) NOT NULL,
    wins INT DEFAULT 0,
    participations INT DEFAULT 0,
    coins_earned BIGINT DEFAULT 0,
    keys_earned INT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_wins (wins DESC)
);

-- Keyall distribution log
CREATE TABLE IF NOT EXISTS server_keyall_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    key_type VARCHAR(32) NOT NULL,
    key_name VARCHAR(32) NOT NULL,
    amount INT NOT NULL,
    player_count INT NOT NULL,
    distributed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_distributed (distributed_at DESC)
);

-- Event history
CREATE TABLE IF NOT EXISTS server_event_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL,
    winner_uuid VARCHAR(36) NULL,
    winner_name VARCHAR(16) NULL,
    participant_count INT DEFAULT 0,
    ended_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_winner (winner_uuid),
    INDEX idx_type (event_type),
    INDEX idx_ended (ended_at DESC)
);
