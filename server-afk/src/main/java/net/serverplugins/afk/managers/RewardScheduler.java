package net.serverplugins.afk.managers;

import net.serverplugins.afk.ServerAFK;
import net.serverplugins.afk.models.AfkZone;
import net.serverplugins.afk.models.PlayerAfkSession;
import net.serverplugins.afk.models.ZoneReward;
import net.serverplugins.api.economy.EconomyProvider;
import net.serverplugins.api.gems.GemsProvider;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.api.permissions.PermissionProvider;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class RewardScheduler {

    private final ServerAFK plugin;
    private BukkitTask task;

    public RewardScheduler(ServerAFK plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) {
            task.cancel();
        }

        // Run every second (20 ticks)
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::checkRewards, 20L, 20L);
        plugin.getLogger().info("Reward scheduler started");
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        plugin.getLogger().info("Reward scheduler stopped");
    }

    private void checkRewards() {
        PlayerTracker tracker = plugin.getPlayerTracker();

        for (PlayerAfkSession session : tracker.getAllSessions()) {
            if (!session.isReadyForReward()) {
                continue;
            }

            Player player = Bukkit.getPlayer(session.getPlayerId());
            if (player == null || !player.isOnline()) {
                tracker.endSession(session.getPlayerId());
                continue;
            }

            // Check if player has permission to receive rewards
            if (!player.hasPermission("serverafk.use")) {
                continue;
            }

            AfkZone zone = session.getCurrentZone();
            if (zone == null || !zone.isEnabled()) {
                continue;
            }

            // Verify player is still in the zone
            if (!zone.contains(player.getLocation())) {
                tracker.endSession(player);
                continue;
            }

            // Give rewards
            giveRewards(player, zone);
            session.resetLastReward();
        }
    }

    private void giveRewards(Player player, AfkZone zone) {
        for (ZoneReward reward : zone.getRewards()) {
            giveReward(player, zone, reward);
        }
    }

    private void giveReward(Player player, AfkZone zone, ZoneReward reward) {
        // Check probability chance
        if (!reward.shouldGive()) {
            return;
        }

        switch (reward.getType()) {
            case CURRENCY -> giveCurrencyReward(player, zone, reward);
            case ITEM -> giveItemReward(player, reward);
            case COMMAND -> giveCommandReward(player, reward);
            case XP -> giveXpReward(player, reward);
            case GEMS -> giveGemsReward(player, zone, reward);
        }
    }

    private void giveCurrencyReward(Player player, AfkZone zone, ZoneReward reward) {
        EconomyProvider economy = plugin.getEconomy();
        if (economy == null || !economy.isAvailable()) {
            return;
        }

        double baseAmount = reward.getCurrencyAmount();
        double multiplier = 1.0;

        // Apply rank multiplier if enabled for this zone
        if (zone.usesRankMultipliers()) {
            PermissionProvider perms = plugin.getPermissions();
            if (perms != null) {
                String group = perms.getPrimaryGroup(player);
                multiplier = plugin.getAfkConfig().getRankMultiplier(group);
            }
        }

        double finalAmount = baseAmount * multiplier;
        economy.deposit(player, finalAmount);

        var messenger = plugin.getAfkConfig().getMessenger();
        messenger.send(
                player,
                "reward-currency",
                Placeholder.of("amount", String.valueOf((int) finalAmount)));

        // Show multiplier bonus if greater than 1
        if (multiplier > 1.0) {
            messenger.sendInfo(player, "(" + multiplier + "x bonus!)");
        }

        // Play reward effects
        playRewardEffects(player);

        // Update statistics
        updatePlayerStats(player, finalAmount, 0);
    }

    private void giveItemReward(Player player, ZoneReward reward) {
        if (reward.getItemReward() == null) {
            return;
        }

        // Add to inventory or drop at feet if full
        var leftover = player.getInventory().addItem(reward.getItemReward().clone());
        if (!leftover.isEmpty()) {
            leftover.values()
                    .forEach(
                            item ->
                                    player.getWorld()
                                            .dropItemNaturally(player.getLocation(), item));
        }

        plugin.getAfkConfig()
                .getMessenger()
                .send(player, "reward-item", Placeholder.of("item", reward.getDisplayName()));

        // Play reward effects
        playRewardEffects(player);

        // Update statistics
        updatePlayerStats(player, 0, 0);
    }

    private void giveCommandReward(Player player, ZoneReward reward) {
        String command = reward.processCommand(player.getName(), player.getUniqueId().toString());
        if (command == null || command.isEmpty()) {
            plugin.getLogger().warning("Empty command reward for player " + player.getName());
            return;
        }

        try {
            // Execute command from console
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                boolean success =
                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                                if (!success) {
                                    plugin.getLogger()
                                            .warning(
                                                    "Failed to execute command reward: " + command);
                                }
                            });

            plugin.getAfkConfig().getMessenger().send(player, "reward-command");

            // Play reward effects
            playRewardEffects(player);

            // Update statistics
            updatePlayerStats(player, 0, 0);

        } catch (Exception e) {
            plugin.getLogger().severe("Error executing command reward: " + e.getMessage());
        }
    }

    private void giveXpReward(Player player, ZoneReward reward) {
        int xpAmount = reward.getXpAmount();
        if (xpAmount <= 0) {
            return;
        }

        // Give XP to player
        player.giveExp(xpAmount);

        plugin.getAfkConfig()
                .getMessenger()
                .send(player, "reward-xp", Placeholder.of("amount", String.valueOf(xpAmount)));

        // Play reward effects
        playRewardEffects(player);

        // Update statistics
        updatePlayerStats(player, 0, xpAmount);
    }

    private void giveGemsReward(Player player, AfkZone zone, ZoneReward reward) {
        GemsProvider gems = net.serverplugins.api.ServerAPI.getInstance().getGemsProvider();
        if (gems == null) {
            return;
        }

        int baseAmount = reward.getGemsAmount();
        if (baseAmount <= 0) {
            return;
        }

        double multiplier = 1.0;

        // Apply rank multiplier if enabled for this zone
        if (zone.usesRankMultipliers()) {
            PermissionProvider perms = plugin.getPermissions();
            if (perms != null) {
                String group = perms.getPrimaryGroup(player);
                multiplier = plugin.getAfkConfig().getRankMultiplier(group);
            }
        }

        int finalAmount = (int) (baseAmount * multiplier);
        gems.deposit(player, finalAmount);

        var messenger = plugin.getAfkConfig().getMessenger();
        messenger.send(
                player, "reward-gems", Placeholder.of("amount", String.valueOf(finalAmount)));

        // Show multiplier bonus if greater than 1
        if (multiplier > 1.0) {
            messenger.sendInfo(player, "(" + multiplier + "x bonus!)");
        }

        // Play reward effects
        playRewardEffects(player);

        // Update statistics
        updatePlayerStats(player, 0, 0);
    }

    /** Plays visual and sound effects for receiving a reward. */
    private void playRewardEffects(Player player) {
        try {
            // Play sound effect
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);

            // Spawn particles
            player.getWorld()
                    .spawnParticle(
                            Particle.HAPPY_VILLAGER,
                            player.getLocation().add(0, 1, 0),
                            10,
                            0.5,
                            0.5,
                            0.5,
                            0.1);
        } catch (Exception e) {
            // Silently fail if effects can't be played
        }
    }

    /** Updates player statistics after receiving rewards. */
    private void updatePlayerStats(Player player, double currency, int xp) {
        if (plugin.getStatsManager() != null) {
            plugin.getStatsManager().addReward(player.getUniqueId(), currency, xp);
        }
    }
}
