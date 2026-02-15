package net.serverplugins.bounty.repository;

import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.serverplugins.api.database.Database;
import net.serverplugins.bounty.models.Bounty;
import net.serverplugins.bounty.models.Contribution;
import net.serverplugins.bounty.models.TrophyHead;

public class BountyRepository {

    private final Database database;
    private final Logger logger;

    public BountyRepository(Database database, Logger logger) {
        this.database = database;
        this.logger = logger;
    }

    public void createTables(InputStream schemaStream) {
        if (schemaStream == null) {
            logger.warning("schema.sql not found, creating tables manually");
            createTablesManually();
            return;
        }

        try {
            String sql = new String(schemaStream.readAllBytes());
            String[] statements = sql.split(";");
            for (String statement : statements) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    database.execute(trimmed);
                }
            }
            logger.info("Bounty database tables created successfully");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to read schema.sql", e);
            createTablesManually();
        }
    }

    private void createTablesManually() {
        database.execute(
                """
            CREATE TABLE IF NOT EXISTS server_bounties (
                id INT AUTO_INCREMENT PRIMARY KEY,
                target_uuid VARCHAR(36) NOT NULL,
                target_name VARCHAR(16) NOT NULL,
                total_amount DOUBLE NOT NULL DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                UNIQUE KEY unique_target (target_uuid)
            )
        """);

        database.execute(
                """
            CREATE TABLE IF NOT EXISTS server_bounty_contributions (
                id INT AUTO_INCREMENT PRIMARY KEY,
                bounty_id INT NOT NULL,
                placer_uuid VARCHAR(36) NOT NULL,
                placer_name VARCHAR(16) NOT NULL,
                amount DOUBLE NOT NULL,
                tax_paid DOUBLE DEFAULT 0,
                placed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

        database.execute(
                """
            CREATE TABLE IF NOT EXISTS server_bounty_claims (
                id INT AUTO_INCREMENT PRIMARY KEY,
                killer_uuid VARCHAR(36) NOT NULL,
                killer_name VARCHAR(16) NOT NULL,
                victim_uuid VARCHAR(36) NOT NULL,
                victim_name VARCHAR(16) NOT NULL,
                amount_claimed DOUBLE NOT NULL,
                kill_world VARCHAR(64),
                kill_x DOUBLE,
                kill_y DOUBLE,
                kill_z DOUBLE,
                claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """);

        database.execute(
                """
            CREATE TABLE IF NOT EXISTS server_bounty_heads (
                id INT AUTO_INCREMENT PRIMARY KEY,
                owner_uuid VARCHAR(36) NOT NULL,
                victim_uuid VARCHAR(36) NOT NULL,
                victim_name VARCHAR(16) NOT NULL,
                bounty_amount DOUBLE NOT NULL,
                head_data TEXT NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                expires_at TIMESTAMP NOT NULL,
                claimed BOOLEAN DEFAULT FALSE,
                claimed_at TIMESTAMP DEFAULT NULL
            )
        """);

        try {
            database.execute(
                    "CREATE INDEX IF NOT EXISTS idx_bounties_amount ON server_bounties (total_amount DESC)");
            database.execute(
                    "CREATE INDEX IF NOT EXISTS idx_contributions_bounty ON server_bounty_contributions (bounty_id)");
            database.execute(
                    "CREATE INDEX IF NOT EXISTS idx_heads_owner ON server_bounty_heads (owner_uuid)");
            database.execute(
                    "CREATE INDEX IF NOT EXISTS idx_heads_expires ON server_bounty_heads (expires_at)");
        } catch (Exception ignored) {
            // Some databases don't support IF NOT EXISTS for indexes
        }
    }

    // ==================== Bounty Operations ====================

    public Bounty getActiveBounty(UUID targetUuid) {
        return database.query(
                "SELECT * FROM server_bounties WHERE target_uuid = ?",
                rs -> {
                    if (rs.next()) {
                        return mapBounty(rs);
                    }
                    return null;
                },
                targetUuid.toString());
    }

    public List<Bounty> getActiveBounties() {
        return database.query(
                "SELECT * FROM server_bounties ORDER BY total_amount DESC",
                rs -> {
                    List<Bounty> bounties = new ArrayList<>();
                    while (rs.next()) {
                        bounties.add(mapBounty(rs));
                    }
                    return bounties;
                });
    }

    public List<Bounty> getTopBounties(int limit) {
        return database.query(
                "SELECT * FROM server_bounties ORDER BY total_amount DESC LIMIT ?",
                rs -> {
                    List<Bounty> bounties = new ArrayList<>();
                    while (rs.next()) {
                        bounties.add(mapBounty(rs));
                    }
                    return bounties;
                },
                limit);
    }

    public Bounty createBounty(UUID targetUuid, String targetName) {
        try {
            int affected =
                    database.executeUpdate(
                            "INSERT INTO server_bounties (target_uuid, target_name, total_amount) VALUES (?, ?, 0)",
                            targetUuid.toString(),
                            targetName);

            if (affected > 0) {
                return getActiveBounty(targetUuid);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create bounty for " + targetName, e);
        }
        return null;
    }

    public void updateBountyTotal(int bountyId, double newTotal) {
        database.execute(
                "UPDATE server_bounties SET total_amount = ? WHERE id = ?", newTotal, bountyId);
    }

    public void deleteBounty(int bountyId) {
        database.execute("DELETE FROM server_bounties WHERE id = ?", bountyId);
    }

    // ==================== Contribution Operations ====================

    public void addContribution(int bountyId, UUID placerUuid, String placerName, double amount) {
        addContribution(bountyId, placerUuid, placerName, amount, 0);
    }

    public void addContribution(
            int bountyId, UUID placerUuid, String placerName, double amount, double taxPaid) {
        try {
            database.executeUpdate(
                    """
                INSERT INTO server_bounty_contributions
                (bounty_id, placer_uuid, placer_name, amount, tax_paid)
                VALUES (?, ?, ?, ?, ?)
                """,
                    bountyId,
                    placerUuid.toString(),
                    placerName,
                    amount,
                    taxPaid);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to add contribution", e);
        }
    }

    public List<Contribution> getContributions(int bountyId) {
        return database.query(
                "SELECT * FROM server_bounty_contributions WHERE bounty_id = ? ORDER BY amount DESC",
                rs -> {
                    List<Contribution> contributions = new ArrayList<>();
                    while (rs.next()) {
                        contributions.add(mapContribution(rs));
                    }
                    return contributions;
                },
                bountyId);
    }

    public Contribution getTopContribution(int bountyId) {
        return database.query(
                "SELECT * FROM server_bounty_contributions WHERE bounty_id = ? ORDER BY amount DESC LIMIT 1",
                rs -> {
                    if (rs.next()) {
                        return mapContribution(rs);
                    }
                    return null;
                },
                bountyId);
    }

    // ==================== History/Claims Operations ====================

    public void recordHistory(
            UUID victimUuid,
            String victimName,
            UUID killerUuid,
            String killerName,
            double amountClaimed,
            String killWorld,
            double killX,
            double killY,
            double killZ) {
        try {
            database.executeUpdate(
                    """
                INSERT INTO server_bounty_claims
                (killer_uuid, killer_name, victim_uuid, victim_name, amount_claimed,
                 kill_world, kill_x, kill_y, kill_z)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                    killerUuid.toString(),
                    killerName,
                    victimUuid.toString(),
                    victimName,
                    amountClaimed,
                    killWorld,
                    killX,
                    killY,
                    killZ);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to record bounty claim", e);
        }
    }

    // Overload for simpler usage without location
    public void recordHistory(
            UUID targetUuid,
            String targetName,
            UUID killerUuid,
            String killerName,
            UUID topContributorUuid,
            String topContributorName,
            double totalAmount,
            double killerPayout) {
        recordHistory(targetUuid, targetName, killerUuid, killerName, killerPayout, null, 0, 0, 0);
    }

    // ==================== Trophy Head Operations ====================

    public void createTrophyHead(
            UUID ownerUuid,
            UUID victimUuid,
            String victimName,
            double bountyAmount,
            String headData,
            Instant expiresAt) {
        try {
            database.executeUpdate(
                    """
                INSERT INTO server_bounty_heads
                (owner_uuid, victim_uuid, victim_name, bounty_amount, head_data, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                    ownerUuid.toString(),
                    victimUuid.toString(),
                    victimName,
                    bountyAmount,
                    headData,
                    Timestamp.from(expiresAt));
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to create trophy head", e);
        }
    }

    // Legacy overload for backward compatibility
    public void createTrophyHead(
            UUID ownerUuid,
            String ownerName,
            UUID victimUuid,
            String victimName,
            double bountyAmount,
            Instant expiresAt) {
        createTrophyHead(ownerUuid, victimUuid, victimName, bountyAmount, "", expiresAt);
    }

    public List<TrophyHead> getUnclaimedHeads(UUID ownerUuid) {
        return database.query(
                """
            SELECT * FROM server_bounty_heads
            WHERE owner_uuid = ? AND claimed = FALSE AND expires_at > NOW()
            ORDER BY created_at DESC
            """,
                rs -> {
                    List<TrophyHead> heads = new ArrayList<>();
                    while (rs.next()) {
                        heads.add(mapTrophyHead(rs));
                    }
                    return heads;
                },
                ownerUuid.toString());
    }

    public void markHeadClaimed(int headId) {
        database.execute(
                "UPDATE server_bounty_heads SET claimed = TRUE, claimed_at = NOW() WHERE id = ?",
                headId);
    }

    public int deleteExpiredHeads() {
        try {
            return database.executeUpdate(
                    "DELETE FROM server_bounty_heads WHERE expires_at < NOW()");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to delete expired heads", e);
            return 0;
        }
    }

    // ==================== Mapping Methods ====================

    private Bounty mapBounty(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");

        return new Bounty(
                rs.getInt("id"),
                UUID.fromString(rs.getString("target_uuid")),
                rs.getString("target_name"),
                rs.getDouble("total_amount"),
                createdAt != null ? createdAt.toInstant() : Instant.now(),
                updatedAt != null ? updatedAt.toInstant() : Instant.now());
    }

    private Contribution mapContribution(ResultSet rs) throws SQLException {
        Timestamp placedAt = rs.getTimestamp("placed_at");

        return new Contribution(
                rs.getInt("id"),
                rs.getInt("bounty_id"),
                UUID.fromString(rs.getString("placer_uuid")),
                rs.getString("placer_name"),
                rs.getDouble("amount"),
                rs.getDouble("tax_paid"),
                placedAt != null ? placedAt.toInstant() : Instant.now());
    }

    private TrophyHead mapTrophyHead(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp expiresAt = rs.getTimestamp("expires_at");
        Timestamp claimedAt = rs.getTimestamp("claimed_at");

        return new TrophyHead(
                rs.getInt("id"),
                UUID.fromString(rs.getString("owner_uuid")),
                UUID.fromString(rs.getString("victim_uuid")),
                rs.getString("victim_name"),
                rs.getDouble("bounty_amount"),
                rs.getString("head_data"),
                createdAt != null ? createdAt.toInstant() : Instant.now(),
                expiresAt != null ? expiresAt.toInstant() : Instant.now(),
                rs.getBoolean("claimed"),
                claimedAt != null ? claimedAt.toInstant() : null);
    }
}
