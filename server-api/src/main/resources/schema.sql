-- Gems premium currency table
CREATE TABLE IF NOT EXISTS server_gems (
    player_uuid VARCHAR(36) PRIMARY KEY,
    balance INT NOT NULL DEFAULT 0,
    total_earned BIGINT NOT NULL DEFAULT 0,
    total_spent BIGINT NOT NULL DEFAULT 0,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_gems_balance ON server_gems(balance DESC);
