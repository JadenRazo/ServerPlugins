-- ServerArcade Database Schema (MariaDB/MySQL Compatible)

-- Arcade machine persistence
CREATE TABLE IF NOT EXISTS server_arcade_machines (
    id VARCHAR(36) PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    direction VARCHAR(16) NOT NULL DEFAULT 'SOUTH',
    placed_by VARCHAR(36) NOT NULL,
    placed_at BIGINT NOT NULL,
    active BOOLEAN DEFAULT TRUE,
    blocks_data TEXT,
    INDEX idx_location (world, x, y, z),
    INDEX idx_type (type)
);

-- Gambling statistics tracking
CREATE TABLE IF NOT EXISTS server_arcade_stats (
    player_uuid VARCHAR(36) PRIMARY KEY,
    player_name VARCHAR(32),

    -- Crash stats
    crash_total_bets INT DEFAULT 0,
    crash_total_wins INT DEFAULT 0,
    crash_biggest_win INT DEFAULT 0,
    crash_highest_mult DOUBLE DEFAULT 0,

    -- Lottery stats
    lottery_total_bets INT DEFAULT 0,
    lottery_total_wins INT DEFAULT 0,
    lottery_biggest_win INT DEFAULT 0,

    -- Dice stats
    dice_total_bets INT DEFAULT 0,
    dice_total_wins INT DEFAULT 0,
    dice_biggest_win INT DEFAULT 0,

    -- Overall
    total_wagered BIGINT DEFAULT 0,
    total_won BIGINT DEFAULT 0,
    total_lost BIGINT DEFAULT 0,
    net_profit BIGINT DEFAULT 0,

    -- Streaks
    current_streak INT DEFAULT 0,
    best_win_streak INT DEFAULT 0,
    worst_loss_streak INT DEFAULT 0,

    last_updated BIGINT,

    INDEX idx_net_profit (net_profit DESC),
    INDEX idx_crash_mult (crash_highest_mult DESC),
    INDEX idx_biggest_win (crash_biggest_win DESC)
);

-- Gambling history
CREATE TABLE IF NOT EXISTS server_arcade_history (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_uuid VARCHAR(36),
    game_type VARCHAR(32),
    bet_amount INT,
    payout INT,
    multiplier DOUBLE,
    won BOOLEAN,
    timestamp BIGINT,

    INDEX idx_player (player_uuid),
    INDEX idx_game (game_type),
    INDEX idx_timestamp (timestamp DESC)
);

-- Self-exclusion system
CREATE TABLE IF NOT EXISTS server_arcade_exclusions (
    player_uuid VARCHAR(36) PRIMARY KEY,
    player_name VARCHAR(32),
    excluded_at BIGINT,
    excluded_until BIGINT,
    is_permanent BOOLEAN DEFAULT FALSE,
    reason VARCHAR(255),

    INDEX idx_active (excluded_until)
);

-- Command access tiers
CREATE TABLE IF NOT EXISTS server_arcade_access (
    player_uuid VARCHAR(36) PRIMARY KEY,
    access_tier VARCHAR(16),  -- basic, premium, vip
    purchased_at BIGINT,

    INDEX idx_tier (access_tier)
);

-- Machine ownership
CREATE TABLE IF NOT EXISTS server_arcade_ownership (
    machine_id VARCHAR(36) PRIMARY KEY,
    owner_uuid VARCHAR(36) NOT NULL,
    machine_type VARCHAR(32) NOT NULL,
    purchase_price INT,
    total_revenue INT DEFAULT 0,
    placed_at BIGINT,

    INDEX idx_owner (owner_uuid),
    FOREIGN KEY (machine_id) REFERENCES server_arcade_machines(id) ON DELETE CASCADE
);
