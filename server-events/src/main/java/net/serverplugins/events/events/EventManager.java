package net.serverplugins.events.events;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.serverplugins.api.messages.MessageBuilder;
import net.serverplugins.events.ServerEvents;
import net.serverplugins.events.events.crafting.CraftingEvent;
import net.serverplugins.events.events.dragon.DragonFightEvent;
import net.serverplugins.events.events.dropparty.DropPartyEvent;
import net.serverplugins.events.events.math.MathEvent;
import net.serverplugins.events.events.pinata.PinataEvent;
import net.serverplugins.events.events.pinata.PremiumPinataEvent;
import net.serverplugins.events.events.spelling.SpellingEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/** Manages server events and scheduling. */
public class EventManager {

    private final ServerEvents plugin;
    private final Random random = new Random();

    private ServerEvent activeEvent;
    private BukkitTask schedulerTask;
    private BukkitTask countdownTask;
    private BukkitTask keyallTask;
    private BukkitTask keyallCountdownTask;
    private long lastEventTime;
    private long lastKeyallTime;
    private final Set<Integer> sentWarnings = new HashSet<>();
    private final Set<Integer> sentKeyallWarnings = new HashSet<>();

    public EventManager(ServerEvents plugin) {
        this.plugin = plugin;
        this.lastEventTime = System.currentTimeMillis();
        this.lastKeyallTime = 0;
    }

    /** Start the automatic event scheduler. */
    public void startScheduler() {
        startScheduler(true);
    }

    /**
     * Start the automatic event scheduler.
     *
     * @param logMessage whether to log the start message
     */
    private void startScheduler(boolean logMessage) {
        if (schedulerTask != null) {
            schedulerTask.cancel();
        }
        if (countdownTask != null) {
            countdownTask.cancel();
        }

        int intervalTicks = plugin.getEventsConfig().getSchedulerInterval() * 20;
        sentWarnings.clear();

        schedulerTask =
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (activeEvent == null || !activeEvent.isActive()) {
                            sentWarnings.clear();
                            triggerRandomEvent();
                        }
                    }
                }.runTaskTimer(plugin, intervalTicks, intervalTicks);

        // Start countdown warning task (checks every second)
        if (plugin.getEventsConfig().isCountdownEnabled()) {
            countdownTask =
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            checkCountdownWarnings();
                        }
                    }.runTaskTimer(plugin, 20L, 20L); // Check every second
        }

        if (logMessage) {
            plugin.getLogger()
                    .info(
                            "Event scheduler started with interval: "
                                    + plugin.getEventsConfig().getSchedulerInterval()
                                    + " seconds");
        }
    }

    /** Check and send countdown warnings. */
    private void checkCountdownWarnings() {
        if (activeEvent != null && activeEvent.isActive()) return;

        int timeUntil = getTimeUntilNextEvent();
        if (timeUntil < 0) return;

        List<Integer> warnings = plugin.getEventsConfig().getCountdownWarnings();
        for (int warning : warnings) {
            if (timeUntil == warning && !sentWarnings.contains(warning)) {
                sentWarnings.add(warning);
                sendCountdownWarning(warning);
                break;
            }
        }
    }

    /** Send a countdown warning to all players. */
    private void sendCountdownWarning(int seconds) {
        String message =
                MessageBuilder.create()
                        .prefix(plugin.getEventsConfig().getMessenger().getPrefix())
                        .star()
                        .warning("Event starting in ")
                        .emphasis(String.valueOf(seconds))
                        .warning(" seconds!")
                        .build();

        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getEventsConfig().getMessenger().sendRaw(player, message);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
        }
    }

    /** Stop the scheduler. */
    public void stopScheduler() {
        if (schedulerTask != null) {
            schedulerTask.cancel();
            schedulerTask = null;
        }
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    /** Reset the scheduler timer (used after manual triggers). */
    public void resetSchedulerTimer() {
        lastEventTime = System.currentTimeMillis();
        sentWarnings.clear();
        if (schedulerTask != null) {
            stopScheduler();
            startScheduler(false); // Don't log on reset
        }
    }

    /**
     * Trigger a random event using weighted random selection. Events with higher weights are more
     * likely to be selected.
     */
    public boolean triggerRandomEvent() {
        if (activeEvent != null && activeEvent.isActive()) {
            return false;
        }

        // Build weighted list of events
        List<ServerEvent.EventType> enabledTypes = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double totalWeight = 0.0;

        for (ServerEvent.EventType type : ServerEvent.EventType.values()) {
            double weight = plugin.getEventsConfig().getEventWeight(type);
            if (weight > 0.0) {
                enabledTypes.add(type);
                weights.add(weight);
                totalWeight += weight;
            }
        }

        if (enabledTypes.isEmpty() || totalWeight <= 0.0) {
            plugin.getLogger().warning("No events enabled for random selection!");
            return false;
        }

        // Weighted random selection
        double roll = random.nextDouble() * totalWeight;
        double cumulative = 0.0;
        ServerEvent.EventType selectedType = enabledTypes.get(0); // fallback

        for (int i = 0; i < enabledTypes.size(); i++) {
            cumulative += weights.get(i);
            if (roll < cumulative) {
                selectedType = enabledTypes.get(i);
                break;
            }
        }

        return triggerEvent(selectedType);
    }

    /** Trigger a specific event type. */
    public boolean triggerEvent(ServerEvent.EventType type) {
        if (activeEvent != null && activeEvent.isActive()) {
            return false;
        }

        activeEvent = createEvent(type);
        if (activeEvent == null) {
            return false;
        }

        activeEvent.start();
        lastEventTime = System.currentTimeMillis();
        resetSchedulerTimer();

        return true;
    }

    /** Trigger a specific crafting challenge. */
    public boolean triggerCraftingEvent(String challengeKey) {
        if (activeEvent != null && activeEvent.isActive()) {
            return false;
        }

        var challenge = plugin.getEventsConfig().getCraftingChallenge(challengeKey);
        if (challenge == null) {
            return false;
        }

        activeEvent = new CraftingEvent(plugin, challengeKey);
        activeEvent.start();
        lastEventTime = System.currentTimeMillis();
        resetSchedulerTimer();

        return true;
    }

    /** Create an event instance. */
    private ServerEvent createEvent(ServerEvent.EventType type) {
        return switch (type) {
            case PINATA -> new PinataEvent(plugin);
            case PREMIUM_PINATA -> new PremiumPinataEvent(plugin);
            case SPELLING -> new SpellingEvent(plugin);
            case CRAFTING -> {
                // Pick a random crafting challenge
                var challenges = plugin.getEventsConfig().getCraftingChallengeKeys();
                if (challenges.isEmpty()) {
                    yield null;
                }
                String randomChallenge = challenges.get(random.nextInt(challenges.size()));
                yield new CraftingEvent(plugin, randomChallenge);
            }
            case MATH -> new MathEvent(plugin);
            case DROP_PARTY -> new DropPartyEvent(plugin);
            case DRAGON -> new DragonFightEvent(plugin);
        };
    }

    /** Stop the current event. */
    public boolean stopCurrentEvent() {
        if (activeEvent == null || !activeEvent.isActive()) {
            return false;
        }

        activeEvent.stop();
        activeEvent = null;
        return true;
    }

    /** Shutdown the manager. */
    public void shutdown() {
        stopScheduler();
        stopKeyallScheduler();
        if (activeEvent != null && activeEvent.isActive()) {
            activeEvent.stop();
        }
        activeEvent = null;
    }

    /** Start the automatic keyall scheduler. */
    public void startKeyallScheduler() {
        if (keyallTask != null) {
            keyallTask.cancel();
        }
        if (keyallCountdownTask != null) {
            keyallCountdownTask.cancel();
        }

        int intervalSeconds = plugin.getEventsConfig().getKeyallInterval();
        sentKeyallWarnings.clear();

        // Load last keyall time from database so we resume from where we left off
        if (lastKeyallTime == 0 && plugin.getRepository() != null) {
            long dbTime = plugin.getRepository().getLastKeyallTime();
            if (dbTime > 0) {
                lastKeyallTime = dbTime;
                plugin.getLogger()
                        .info(
                                "Loaded last keyall time from database: "
                                        + ((System.currentTimeMillis() - dbTime) / 1000)
                                        + "s ago");
            }
        }

        // Calculate initial delay based on time since last keyall
        long initialDelayTicks;
        if (lastKeyallTime == 0) {
            // Never distributed before - fire after warmup
            initialDelayTicks = 100L;
        } else {
            long elapsedSeconds = (System.currentTimeMillis() - lastKeyallTime) / 1000;
            long remainingSeconds = intervalSeconds - elapsedSeconds;
            if (remainingSeconds <= 0) {
                // Overdue - fire after warmup
                initialDelayTicks = 100L;
            } else {
                // Resume timer from where it left off
                initialDelayTicks = remainingSeconds * 20L;
                plugin.getLogger()
                        .info(
                                "Next keyall in "
                                        + remainingSeconds
                                        + "s (resuming from last distribution)");
            }
        }

        // Schedule the first tick, then repeat at the configured interval
        long intervalTicks = (long) intervalSeconds * 20;
        keyallTask =
                new BukkitRunnable() {
                    private boolean firstRun = true;

                    @Override
                    public void run() {
                        if (firstRun) {
                            firstRun = false;
                            executeKeyall();
                            // Reschedule at the regular interval after the first run
                            cancel();
                            keyallTask =
                                    new BukkitRunnable() {
                                        @Override
                                        public void run() {
                                            executeKeyall();
                                        }
                                    }.runTaskTimer(plugin, intervalTicks, intervalTicks);
                        }
                    }
                }.runTaskLater(plugin, initialDelayTicks);

        // Start countdown warning task (checks every second)
        keyallCountdownTask =
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        checkKeyallCountdownWarnings();
                    }
                }.runTaskTimer(plugin, 20L, 20L);

        plugin.getLogger()
                .info("Keyall scheduler started with interval: " + intervalSeconds + " seconds");
    }

    /** Stop the keyall scheduler. */
    public void stopKeyallScheduler() {
        if (keyallTask != null) {
            keyallTask.cancel();
            keyallTask = null;
        }
        if (keyallCountdownTask != null) {
            keyallCountdownTask.cancel();
            keyallCountdownTask = null;
        }
        sentKeyallWarnings.clear();
    }

    /** Check and send keyall countdown warnings. */
    private void checkKeyallCountdownWarnings() {
        int timeUntil = getTimeUntilNextKeyall();
        if (timeUntil < 0) return;

        List<Integer> warnings = plugin.getEventsConfig().getKeyallWarnings();
        for (int warning : warnings) {
            if (timeUntil == warning && !sentKeyallWarnings.contains(warning)) {
                sentKeyallWarnings.add(warning);
                sendKeyallCountdownWarning(warning);
                break;
            }
        }
    }

    /** Send a keyall countdown warning to all players. */
    private void sendKeyallCountdownWarning(int seconds) {
        String timeFormatted = formatTime(seconds);
        String message =
                MessageBuilder.create()
                        .prefix(plugin.getEventsConfig().getMessenger().getPrefix())
                        .star()
                        .emphasis("Next keyall in ")
                        .highlight(timeFormatted)
                        .emphasis("!")
                        .build();

        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getEventsConfig().getMessenger().sendRaw(player, message);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.2f);
        }
    }

    /** Format seconds into a human-readable string. */
    private String formatTime(int totalSeconds) {
        if (totalSeconds >= 3600) {
            int hours = totalSeconds / 3600;
            int minutes = (totalSeconds % 3600) / 60;
            if (minutes > 0) {
                return hours
                        + (hours == 1 ? " hour " : " hours ")
                        + minutes
                        + (minutes == 1 ? " minute" : " minutes");
            }
            return hours + (hours == 1 ? " hour" : " hours");
        } else if (totalSeconds >= 60) {
            int minutes = totalSeconds / 60;
            return minutes + (minutes == 1 ? " minute" : " minutes");
        } else {
            return totalSeconds + (totalSeconds == 1 ? " second" : " seconds");
        }
    }

    /** Get time until next scheduled keyall (in seconds). */
    public int getTimeUntilNextKeyall() {
        if (keyallTask == null) return -1;
        if (lastKeyallTime == 0) {
            // No previous keyall - return time until the initial warmup fires
            return 5; // approximate warmup delay
        }

        long elapsed = (System.currentTimeMillis() - lastKeyallTime) / 1000;
        int interval = plugin.getEventsConfig().getKeyallInterval();
        return Math.max(0, interval - (int) elapsed);
    }

    /** Execute the keyall command from console. */
    private void executeKeyall() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return;
        }

        // Cooldown guard - never more than once per hour
        long now = System.currentTimeMillis();
        if (lastKeyallTime > 0 && (now - lastKeyallTime) < 3600_000L) {
            return;
        }

        String type = plugin.getEventsConfig().getKeyallType();
        String key = plugin.getEventsConfig().getKeyallKey();
        int amount = plugin.getEventsConfig().getKeyallAmount();

        // Announce to all players before distributing
        String announcement =
                MessageBuilder.create()
                        .prefix(plugin.getEventsConfig().getMessenger().getPrefix())
                        .star()
                        .emphasis("Keys are being distributed to all online players!")
                        .build();
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.getEventsConfig().getMessenger().sendRaw(player, announcement);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        }

        String command = "keyall " + type + " " + key + " " + amount;
        plugin.getLogger().info("Auto keyall: dispatching /" + command);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

        lastKeyallTime = now;
        sentKeyallWarnings.clear();

        // Record to database for persistence across restarts
        if (plugin.getRepository() != null) {
            int playerCount = Bukkit.getOnlinePlayers().size();
            plugin.getRepository().recordKeyall(type, key, amount, playerCount);
        }
    }

    /** Get the currently active event. */
    public ServerEvent getActiveEvent() {
        return activeEvent;
    }

    /** Check if an event is currently active. */
    public boolean hasActiveEvent() {
        return activeEvent != null && activeEvent.isActive();
    }

    /** Clear the active event reference (called when event completes naturally). */
    public void clearActiveEvent() {
        activeEvent = null;
    }

    /** Get time until next scheduled event (in seconds). */
    public int getTimeUntilNextEvent() {
        if (schedulerTask == null) return -1;

        long elapsed = (System.currentTimeMillis() - lastEventTime) / 1000;
        int interval = plugin.getEventsConfig().getSchedulerInterval();
        return Math.max(0, interval - (int) elapsed);
    }
}
