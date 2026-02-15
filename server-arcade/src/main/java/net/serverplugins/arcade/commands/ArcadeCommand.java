package net.serverplugins.arcade.commands;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.statistics.StatisticsTracker;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class ArcadeCommand implements CommandExecutor {

    private final ServerArcade plugin;

    public ArcadeCommand(ServerArcade plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getArcadeConfig().getMessenger().sendError(sender, "Players only!");
            return true;
        }

        openStatsMenu(player);
        return true;
    }

    private void openStatsMenu(Player player) {
        Gui menu = new Gui(plugin, "<gradient:#ff6b6b:#feca57>Server Arcade</gradient>", 54);

        // Row 1 (slot 4): Player head - gambling profile
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(player);
            head.setItemMeta(skullMeta);
        }

        StatisticsTracker tracker = plugin.getStatisticsTracker();
        StatisticsTracker.PlayerStats stats =
                tracker != null ? tracker.getPlayerStats(player.getUniqueId()) : null;

        List<String> profileLore = new ArrayList<>();
        profileLore.add("");
        if (stats != null) {
            int totalGames = stats.crashTotalBets + stats.lotteryTotalBets + stats.diceTotalBets;
            profileLore.add("<gray>Games Played: <white>" + totalGames);
            profileLore.add(
                    "<gray>Win Rate: <white>" + String.format("%.1f%%", stats.getWinRate()));
            profileLore.add("");
            String profitColor = stats.netProfit >= 0 ? "<green>" : "<red>";
            profileLore.add(
                    "<gray>Net Profit: "
                            + profitColor
                            + "$"
                            + String.format("%,d", stats.netProfit));
        } else {
            profileLore.add("<gray>No stats yet - get gambling!");
        }

        ItemStack profileItem =
                new ItemBuilder(head)
                        .name("<gold>Your Gambling Profile")
                        .lore(profileLore.toArray(new String[0]))
                        .build();
        menu.setItem(4, new GuiItem(profileItem));

        // Row 2 (slots 10-16): Stats summary items
        if (stats != null) {
            // Net Profit
            String profitColor = stats.netProfit >= 0 ? "<green>" : "<red>";
            Material profitMat = stats.netProfit >= 0 ? Material.EMERALD : Material.REDSTONE;
            menu.setItem(
                    10,
                    new GuiItem(
                            new ItemBuilder(profitMat)
                                    .name("<gold>Net Profit")
                                    .lore(
                                            "",
                                            profitColor
                                                    + "$"
                                                    + String.format("%,d", stats.netProfit),
                                            "",
                                            "<dark_gray>Total Won: <green>$"
                                                    + String.format("%,d", stats.totalWon),
                                            "<dark_gray>Total Lost: <red>$"
                                                    + String.format("%,d", stats.totalLost))
                                    .build()));

            // Win Rate
            int totalGames = stats.crashTotalBets + stats.lotteryTotalBets + stats.diceTotalBets;
            int totalWins = stats.crashTotalWins + stats.lotteryTotalWins + stats.diceTotalWins;
            menu.setItem(
                    12,
                    new GuiItem(
                            new ItemBuilder(Material.TARGET)
                                    .name("<gold>Win Rate")
                                    .lore(
                                            "",
                                            "<white>" + String.format("%.1f%%", stats.getWinRate()),
                                            "",
                                            "<dark_gray>Wins: <green>" + totalWins,
                                            "<dark_gray>Total Games: <white>" + totalGames)
                                    .build()));

            // Total Wagered
            menu.setItem(
                    14,
                    new GuiItem(
                            new ItemBuilder(Material.GOLD_INGOT)
                                    .name("<gold>Total Wagered")
                                    .lore(
                                            "",
                                            "<yellow>$" + String.format("%,d", stats.totalWagered))
                                    .build()));

            // Streaks
            menu.setItem(
                    16,
                    new GuiItem(
                            new ItemBuilder(Material.BLAZE_POWDER)
                                    .name("<gold>Streaks")
                                    .lore(
                                            "",
                                            "<gray>Current: <white>"
                                                    + formatStreak(stats.currentStreak),
                                            "<gray>Best Win Streak: <green>" + stats.bestWinStreak,
                                            "<gray>Worst Loss Streak: <red>"
                                                    + Math.abs(stats.worstLossStreak))
                                    .build()));
        } else {
            // Stats unavailable placeholder
            for (int slot : new int[] {10, 12, 14, 16}) {
                menu.setItem(
                        slot,
                        new GuiItem(
                                new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                                        .name("<gray>Stats unavailable")
                                        .lore("", "<dark_gray>Play some games first!")
                                        .build()));
            }
        }

        // Row 4-5 (slots 28-34): Game info cards
        // Slots
        menu.setItem(
                28,
                new GuiItem(
                        buildGameCard(
                                Material.GOLD_BLOCK,
                                "<yellow>Slots",
                                "Spin the 5 reels and match symbols!",
                                "/slots",
                                "serverarcade.command.slots",
                                player,
                                stats,
                                "slots")));

        // Blackjack
        menu.setItem(
                30,
                new GuiItem(
                        buildGameCard(
                                Material.PAPER,
                                "<red>Blackjack",
                                "Beat the dealer to 21!",
                                "/blackjack <bet>",
                                "serverarcade.command.blackjack",
                                player,
                                stats,
                                "blackjack")));

        // Crash
        menu.setItem(
                32,
                new GuiItem(
                        buildGameCard(
                                Material.FIREWORK_ROCKET,
                                "<gold>Crash",
                                "Cash out before the multiplier crashes!",
                                "/crash",
                                "serverarcade.command.crash",
                                player,
                                stats,
                                "crash")));

        // Jackpot
        menu.setItem(
                34,
                new GuiItem(
                        buildGameCard(
                                Material.DRAGON_EGG,
                                "<light_purple>Jackpot",
                                "Pool bets with other players!",
                                "/jackpot",
                                "serverarcade.command.jackpot",
                                player,
                                stats,
                                "jackpot")));

        // Coinflip
        menu.setItem(
                37,
                new GuiItem(
                        buildGameCard(
                                Material.SUNFLOWER,
                                "<aqua>Coinflip",
                                "Challenge players to a 50/50 flip!",
                                "/coinflip <bet>",
                                "serverarcade.command.coinflip",
                                player,
                                stats,
                                "coinflip")));

        // Dice
        menu.setItem(
                39,
                new GuiItem(
                        buildGameCard(
                                Material.BONE_BLOCK,
                                "<white>Dice",
                                "Roll and bet on the outcome!",
                                "/dice bet <amount> <type>",
                                "serverarcade.command.dice",
                                player,
                                stats,
                                "dice")));

        // Row 6 (slot 49): Close button
        menu.setItem(
                49,
                new GuiItem(
                        new ItemBuilder(Material.BARRIER).name("<red>Close").build(),
                        event -> player.closeInventory()));

        // Fill empty with dark glass
        menu.fillEmpty(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());

        menu.open(player);
    }

    private ItemStack buildGameCard(
            Material material,
            String name,
            String description,
            String command,
            String permission,
            Player player,
            StatisticsTracker.PlayerStats stats,
            String gameKey) {

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>" + description);
        lore.add("");

        // Per-game stats
        if (stats != null) {
            switch (gameKey) {
                case "crash" -> {
                    lore.add(
                            "<dark_gray>Bets: <white>"
                                    + stats.crashTotalBets
                                    + "  <dark_gray>Wins: <green>"
                                    + stats.crashTotalWins);
                    if (stats.crashBiggestWin > 0) {
                        lore.add(
                                "<dark_gray>Best Win: <gold>$"
                                        + String.format("%,d", stats.crashBiggestWin));
                    }
                    if (stats.crashHighestMult > 0) {
                        lore.add(
                                "<dark_gray>Best Multiplier: <gold>"
                                        + String.format("%.2fx", stats.crashHighestMult));
                    }
                }
                case "jackpot" -> {
                    lore.add(
                            "<dark_gray>Bets: <white>"
                                    + stats.lotteryTotalBets
                                    + "  <dark_gray>Wins: <green>"
                                    + stats.lotteryTotalWins);
                    if (stats.lotteryBiggestWin > 0) {
                        lore.add(
                                "<dark_gray>Best Win: <gold>$"
                                        + String.format("%,d", stats.lotteryBiggestWin));
                    }
                }
                case "dice" -> {
                    lore.add(
                            "<dark_gray>Bets: <white>"
                                    + stats.diceTotalBets
                                    + "  <dark_gray>Wins: <green>"
                                    + stats.diceTotalWins);
                    if (stats.diceBiggestWin > 0) {
                        lore.add(
                                "<dark_gray>Best Win: <gold>$"
                                        + String.format("%,d", stats.diceBiggestWin));
                    }
                }
                default -> lore.add("<dark_gray>Stats tracked via machines");
            }
        }

        lore.add("");

        // Command access status
        boolean hasAccess =
                player.hasPermission(permission) || player.hasPermission("serverarcade.admin");
        if (hasAccess) {
            lore.add("<green>Command Unlocked");
            lore.add("<gray>Use: <yellow>" + command);
        } else {
            lore.add("<red>Command Locked");
            lore.add("<gray>Find a machine or unlock in <gold>/store");
        }

        return new ItemBuilder(material).name(name).lore(lore.toArray(new String[0])).build();
    }

    private String formatStreak(int streak) {
        if (streak > 0) return "<green>" + streak + " wins";
        if (streak < 0) return "<red>" + Math.abs(streak) + " losses";
        return "<gray>None";
    }
}
