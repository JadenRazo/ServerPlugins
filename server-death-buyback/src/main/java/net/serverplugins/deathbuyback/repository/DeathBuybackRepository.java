package net.serverplugins.deathbuyback.repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.serverplugins.api.database.Database;
import net.serverplugins.deathbuyback.models.DeathInventory;

public class DeathBuybackRepository {

    private final Database database;

    public DeathBuybackRepository(Database database) {
        this.database = database;
    }

    public void createTables() {
        database.execute(
                """
            CREATE TABLE IF NOT EXISTS server_death_inventories (
                id INT AUTO_INCREMENT PRIMARY KEY,
                player_uuid VARCHAR(36) NOT NULL,
                player_name VARCHAR(16),
                world VARCHAR(64) NOT NULL,
                death_x DOUBLE NOT NULL,
                death_y DOUBLE NOT NULL,
                death_z DOUBLE NOT NULL,
                death_cause VARCHAR(128),
                inventory_data MEDIUMTEXT NOT NULL,
                armor_data TEXT,
                offhand_data TEXT,
                xp_levels INT DEFAULT 0,
                base_worth DOUBLE NOT NULL,
                buyback_price DOUBLE NOT NULL,
                item_count INT DEFAULT 0,
                died_at BIGINT NOT NULL,
                expires_at BIGINT NOT NULL,
                purchased BOOLEAN DEFAULT FALSE,
                purchased_at BIGINT DEFAULT NULL
            )
        """);

        // Create indexes
        try {
            database.execute(
                    "CREATE INDEX IF NOT EXISTS idx_death_player ON server_death_inventories (player_uuid)");
            database.execute(
                    "CREATE INDEX IF NOT EXISTS idx_death_expires ON server_death_inventories (expires_at)");
            database.execute(
                    "CREATE INDEX IF NOT EXISTS idx_death_active ON server_death_inventories (player_uuid, purchased)");
        } catch (Exception ignored) {
            // Some databases don't support IF NOT EXISTS for indexes
        }
    }

    public void saveDeathInventory(DeathInventory death) {
        try {
            int affected =
                    database.executeUpdate(
                            """
                INSERT INTO server_death_inventories
                (player_uuid, player_name, world, death_x, death_y, death_z, death_cause,
                 inventory_data, armor_data, offhand_data, xp_levels, base_worth, buyback_price,
                 item_count, died_at, expires_at, purchased)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                            death.getPlayerUuid().toString(),
                            death.getPlayerName(),
                            death.getWorld(),
                            death.getDeathX(),
                            death.getDeathY(),
                            death.getDeathZ(),
                            death.getDeathCause(),
                            death.getInventoryData(),
                            death.getArmorData(),
                            death.getOffhandData(),
                            death.getXpLevels(),
                            death.getBaseWorth(),
                            death.getBuybackPrice(),
                            death.getItemCount(),
                            death.getDiedAt(),
                            death.getExpiresAt(),
                            death.isPurchased());

            if (affected > 0) {
                // Get the generated ID
                database.query(
                        "SELECT MAX(id) as id FROM server_death_inventories WHERE player_uuid = ?",
                        rs -> {
                            if (rs.next()) death.setId(rs.getInt("id"));
                            return null;
                        },
                        death.getPlayerUuid().toString());
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save death inventory", e);
        }
    }

    public List<DeathInventory> getActiveInventories(UUID playerUuid) {
        return database.query(
                """
            SELECT * FROM server_death_inventories
            WHERE player_uuid = ? AND purchased = FALSE AND expires_at > ?
            ORDER BY died_at ASC
            """,
                rs -> {
                    List<DeathInventory> inventories = new ArrayList<>();
                    while (rs.next()) {
                        inventories.add(mapDeathInventory(rs));
                    }
                    return inventories;
                },
                playerUuid.toString(),
                System.currentTimeMillis());
    }

    public DeathInventory getById(int id) {
        return database.query(
                "SELECT * FROM server_death_inventories WHERE id = ?",
                rs -> {
                    if (rs.next()) {
                        return mapDeathInventory(rs);
                    }
                    return null;
                },
                id);
    }

    public void markAsPurchased(int id) {
        database.execute(
                "UPDATE server_death_inventories SET purchased = TRUE, purchased_at = ? WHERE id = ?",
                System.currentTimeMillis(),
                id);
    }

    public void deleteInventory(int id) {
        database.execute("DELETE FROM server_death_inventories WHERE id = ?", id);
    }

    public int deleteExpiredInventories() {
        try {
            return database.executeUpdate(
                    "DELETE FROM server_death_inventories WHERE expires_at < ? AND purchased = FALSE",
                    System.currentTimeMillis());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete expired inventories", e);
        }
    }

    public int deletePlayerInventories(UUID playerUuid) {
        try {
            return database.executeUpdate(
                    "DELETE FROM server_death_inventories WHERE player_uuid = ?",
                    playerUuid.toString());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete player inventories", e);
        }
    }

    public DeathInventory getOldestActiveInventory(UUID playerUuid) {
        return database.query(
                """
            SELECT * FROM server_death_inventories
            WHERE player_uuid = ? AND purchased = FALSE AND expires_at > ?
            ORDER BY died_at ASC
            LIMIT 1
            """,
                rs -> {
                    if (rs.next()) {
                        return mapDeathInventory(rs);
                    }
                    return null;
                },
                playerUuid.toString(),
                System.currentTimeMillis());
    }

    public int getActiveInventoryCount(UUID playerUuid) {
        return database.query(
                """
            SELECT COUNT(*) as count FROM server_death_inventories
            WHERE player_uuid = ? AND purchased = FALSE AND expires_at > ?
            """,
                rs -> rs.next() ? rs.getInt("count") : 0,
                playerUuid.toString(),
                System.currentTimeMillis());
    }

    private DeathInventory mapDeathInventory(java.sql.ResultSet rs) throws java.sql.SQLException {
        Long purchasedAt = rs.getLong("purchased_at");
        if (rs.wasNull()) purchasedAt = null;

        return new DeathInventory(
                rs.getInt("id"),
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("player_name"),
                rs.getString("world"),
                rs.getDouble("death_x"),
                rs.getDouble("death_y"),
                rs.getDouble("death_z"),
                rs.getString("death_cause"),
                rs.getString("inventory_data"),
                rs.getString("armor_data"),
                rs.getString("offhand_data"),
                rs.getInt("xp_levels"),
                rs.getDouble("base_worth"),
                rs.getDouble("buyback_price"),
                rs.getInt("item_count"),
                rs.getLong("died_at"),
                rs.getLong("expires_at"),
                rs.getBoolean("purchased"),
                purchasedAt);
    }
}
