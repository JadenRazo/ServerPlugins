package net.serverplugins.events.events.spelling;

import java.util.List;
import java.util.Random;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.serverplugins.api.messages.MessageBuilder;
import net.serverplugins.api.messages.PluginMessenger;
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

/** Spelling bee event - first player to type the hidden word wins. */
public class SpellingEvent implements ServerEvent, Listener {

    private final ServerEvents plugin;
    private final EventsConfig config;
    private final Random random = new Random();

    private boolean active;
    private String targetWord;
    private Player winner;
    private BukkitTask timeoutTask;

    public SpellingEvent(ServerEvents plugin) {
        this.plugin = plugin;
        this.config = plugin.getEventsConfig();
    }

    @Override
    public EventType getType() {
        return EventType.SPELLING;
    }

    @Override
    public String getDisplayName() {
        return "Spelling Bee";
    }

    @Override
    public void start() {
        if (active) return;

        active = true;
        winner = null;

        // Pick a random word
        List<String> words = config.getSpellingWords();
        targetWord = words.get(random.nextInt(words.size())).toLowerCase();

        // Register listener
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Announce the event with hover-to-reveal word
        announceEvent();

        // Set timeout
        int timeLimit = config.getSpellingTimeLimit();
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

    private void announceEvent() {
        PluginMessenger messenger = config.getMessenger();
        String startMessage = config.getSpellingMessage("start");
        String hintMessage =
                config.getSpellingMessage("hint")
                        .replace("%length%", String.valueOf(targetWord.length()));

        // Broadcast start message with star icon
        String eventStart =
                MessageBuilder.create()
                        .prefix(messenger.getPrefix())
                        .star()
                        .append(startMessage)
                        .build();
        TextUtil.broadcastRaw(eventStart);

        // Create hover-to-reveal component
        Component spoilerText =
                Component.text("???")
                        .color(NamedTextColor.DARK_GRAY)
                        .decorate(TextDecoration.OBFUSCATED)
                        .hoverEvent(
                                HoverEvent.showText(
                                        Component.text(targetWord)
                                                .color(NamedTextColor.GOLD)
                                                .decorate(TextDecoration.BOLD)));

        Component wordComponent =
                TextUtil.parse(messenger.getPrefix())
                        .append(Component.text("Word: ").color(NamedTextColor.YELLOW))
                        .append(spoilerText)
                        .append(Component.text(" (hover to reveal)").color(NamedTextColor.GRAY));

        // Send to all players
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(wordComponent);
            String hint =
                    MessageBuilder.create()
                            .prefix(messenger.getPrefix())
                            .append(hintMessage)
                            .build();
            TextUtil.send(player, hint);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!active || winner != null) return;

        String message = event.getMessage().toLowerCase().trim();

        // Check if the message matches the word
        if (message.equals(targetWord)) {
            event.setCancelled(true);
            winner = event.getPlayer();

            // Run sync
            Bukkit.getScheduler().runTask(plugin, this::announceWinner);
        }
    }

    private void announceWinner() {
        if (winner == null) return;

        // Cancel timeout
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }

        // Announce winner with checkmark
        String message =
                config.getSpellingMessage("winner")
                        .replace("%player%", winner.getName())
                        .replace("%word%", targetWord);
        String winnerMsg =
                MessageBuilder.create()
                        .prefix(config.getMessenger().getPrefix())
                        .checkmark()
                        .append(message)
                        .build();
        TextUtil.broadcastRaw(winnerMsg);

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
                        config.getSpellingCoins(),
                        config.getSpellingKeyChance(),
                        config.getSpellingKeyType());
        reward.give(plugin, winner);

        // Stop the event
        stop();
    }

    private void timeout() {
        // Announce timeout
        String message = config.getSpellingMessage("timeout").replace("%word%", targetWord);
        String timeoutMsg =
                MessageBuilder.create()
                        .prefix(config.getMessenger().getPrefix())
                        .cross()
                        .append(message)
                        .build();
        TextUtil.broadcastRaw(timeoutMsg);

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

    public String getTargetWord() {
        return targetWord;
    }
}
