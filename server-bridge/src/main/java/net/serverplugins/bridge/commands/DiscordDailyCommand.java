package net.serverplugins.bridge.commands;

import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import net.milkbowl.vault.economy.Economy;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.bridge.ServerBridge;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class DiscordDailyCommand implements CommandExecutor {

    private final ServerBridge plugin;
    private final Economy economy;

    // Base money reward
    private static final double BASE_REWARD = 5000.0;
    private static final double MAX_MULTIPLIER = 5.0;

    // Streak thresholds for key upgrades
    private static final int DIVERSITY_KEY_THRESHOLD = 7; // 1 week
    private static final int EPIC_KEY_THRESHOLD = 30; // 1 month

    // Milestone definitions: day threshold -> rewards
    private static final Milestone[] MILESTONES = {
        new Milestone(7, 1.5, "1 Week", new String[] {"diversity:1"}, null),
        new Milestone(14, 2.0, "2 Weeks", new String[] {"diversity:2"}, null),
        new Milestone(30, 3.0, "1 Month", new String[] {"epic:1"}, Material.ELYTRA),
        new Milestone(60, 4.0, "2 Months", new String[] {"epic:2"}, Material.DRAGON_HEAD),
        new Milestone(90, 5.0, "3 Months", new String[] {"epic:3"}, Material.DRAGON_EGG),
        new Milestone(180, 6.0, "6 Months", new String[] {"epic:5"}, Material.NETHER_STAR),
        new Milestone(365, 10.0, "1 Year", new String[] {"epic:10"}, Material.BEACON),
    };

    public DiscordDailyCommand(ServerBridge plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            String[] args) {
        var messenger = plugin.getBridgeConfig().getMessenger();

        if (!(sender instanceof Player player)) {
            messenger.sendError(sender, "This command can only be used by players.");
            return true;
        }

        if (economy == null) {
            messenger.sendError(player, "Economy system not available.");
            return true;
        }

        if (!plugin.getDatabaseManager().isAvailable()) {
            messenger.sendError(
                    player, "Database connection not available. Please try again later.");
            return true;
        }

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                String uuid = player.getUniqueId().toString();

                                // Check if player is linked
                                if (!isLinked(uuid)) {
                                    plugin.getServer()
                                            .getScheduler()
                                            .runTask(
                                                    plugin,
                                                    () -> {
                                                        String message =
                                                                ColorScheme.ERROR
                                                                        + "You must link your Discord account first!\n"
                                                                        + ColorScheme.INFO
                                                                        + "Use "
                                                                        + ColorScheme.wrap(
                                                                                "/link start",
                                                                                ColorScheme.WARNING)
                                                                        + ColorScheme.INFO
                                                                        + " on Discord to get a code, then "
                                                                        + ColorScheme.wrap(
                                                                                "/verifylink <code>",
                                                                                ColorScheme.WARNING)
                                                                        + ColorScheme.INFO
                                                                        + " in-game.";
                                                        messenger.sendRaw(player, message);
                                                    });
                                    return;
                                }

                                // Get current daily reward data
                                DailyData dailyData = getDailyData(uuid);

                                // Check if already claimed today
                                if (dailyData != null
                                        && dailyData.lastClaim != null
                                        && dailyData.lastClaim.equals(LocalDate.now())) {
                                    plugin.getServer()
                                            .getScheduler()
                                            .runTask(
                                                    plugin,
                                                    () -> {
                                                        String message =
                                                                ColorScheme.ERROR
                                                                        + "You've already claimed your daily reward today!\n"
                                                                        + ColorScheme.INFO
                                                                        + "Come back tomorrow to continue your streak!\n"
                                                                        + ColorScheme.COMMAND
                                                                        + "Current Streak: "
                                                                        + ColorScheme.wrap(
                                                                                dailyData
                                                                                                .currentStreak
                                                                                        + " days",
                                                                                ColorScheme
                                                                                        .EMPHASIS);
                                                        messenger.sendRaw(player, message);
                                                    });
                                    return;
                                }

                                // Calculate new streak
                                int newStreak = 1;
                                if (dailyData != null && dailyData.lastClaim != null) {
                                    long daysSinceClaim =
                                            ChronoUnit.DAYS.between(
                                                    dailyData.lastClaim, LocalDate.now());
                                    if (daysSinceClaim == 1) {
                                        newStreak = dailyData.currentStreak + 1;
                                    }
                                }

                                // Calculate money multiplier based on streak
                                double multiplier =
                                        Math.min(1.0 + (newStreak - 1) * 0.1, MAX_MULTIPLIER);

                                // Check for milestone multiplier
                                Milestone currentMilestone = null;
                                Milestone nextMilestone = null;
                                boolean isNewMilestone = false;

                                for (int i = MILESTONES.length - 1; i >= 0; i--) {
                                    if (newStreak >= MILESTONES[i].days) {
                                        currentMilestone = MILESTONES[i];
                                        multiplier =
                                                Math.max(multiplier, currentMilestone.multiplier);
                                        // Check if this is a new milestone (exact day)
                                        if (newStreak == MILESTONES[i].days) {
                                            isNewMilestone = true;
                                        }
                                        break;
                                    }
                                }

                                // Find next milestone
                                for (Milestone milestone : MILESTONES) {
                                    if (newStreak < milestone.days) {
                                        nextMilestone = milestone;
                                        break;
                                    }
                                }

                                double reward = BASE_REWARD * multiplier;
                                int longestStreak =
                                        dailyData != null
                                                ? Math.max(dailyData.longestStreak, newStreak)
                                                : newStreak;
                                int totalClaims = dailyData != null ? dailyData.totalClaims + 1 : 1;
                                double totalEarned =
                                        dailyData != null ? dailyData.totalEarned + reward : reward;

                                // Update database
                                updateDailyData(
                                        uuid, newStreak, longestStreak, totalClaims, totalEarned);

                                // Prepare final variables for lambda
                                final int finalStreak = newStreak;
                                final double finalReward = reward;
                                final double finalMultiplier = multiplier;
                                final int finalLongestStreak = longestStreak;
                                final Milestone finalCurrentMilestone = currentMilestone;
                                final Milestone finalNextMilestone = nextMilestone;
                                final boolean finalIsNewMilestone = isNewMilestone;

                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    // Give money
                                                    economy.depositPlayer(player, finalReward);

                                                    // Determine which key to give based on streak
                                                    KeyReward keyReward =
                                                            getKeyRewardForStreak(finalStreak);
                                                    boolean keyGiven =
                                                            giveKey(
                                                                    player,
                                                                    keyReward.keyType,
                                                                    keyReward.amount);

                                                    // Build reward message
                                                    StringBuilder message = new StringBuilder();

                                                    if (finalIsNewMilestone
                                                            && finalCurrentMilestone != null) {
                                                        message.append("<gradient:#FFD700:#FF6B00>")
                                                                .append(ColorScheme.STAR)
                                                                .append(" MILESTONE REACHED: ")
                                                                .append(
                                                                        finalCurrentMilestone.name
                                                                                .toUpperCase())
                                                                .append("! ")
                                                                .append(ColorScheme.STAR)
                                                                .append("</gradient>\n");
                                                    } else {
                                                        message.append(
                                                                        ColorScheme.wrap(
                                                                                ColorScheme
                                                                                                .CHECKMARK
                                                                                        + " DAILY REWARD CLAIMED!",
                                                                                ColorScheme
                                                                                        .SUCCESS))
                                                                .append("\n");
                                                    }

                                                    message.append(ColorScheme.INFO)
                                                            .append("━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

                                                    // Base rewards
                                                    message.append(ColorScheme.COMMAND)
                                                            .append("Money: ")
                                                            .append(
                                                                    ColorScheme.wrap(
                                                                            "$"
                                                                                    + String.format(
                                                                                            "%,.0f",
                                                                                            finalReward),
                                                                            ColorScheme.EMPHASIS))
                                                            .append("\n");

                                                    if (keyGiven) {
                                                        String keyColor =
                                                                getKeyColor(keyReward.keyType);
                                                        String keyDisplay =
                                                                formatKeyName(keyReward.keyType);
                                                        message.append(ColorScheme.COMMAND)
                                                                .append("Key: ")
                                                                .append(keyColor)
                                                                .append(keyReward.amount)
                                                                .append("x ")
                                                                .append(keyDisplay)
                                                                .append(" Key</")
                                                                .append(keyColor.substring(1))
                                                                .append("\n");
                                                    }

                                                    // Milestone bonus rewards
                                                    if (finalIsNewMilestone
                                                            && finalCurrentMilestone != null) {
                                                        message.append(ColorScheme.INFO)
                                                                .append(
                                                                        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                                                        message.append("<gradient:#FFD700:#FF6B00>")
                                                                .append(ColorScheme.STAR)
                                                                .append(
                                                                        " MILESTONE BONUS!</gradient>\n");

                                                        // Give milestone bonus keys
                                                        List<String> bonusesGiven =
                                                                new ArrayList<>();
                                                        for (String bonusKey :
                                                                finalCurrentMilestone.bonusKeys) {
                                                            String[] parts = bonusKey.split(":");
                                                            String keyType = parts[0];
                                                            int amount = Integer.parseInt(parts[1]);
                                                            if (giveKey(player, keyType, amount)) {
                                                                String keyColor =
                                                                        getKeyColor(keyType);
                                                                bonusesGiven.add(
                                                                        keyColor
                                                                                + amount
                                                                                + "x "
                                                                                + formatKeyName(
                                                                                        keyType)
                                                                                + " Key</"
                                                                                + keyColor
                                                                                        .substring(
                                                                                                1));
                                                            }
                                                        }

                                                        // Give milestone bonus item
                                                        if (finalCurrentMilestone.bonusItem
                                                                != null) {
                                                            ItemStack item =
                                                                    new ItemStack(
                                                                            finalCurrentMilestone
                                                                                    .bonusItem,
                                                                            1);
                                                            player.getInventory().addItem(item);
                                                            bonusesGiven.add(
                                                                    "<light_purple>1x "
                                                                            + formatItemName(
                                                                                    finalCurrentMilestone
                                                                                            .bonusItem)
                                                                            + "</light_purple>");
                                                        }

                                                        for (String bonus : bonusesGiven) {
                                                            message.append(ColorScheme.WARNING)
                                                                    .append("+ ")
                                                                    .append(bonus)
                                                                    .append("\n");
                                                        }
                                                    }

                                                    message.append(ColorScheme.INFO)
                                                            .append("━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                                                    message.append(ColorScheme.COMMAND)
                                                            .append("Multiplier: ")
                                                            .append(
                                                                    ColorScheme.wrap(
                                                                            String.format(
                                                                                    "%.1fx",
                                                                                    finalMultiplier),
                                                                            ColorScheme.WARNING))
                                                            .append("\n");
                                                    message.append(ColorScheme.COMMAND)
                                                            .append("Current Streak: ")
                                                            .append(
                                                                    ColorScheme.wrap(
                                                                            finalStreak + " days",
                                                                            ColorScheme.EMPHASIS));

                                                    if (finalStreak >= 7) {
                                                        message.append(" ")
                                                                .append(
                                                                        ColorScheme.wrap(
                                                                                "("
                                                                                        + (finalStreak
                                                                                                / 7)
                                                                                        + " week"
                                                                                        + (finalStreak
                                                                                                                / 7
                                                                                                        > 1
                                                                                                ? "s"
                                                                                                : "")
                                                                                        + ")",
                                                                                ColorScheme.INFO));
                                                    }
                                                    message.append("\n");

                                                    if (finalStreak > 1) {
                                                        message.append(ColorScheme.COMMAND)
                                                                .append("Longest Streak: ")
                                                                .append(
                                                                        ColorScheme.wrap(
                                                                                finalLongestStreak
                                                                                        + " days",
                                                                                "<light_purple>"))
                                                                .append("\n");
                                                    }

                                                    // Show next milestone progress
                                                    if (finalNextMilestone != null) {
                                                        int daysUntil =
                                                                finalNextMilestone.days
                                                                        - finalStreak;
                                                        message.append(ColorScheme.INFO)
                                                                .append(
                                                                        "━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                                                        message.append(ColorScheme.WARNING)
                                                                .append(daysUntil)
                                                                .append(" day")
                                                                .append(daysUntil > 1 ? "s" : "")
                                                                .append(" until ")
                                                                .append(
                                                                        ColorScheme.wrap(
                                                                                finalNextMilestone
                                                                                        .name,
                                                                                ColorScheme
                                                                                        .EMPHASIS))
                                                                .append(ColorScheme.WARNING)
                                                                .append(" milestone!\n");

                                                        // Show what they'll get
                                                        message.append(ColorScheme.INFO)
                                                                .append("Rewards: ");
                                                        List<String> upcomingRewards =
                                                                new ArrayList<>();
                                                        for (String bonusKey :
                                                                finalNextMilestone.bonusKeys) {
                                                            String[] parts = bonusKey.split(":");
                                                            upcomingRewards.add(
                                                                    parts[1]
                                                                            + "x "
                                                                            + formatKeyName(
                                                                                    parts[0])
                                                                            + " Key");
                                                        }
                                                        if (finalNextMilestone.bonusItem != null) {
                                                            upcomingRewards.add(
                                                                    formatItemName(
                                                                            finalNextMilestone
                                                                                    .bonusItem));
                                                        }
                                                        message.append(
                                                                String.join(", ", upcomingRewards));
                                                    }

                                                    messenger.sendRaw(player, message.toString());
                                                });

                            } catch (SQLException e) {
                                plugin.getLogger()
                                        .severe(
                                                "Database error during daily claim: "
                                                        + e.getMessage());
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    messenger.sendError(
                                                            player,
                                                            "An error occurred. Please try again later.");
                                                });
                            }
                        });

        return true;
    }

    /** Determine which key type and amount to give based on streak. */
    private KeyReward getKeyRewardForStreak(int streak) {
        if (streak >= EPIC_KEY_THRESHOLD) {
            // 30+ days: Epic keys, scaling amount
            int amount = 1 + (streak - EPIC_KEY_THRESHOLD) / 30; // +1 every month after first
            return new KeyReward("epic", Math.min(amount, 3)); // Cap at 3
        } else if (streak >= DIVERSITY_KEY_THRESHOLD) {
            // 7-29 days: Diversity keys, scaling amount
            int amount = 1 + (streak - DIVERSITY_KEY_THRESHOLD) / 7; // +1 every week
            return new KeyReward("diversity", Math.min(amount, 3)); // Cap at 3
        } else {
            // 1-6 days: Daily keys
            return new KeyReward("daily", 1);
        }
    }

    private boolean giveKey(Player player, String keyType, int amount) {
        if (Bukkit.getPluginManager().getPlugin("ExcellentCrates") == null) {
            plugin.getLogger()
                    .warning("ExcellentCrates not found - cannot give " + keyType + " key");
            return false;
        }

        try {
            String command = "crate key give " + player.getName() + " " + keyType + " " + amount;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return true;
        } catch (Exception e) {
            plugin.getLogger()
                    .warning("Failed to give " + keyType + " crate key: " + e.getMessage());
            return false;
        }
    }

    private String getKeyColor(String keyType) {
        return switch (keyType) {
            case "epic" -> "<gradient:#FF00FF:#8B00FF>";
            case "diversity" -> "<gradient:#00CED1:#20B2AA>";
            case "balanced" -> "<gradient:#FFD700:#FFA500>";
            default -> "<yellow>";
        };
    }

    private String formatKeyName(String keyType) {
        return switch (keyType) {
            case "epic" -> "Epic";
            case "diversity" -> "Diversity";
            case "balanced" -> "Balanced";
            case "daily" -> "Daily";
            default -> keyType.substring(0, 1).toUpperCase() + keyType.substring(1);
        };
    }

    private String formatItemName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            result.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1))
                    .append(" ");
        }
        return result.toString().trim();
    }

    private boolean isLinked(String uuid) throws SQLException {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "SELECT 1 FROM linked_accounts WHERE minecraft_uuid = ?")) {
            stmt.setString(1, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private DailyData getDailyData(String uuid) throws SQLException {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "SELECT last_claim, current_streak, longest_streak, total_claims, total_earned FROM daily_rewards WHERE minecraft_uuid = ?")) {
            stmt.setString(1, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Date lastClaimDate = rs.getDate("last_claim");
                    LocalDate lastClaim =
                            lastClaimDate != null ? lastClaimDate.toLocalDate() : null;
                    return new DailyData(
                            lastClaim,
                            rs.getInt("current_streak"),
                            rs.getInt("longest_streak"),
                            rs.getInt("total_claims"),
                            rs.getDouble("total_earned"));
                }
            }
        }
        return null;
    }

    private void updateDailyData(
            String uuid, int currentStreak, int longestStreak, int totalClaims, double totalEarned)
            throws SQLException {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                """
                     INSERT INTO daily_rewards (minecraft_uuid, last_claim, current_streak, longest_streak, total_claims, total_earned)
                     VALUES (?, CURDATE(), ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE
                         last_claim = CURDATE(),
                         current_streak = VALUES(current_streak),
                         longest_streak = VALUES(longest_streak),
                         total_claims = VALUES(total_claims),
                         total_earned = VALUES(total_earned)
                     """)) {
            stmt.setString(1, uuid);
            stmt.setInt(2, currentStreak);
            stmt.setInt(3, longestStreak);
            stmt.setInt(4, totalClaims);
            stmt.setDouble(5, totalEarned);
            stmt.executeUpdate();
        }
    }

    // Data classes
    private record DailyData(
            LocalDate lastClaim,
            int currentStreak,
            int longestStreak,
            int totalClaims,
            double totalEarned) {}

    private record KeyReward(String keyType, int amount) {}

    private record Milestone(
            int days, double multiplier, String name, String[] bonusKeys, Material bonusItem) {}
}
