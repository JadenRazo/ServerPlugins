package net.serverplugins.arcade.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * Utility for logging game outcomes for fairness auditing and debugging. Creates daily log files
 * with game results, player actions, and outcomes.
 *
 * <p>This logger uses async file I/O to prevent blocking the main thread during high-frequency
 * gambling events (bets, wins, crashes, etc). Log entries are queued and written in batches on
 * async tasks.
 *
 * <p><b>Thread Safety:</b> All public methods are thread-safe and can be called from any thread.
 * File writes occur asynchronously via Bukkit's scheduler.
 */
public class AuditLogger {

    private static final DateTimeFormatter FILE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter LOG_TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss");
    private static File logDirectory;
    private static Plugin plugin;

    /**
     * Thread-safe queues for pending log entries, organized by game type. Allows batching writes
     * for each game type's daily log file.
     */
    private static final ConcurrentHashMap<String, ConcurrentLinkedQueue<String>> logQueues =
            new ConcurrentHashMap<>();

    /**
     * Initialize the audit logger with the plugin instance.
     *
     * @param pluginInstance The ServerArcade plugin instance (required for async task scheduling)
     */
    public static void initialize(Plugin pluginInstance) {
        plugin = pluginInstance;
        logDirectory = new File(pluginInstance.getDataFolder(), "audit-logs");
        if (!logDirectory.exists()) {
            logDirectory.mkdirs();
        }
    }

    /** Log a crash game outcome. */
    public static void logCrashGame(
            UUID playerId,
            String playerName,
            int betAmount,
            double crashPoint,
            Double cashoutMultiplier,
            int payout) {
        String timestamp = getCurrentTimestamp();
        String action = cashoutMultiplier != null ? "CASHOUT" : "CRASH";
        String multiplier =
                cashoutMultiplier != null
                        ? String.format("%.2f", cashoutMultiplier)
                        : String.format("%.2f", crashPoint);

        String logEntry =
                String.format(
                        "[%s] CRASH | Player: %s (%s) | Action: %s | Bet: $%d | Multiplier: %sx | Payout: $%d | CrashPoint: %.2fx",
                        timestamp,
                        playerName,
                        playerId,
                        action,
                        betAmount,
                        multiplier,
                        payout,
                        crashPoint);

        writeLog("crash", logEntry);
    }

    /** Log a lottery game outcome. */
    public static void logLotteryGame(
            String winnerName, UUID winnerId, int totalPool, int playerCount, String gameName) {
        String timestamp = getCurrentTimestamp();
        String logEntry =
                String.format(
                        "[%s] LOTTERY | Game: %s | Winner: %s (%s) | Pool: $%d | Players: %d",
                        timestamp, gameName, winnerName, winnerId, totalPool, playerCount);

        writeLog("lottery", logEntry);
    }

    /** Log a slots game outcome. */
    public static void logSlotsGame(
            UUID playerId,
            String playerName,
            int betAmount,
            String[] symbols,
            String rewardType,
            int payout) {
        String timestamp = getCurrentTimestamp();
        String symbolStr = String.join(",", symbols);
        String outcome = payout > 0 ? "WIN" : "LOSS";

        String logEntry =
                String.format(
                        "[%s] SLOTS | Player: %s (%s) | Bet: $%d | Symbols: [%s] | Outcome: %s | Reward: %s | Payout: $%d",
                        timestamp,
                        playerName,
                        playerId,
                        betAmount,
                        symbolStr,
                        outcome,
                        rewardType != null ? rewardType : "none",
                        payout);

        writeLog("slots", logEntry);
    }

    /** Log a general game event. */
    public static void logGameEvent(String gameType, String event, String details) {
        String timestamp = getCurrentTimestamp();
        String logEntry =
                String.format(
                        "[%s] %s | Event: %s | Details: %s",
                        timestamp, gameType.toUpperCase(), event, details);

        writeLog(gameType.toLowerCase(), logEntry);
    }

    /** Log a player action (bet placed, game joined, etc.). */
    public static void logPlayerAction(
            String gameType, UUID playerId, String playerName, String action, String details) {
        String timestamp = getCurrentTimestamp();
        String logEntry =
                String.format(
                        "[%s] %s | Player: %s (%s) | Action: %s | Details: %s",
                        timestamp, gameType.toUpperCase(), playerName, playerId, action, details);

        writeLog(gameType.toLowerCase(), logEntry);
    }

    /**
     * Queues a log entry for async writing to the appropriate daily log file. This method is
     * non-blocking and safe to call from the main thread.
     *
     * <p>Log entries are written asynchronously in batches to minimize disk I/O overhead during
     * high-frequency events (e.g., rapid slot machine spins).
     *
     * @param gameType The game type (crash, slots, lottery, etc.) - determines the log file
     * @param logEntry The formatted log entry to write
     */
    private static void writeLog(String gameType, String logEntry) {
        if (logDirectory == null || plugin == null) {
            Bukkit.getLogger().warning("[AuditLogger] Not initialized! Call initialize() first.");
            return;
        }

        // Get or create queue for this game type
        ConcurrentLinkedQueue<String> queue =
                logQueues.computeIfAbsent(gameType, k -> new ConcurrentLinkedQueue<>());

        // Queue the entry (non-blocking)
        queue.offer(logEntry);

        // Schedule async flush for this game type
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> flushLogQueue(gameType));
    }

    /**
     * Flushes all queued log entries for a specific game type to disk. This method runs
     * asynchronously to prevent blocking the main thread.
     *
     * <p>Multiple log entries are written in a single file operation for efficiency. This is
     * particularly important for high-frequency games like slots and crash.
     *
     * @param gameType The game type whose queue should be flushed
     */
    private static void flushLogQueue(String gameType) {
        ConcurrentLinkedQueue<String> queue = logQueues.get(gameType);
        if (queue == null || queue.isEmpty()) {
            return;
        }

        try {
            String date = LocalDateTime.now().format(FILE_DATE_FORMAT);
            File logFile = new File(logDirectory, gameType + "-" + date + ".log");

            // Write all pending entries in a single file operation
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                String entry;
                while ((entry = queue.poll()) != null) {
                    writer.write(entry);
                    writer.newLine();
                }
            }

        } catch (IOException e) {
            Bukkit.getLogger()
                    .log(
                            Level.SEVERE,
                            "[AuditLogger] Failed to write log entries for " + gameType,
                            e);

            // Note: We don't re-queue entries on failure to prevent infinite loops
            // Console logging above provides backup audit trail
        }
    }

    /**
     * Flushes all pending log entries for all game types synchronously. Should be called during
     * plugin shutdown to prevent data loss.
     *
     * <p>This method blocks until all queued entries are written to disk. It should only be called
     * from the onDisable() method.
     */
    public static void shutdown() {
        if (logQueues.isEmpty()) {
            return;
        }

        Bukkit.getLogger().info("[AuditLogger] Flushing " + logQueues.size() + " log queues...");

        for (String gameType : logQueues.keySet()) {
            flushLogQueue(gameType);
        }

        Bukkit.getLogger().info("[AuditLogger] All log queues flushed");
    }

    /** Get current timestamp for log entries. */
    private static String getCurrentTimestamp() {
        return LocalDateTime.now().format(LOG_TIME_FORMAT);
    }

    /** Helper to extract symbols from SlotItem array. */
    public static String[] symbolsToArray(Object[] items) {
        String[] symbols = new String[items.length];
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
                symbols[i] = items[i].toString();
            } else {
                symbols[i] = "null";
            }
        }
        return symbols;
    }
}
