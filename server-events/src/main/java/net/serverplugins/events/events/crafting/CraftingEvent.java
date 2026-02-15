package net.serverplugins.events.events.crafting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.serverplugins.api.messages.MessageBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.events.EventsConfig;
import net.serverplugins.events.EventsConfig.CraftingChallenge;
import net.serverplugins.events.ServerEvents;
import net.serverplugins.events.events.EventReward;
import net.serverplugins.events.events.ServerEvent;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/** Crafting/Smelting challenge event - first to craft/smelt X items wins. */
public class CraftingEvent implements ServerEvent, Listener {

    private final ServerEvents plugin;
    private final EventsConfig config;
    private final String challengeKey;
    private final CraftingChallenge challenge;

    private boolean active;
    private Player winner;
    private BukkitTask timeoutTask;
    private final Map<UUID, Integer> playerProgress = new HashMap<>();

    public CraftingEvent(ServerEvents plugin, String challengeKey) {
        this.plugin = plugin;
        this.config = plugin.getEventsConfig();
        this.challengeKey = challengeKey;
        this.challenge = config.getCraftingChallenge(challengeKey);
    }

    @Override
    public EventType getType() {
        return EventType.CRAFTING;
    }

    @Override
    public String getDisplayName() {
        return "Crafting Challenge: "
                + (challenge != null ? challenge.displayName() : challengeKey);
    }

    @Override
    public void start() {
        if (active || challenge == null) return;

        active = true;
        winner = null;
        playerProgress.clear();

        // Register listener
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Announce the event
        announceEvent();

        // Set timeout
        int timeLimit = challenge.timeLimit();
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
        String message =
                config.getCraftingMessage("start")
                        .replace("%amount%", String.valueOf(challenge.amount()))
                        .replace("%item%", challenge.displayName());

        TextUtil.broadcastRaw(config.getPrefix() + message);

        // Additional info
        String typeAction = challenge.isCraft() ? "Craft" : "Smelt";
        TextUtil.broadcastRaw(
                MessageBuilder.create()
                        .prefix(config.getMessenger().getPrefix())
                        .warning(typeAction + " ")
                        .emphasis(challenge.amount() + "x " + challenge.displayName())
                        .warning(" in ")
                        .emphasis(String.valueOf(challenge.timeLimit()))
                        .warning(" seconds!")
                        .build());

        // Play sound
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!active || winner != null || !challenge.isCraft()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Check if correct item
        if (event.getRecipe().getResult().getType() != challenge.material()) return;

        // Calculate amount crafted (accounting for shift-click)
        int amount = calculateCraftAmount(event);
        if (amount <= 0) return;

        // Update progress
        int newProgress = playerProgress.merge(player.getUniqueId(), amount, Integer::sum);

        // Notify player
        sendProgress(player, newProgress);

        // Check for win
        if (newProgress >= challenge.amount()) {
            winner = player;
            announceWinner();
        }
    }

    @EventHandler
    public void onSmelt(FurnaceExtractEvent event) {
        if (!active || winner != null || !challenge.isSmelt()) return;

        Player player = event.getPlayer();

        // Check if correct item
        if (event.getItemType() != challenge.material()) return;

        int amount = event.getItemAmount();

        // Update progress
        int newProgress = playerProgress.merge(player.getUniqueId(), amount, Integer::sum);

        // Notify player
        sendProgress(player, newProgress);

        // Check for win
        if (newProgress >= challenge.amount()) {
            winner = player;
            announceWinner();
        }
    }

    private int calculateCraftAmount(CraftItemEvent event) {
        int amount = event.getRecipe().getResult().getAmount();

        // For shift-click, calculate max possible crafts
        if (event.isShiftClick()) {
            int maxCrafts = 64; // reasonable limit
            var matrix = event.getInventory().getMatrix();
            for (var item : matrix) {
                if (item != null && item.getAmount() > 0) {
                    maxCrafts = Math.min(maxCrafts, item.getAmount());
                }
            }
            amount *= maxCrafts;
        }

        return amount;
    }

    private void sendProgress(Player player, int progress) {
        String message =
                config.getCraftingMessage("progress")
                        .replace(
                                "%current%", String.valueOf(Math.min(progress, challenge.amount())))
                        .replace("%amount%", String.valueOf(challenge.amount()));

        TextUtil.send(player, config.getPrefix() + message);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
    }

    private void announceWinner() {
        if (winner == null) return;

        // Cancel timeout
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }

        // Announce winner
        String message = config.getCraftingMessage("winner").replace("%player%", winner.getName());
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
                EventReward.of(challenge.rewardCoins(), challenge.keyChance(), challenge.keyType());
        reward.give(plugin, winner);

        // Stop the event
        stop();
    }

    private void timeout() {
        // Announce timeout
        String message = config.getCraftingMessage("timeout");
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

    public CraftingChallenge getChallenge() {
        return challenge;
    }

    public Map<UUID, Integer> getPlayerProgress() {
        return playerProgress;
    }
}
