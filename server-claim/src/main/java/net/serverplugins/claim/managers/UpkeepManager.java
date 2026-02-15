package net.serverplugins.claim.managers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.BankTransaction;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimBank;
import net.serverplugins.claim.models.ClaimBenefits;
import net.serverplugins.claim.models.ClaimLevel;
import net.serverplugins.claim.models.ClaimedChunk;
import net.serverplugins.claim.models.UpkeepConfig;
import net.serverplugins.claim.models.XpSource;
import net.serverplugins.claim.repository.ClaimBankRepository;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class UpkeepManager {

    private final ServerClaim plugin;
    private final ClaimBankRepository repository;
    private final BankManager bankManager;
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private BukkitTask startupTask;
    private BukkitTask upkeepTask;
    private BukkitTask reminderTask;

    // Config values - volatile for thread-safe reads from async task
    private volatile boolean upkeepEnabled;
    private volatile double defaultCostPerChunk;
    private volatile int paymentIntervalHours;
    private volatile int gracePeriodDays;
    private volatile boolean autoUnclaimEnabled;
    private volatile List<Integer> notificationHours;
    private volatile int upkeepBatchSize;

    public UpkeepManager(
            ServerClaim plugin, ClaimBankRepository repository, BankManager bankManager) {
        this.plugin = plugin;
        this.repository = repository;
        this.bankManager = bankManager;
        loadConfig();
    }

    private void loadConfig() {
        upkeepEnabled = plugin.getClaimConfig().isUpkeepEnabled();
        defaultCostPerChunk = plugin.getClaimConfig().getUpkeepCostPerChunk();
        paymentIntervalHours = plugin.getClaimConfig().getUpkeepPaymentIntervalHours();
        gracePeriodDays = plugin.getClaimConfig().getUpkeepGracePeriodDays();
        autoUnclaimEnabled = plugin.getClaimConfig().isUpkeepAutoUnclaimEnabled();
        notificationHours = plugin.getClaimConfig().getUpkeepNotificationTimes();
        upkeepBatchSize = plugin.getClaimConfig().getUpkeepBatchSize();
    }

    /** Reload configuration values. Called when /claim reload is executed. */
    public void reloadConfig() {
        loadConfig();
        plugin.getLogger()
                .info(
                        "UpkeepManager config reloaded - enabled: "
                                + upkeepEnabled
                                + ", cost/chunk: $"
                                + defaultCostPerChunk
                                + ", interval: "
                                + paymentIntervalHours
                                + "h");
    }

    public void start() {
        if (!upkeepEnabled) {
            plugin.getLogger().info("Upkeep system is disabled in config");
            return;
        }

        // Run startup upkeep check immediately (async, with 5 second delay for other systems to
        // initialize)
        startupTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskLaterAsynchronously(
                                plugin,
                                () -> {
                                    plugin.getLogger().info("Running startup upkeep check...");
                                    processUpkeep();
                                },
                                100L); // 5 seconds after enable

        // Run upkeep check every hour
        long ticksPerHour = 20L * 60 * 60;
        upkeepTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimerAsynchronously(
                                plugin, this::processUpkeep, ticksPerHour, ticksPerHour);

        // Run actionbar reminder every 5 minutes for online players with at-risk claims
        // Runs async since getClaimsInGracePeriod does DB calls, and sendActionBar is packet-based
        long ticksPer5Minutes = 20L * 60 * 5;
        reminderTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimerAsynchronously(
                                plugin,
                                this::sendActionbarReminders,
                                ticksPer5Minutes,
                                ticksPer5Minutes);

        plugin.getLogger()
                .info("Upkeep manager started - checking every hour with actionbar reminders");
    }

    public void stop() {
        // Cancel scheduled tasks to prevent NEW processUpkeep() invocations.
        // Do NOT reset processing flag - if a processUpkeep() is currently mid-execution
        // on an async thread, it will naturally set processing=false in its finally block.
        // Resetting here would allow a reload's startup task to run concurrently with the
        // still-running old processUpkeep(), causing double-charges.
        if (startupTask != null) {
            startupTask.cancel();
            startupTask = null;
        }
        if (upkeepTask != null) {
            upkeepTask.cancel();
            upkeepTask = null;
        }
        if (reminderTask != null) {
            reminderTask.cancel();
            reminderTask = null;
        }
    }

    /**
     * Process upkeep payments in batches to prevent memory exhaustion. Uses pagination to process
     * claims in chunks, maintaining constant memory usage regardless of total claim count.
     */
    private void processUpkeep() {
        if (!upkeepEnabled) return;

        // Prevent concurrent execution (e.g., old async task still running after reload)
        if (!processing.compareAndSet(false, true)) {
            plugin.getLogger().info("Upkeep processing already in progress, skipping");
            return;
        }

        try {
            long startTime = System.currentTimeMillis();
            plugin.getLogger().info("Starting upkeep processing...");

            // Process banks with upkeep due in batches
            int processedDue =
                    processBanksInBatches(
                            this::processClaimUpkeep,
                            repository::getBanksWithUpkeepDuePaginated,
                            repository::getUpkeepDueCount,
                            "upkeep due");

            // Process grace period claims in batches
            int processedGrace =
                    processBanksInBatches(
                            this::processGracePeriodClaim,
                            repository::getBanksInGracePeriodPaginated,
                            repository::getGracePeriodCount,
                            "in grace period");

            long elapsed = System.currentTimeMillis() - startTime;
            plugin.getLogger()
                    .info(
                            "Upkeep processing complete: "
                                    + processedDue
                                    + " payments, "
                                    + processedGrace
                                    + " grace period claims "
                                    + "("
                                    + elapsed
                                    + "ms)");
        } finally {
            processing.set(false);
        }
    }

    /**
     * Generic batch processor for bank operations. Processes banks in batches using pagination to
     * maintain constant memory usage.
     *
     * @param processor Function to process each bank
     * @param fetcher Function to fetch paginated banks (offset, limit) -> List<ClaimBank>
     * @param counter Function to get total count
     * @param description Description for logging
     * @return Total number of banks processed
     */
    private int processBanksInBatches(
            java.util.function.Consumer<ClaimBank> processor,
            java.util.function.BiFunction<Integer, Integer, List<ClaimBank>> fetcher,
            java.util.function.Supplier<Integer> counter,
            String description) {
        int totalCount = counter.get();
        if (totalCount == 0) {
            return 0;
        }

        int processed = 0;
        int batchSize = upkeepBatchSize;

        plugin.getLogger()
                .info(
                        "Processing "
                                + totalCount
                                + " banks "
                                + description
                                + " in batches of "
                                + batchSize);

        // Always fetch from offset 0 since processed records drop out of the result set
        // (their next_upkeep_due moves to the future). Use a safety counter to prevent
        // infinite loops if a record fails to update.
        int maxIterations = (totalCount / batchSize) + 2;
        int iteration = 0;

        while (iteration < maxIterations) {
            // Fetch batch - always offset 0 since processed records leave the result set
            List<ClaimBank> batch = fetcher.apply(0, batchSize);

            if (batch.isEmpty()) {
                break; // No more records
            }

            // Process each bank in the batch
            for (ClaimBank bank : batch) {
                try {
                    processor.accept(bank);
                    processed++;
                } catch (Exception e) {
                    plugin.getLogger()
                            .severe(
                                    "Error processing bank for claim "
                                            + bank.getClaimId()
                                            + " during upkeep: "
                                            + e.getMessage());
                    e.printStackTrace();
                }
            }

            iteration++;

            // Log progress every 10 batches
            if (iteration % 10 == 0) {
                plugin.getLogger()
                        .info(
                                "Upkeep progress: "
                                        + processed
                                        + "/"
                                        + totalCount
                                        + " banks processed ("
                                        + description
                                        + ")");
            }
        }

        return processed;
    }

    private void processClaimUpkeep(ClaimBank bank) {
        // Re-fetch from DB to avoid stale batch data (deposit may have already paid upkeep)
        ClaimBank freshBank = repository.getBank(bank.getClaimId());
        if (freshBank == null || freshBank.getNextUpkeepDue() == null) {
            return; // Already processed or moved to grace period
        }
        // Early bail: if next_upkeep_due is in the future, another thread already charged this
        if (freshBank.getNextUpkeepDue().isAfter(Instant.now())) {
            return;
        }
        bank = freshBank;

        Claim claim = plugin.getClaimManager().getClaimById(bank.getClaimId());
        if (claim == null) {
            plugin.getLogger()
                    .warning(
                            "Skipping upkeep for bank " + bank.getClaimId() + " - claim not found");
            return;
        }

        UpkeepConfig upkeepConfig = repository.getOrCreateUpkeepConfig(bank.getClaimId());

        // Calculate upkeep cost with level discount
        ClaimLevel level = repository.getOrCreateLevel(claim.getId());
        double levelDiscount = ClaimBenefits.getUpkeepDiscountForLevel(level.getLevel());
        double effectiveDiscount = Math.max(upkeepConfig.getDiscountPercentage(), levelDiscount);
        double costPerChunk = defaultCostPerChunk * (1.0 - effectiveDiscount / 100.0);
        double totalCost = costPerChunk * claim.getChunks().size();

        // Calculate the next due date anchored off the previous due date to prevent drift
        Instant anchor = bank.getNextUpkeepDue() != null ? bank.getNextUpkeepDue() : Instant.now();
        Instant nextDue = anchor.plus(Duration.ofHours(paymentIntervalHours));
        if (nextDue.isBefore(Instant.now())) {
            nextDue = Instant.now().plus(Duration.ofHours(paymentIntervalHours));
        }

        // Attempt atomic charge at the DB level. This single SQL UPDATE:
        // 1. Checks balance >= cost
        // 2. Checks last_upkeep_payment is old enough (dedup guard)
        // 3. Atomically deducts balance, sets timestamps, clears grace period
        // Even if two threads race, only one UPDATE will match the WHERE clause.
        boolean charged =
                repository.chargeUpkeepAtomically(
                        claim.getId(),
                        totalCost,
                        java.sql.Timestamp.from(nextDue),
                        paymentIntervalHours);

        if (charged) {
            // Charge succeeded atomically - now handle the non-critical follow-ups
            upkeepConfig.resetNotifications();
            repository.saveUpkeepConfig(upkeepConfig);
            bankManager.invalidateCache(claim.getId());

            // Re-read the new balance for the transaction record
            ClaimBank updated = repository.getBank(claim.getId());
            double newBalance = updated != null ? updated.getBalance() : 0;

            BankTransaction transaction =
                    new BankTransaction(
                            claim.getId(),
                            null,
                            BankTransaction.TransactionType.UPKEEP,
                            totalCost,
                            newBalance,
                            "Upkeep for " + claim.getChunks().size() + " chunks");
            repository.recordTransaction(transaction);

            // Grant XP for paying upkeep on time
            plugin.getServer()
                    .getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (plugin.getLevelManager() != null) {
                                    plugin.getLevelManager()
                                            .grantXp(claim.getId(), null, XpSource.UPKEEP_PAID);
                                }
                            });

            plugin.getLogger()
                    .info(
                            "Claim "
                                    + claim.getName()
                                    + " (ID: "
                                    + claim.getId()
                                    + ") paid upkeep: $"
                                    + String.format("%.2f", totalCost));
        } else {
            // Atomic charge failed - either insufficient balance or already paid recently.
            // Re-read from DB to determine which case.
            ClaimBank current = repository.getBank(claim.getId());
            if (current == null) return;

            // If already paid recently (dedup blocked it), just ensure timestamps are correct
            if (current.getLastUpkeepPayment() != null) {
                Duration sinceLastPayment =
                        Duration.between(current.getLastUpkeepPayment(), Instant.now());
                if (sinceLastPayment.toHours() < paymentIntervalHours) {
                    // Already paid - fix the next due timestamp if needed.
                    // Use atomic update (only touches next_upkeep_due) instead of saveBank()
                    // to avoid overwriting balance/grace_period_start with stale values.
                    if (current.getNextUpkeepDue() == null
                            || current.getNextUpkeepDue().isBefore(Instant.now())) {
                        Instant correctedDue =
                                current.getLastUpkeepPayment()
                                        .plus(Duration.ofHours(paymentIntervalHours));
                        repository.updateNextUpkeepDue(
                                claim.getId(), java.sql.Timestamp.from(correctedDue));
                    }
                    bankManager.invalidateCache(claim.getId());
                    return;
                }
            }

            // Insufficient balance - start grace period
            if (current.getGracePeriodStart() == null) {
                current.setGracePeriodStart(Instant.now());
                current.setNextUpkeepDue(null);
                repository.saveBank(current);
                bankManager.invalidateCache(claim.getId());

                notifyOwner(
                        claim,
                        "<red>Claim '"
                                + claim.getName()
                                + "' can't afford upkeep! Deposit funds within "
                                + gracePeriodDays
                                + " days or chunks will be unclaimed.");
            }
        }
    }

    private void processGracePeriodClaim(ClaimBank bank) {
        // Re-fetch from DB to avoid stale batch data (deposit may have already cleared grace
        // period)
        ClaimBank freshBank = repository.getBank(bank.getClaimId());
        if (freshBank == null || freshBank.getGracePeriodStart() == null) {
            return; // Grace period was already cleared (likely by a deposit)
        }
        bank = freshBank;

        Claim claim = plugin.getClaimManager().getClaimById(bank.getClaimId());
        if (claim == null) {
            plugin.getLogger()
                    .warning(
                            "Skipping grace period processing for bank "
                                    + bank.getClaimId()
                                    + " - claim not found");
            return;
        }

        UpkeepConfig upkeepConfig = repository.getOrCreateUpkeepConfig(bank.getClaimId());

        // Calculate upkeep cost to check if bank can now afford it
        ClaimLevel level = repository.getOrCreateLevel(claim.getId());
        double levelDiscount = ClaimBenefits.getUpkeepDiscountForLevel(level.getLevel());
        double effectiveDiscount = Math.max(upkeepConfig.getDiscountPercentage(), levelDiscount);
        double costPerChunk = defaultCostPerChunk * (1.0 - effectiveDiscount / 100.0);
        double totalCost = costPerChunk * claim.getChunks().size();

        // Auto-recover: attempt atomic charge with dedup guard.
        // The TIMESTAMPDIFF guard prevents double-charging if the upkeep-due batch
        // already charged this claim in the same cycle.
        Instant nextDue = Instant.now().plus(Duration.ofHours(paymentIntervalHours));
        boolean charged =
                repository.recoverFromGracePeriodAtomically(
                        claim.getId(),
                        totalCost,
                        java.sql.Timestamp.from(nextDue),
                        paymentIntervalHours);

        if (charged) {
            upkeepConfig.resetNotifications();
            repository.saveUpkeepConfig(upkeepConfig);
            bankManager.invalidateCache(claim.getId());

            ClaimBank updated = repository.getBank(claim.getId());
            double newBalance = updated != null ? updated.getBalance() : 0;

            BankTransaction transaction =
                    new BankTransaction(
                            claim.getId(),
                            null,
                            BankTransaction.TransactionType.UPKEEP,
                            totalCost,
                            newBalance,
                            "Upkeep recovery for " + claim.getChunks().size() + " chunks");
            repository.recordTransaction(transaction);

            plugin.getLogger()
                    .info(
                            "Claim '"
                                    + claim.getName()
                                    + "' (ID: "
                                    + claim.getId()
                                    + ") recovered from grace period - paid $"
                                    + String.format("%.2f", totalCost));

            notifyOwnerChat(
                    claim,
                    "<green>Claim '"
                            + claim.getName()
                            + "' restored - $"
                            + String.format("%.2f", totalCost)
                            + " upkeep paid from bank.");
            return;
        }

        Duration graceDuration = Duration.between(bank.getGracePeriodStart(), Instant.now());
        long hoursInGrace = graceDuration.toHours();
        long daysInGrace = graceDuration.toDays();

        // Check if grace period expired
        if (daysInGrace >= gracePeriodDays
                && autoUnclaimEnabled
                && upkeepConfig.isAutoUnclaimEnabled()) {
            unclaimFurthestChunks(claim, bank, upkeepConfig);
            return;
        }

        // Send notifications
        for (int notifyHour : notificationHours) {
            int notifyDay = notifyHour / 24;
            if (hoursInGrace >= (gracePeriodDays * 24 - notifyHour)
                    && upkeepConfig.getNotificationsSent()
                            < notificationHours.indexOf(notifyHour) + 1) {

                int daysRemaining = gracePeriodDays - (int) daysInGrace;
                notifyOwner(
                        claim,
                        "<yellow>Claim '"
                                + claim.getName()
                                + "' - "
                                + daysRemaining
                                + " day(s) left before chunks are unclaimed.");

                upkeepConfig.incrementNotificationsSent();
                repository.saveUpkeepConfig(upkeepConfig);
                break;
            }
        }
    }

    private void unclaimFurthestChunks(Claim claim, ClaimBank bank, UpkeepConfig upkeepConfig) {
        if (claim.getChunks().isEmpty()) return;

        // Calculate how many chunks we need to remove (apply level discount like processClaimUpkeep
        // does)
        ClaimLevel level = repository.getOrCreateLevel(claim.getId());
        double levelDiscount = ClaimBenefits.getUpkeepDiscountForLevel(level.getLevel());
        double effectiveDiscount = Math.max(upkeepConfig.getDiscountPercentage(), levelDiscount);
        double costPerChunk = defaultCostPerChunk * (1.0 - effectiveDiscount / 100.0);
        int chunksToKeep = Math.max(1, (int) (bank.getBalance() / costPerChunk));

        if (chunksToKeep >= claim.getChunks().size()) {
            // Balance now covers upkeep, exit grace period
            bank.setGracePeriodStart(null);
            repository.saveBank(bank);
            return;
        }

        int chunksToRemove = claim.getChunks().size() - chunksToKeep;

        // Calculate claim center (geometric centroid of all chunks)
        List<ClaimedChunk> chunks = new ArrayList<>(claim.getChunks());
        int centerX = chunks.stream().mapToInt(ClaimedChunk::getChunkX).sum() / chunks.size();
        int centerZ = chunks.stream().mapToInt(ClaimedChunk::getChunkZ).sum() / chunks.size();

        // PERFORMANCE OPTIMIZATION: Pre-calculate distances once instead of during sort comparisons
        // This changes complexity from O(n²) to O(n log n) for large claims
        // For 1000 chunks: ~1,000,000 calculations -> ~10,000 calculations (100x faster)
        Map<ClaimedChunk, Integer> distanceSquared = new HashMap<>(chunks.size());
        for (ClaimedChunk chunk : chunks) {
            int dx = chunk.getChunkX() - centerX;
            int dz = chunk.getChunkZ() - centerZ;
            // Use squared distance to avoid expensive Math.sqrt() calls
            // Since we only need relative ordering, squared distance works perfectly
            int dist = dx * dx + dz * dz;
            distanceSquared.put(chunk, dist);
        }

        // Sort chunks by pre-calculated distances (furthest first)
        chunks.sort((a, b) -> Integer.compare(distanceSquared.get(b), distanceSquared.get(a)));

        // Calculate remaining chunk count before starting removal
        final int originalChunkCount = claim.getChunks().size();
        final int actualChunksToRemove = Math.min(chunksToRemove, chunks.size());
        final int remainingChunkCount = originalChunkCount - actualChunksToRemove;

        // Remove chunks
        int removed = 0;
        for (int i = 0; i < actualChunksToRemove; i++) {
            ClaimedChunk chunk = chunks.get(i);

            // Record unclaim
            repository.recordUnclaimedByUpkeep(
                    claim.getId(), chunk.getWorld(), chunk.getChunkX(), chunk.getChunkZ());

            // Remove from claim
            plugin.getServer()
                    .getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                plugin.getRepository().deleteChunk(chunk);
                                claim.removeChunk(chunk);
                                plugin.getClaimManager()
                                        .invalidateChunkCache(
                                                chunk.getWorld(),
                                                chunk.getChunkX(),
                                                chunk.getChunkZ());
                            });

            removed++;
        }

        // Reset grace period if remaining chunks can afford upkeep
        // Use pre-calculated remaining count since actual removal is async
        double remainingCost = remainingChunkCount * costPerChunk;
        if (bank.getBalance() >= remainingCost) {
            bank.setGracePeriodStart(null);
            upkeepConfig.resetNotifications();
            repository.saveBank(bank);
            repository.saveUpkeepConfig(upkeepConfig);
            bankManager.invalidateCache(claim.getId());
        }

        final int finalRemoved = removed;
        notifyOwner(
                claim,
                "<red>"
                        + finalRemoved
                        + " chunk(s) unclaimed from '"
                        + claim.getName()
                        + "' due to unpaid upkeep. Deposit funds to keep remaining chunks.");

        plugin.getLogger()
                .info(
                        "Unclaimed "
                                + finalRemoved
                                + " chunks from claim '"
                                + claim.getName()
                                + "' (ID: "
                                + claim.getId()
                                + ") due to unpaid upkeep");
    }

    /**
     * Send a persistent upkeep notification to a claim owner. The NotificationManager handles both
     * chat delivery (if online) and persistent storage (for offline players), so we only call it
     * once to avoid duplicate chat messages.
     */
    private void notifyOwner(Claim claim, String message) {
        if (plugin.getNotificationManager() != null) {
            plugin.getNotificationManager()
                    .notify(
                            claim.getOwnerUuid(),
                            net.serverplugins.claim.models.NotificationType.UPKEEP_WARNING,
                            "Upkeep Notice",
                            message,
                            net.serverplugins.claim.models.NotificationPriority.HIGH);
        } else {
            // Fallback if notification manager is unavailable
            plugin.getServer()
                    .getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                OfflinePlayer owner = Bukkit.getOfflinePlayer(claim.getOwnerUuid());
                                if (owner.isOnline() && owner.getPlayer() != null) {
                                    TextUtil.send(
                                            owner.getPlayer(),
                                            plugin.getClaimConfig().getMessage("prefix") + message);
                                }
                            });
        }
    }

    /**
     * Send a simple chat message to a claim owner (no persistent notification). Used for positive
     * messages like recovery confirmations that don't need to persist.
     */
    private void notifyOwnerChat(Claim claim, String message) {
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            OfflinePlayer owner = Bukkit.getOfflinePlayer(claim.getOwnerUuid());
                            if (owner.isOnline() && owner.getPlayer() != null) {
                                TextUtil.send(
                                        owner.getPlayer(),
                                        plugin.getClaimConfig().getMessage("prefix") + message);
                            }
                        });
    }

    public void initializeClaimUpkeep(int claimId) {
        if (!upkeepEnabled) return;

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            ClaimBank bank = repository.getOrCreateBank(claimId);
                            bank.setNextUpkeepDue(
                                    Instant.now().plus(Duration.ofHours(paymentIntervalHours)));
                            repository.saveBank(bank);
                            repository.getOrCreateUpkeepConfig(claimId);
                        });
    }

    public double getUpkeepCost(Claim claim) {
        UpkeepConfig config = repository.getOrCreateUpkeepConfig(claim.getId());
        ClaimLevel level = repository.getOrCreateLevel(claim.getId());
        double levelDiscount = ClaimBenefits.getUpkeepDiscountForLevel(level.getLevel());
        double effectiveDiscount = Math.max(config.getDiscountPercentage(), levelDiscount);
        return defaultCostPerChunk * claim.getChunks().size() * (1.0 - effectiveDiscount / 100.0);
    }

    public boolean isUpkeepEnabled() {
        return upkeepEnabled;
    }

    /** Send actionbar reminders to online players with claims in grace period. */
    private void sendActionbarReminders() {
        if (!upkeepEnabled) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            List<ClaimGraceInfo> atRiskClaims = getClaimsInGracePeriod(player.getUniqueId());
            if (!atRiskClaims.isEmpty()) {
                // Show actionbar warning for the most urgent claim
                ClaimGraceInfo mostUrgent =
                        atRiskClaims.stream()
                                .min(Comparator.comparingLong(ClaimGraceInfo::hoursRemaining))
                                .orElse(null);

                if (mostUrgent != null && mostUrgent.hoursRemaining() <= 72) {
                    String message;
                    if (mostUrgent.hoursRemaining <= 24) {
                        message =
                                "<red><bold>CLAIM AT RISK!</bold></red> <gold>"
                                        + mostUrgent.claimName
                                        + "</gold> <red>expires in "
                                        + mostUrgent.hoursRemaining
                                        + "h!</red>";
                    } else {
                        long days = mostUrgent.hoursRemaining / 24;
                        message =
                                "<yellow>Claim <gold>"
                                        + mostUrgent.claimName
                                        + "</gold> needs upkeep - "
                                        + days
                                        + " day(s) remaining</yellow>";
                    }
                    TextUtil.sendActionBar(player, message);
                }
            }
        }
    }

    /**
     * Proactively clear a stale grace period flag when balance covers upkeep. This handles the case
     * where a deposit funded the bank but the dedup guard in chargeUpkeepAtomically() blocked
     * recovery.
     *
     * @param claim The claim to check
     * @param bank The claim's bank (must have grace_period_start != null)
     * @return true if grace period was cleared
     */
    private boolean tryProactivelyClearGracePeriod(Claim claim, ClaimBank bank) {
        double upkeepCost = getUpkeepCost(claim);
        if (upkeepCost <= 0 || bank.getBalance() < upkeepCost) {
            return false;
        }

        boolean cleared =
                repository.clearGracePeriodIfFunded(
                        claim.getId(), upkeepCost, paymentIntervalHours);
        if (cleared) {
            UpkeepConfig upkeepConfig = repository.getOrCreateUpkeepConfig(claim.getId());
            upkeepConfig.resetNotifications();
            repository.saveUpkeepConfig(upkeepConfig);
            bankManager.invalidateCache(claim.getId());

            plugin.getLogger()
                    .info(
                            "Proactively cleared stale grace period for claim '"
                                    + claim.getName()
                                    + "' (ID: "
                                    + claim.getId()
                                    + ") - balance covers upkeep");
        }
        return cleared;
    }

    /**
     * Get all claims in grace period for a player. Used for login warnings and actionbar reminders.
     *
     * @param playerUuid The player's UUID
     * @return List of claim grace period info
     */
    public List<ClaimGraceInfo> getClaimsInGracePeriod(UUID playerUuid) {
        if (!upkeepEnabled) return Collections.emptyList();

        List<Claim> playerClaims = plugin.getClaimManager().getPlayerClaims(playerUuid);
        List<ClaimGraceInfo> result = new ArrayList<>();

        for (Claim claim : playerClaims) {
            ClaimBank bank = repository.getBank(claim.getId());
            if (bank != null && bank.getGracePeriodStart() != null) {
                // If the bank can afford upkeep, proactively clear the stale grace flag
                double upkeepCost = getUpkeepCost(claim);
                if (upkeepCost > 0 && bank.getBalance() >= upkeepCost) {
                    tryProactivelyClearGracePeriod(claim, bank);
                    continue; // Funded - not actually at risk
                }

                Duration graceDuration =
                        Duration.between(bank.getGracePeriodStart(), Instant.now());
                long hoursRemaining = (gracePeriodDays * 24L) - graceDuration.toHours();

                if (hoursRemaining > 0) {
                    result.add(
                            new ClaimGraceInfo(
                                    claim.getId(),
                                    claim.getName(),
                                    hoursRemaining,
                                    bank.getBalance(),
                                    upkeepCost));
                }
            }
        }

        return result;
    }

    /** Info about a claim in grace period. */
    public record ClaimGraceInfo(
            int claimId,
            String claimName,
            long hoursRemaining,
            double currentBalance,
            double upkeepCost) {}

    /** Full upkeep status info for a claim, used by login messages. */
    public record ClaimUpkeepStatus(
            int claimId,
            String claimName,
            double balance,
            double upkeepCost,
            int daysRemaining,
            Instant nextPaymentDue,
            boolean inGracePeriod,
            long graceHoursRemaining) {}

    /**
     * Get upkeep status for all of a player's claims.
     *
     * @param playerUuid The player's UUID
     * @return List of statuses for every claim the player owns
     */
    public List<ClaimUpkeepStatus> getClaimUpkeepStatuses(UUID playerUuid) {
        if (!upkeepEnabled) return Collections.emptyList();

        List<Claim> playerClaims = plugin.getClaimManager().getPlayerClaims(playerUuid);
        List<ClaimUpkeepStatus> result = new ArrayList<>();

        for (Claim claim : playerClaims) {
            ClaimBank bank = repository.getBank(claim.getId());
            if (bank == null) continue;

            double upkeepCost = getUpkeepCost(claim);
            int daysRemaining = upkeepCost > 0 ? (int) (bank.getBalance() / upkeepCost) : -1;

            boolean inGrace = bank.getGracePeriodStart() != null;
            long graceHoursRemaining = 0;
            if (inGrace) {
                // If balance covers upkeep, this is a stale grace flag - clear it
                if (upkeepCost > 0 && bank.getBalance() >= upkeepCost) {
                    tryProactivelyClearGracePeriod(claim, bank);
                    inGrace = false;
                } else {
                    Duration graceDuration =
                            Duration.between(bank.getGracePeriodStart(), Instant.now());
                    graceHoursRemaining = (gracePeriodDays * 24L) - graceDuration.toHours();
                    if (graceHoursRemaining < 0) graceHoursRemaining = 0;
                }
            }

            result.add(
                    new ClaimUpkeepStatus(
                            claim.getId(),
                            claim.getName(),
                            bank.getBalance(),
                            upkeepCost,
                            daysRemaining,
                            bank.getNextUpkeepDue(),
                            inGrace,
                            graceHoursRemaining));
        }

        return result;
    }
}
