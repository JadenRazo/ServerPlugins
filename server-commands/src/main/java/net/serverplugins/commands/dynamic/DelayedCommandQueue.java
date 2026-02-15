package net.serverplugins.commands.dynamic;

import java.util.*;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.dynamic.placeholders.PlaceholderProcessor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manages per-player command queues for sequential execution with delays. Automatically cancels
 * queues when players disconnect.
 */
public class DelayedCommandQueue {

    private final ServerCommands plugin;
    private final Map<UUID, QueuedExecution> activeQueues = new HashMap<>();

    public DelayedCommandQueue(ServerCommands plugin) {
        this.plugin = plugin;
    }

    /**
     * Execute a list of parsed commands sequentially for a player
     *
     * @param player Player to execute commands for
     * @param commands List of parsed commands to execute
     */
    public void executeSequentially(
            Player player, List<PlaceholderProcessor.ParsedCommand> commands) {
        UUID playerId = player.getUniqueId();

        // Cancel any existing queue for this player
        cancelQueue(playerId);

        // Create new queue execution
        QueuedExecution execution = new QueuedExecution(player, commands);
        activeQueues.put(playerId, execution);

        // Start execution
        execution.start();
    }

    /**
     * Cancel the command queue for a specific player
     *
     * @param playerId UUID of the player
     */
    public void cancelQueue(UUID playerId) {
        QueuedExecution execution = activeQueues.remove(playerId);
        if (execution != null) {
            execution.cancel();
        }
    }

    /** Cancel all active command queues */
    public void cancelAll() {
        for (QueuedExecution execution : activeQueues.values()) {
            execution.cancel();
        }
        activeQueues.clear();
    }

    /**
     * Check if a player has an active command queue
     *
     * @param playerId UUID of the player
     * @return true if player has active queue
     */
    public boolean hasActiveQueue(UUID playerId) {
        return activeQueues.containsKey(playerId);
    }

    /** Represents a queued command execution for a player */
    private class QueuedExecution {
        private final Player player;
        private final List<PlaceholderProcessor.ParsedCommand> commands;
        private int currentIndex = 0;
        private BukkitTask currentTask;
        private boolean cancelled = false;

        public QueuedExecution(Player player, List<PlaceholderProcessor.ParsedCommand> commands) {
            this.player = player;
            this.commands = commands;
        }

        /** Start executing the command queue */
        public void start() {
            executeNext();
        }

        /** Execute the next command in the queue */
        private void executeNext() {
            if (cancelled || currentIndex >= commands.size()) {
                // Queue finished, remove from active queues
                activeQueues.remove(player.getUniqueId());
                return;
            }

            // Check if player is still online
            if (!player.isOnline()) {
                cancel();
                return;
            }

            PlaceholderProcessor.ParsedCommand command = commands.get(currentIndex);
            currentIndex++;

            switch (command.getType()) {
                case TEXT -> {
                    // Send text message immediately
                    TextUtil.send(player, command.asComponent());
                    executeNext();
                }
                case DELAY -> {
                    // Schedule next command after delay
                    int delayTicks = command.asInt();
                    currentTask =
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    executeNext();
                                }
                            }.runTaskLater(plugin, delayTicks);
                }
                case COMMAND -> {
                    // Execute command immediately
                    String cmd = command.asString();
                    // Strip leading slash if present (dispatchCommand doesn't expect it)
                    if (cmd.startsWith("/")) {
                        cmd = cmd.substring(1);
                    }
                    Bukkit.dispatchCommand(player, cmd);
                    executeNext();
                }
            }
        }

        /** Cancel the queue execution */
        public void cancel() {
            cancelled = true;
            if (currentTask != null && !currentTask.isCancelled()) {
                currentTask.cancel();
            }
        }
    }
}
