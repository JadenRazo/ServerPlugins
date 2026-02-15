CREATE TABLE IF NOT EXISTS enchanter_unlocks (
    player_uuid VARCHAR(36) NOT NULL,
    enchantment_id VARCHAR(64) NOT NULL,
    current_level INT DEFAULT 1,
    unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (player_uuid, enchantment_id)
);

CREATE TABLE IF NOT EXISTS enchanter_progression (
    player_uuid VARCHAR(36) PRIMARY KEY,
    level INT DEFAULT 1,
    experience INT DEFAULT 0,
    total_fragments INT DEFAULT 0,
    lifetime_games_played INT DEFAULT 0,
    lifetime_games_won INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS enchanter_daily_attempts (
    player_uuid VARCHAR(36) PRIMARY KEY,
    last_free_attempt DATE NULL,
    attempts_remaining INT DEFAULT 1
);

CREATE TABLE IF NOT EXISTS enchanter_game_stats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36) NOT NULL,
    game_type VARCHAR(32) NOT NULL,
    tier VARCHAR(16) NOT NULL,
    won BOOLEAN NOT NULL,
    score INT DEFAULT 0,
    fragments_earned INT DEFAULT 0,
    played_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_player (player_uuid)
);
