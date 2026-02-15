package net.serverplugins.filter.chat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.filter.FilterConfig;
import net.serverplugins.filter.ServerFilter;
import net.serverplugins.filter.filter.FilterResult;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Handles chat filter violations with async file logging. Uses a concurrent queue to prevent main
 * thread blocking during file I/O operations.
 */
public class ViolationHandler {

    private final ServerFilter plugin;
    private final FilterConfig config;
    private final File logFile;

    /**
     * Thread-safe queue for pending log entries. Violations are queued from the main thread and
     * written asynchronously.
     */
    private final ConcurrentLinkedQueue<String> logQueue;

    public ViolationHandler(ServerFilter plugin, FilterConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.logFile = new File(plugin.getDataFolder(), "violations.log");
        this.logQueue = new ConcurrentLinkedQueue<>();
    }

    public void handleSlurViolation(Player player, String originalMessage, FilterResult result) {
        // Notify the player using configured message
        config.getMessenger().send(player, "slur-blocked");

        // Get matched words for logging
        String matchedWords =
                result.getMatches().stream()
                        .map(m -> m.matchedText())
                        .distinct()
                        .collect(Collectors.joining(", "));

        // Notify staff
        notifyStaff(player, "SLUR", originalMessage, matchedWords);

        // Log violation
        logViolation(player, "SLUR", originalMessage, matchedWords);
    }

    public void handleSpamViolation(Player player, String reason) {
        config.getMessenger().send(player, "spam-warning", Placeholder.of("reason", reason));
    }

    public void handleCapsViolation(Player player) {
        config.getMessenger().send(player, "caps-warning");
    }

    public void handleAdvertisingViolation(Player player, String originalMessage) {
        config.getMessenger().send(player, "advertising-blocked");

        notifyStaff(player, "ADVERTISING", originalMessage, "URL/IP detected");
        logViolation(player, "ADVERTISING", originalMessage, "URL/IP detected");
    }

    private void notifyStaff(Player violator, String type, String message, String details) {
        String staffPermission = config.getStaffNotificationPermission();

        // Build staff notification using ColorScheme
        String notification =
                config.getMessenger().getPrefix()
                        + ColorScheme.HIGHLIGHT
                        + violator.getName()
                        + ColorScheme.INFO
                        + " triggered "
                        + ColorScheme.ERROR
                        + type
                        + ColorScheme.INFO
                        + ": "
                        + ColorScheme.SECONDARY
                        + truncate(message, 50);

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(staffPermission)) {
                config.getMessenger().sendRaw(staff, notification);
            }
        }

        // Also log to console
        plugin.getLogger()
                .warning(
                        "["
                                + type
                                + "] "
                                + violator.getName()
                                + ": "
                                + message
                                + " (matched: "
                                + details
                                + ")");
    }

    /**
     * Logs a violation asynchronously to prevent main thread blocking. The log entry is queued and
     * written to disk on an async task.
     *
     * @param player The player who triggered the violation
     * @param type The violation type (SLUR, ADVERTISING, etc.)
     * @param message The original message content
     * @param details Additional details about the match
     */
    private void logViolation(Player player, String type, String message, String details) {
        if (!config.isLogToFileEnabled()) return;

        // Format the log entry on the main thread (cheap operation)
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String logEntry =
                String.format(
                        "[%s] [%s] %s (%s): %s | Matched: %s",
                        timestamp, type, player.getName(), player.getUniqueId(), message, details);

        // Queue the entry for async writing
        logQueue.offer(logEntry);

        // Schedule async file write
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::flushLogQueue);
    }

    /**
     * Flushes all queued log entries to disk asynchronously. This method runs off the main thread
     * to prevent I/O blocking. Uses batched writes for efficiency when multiple violations occur
     * simultaneously.
     */
    private void flushLogQueue() {
        if (logQueue.isEmpty()) return;

        try {
            // Ensure log file exists (safe to do async)
            if (!logFile.exists()) {
                logFile.getParentFile().mkdirs();
                logFile.createNewFile();
            }

            // Write all pending entries in a single file operation
            try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
                String entry;
                while ((entry = logQueue.poll()) != null) {
                    writer.println(entry);
                }
            }
        } catch (IOException e) {
            // Log to console if file write fails (still on async thread)
            plugin.getLogger().warning("Failed to write violation log: " + e.getMessage());

            // Note: We don't re-queue entries on failure to prevent infinite loops
            // Console logging in notifyStaff() provides backup audit trail
        }
    }

    /**
     * Flushes any remaining queued log entries synchronously. Should be called during plugin
     * shutdown to prevent data loss.
     */
    public void shutdown() {
        if (!logQueue.isEmpty()) {
            flushLogQueue();
        }
    }

    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }
}
