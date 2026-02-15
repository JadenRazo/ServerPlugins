package net.serverplugins.keys.managers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.serverplugins.keys.KeysConfig;
import net.serverplugins.keys.ServerKeys;
import net.serverplugins.keys.models.KeyType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

public class ScheduleManager {

    private final ServerKeys plugin;
    private final KeysConfig config;
    private final KeyManager keyManager;
    private final Map<String, BukkitTask> activeTasks = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    public ScheduleManager(ServerKeys plugin, KeysConfig config, KeyManager keyManager) {
        this.plugin = plugin;
        this.config = config;
        this.keyManager = keyManager;
    }

    /** Load and start all schedules from config. */
    public void loadSchedules() {
        // Cancel all existing tasks
        cancelAll();

        ConfigurationSection schedules = config.getSchedulesSection();
        if (schedules == null) {
            plugin.getLogger().info("No schedules configured");
            return;
        }

        int loaded = 0;
        for (String scheduleId : schedules.getKeys(false)) {
            if (config.isScheduleEnabled(scheduleId)) {
                scheduleDistribution(scheduleId);
                loaded++;
            }
        }

        plugin.getLogger().info("Loaded " + loaded + " key distribution schedules");
    }

    /** Cancel all active schedules. */
    public void cancelAll() {
        for (BukkitTask task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
    }

    /** Schedule a distribution based on config. */
    private void scheduleDistribution(String scheduleId) {
        List<String> times = config.getScheduleTimes(scheduleId);
        List<String> days = config.getScheduleDays(scheduleId);

        if (times.isEmpty()) {
            plugin.getLogger().warning("Schedule '" + scheduleId + "' has no times configured");
            return;
        }

        // Schedule for each time
        for (String timeStr : times) {
            try {
                LocalTime targetTime = LocalTime.parse(timeStr, timeFormatter);
                scheduleAtTime(scheduleId, targetTime, days);
            } catch (Exception e) {
                plugin.getLogger()
                        .warning(
                                "Invalid time format in schedule '" + scheduleId + "': " + timeStr);
            }
        }
    }

    /** Schedule distribution at a specific time. */
    private void scheduleAtTime(String scheduleId, LocalTime targetTime, List<String> days) {
        // Calculate initial delay
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.with(targetTime);

        // If the time has passed today, schedule for tomorrow
        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }

        // Adjust for day restrictions
        if (!days.isEmpty()) {
            nextRun = findNextValidDay(nextRun, days);
        }

        long delayTicks = ChronoUnit.SECONDS.between(now, nextRun) * 20L;
        long periodTicks = 24 * 60 * 60 * 20L; // 24 hours in ticks

        String taskKey = scheduleId + "_" + targetTime.toString();

        BukkitTask task =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    // Check day restriction at runtime
                                    if (!days.isEmpty()) {
                                        String today = LocalDate.now().getDayOfWeek().name();
                                        boolean validDay =
                                                days.stream()
                                                        .anyMatch(d -> d.equalsIgnoreCase(today));
                                        if (!validDay) {
                                            return;
                                        }
                                    }

                                    executeDistribution(scheduleId);
                                },
                                delayTicks,
                                periodTicks);

        activeTasks.put(taskKey, task);
        plugin.getLogger()
                .info(
                        "Scheduled '"
                                + scheduleId
                                + "' at "
                                + targetTime
                                + " (next run in "
                                + (delayTicks / 20)
                                + "s)");
    }

    /** Find the next valid day for distribution. */
    private LocalDateTime findNextValidDay(LocalDateTime dateTime, List<String> days) {
        for (int i = 0; i < 7; i++) {
            String dayName = dateTime.getDayOfWeek().name();
            boolean validDay = days.stream().anyMatch(d -> d.equalsIgnoreCase(dayName));
            if (validDay) {
                return dateTime;
            }
            dateTime = dateTime.plusDays(1);
        }
        return dateTime; // Fallback
    }

    /** Execute a scheduled distribution. */
    private void executeDistribution(String scheduleId) {
        String typeStr = config.getScheduleType(scheduleId);
        String keyName = config.getScheduleKey(scheduleId);
        int amount = config.getScheduleAmount(scheduleId);
        String broadcast = config.getScheduleBroadcast(scheduleId);

        KeyType type = KeyType.fromString(typeStr);
        if (type == null) {
            plugin.getLogger()
                    .warning("Invalid key type in schedule '" + scheduleId + "': " + typeStr);
            return;
        }

        int count = keyManager.giveToAll(type, keyName, amount, "SCHEDULE:" + scheduleId);

        plugin.getLogger()
                .info(
                        "Scheduled distribution '"
                                + scheduleId
                                + "': Gave "
                                + amount
                                + "x "
                                + keyName
                                + " keys to "
                                + count
                                + " players");

        // Broadcast if configured
        if (!broadcast.isEmpty() && count > 0) {
            String message =
                    broadcast
                            .replace("{count}", String.valueOf(count))
                            .replace("{amount}", String.valueOf(amount))
                            .replace("{key}", config.getKeyDisplay(type, keyName));
            Bukkit.broadcast(miniMessage.deserialize(message));
        }
    }

    /** Get the number of active schedules. */
    public int getActiveScheduleCount() {
        return activeTasks.size();
    }
}
