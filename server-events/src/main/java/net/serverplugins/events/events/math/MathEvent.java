package net.serverplugins.events.events.math;

import java.util.Random;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.MessageBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.events.EventsConfig;
import net.serverplugins.events.ServerEvents;
import net.serverplugins.events.events.EventReward;
import net.serverplugins.events.events.ServerEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/** Math race event - first player to solve the math problem wins. */
public class MathEvent implements ServerEvent, Listener {

    private final ServerEvents plugin;
    private final EventsConfig config;
    private final Random random = new Random();

    private boolean active;
    private String problem;
    private int answer;
    private Player winner;
    private BukkitTask timeoutTask;

    public MathEvent(ServerEvents plugin) {
        this.plugin = plugin;
        this.config = plugin.getEventsConfig();
    }

    @Override
    public EventType getType() {
        return EventType.MATH;
    }

    @Override
    public String getDisplayName() {
        return "Math Race";
    }

    @Override
    public void start() {
        if (active) return;

        active = true;
        winner = null;

        // Generate a random math problem
        generateProblem();

        // Register listener
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Announce the event
        announceEvent();

        // Set timeout
        int timeLimit = config.getMathTimeLimit();
        timeoutTask =
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (active && winner == null) {
                            timeout();
                        }
                    }
                }.runTaskLater(plugin, timeLimit * 20L);
    }

    private void generateProblem() {
        int difficulty = config.getMathDifficulty();
        int maxNum =
                switch (difficulty) {
                    case 1 -> 20; // Easy: 1-20
                    case 2 -> 50; // Medium: 1-50
                    case 3 -> 100; // Hard: 1-100
                    default -> 50;
                };

        int a, b;
        int operation = random.nextInt(difficulty >= 2 ? 3 : 2); // Easy: +/-, Medium+: +/-/*

        switch (operation) {
            case 0 -> { // Addition
                a = random.nextInt(maxNum) + 1;
                b = random.nextInt(maxNum) + 1;
                problem = a + " + " + b;
                answer = a + b;
            }
            case 1 -> { // Subtraction (ensure positive result)
                a = random.nextInt(maxNum) + 1;
                b = random.nextInt(a) + 1;
                problem = a + " - " + b;
                answer = a - b;
            }
            case 2 -> { // Multiplication
                int multMax = difficulty >= 3 ? 15 : 12;
                a = random.nextInt(multMax) + 1;
                b = random.nextInt(multMax) + 1;
                problem = a + " x " + b;
                answer = a * b;
            }
            default -> {
                a = random.nextInt(maxNum) + 1;
                b = random.nextInt(maxNum) + 1;
                problem = a + " + " + b;
                answer = a + b;
            }
        }
    }

    private void announceEvent() {
        String startMessage = config.getMathMessage("start");
        TextUtil.broadcastRaw(config.getPrefix() + startMessage);

        TextUtil.broadcastRaw(
                MessageBuilder.create()
                        .prefix(config.getMessenger().getPrefix())
                        .emphasis("Solve: ")
                        .warning(problem + " = ?")
                        .build());

        TextUtil.broadcastRaw(
                MessageBuilder.create()
                        .prefix(config.getMessenger().getPrefix())
                        .append(ColorScheme.SECONDARY + "Type your answer in chat!")
                        .build());

        // Play sound
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!active || winner != null) return;

        String message = event.getMessage().trim();

        // Try to parse as number
        try {
            int playerAnswer = Integer.parseInt(message);

            if (playerAnswer == answer) {
                event.setCancelled(true);
                winner = event.getPlayer();

                // Run sync
                Bukkit.getScheduler().runTask(plugin, this::announceWinner);
            }
        } catch (NumberFormatException ignored) {
            // Not a number, ignore
        }
    }

    private void announceWinner() {
        if (winner == null) return;

        // Cancel timeout
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }

        // Announce winner
        String message =
                config.getMathMessage("winner")
                        .replace("%player%", winner.getName())
                        .replace("%answer%", String.valueOf(answer));
        TextUtil.broadcastRaw(config.getPrefix() + message);

        // Play sounds
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(winner)) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            } else {
                player.playSound(
                        player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }

        // Give reward
        EventReward reward =
                EventReward.of(
                        config.getMathCoins(), config.getMathKeyChance(), config.getMathKeyType());
        reward.give(plugin, winner);

        // Stop the event
        stop();
    }

    private void timeout() {
        // Announce timeout
        String message =
                config.getMathMessage("timeout").replace("%answer%", String.valueOf(answer));
        TextUtil.broadcastRaw(config.getPrefix() + message);

        // Play sad sound
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }

        // Stop the event
        stop();
    }

    @Override
    public void stop() {
        if (!active) return;

        active = false;

        // Cancel timeout task
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }

        // Unregister listener
        HandlerList.unregisterAll(this);

        // Clear event reference
        plugin.getEventManager().clearActiveEvent();
    }

    @Override
    public boolean isActive() {
        return active;
    }

    public String getProblem() {
        return problem;
    }

    public int getAnswer() {
        return answer;
    }
}
