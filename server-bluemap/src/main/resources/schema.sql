-- BlueMap Points of Interest table
CREATE TABLE IF NOT EXISTS server_bluemap_poi (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    world VARCHAR(50) NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    category VARCHAR(30) NOT NULL DEFAULT 'landmark',
    description TEXT,
    creator_uuid VARCHAR(36) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    visible BOOLEAN DEFAULT TRUE,
    INDEX idx_world (world),
    INDEX idx_category (category),
    INDEX idx_creator (creator_uuid),
    INDEX idx_visible (visible)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
