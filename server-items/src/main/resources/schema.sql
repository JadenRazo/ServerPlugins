-- ServerItems Database Schema

-- Placed custom blocks (NoteBlock state hijacking)
CREATE TABLE IF NOT EXISTS server_placed_blocks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    world VARCHAR(64) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    block_id VARCHAR(64) NOT NULL,
    placed_by VARCHAR(36),
    placed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_location (world, x, y, z),
    INDEX idx_chunk (world, x, z)
);

-- NoteBlock state assignments (persistent across reloads)
CREATE TABLE IF NOT EXISTS server_block_state_map (
    item_id VARCHAR(64) NOT NULL PRIMARY KEY,
    instrument VARCHAR(32) NOT NULL,
    note INT NOT NULL,
    powered BOOLEAN NOT NULL DEFAULT FALSE
);

-- Placed furniture (Display Entity based)
CREATE TABLE IF NOT EXISTS server_furniture (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    display_uuid VARCHAR(36) NOT NULL UNIQUE,
    interaction_uuid VARCHAR(36),
    furniture_id VARCHAR(64) NOT NULL,
    world VARCHAR(64) NOT NULL,
    x DOUBLE NOT NULL,
    y DOUBLE NOT NULL,
    z DOUBLE NOT NULL,
    yaw FLOAT NOT NULL DEFAULT 0,
    placed_by VARCHAR(36),
    placed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_chunk (world, x, z)
);
