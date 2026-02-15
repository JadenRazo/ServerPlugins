package net.serverplugins.claim.managers;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.BankTransaction;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimBank;
import net.serverplugins.claim.repository.AuditLogRepository;
import net.serverplugins.claim.repository.ClaimBankRepository;
import net.serverplugins.claim.repository.ClaimRepository;
import org.bukkit.entity.Player;

/**
 * Manages claim bank operations including deposits, withdrawals, and transaction history.
 *
 * <p>Thread Safety: - Uses ConcurrentHashMap for cache (thread-safe reads/writes) - All multi-step
 * operations are wrapped in database transactions for atomicity - No explicit locks needed -
 * transactions provide ACID guarantees
 */
public class BankManager {

    private final ServerClaim plugin;
    private final ClaimBankRepository repository;
    private final ClaimRepository claimRepository;
    private final Map<Integer, ClaimBank> bankCache = new ConcurrentHashMap<>();

    public BankManager(
            ServerClaim plugin, ClaimBankRepository repository, ClaimRepository claimRepository) {
        this.plugin = plugin;
        this.repository = repository;
        this.claimRepository = claimRepository;
    }

    private AuditLogRepository getAuditLogRepository() {
        return plugin.getAuditLogRepository();
    }

    public ClaimBank getBank(int claimId) {
        return bankCache.computeIfAbsent(claimId, repository::getOrCreateBank);
    }

    public void invalidateCache(int claimId) {
        bankCache.remove(claimId);
    }

    public double getBalance(int claimId) {
        ClaimBank bank = getBank(claimId);
        if (bank == null) {
            plugin.getLogger()
                    .warning("Failed to get bank for claim " + claimId + " - returning 0 balance");
            return 0.0;
        }
        return bank.getBalance();
    }

    public void deposit(
            Player player, Claim claim, double amount, Consumer<DepositResult> callback) {
        if (claim == null) {
            plugin.getLogger()
                    .warning("Attempted deposit with null claim for player " + player.getName());
            callback.accept(DepositResult.ECONOMY_ERROR);
            return;
        }

        if (amount <= 0) {
            callback.accept(DepositResult.INVALID_AMOUNT);
            return;
        }

        if (plugin.getEconomy() == null) {
            callback.accept(DepositResult.ECONOMY_ERROR);
            return;
        }

        if (!plugin.getEconomy().has(player, amount)) {
            callback.accept(DepositResult.INSUFFICIENT_FUNDS);
            return;
        }

        final int claimId = claim.getId();

        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            boolean withdrawn = plugin.getEconomy().withdraw(player, amount);
                            if (!withdrawn) {
                                callback.accept(DepositResult.ECONOMY_ERROR);
                                return;
                            }

                            final double depositAmount = amount;

                            plugin.getServer()
                                    .getScheduler()
                                    .runTaskAsynchronously(
                                            plugin,
                                            () -> {
                                                try {
                                                    // Read current grace state before deposit
                                                    ClaimBank preDeposit =
                                                            repository.getBank(claimId);
                                                    boolean wasInGracePeriod =
                                                            preDeposit != null
                                                                    && preDeposit
                                                                                    .getGracePeriodStart()
                                                                            != null;

                                                    // Deposit: atomically increment balance
                                                    // (only touches balance column, won't
                                                    // overwrite grace_period_start or other
                                                    // fields managed by atomic operations)
                                                    claimRepository.executeInTransaction(
                                                            () -> {
                                                                repository.depositBalance(
                                                                        claimId, depositAmount);

                                                                // Read updated balance for
                                                                // transaction record
                                                                ClaimBank updated =
                                                                        repository.getBank(claimId);
                                                                double newBalance =
                                                                        updated != null
                                                                                ? updated
                                                                                        .getBalance()
                                                                                : depositAmount;

                                                                BankTransaction transaction =
                                                                        new BankTransaction(
                                                                                claimId,
                                                                                player
                                                                                        .getUniqueId(),
                                                                                BankTransaction
                                                                                        .TransactionType
                                                                                        .DEPOSIT,
                                                                                depositAmount,
                                                                                newBalance,
                                                                                "Deposit by "
                                                                                        + player
                                                                                                .getName());
                                                                repository.recordTransaction(
                                                                        transaction);

                                                                if (getAuditLogRepository()
                                                                        != null) {
                                                                    getAuditLogRepository()
                                                                            .logActivity(
                                                                                    claimId,
                                                                                    player
                                                                                            .getUniqueId(),
                                                                                    AuditLogRepository
                                                                                            .ActivityType
                                                                                            .BANK_DEPOSIT,
                                                                                    "Deposited $"
                                                                                            + String
                                                                                                    .format(
                                                                                                            "%.2f",
                                                                                                            depositAmount),
                                                                                    depositAmount);
                                                                }
                                                            });

                                                    // Deposit committed - invalidate cache
                                                    invalidateCache(claimId);

                                                    // After deposit commits, attempt atomic
                                                    // grace period recovery as a SEPARATE step.
                                                    // Uses SQL WHERE guards to prevent
                                                    // double-charging if the hourly batch
                                                    // already recovered this claim.
                                                    if (wasInGracePeriod) {
                                                        attemptGraceRecoveryAfterDeposit(
                                                                claimId, player.getName());
                                                    }

                                                    plugin.getServer()
                                                            .getScheduler()
                                                            .runTask(
                                                                    plugin,
                                                                    () -> {
                                                                        plugin.getLogger()
                                                                                .info(
                                                                                        "Player "
                                                                                                + player
                                                                                                        .getName()
                                                                                                + " deposited $"
                                                                                                + String
                                                                                                        .format(
                                                                                                                "%.2f",
                                                                                                                depositAmount)
                                                                                                + " to claim "
                                                                                                + claimId
                                                                                                + " bank");
                                                                        callback.accept(
                                                                                DepositResult
                                                                                        .SUCCESS);
                                                                    });

                                                } catch (Exception e) {
                                                    plugin.getLogger()
                                                            .severe(
                                                                    "Bank deposit transaction failed for claim "
                                                                            + claimId
                                                                            + ": "
                                                                            + e.getMessage());
                                                    e.printStackTrace();

                                                    invalidateCache(claimId);

                                                    plugin.getServer()
                                                            .getScheduler()
                                                            .runTask(
                                                                    plugin,
                                                                    () -> {
                                                                        plugin.getEconomy()
                                                                                .deposit(
                                                                                        player,
                                                                                        depositAmount);
                                                                        plugin.getLogger()
                                                                                .warning(
                                                                                        "Refunded $"
                                                                                                + String
                                                                                                        .format(
                                                                                                                "%.2f",
                                                                                                                depositAmount)
                                                                                                + " to "
                                                                                                + player
                                                                                                        .getName()
                                                                                                + " after bank transaction failure (rolled back)");
                                                                        callback.accept(
                                                                                DepositResult
                                                                                        .ECONOMY_ERROR);
                                                                    });
                                                }
                                            });
                        });
    }

    /**
     * Attempt atomic grace period recovery after a deposit. Uses recoverFromGracePeriodAtomically()
     * which has a SQL WHERE guard (grace_period_start IS NOT NULL AND balance >= cost) to prevent
     * double-charging if the hourly batch already recovered this claim.
     */
    private void attemptGraceRecoveryAfterDeposit(int claimId, String playerName) {
        if (plugin.getUpkeepManager() == null || !plugin.getUpkeepManager().isUpkeepEnabled()) {
            return;
        }

        Claim claim = plugin.getClaimManager().getClaimById(claimId);
        if (claim == null) return;

        double upkeepCost = plugin.getUpkeepManager().getUpkeepCost(claim);
        int intervalHours = plugin.getClaimConfig().getUpkeepPaymentIntervalHours();

        if (upkeepCost > 0) {
            Instant nextDue = Instant.now().plus(Duration.ofHours(intervalHours));
            boolean recovered =
                    repository.recoverFromGracePeriodAtomically(
                            claimId, upkeepCost, java.sql.Timestamp.from(nextDue), intervalHours);

            if (recovered) {
                net.serverplugins.claim.models.UpkeepConfig upkeepCfg =
                        repository.getOrCreateUpkeepConfig(claimId);
                upkeepCfg.resetNotifications();
                repository.saveUpkeepConfig(upkeepCfg);

                ClaimBank postRecovery = repository.getBank(claimId);
                double postBalance = postRecovery != null ? postRecovery.getBalance() : 0;

                BankTransaction upkeepTx =
                        new BankTransaction(
                                claimId,
                                null,
                                BankTransaction.TransactionType.UPKEEP,
                                upkeepCost,
                                postBalance,
                                "Upkeep recovery after deposit by " + playerName);
                repository.recordTransaction(upkeepTx);

                invalidateCache(claimId);
            }
        } else {
            // No upkeep cost - just clear stale grace flag
            repository.clearGracePeriodIfFunded(claimId, 0, intervalHours);
            invalidateCache(claimId);
        }
    }

    public void withdraw(
            Player player, Claim claim, double amount, Consumer<WithdrawResult> callback) {
        if (claim == null) {
            plugin.getLogger()
                    .warning("Attempted withdrawal with null claim for player " + player.getName());
            callback.accept(WithdrawResult.ECONOMY_ERROR);
            return;
        }

        if (amount <= 0) {
            callback.accept(WithdrawResult.INVALID_AMOUNT);
            return;
        }

        // Only owner can withdraw
        if (!claim.isOwner(player.getUniqueId()) && !player.hasPermission("serverclaim.admin")) {
            callback.accept(WithdrawResult.NO_PERMISSION);
            return;
        }

        if (plugin.getEconomy() == null) {
            callback.accept(WithdrawResult.ECONOMY_ERROR);
            return;
        }

        final int claimId = claim.getId();

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                final double withdrawAmount = amount;

                                // Atomic withdrawal: only touches balance column,
                                // won't overwrite upkeep fields (last_upkeep_payment,
                                // next_upkeep_due, grace_period_start)
                                claimRepository.executeInTransaction(
                                        () -> {
                                            boolean withdrawn =
                                                    repository.withdrawBalance(
                                                            claimId, withdrawAmount);
                                            if (!withdrawn) {
                                                throw new IllegalStateException(
                                                        "INSUFFICIENT_FUNDS");
                                            }

                                            // Read updated balance for transaction record
                                            ClaimBank updated = repository.getBank(claimId);
                                            double newBalance =
                                                    updated != null ? updated.getBalance() : 0;

                                            BankTransaction transaction =
                                                    new BankTransaction(
                                                            claimId,
                                                            player.getUniqueId(),
                                                            BankTransaction.TransactionType
                                                                    .WITHDRAW,
                                                            withdrawAmount,
                                                            newBalance,
                                                            "Withdrawal by " + player.getName());
                                            repository.recordTransaction(transaction);

                                            if (getAuditLogRepository() != null) {
                                                getAuditLogRepository()
                                                        .logActivity(
                                                                claimId,
                                                                player.getUniqueId(),
                                                                AuditLogRepository.ActivityType
                                                                        .BANK_WITHDRAW,
                                                                "Withdrew $"
                                                                        + String.format(
                                                                                "%.2f",
                                                                                withdrawAmount),
                                                                withdrawAmount);
                                            }
                                        });

                                invalidateCache(claimId);

                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    plugin.getEconomy()
                                                            .deposit(player, withdrawAmount);
                                                    plugin.getLogger()
                                                            .info(
                                                                    "Player "
                                                                            + player.getName()
                                                                            + " withdrew $"
                                                                            + String.format(
                                                                                    "%.2f",
                                                                                    withdrawAmount)
                                                                            + " from claim "
                                                                            + claimId
                                                                            + " bank");
                                                    callback.accept(WithdrawResult.SUCCESS);
                                                });

                            } catch (IllegalStateException e) {
                                // Insufficient funds (atomic check failed)
                                invalidateCache(claimId);
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () ->
                                                        callback.accept(
                                                                WithdrawResult.INSUFFICIENT_FUNDS));
                            } catch (Exception e) {
                                plugin.getLogger()
                                        .severe(
                                                "Bank withdrawal transaction failed for claim "
                                                        + claimId
                                                        + ": "
                                                        + e.getMessage());
                                e.printStackTrace();

                                invalidateCache(claimId);

                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    callback.accept(WithdrawResult.ECONOMY_ERROR);
                                                });
                            }
                        });
    }

    public void getTransactionHistory(
            int claimId, int limit, Consumer<java.util.List<BankTransaction>> callback) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            java.util.List<BankTransaction> transactions =
                                    repository.getTransactionHistory(claimId, limit);
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                callback.accept(transactions);
                                            });
                        });
    }

    public void recordUpkeepPayment(int claimId, double amount) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                ClaimBank bank = getBank(claimId);
                                boolean paid = bank.payUpkeep(amount);

                                if (paid) {
                                    // Wrap database operations in transaction for atomicity
                                    claimRepository.executeInTransaction(
                                            () -> {
                                                repository.saveBank(bank);

                                                BankTransaction transaction =
                                                        new BankTransaction(
                                                                claimId,
                                                                null,
                                                                BankTransaction.TransactionType
                                                                        .UPKEEP,
                                                                amount,
                                                                bank.getBalance(),
                                                                "Automatic upkeep payment");
                                                repository.recordTransaction(transaction);
                                            });

                                    plugin.getLogger()
                                            .fine(
                                                    "Recorded upkeep payment of $"
                                                            + String.format("%.2f", amount)
                                                            + " for claim "
                                                            + claimId
                                                            + " (new balance: $"
                                                            + String.format(
                                                                    "%.2f", bank.getBalance())
                                                            + ")");
                                }

                                // Invalidate cache to ensure fresh data on next access
                                invalidateCache(claimId);

                            } catch (Exception e) {
                                plugin.getLogger()
                                        .severe(
                                                "Failed to record upkeep payment for claim "
                                                        + claimId
                                                        + ": "
                                                        + e.getMessage());
                                e.printStackTrace();
                                // Invalidate cache to prevent stale data
                                invalidateCache(claimId);
                            }
                        });
    }

    public void recordNationTax(int claimId, double amount, String nationName) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                // Atomic withdrawal: only touches balance column
                                boolean withdrawn = repository.withdrawBalance(claimId, amount);

                                if (withdrawn) {
                                    ClaimBank updated = repository.getBank(claimId);
                                    double newBalance = updated != null ? updated.getBalance() : 0;

                                    BankTransaction transaction =
                                            new BankTransaction(
                                                    claimId,
                                                    null,
                                                    BankTransaction.TransactionType.NATION_TAX,
                                                    amount,
                                                    newBalance,
                                                    "Nation tax to " + nationName);
                                    repository.recordTransaction(transaction);

                                    plugin.getLogger()
                                            .fine(
                                                    "Recorded nation tax of $"
                                                            + String.format("%.2f", amount)
                                                            + " for claim "
                                                            + claimId
                                                            + " to nation "
                                                            + nationName
                                                            + " (new balance: $"
                                                            + String.format("%.2f", newBalance)
                                                            + ")");
                                }

                                invalidateCache(claimId);

                            } catch (Exception e) {
                                plugin.getLogger()
                                        .severe(
                                                "Failed to record nation tax for claim "
                                                        + claimId
                                                        + ": "
                                                        + e.getMessage());
                                e.printStackTrace();
                                // Invalidate cache to prevent stale data
                                invalidateCache(claimId);
                            }
                        });
    }

    public void deleteBankData(int claimId) {
        bankCache.remove(claimId);
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            repository.deleteBank(claimId);
                            repository.deleteUpkeepConfig(claimId);
                        });
    }

    public enum DepositResult {
        SUCCESS,
        INVALID_AMOUNT,
        INSUFFICIENT_FUNDS,
        ECONOMY_ERROR
    }

    public enum WithdrawResult {
        SUCCESS,
        INVALID_AMOUNT,
        INSUFFICIENT_FUNDS,
        NO_PERMISSION,
        ECONOMY_ERROR
    }
}
