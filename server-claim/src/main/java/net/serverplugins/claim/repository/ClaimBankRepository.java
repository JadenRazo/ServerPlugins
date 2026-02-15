package net.serverplugins.claim.repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import net.serverplugins.api.database.Database;
import net.serverplugins.claim.models.BankTransaction;
import net.serverplugins.claim.models.ClaimBank;
import net.serverplugins.claim.models.ClaimBenefits;
import net.serverplugins.claim.models.ClaimLevel;
import net.serverplugins.claim.models.UpkeepConfig;
import net.serverplugins.claim.models.XpSource;

public class ClaimBankRepository {

    private final Database database;
    private static final Logger LOGGER = Logger.getLogger("ServerClaimBankRepository");

    public ClaimBankRepository(Database database) {
        this.database = database;
    }

    // ==================== BANK METHODS ====================

    public ClaimBank getBank(int claimId) {
        return database.query(
                "SELECT * FROM server_claim_banks WHERE claim_id = ?",
                rs -> {
                    if (rs.next()) {
                        Timestamp lastUpkeep = rs.getTimestamp("last_upkeep_payment");
                        Timestamp nextDue = rs.getTimestamp("next_upkeep_due");
                        Timestamp graceStart = rs.getTimestamp("grace_period_start");
                        return new ClaimBank(
                                rs.getInt("claim_id"),
                                rs.getDouble("balance"),
                                rs.getDouble("minimum_balance_warning"),
                                lastUpkeep != null ? lastUpkeep.toInstant() : null,
                                nextDue != null ? nextDue.toInstant() : null,
                                graceStart != null ? graceStart.toInstant() : null);
                    }
                    return null;
                },
                claimId);
    }

    public ClaimBank getOrCreateBank(int claimId) {
        ClaimBank bank = getBank(claimId);
        if (bank == null) {
            bank = new ClaimBank(claimId);
            saveBank(bank);
        }
        return bank;
    }

    public void saveBank(ClaimBank bank) {
        try {
            database.execute(
                    "INSERT INTO server_claim_banks (claim_id, balance, minimum_balance_warning, "
                            + "last_upkeep_payment, next_upkeep_due, grace_period_start) "
                            + "VALUES (?, ?, ?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE balance = VALUES(balance), "
                            + "minimum_balance_warning = VALUES(minimum_balance_warning), "
                            + "last_upkeep_payment = VALUES(last_upkeep_payment), "
                            + "next_upkeep_due = VALUES(next_upkeep_due), "
                            + "grace_period_start = VALUES(grace_period_start)",
                    bank.getClaimId(),
                    bank.getBalance(),
                    bank.getMinimumBalanceWarning(),
                    bank.getLastUpkeepPayment() != null
                            ? Timestamp.from(bank.getLastUpkeepPayment())
                            : null,
                    bank.getNextUpkeepDue() != null
                            ? Timestamp.from(bank.getNextUpkeepDue())
                            : null,
                    bank.getGracePeriodStart() != null
                            ? Timestamp.from(bank.getGracePeriodStart())
                            : null);
        } catch (Exception e) {
            LOGGER.severe(
                    "Failed to save bank for claim "
                            + bank.getClaimId()
                            + " (balance: "
                            + bank.getBalance()
                            + "): "
                            + e.getMessage());
            e.printStackTrace();
        }
    }

    public void updateBalance(int claimId, double newBalance) {
        database.execute(
                "UPDATE server_claim_banks SET balance = ? WHERE claim_id = ?",
                newBalance,
                claimId);
    }

    /**
     * Atomically increment balance without overwriting other fields. Unlike saveBank(), this only
     * touches the balance column, so it won't overwrite grace_period_start or other fields managed
     * by atomic operations.
     */
    public void depositBalance(int claimId, double amount) {
        database.execute(
                "UPDATE server_claim_banks SET balance = balance + ? WHERE claim_id = ?",
                amount,
                claimId);
    }

    /**
     * Atomically decrement balance without overwriting other fields. Only succeeds if balance >=
     * amount (prevents negative balance).
     *
     * @return true if withdrawal succeeded, false if insufficient balance
     */
    public boolean withdrawBalance(int claimId, double amount) {
        try {
            int rows =
                    database.executeUpdate(
                            "UPDATE server_claim_banks SET balance = balance - ? "
                                    + "WHERE claim_id = ? AND balance >= ?",
                            amount,
                            claimId,
                            amount);
            return rows > 0;
        } catch (Exception e) {
            LOGGER.severe(
                    "Failed to withdraw from bank for claim " + claimId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void deleteBank(int claimId) {
        database.execute("DELETE FROM server_claim_bank_transactions WHERE claim_id = ?", claimId);
        database.execute("DELETE FROM server_claim_banks WHERE claim_id = ?", claimId);
    }

    // ==================== TRANSACTION METHODS ====================

    public void recordTransaction(BankTransaction transaction) {
        database.execute(
                "INSERT INTO server_claim_bank_transactions "
                        + "(claim_id, player_uuid, transaction_type, amount, balance_after, description) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                transaction.getClaimId(),
                transaction.getPlayerUuid() != null ? transaction.getPlayerUuid().toString() : null,
                transaction.getType().name(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getDescription());
    }

    public List<BankTransaction> getTransactionHistory(int claimId, int limit) {
        return database.query(
                "SELECT * FROM server_claim_bank_transactions WHERE claim_id = ? "
                        + "ORDER BY created_at DESC LIMIT ?",
                rs -> {
                    List<BankTransaction> transactions = new ArrayList<>();
                    while (rs.next()) {
                        String uuidStr = rs.getString("player_uuid");
                        transactions.add(
                                new BankTransaction(
                                        rs.getInt("id"),
                                        rs.getInt("claim_id"),
                                        uuidStr != null ? UUID.fromString(uuidStr) : null,
                                        BankTransaction.TransactionType.valueOf(
                                                rs.getString("transaction_type")),
                                        rs.getDouble("amount"),
                                        rs.getDouble("balance_after"),
                                        rs.getString("description"),
                                        rs.getTimestamp("created_at").toInstant()));
                    }
                    return transactions;
                },
                claimId,
                limit);
    }

    public List<BankTransaction> getTransactionsByType(
            int claimId, BankTransaction.TransactionType type, int limit) {
        return database.query(
                "SELECT * FROM server_claim_bank_transactions "
                        + "WHERE claim_id = ? AND transaction_type = ? "
                        + "ORDER BY created_at DESC LIMIT ?",
                rs -> {
                    List<BankTransaction> transactions = new ArrayList<>();
                    while (rs.next()) {
                        String uuidStr = rs.getString("player_uuid");
                        transactions.add(
                                new BankTransaction(
                                        rs.getInt("id"),
                                        rs.getInt("claim_id"),
                                        uuidStr != null ? UUID.fromString(uuidStr) : null,
                                        BankTransaction.TransactionType.valueOf(
                                                rs.getString("transaction_type")),
                                        rs.getDouble("amount"),
                                        rs.getDouble("balance_after"),
                                        rs.getString("description"),
                                        rs.getTimestamp("created_at").toInstant()));
                    }
                    return transactions;
                },
                claimId,
                type.name(),
                limit);
    }

    // ==================== UPKEEP CONFIG METHODS ====================

    public UpkeepConfig getUpkeepConfig(int claimId) {
        return database.query(
                "SELECT * FROM server_claim_upkeep WHERE claim_id = ?",
                rs -> {
                    if (rs.next()) {
                        return new UpkeepConfig(
                                rs.getInt("claim_id"),
                                rs.getDouble("cost_per_chunk"),
                                rs.getDouble("discount_percentage"),
                                rs.getInt("grace_days"),
                                rs.getBoolean("auto_unclaim_enabled"),
                                rs.getInt("notifications_sent"));
                    }
                    return null;
                },
                claimId);
    }

    public UpkeepConfig getOrCreateUpkeepConfig(int claimId) {
        UpkeepConfig config = getUpkeepConfig(claimId);
        if (config == null) {
            config = new UpkeepConfig(claimId);
            saveUpkeepConfig(config);
        }
        return config;
    }

    public void saveUpkeepConfig(UpkeepConfig config) {
        database.execute(
                "INSERT INTO server_claim_upkeep "
                        + "(claim_id, cost_per_chunk, discount_percentage, grace_days, auto_unclaim_enabled, notifications_sent) "
                        + "VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE cost_per_chunk = VALUES(cost_per_chunk), "
                        + "discount_percentage = VALUES(discount_percentage), grace_days = VALUES(grace_days), "
                        + "auto_unclaim_enabled = VALUES(auto_unclaim_enabled), notifications_sent = VALUES(notifications_sent)",
                config.getClaimId(),
                config.getCostPerChunk(),
                config.getDiscountPercentage(),
                config.getGraceDays(),
                config.isAutoUnclaimEnabled(),
                config.getNotificationsSent());
    }

    public void deleteUpkeepConfig(int claimId) {
        database.execute("DELETE FROM server_claim_upkeep WHERE claim_id = ?", claimId);
    }

    // ==================== ATOMIC UPKEEP CHARGE ====================

    /**
     * Atomically charge upkeep at the database level. This single UPDATE prevents double-charging
     * even under concurrent execution (reload races, multiple threads). The WHERE clause ensures: -
     * The bank has enough balance - Upkeep is actually due (next_upkeep_due <= now) - The last
     * payment was at least {@code minIntervalHours} ago (or never made)
     *
     * <p>The next_upkeep_due guard is critical: even if a stale saveBank() overwrites
     * last_upkeep_payment to an old value, the charge still won't go through unless the claim is
     * genuinely due for payment.
     *
     * @param claimId The claim to charge
     * @param amount The upkeep cost
     * @param nextUpkeepDue The next upkeep due timestamp to set
     * @param minIntervalHours Minimum hours since last payment (dedup guard)
     * @return true if the charge was applied (1 row affected), false if blocked
     */
    public boolean chargeUpkeepAtomically(
            int claimId, double amount, Timestamp nextUpkeepDue, int minIntervalHours) {
        try {
            int rows =
                    database.executeUpdate(
                            "UPDATE server_claim_banks "
                                    + "SET balance = balance - ?, "
                                    + "    last_upkeep_payment = CURRENT_TIMESTAMP, "
                                    + "    next_upkeep_due = ?, "
                                    + "    grace_period_start = NULL "
                                    + "WHERE claim_id = ? "
                                    + "  AND balance >= ? "
                                    + "  AND next_upkeep_due IS NOT NULL "
                                    + "  AND next_upkeep_due <= CURRENT_TIMESTAMP "
                                    + "  AND (last_upkeep_payment IS NULL "
                                    + "       OR TIMESTAMPDIFF(HOUR, last_upkeep_payment, CURRENT_TIMESTAMP) >= ?)",
                            amount,
                            nextUpkeepDue,
                            claimId,
                            amount,
                            minIntervalHours);
            return rows > 0;
        } catch (Exception e) {
            LOGGER.severe(
                    "Failed to atomically charge upkeep for claim "
                            + claimId
                            + ": "
                            + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Atomically recover a claim from grace period by charging upkeep. Includes a TIMESTAMPDIFF
     * dedup guard to prevent double-charging if the upkeep-due batch already charged this claim in
     * the same cycle (which can happen when a stale saveBank() restores grace_period_start after
     * the upkeep-due batch cleared it).
     *
     * @param claimId The claim to recover
     * @param amount The upkeep cost to charge
     * @param nextUpkeepDue The next upkeep due timestamp to set
     * @param minIntervalHours Minimum hours since last payment (dedup guard)
     * @return true if recovery succeeded (1 row affected), false if blocked
     */
    public boolean recoverFromGracePeriodAtomically(
            int claimId, double amount, Timestamp nextUpkeepDue, int minIntervalHours) {
        try {
            int rows =
                    database.executeUpdate(
                            "UPDATE server_claim_banks "
                                    + "SET balance = balance - ?, "
                                    + "    last_upkeep_payment = CURRENT_TIMESTAMP, "
                                    + "    next_upkeep_due = ?, "
                                    + "    grace_period_start = NULL "
                                    + "WHERE claim_id = ? "
                                    + "  AND balance >= ? "
                                    + "  AND grace_period_start IS NOT NULL "
                                    + "  AND (last_upkeep_payment IS NULL "
                                    + "       OR TIMESTAMPDIFF(HOUR, last_upkeep_payment, CURRENT_TIMESTAMP) >= ?)",
                            amount,
                            nextUpkeepDue,
                            claimId,
                            amount,
                            minIntervalHours);
            return rows > 0;
        } catch (Exception e) {
            LOGGER.severe(
                    "Failed to recover claim " + claimId + " from grace period: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Proactively clear a stale grace period flag when balance already covers upkeep. Does NOT
     * charge upkeep - just clears the flag and sets next_upkeep_due so warnings stop. Used when
     * reading claim status to clean up stale grace flags left by the dedup guard.
     *
     * @param claimId The claim to clear
     * @param upkeepCost The upkeep cost to check against
     * @param paymentIntervalHours Hours until next upkeep due
     * @return true if the flag was cleared (1 row affected), false otherwise
     */
    public boolean clearGracePeriodIfFunded(
            int claimId, double upkeepCost, int paymentIntervalHours) {
        try {
            int rows =
                    database.executeUpdate(
                            "UPDATE server_claim_banks "
                                    + "SET grace_period_start = NULL, "
                                    + "    next_upkeep_due = DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? HOUR) "
                                    + "WHERE claim_id = ? "
                                    + "  AND grace_period_start IS NOT NULL "
                                    + "  AND balance >= ?",
                            paymentIntervalHours,
                            claimId,
                            upkeepCost);
            return rows > 0;
        } catch (Exception e) {
            LOGGER.severe(
                    "Failed to clear grace period for claim " + claimId + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Atomically update only the next_upkeep_due field without touching other columns. Safe to call
     * even when other threads are modifying balance or grace_period_start.
     */
    public void updateNextUpkeepDue(int claimId, Timestamp nextUpkeepDue) {
        database.execute(
                "UPDATE server_claim_banks SET next_upkeep_due = ? WHERE claim_id = ?",
                nextUpkeepDue,
                claimId);
    }

    // ==================== UPKEEP DUE QUERIES ====================

    /**
     * Get all banks with upkeep due (no pagination). WARNING: This loads all results into memory -
     * use getBanksWithUpkeepDuePaginated() for large datasets.
     */
    public List<ClaimBank> getBanksWithUpkeepDue() {
        return database.query(
                "SELECT * FROM server_claim_banks WHERE next_upkeep_due IS NOT NULL "
                        + "AND next_upkeep_due <= CURRENT_TIMESTAMP",
                rs -> {
                    List<ClaimBank> banks = new ArrayList<>();
                    while (rs.next()) {
                        Timestamp lastUpkeep = rs.getTimestamp("last_upkeep_payment");
                        Timestamp nextDue = rs.getTimestamp("next_upkeep_due");
                        Timestamp graceStart = rs.getTimestamp("grace_period_start");
                        banks.add(
                                new ClaimBank(
                                        rs.getInt("claim_id"),
                                        rs.getDouble("balance"),
                                        rs.getDouble("minimum_balance_warning"),
                                        lastUpkeep != null ? lastUpkeep.toInstant() : null,
                                        nextDue != null ? nextDue.toInstant() : null,
                                        graceStart != null ? graceStart.toInstant() : null));
                    }
                    return banks;
                });
    }

    /**
     * Get banks with upkeep due, paginated for processing large datasets.
     *
     * @param offset Number of records to skip
     * @param limit Maximum number of records to return
     * @return List of banks with upkeep due for this page
     */
    public List<ClaimBank> getBanksWithUpkeepDuePaginated(int offset, int limit) {
        return database.query(
                "SELECT * FROM server_claim_banks WHERE next_upkeep_due IS NOT NULL "
                        + "AND next_upkeep_due <= CURRENT_TIMESTAMP "
                        + "ORDER BY claim_id LIMIT ? OFFSET ?",
                rs -> {
                    List<ClaimBank> banks = new ArrayList<>();
                    while (rs.next()) {
                        Timestamp lastUpkeep = rs.getTimestamp("last_upkeep_payment");
                        Timestamp nextDue = rs.getTimestamp("next_upkeep_due");
                        Timestamp graceStart = rs.getTimestamp("grace_period_start");
                        banks.add(
                                new ClaimBank(
                                        rs.getInt("claim_id"),
                                        rs.getDouble("balance"),
                                        rs.getDouble("minimum_balance_warning"),
                                        lastUpkeep != null ? lastUpkeep.toInstant() : null,
                                        nextDue != null ? nextDue.toInstant() : null,
                                        graceStart != null ? graceStart.toInstant() : null));
                    }
                    return banks;
                },
                limit,
                offset);
    }

    /**
     * Get all banks in grace period (no pagination). WARNING: This loads all results into memory -
     * use getBanksInGracePeriodPaginated() for large datasets.
     */
    public List<ClaimBank> getBanksInGracePeriod() {
        return database.query(
                "SELECT * FROM server_claim_banks WHERE grace_period_start IS NOT NULL",
                rs -> {
                    List<ClaimBank> banks = new ArrayList<>();
                    while (rs.next()) {
                        Timestamp lastUpkeep = rs.getTimestamp("last_upkeep_payment");
                        Timestamp nextDue = rs.getTimestamp("next_upkeep_due");
                        Timestamp graceStart = rs.getTimestamp("grace_period_start");
                        banks.add(
                                new ClaimBank(
                                        rs.getInt("claim_id"),
                                        rs.getDouble("balance"),
                                        rs.getDouble("minimum_balance_warning"),
                                        lastUpkeep != null ? lastUpkeep.toInstant() : null,
                                        nextDue != null ? nextDue.toInstant() : null,
                                        graceStart != null ? graceStart.toInstant() : null));
                    }
                    return banks;
                });
    }

    /**
     * Get banks in grace period, paginated for processing large datasets.
     *
     * @param offset Number of records to skip
     * @param limit Maximum number of records to return
     * @return List of banks in grace period for this page
     */
    public List<ClaimBank> getBanksInGracePeriodPaginated(int offset, int limit) {
        return database.query(
                "SELECT * FROM server_claim_banks WHERE grace_period_start IS NOT NULL "
                        + "ORDER BY claim_id LIMIT ? OFFSET ?",
                rs -> {
                    List<ClaimBank> banks = new ArrayList<>();
                    while (rs.next()) {
                        Timestamp lastUpkeep = rs.getTimestamp("last_upkeep_payment");
                        Timestamp nextDue = rs.getTimestamp("next_upkeep_due");
                        Timestamp graceStart = rs.getTimestamp("grace_period_start");
                        banks.add(
                                new ClaimBank(
                                        rs.getInt("claim_id"),
                                        rs.getDouble("balance"),
                                        rs.getDouble("minimum_balance_warning"),
                                        lastUpkeep != null ? lastUpkeep.toInstant() : null,
                                        nextDue != null ? nextDue.toInstant() : null,
                                        graceStart != null ? graceStart.toInstant() : null));
                    }
                    return banks;
                },
                limit,
                offset);
    }

    /**
     * Get count of banks with upkeep due.
     *
     * @return Number of banks with upkeep due
     */
    public int getUpkeepDueCount() {
        return database.query(
                "SELECT COUNT(*) as count FROM server_claim_banks WHERE next_upkeep_due IS NOT NULL "
                        + "AND next_upkeep_due <= CURRENT_TIMESTAMP",
                rs -> rs.next() ? rs.getInt("count") : 0);
    }

    /**
     * Get count of banks in grace period.
     *
     * @return Number of banks in grace period
     */
    public int getGracePeriodCount() {
        return database.query(
                "SELECT COUNT(*) as count FROM server_claim_banks WHERE grace_period_start IS NOT NULL",
                rs -> rs.next() ? rs.getInt("count") : 0);
    }

    // ==================== UNCLAIMED BY UPKEEP AUDIT ====================

    public void recordUnclaimedByUpkeep(int claimId, String world, int chunkX, int chunkZ) {
        database.execute(
                "INSERT INTO server_unclaimed_by_upkeep (claim_id, chunk_world, chunk_x, chunk_z) "
                        + "VALUES (?, ?, ?, ?)",
                claimId,
                world,
                chunkX,
                chunkZ);
    }

    // ==================== LEVEL METHODS ====================

    public ClaimLevel getLevel(int claimId) {
        return database.query(
                "SELECT * FROM server_claim_levels WHERE claim_id = ?",
                rs -> {
                    if (rs.next()) {
                        return new ClaimLevel(
                                rs.getInt("claim_id"),
                                rs.getInt("level"),
                                rs.getLong("current_xp"),
                                rs.getLong("total_xp_earned"));
                    }
                    return null;
                },
                claimId);
    }

    public ClaimLevel getOrCreateLevel(int claimId) {
        ClaimLevel level = getLevel(claimId);
        if (level == null) {
            level = new ClaimLevel(claimId);
            saveLevel(level);
        }
        return level;
    }

    public void saveLevel(ClaimLevel level) {
        database.execute(
                "INSERT INTO server_claim_levels (claim_id, level, current_xp, total_xp_earned) "
                        + "VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE level = VALUES(level), "
                        + "current_xp = VALUES(current_xp), total_xp_earned = VALUES(total_xp_earned)",
                level.getClaimId(),
                level.getLevel(),
                level.getCurrentXp(),
                level.getTotalXpEarned());
    }

    public void recordXpGain(int claimId, UUID playerUuid, int amount, XpSource source) {
        database.execute(
                "INSERT INTO server_claim_xp_history (claim_id, player_uuid, xp_amount, xp_source) "
                        + "VALUES (?, ?, ?, ?)",
                claimId,
                playerUuid != null ? playerUuid.toString() : null,
                amount,
                source.name());
    }

    // ==================== BENEFITS METHODS ====================

    public ClaimBenefits getBenefits(int claimId) {
        return database.query(
                "SELECT * FROM server_claim_benefits WHERE claim_id = ?",
                rs -> {
                    if (rs.next()) {
                        return new ClaimBenefits(
                                rs.getInt("claim_id"),
                                rs.getInt("max_member_slots"),
                                rs.getInt("max_warp_slots"),
                                rs.getDouble("upkeep_discount_percent"),
                                rs.getInt("welcome_message_length"),
                                rs.getInt("particle_tier"),
                                rs.getInt("bonus_chunk_slots"));
                    }
                    return null;
                },
                claimId);
    }

    public void saveBenefits(ClaimBenefits benefits) {
        database.execute(
                "INSERT INTO server_claim_benefits "
                        + "(claim_id, max_member_slots, max_warp_slots, upkeep_discount_percent, "
                        + "welcome_message_length, particle_tier, bonus_chunk_slots) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE max_member_slots = VALUES(max_member_slots), "
                        + "max_warp_slots = VALUES(max_warp_slots), upkeep_discount_percent = VALUES(upkeep_discount_percent), "
                        + "welcome_message_length = VALUES(welcome_message_length), particle_tier = VALUES(particle_tier), "
                        + "bonus_chunk_slots = VALUES(bonus_chunk_slots)",
                benefits.getClaimId(),
                benefits.getMaxMemberSlots(),
                benefits.getMaxWarpSlots(),
                benefits.getUpkeepDiscountPercent(),
                benefits.getWelcomeMessageLength(),
                benefits.getParticleTier(),
                benefits.getBonusChunkSlots());
    }

    // ==================== PLAYTIME TRACKING ====================

    public void updatePlaytime(int claimId, UUID playerUuid, long additionalSeconds) {
        database.execute(
                "INSERT INTO server_claim_playtime (claim_id, player_uuid, total_seconds, session_start) "
                        + "VALUES (?, ?, ?, CURRENT_TIMESTAMP) "
                        + "ON DUPLICATE KEY UPDATE total_seconds = total_seconds + ?",
                claimId,
                playerUuid.toString(),
                additionalSeconds,
                additionalSeconds);
    }

    public long getPlaytime(int claimId, UUID playerUuid) {
        return database.query(
                "SELECT total_seconds FROM server_claim_playtime WHERE claim_id = ? AND player_uuid = ?",
                rs -> rs.next() ? rs.getLong("total_seconds") : 0L,
                claimId,
                playerUuid.toString());
    }

    public void startSession(int claimId, UUID playerUuid) {
        database.execute(
                "INSERT INTO server_claim_playtime (claim_id, player_uuid, session_start) "
                        + "VALUES (?, ?, CURRENT_TIMESTAMP) "
                        + "ON DUPLICATE KEY UPDATE session_start = CURRENT_TIMESTAMP",
                claimId,
                playerUuid.toString());
    }

    public void endSession(int claimId, UUID playerUuid) {
        database.execute(
                "UPDATE server_claim_playtime SET "
                        + "total_seconds = total_seconds + TIMESTAMPDIFF(SECOND, session_start, CURRENT_TIMESTAMP), "
                        + "session_start = NULL "
                        + "WHERE claim_id = ? AND player_uuid = ? AND session_start IS NOT NULL",
                claimId,
                playerUuid.toString());
    }
}
