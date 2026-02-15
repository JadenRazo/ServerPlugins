package net.serverplugins.claim.commands;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.serverplugins.api.economy.EconomyProvider;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.PlayerClaimData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class ClaimRefundCommand implements CommandExecutor, TabCompleter {

    private final ServerClaim plugin;
    private final NumberFormat currencyFormat;

    // Old pricing values (before the change)
    private static final double OLD_BASE_PRICE = 50000.0;
    private static final double OLD_GROWTH_RATE = 1.35;

    public ClaimRefundCommand(ServerClaim plugin) {
        this.plugin = plugin;
        this.currencyFormat = NumberFormat.getCurrencyInstance(Locale.US);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("serverclaim.admin.refund")) {
            TextUtil.send(sender, "<red>You don't have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            TextUtil.send(sender, "<yellow>Usage:");
            TextUtil.send(
                    sender,
                    "<gray>/claimrefund preview <white>- Preview refunds without issuing them");
            TextUtil.send(
                    sender, "<gray>/claimrefund execute <white>- Issue refunds to all players");
            return true;
        }

        String action = args[0].toLowerCase();

        if (action.equals("preview")) {
            previewRefunds(sender);
        } else if (action.equals("execute")) {
            executeRefunds(sender);
        } else {
            TextUtil.send(sender, "<red>Unknown action. Use 'preview' or 'execute'.");
        }

        return true;
    }

    private void previewRefunds(CommandSender sender) {
        TextUtil.send(sender, "<yellow>Calculating refunds...");

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            List<PlayerClaimData> players =
                                    plugin.getRepository().getAllPlayersWithPurchases();

                            if (players == null || players.isEmpty()) {
                                Bukkit.getScheduler()
                                        .runTask(
                                                plugin,
                                                () ->
                                                        TextUtil.send(
                                                                sender,
                                                                "<yellow>No players have purchased chunks."));
                                return;
                            }

                            double newBasePrice = plugin.getClaimConfig().getBasePrice();
                            double newGrowthRate = plugin.getClaimConfig().getGrowthRate();

                            StringBuilder report = new StringBuilder();
                            report.append("\n<gold>========== REFUND PREVIEW ==========</gold>\n");
                            report.append("<gray>Old Pricing: Base=")
                                    .append(formatCurrency(OLD_BASE_PRICE))
                                    .append(", Rate=")
                                    .append(OLD_GROWTH_RATE)
                                    .append("x\n");
                            report.append("<gray>New Pricing: Base=")
                                    .append(formatCurrency(newBasePrice))
                                    .append(", Rate=")
                                    .append(newGrowthRate)
                                    .append("x\n\n");

                            double totalRefund = 0;
                            int playerCount = 0;

                            for (PlayerClaimData player : players) {
                                int purchasedChunks = player.getPurchasedChunks();
                                if (purchasedChunks <= 0) continue;

                                double oldTotal =
                                        calculateTotalCost(
                                                purchasedChunks, OLD_BASE_PRICE, OLD_GROWTH_RATE);
                                double newTotal =
                                        calculateTotalCost(
                                                purchasedChunks, newBasePrice, newGrowthRate);
                                double refund = oldTotal - newTotal;

                                if (refund > 0) {
                                    report.append("<white>")
                                            .append(player.getUsername())
                                            .append(" <gray>(")
                                            .append(purchasedChunks)
                                            .append(" chunks): ")
                                            .append("<green>")
                                            .append(formatCurrency(refund))
                                            .append("\n");
                                    totalRefund += refund;
                                    playerCount++;
                                }
                            }

                            report.append("\n<gold>Total Players: <white>").append(playerCount);
                            report.append("\n<gold>Total Refund Amount: <green>")
                                    .append(formatCurrency(totalRefund));
                            report.append("\n<gold>=====================================</gold>");

                            String finalReport = report.toString();
                            Bukkit.getScheduler()
                                    .runTask(plugin, () -> TextUtil.send(sender, finalReport));
                        });
    }

    private void executeRefunds(CommandSender sender) {
        EconomyProvider economy = plugin.getEconomy();
        if (economy == null) {
            TextUtil.send(sender, "<red>Economy provider not available!");
            return;
        }

        TextUtil.send(sender, "<yellow>Processing refunds...");

        Bukkit.getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            List<PlayerClaimData> players =
                                    plugin.getRepository().getAllPlayersWithPurchases();

                            if (players == null || players.isEmpty()) {
                                Bukkit.getScheduler()
                                        .runTask(
                                                plugin,
                                                () ->
                                                        TextUtil.send(
                                                                sender,
                                                                "<yellow>No players have purchased chunks."));
                                return;
                            }

                            double newBasePrice = plugin.getClaimConfig().getBasePrice();
                            double newGrowthRate = plugin.getClaimConfig().getGrowthRate();

                            List<String> results = new ArrayList<>();
                            double totalRefunded = 0;
                            int successCount = 0;
                            int failCount = 0;

                            for (PlayerClaimData player : players) {
                                int purchasedChunks = player.getPurchasedChunks();
                                if (purchasedChunks <= 0) continue;

                                double oldTotal =
                                        calculateTotalCost(
                                                purchasedChunks, OLD_BASE_PRICE, OLD_GROWTH_RATE);
                                double newTotal =
                                        calculateTotalCost(
                                                purchasedChunks, newBasePrice, newGrowthRate);
                                double refund = oldTotal - newTotal;

                                if (refund > 0) {
                                    boolean success = economy.deposit(player.getUuid(), refund);
                                    if (success) {
                                        results.add(
                                                "<green>+ "
                                                        + player.getUsername()
                                                        + ": "
                                                        + formatCurrency(refund));
                                        totalRefunded += refund;
                                        successCount++;
                                        plugin.getLogger()
                                                .info(
                                                        "Refunded "
                                                                + formatCurrency(refund)
                                                                + " to "
                                                                + player.getUsername()
                                                                + " for "
                                                                + purchasedChunks
                                                                + " chunks");
                                    } else {
                                        results.add("<red>X " + player.getUsername() + ": FAILED");
                                        failCount++;
                                        plugin.getLogger()
                                                .warning(
                                                        "Failed to refund " + player.getUsername());
                                    }
                                }
                            }

                            double finalTotal = totalRefunded;
                            int finalSuccess = successCount;
                            int finalFail = failCount;

                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                TextUtil.send(
                                                        sender,
                                                        "\n<gold>========== REFUND RESULTS ==========</gold>");
                                                for (String result : results) {
                                                    TextUtil.send(sender, result);
                                                }
                                                TextUtil.send(
                                                        sender,
                                                        "\n<gold>Successful: <green>"
                                                                + finalSuccess);
                                                if (finalFail > 0) {
                                                    TextUtil.send(
                                                            sender,
                                                            "<gold>Failed: <red>" + finalFail);
                                                }
                                                TextUtil.send(
                                                        sender,
                                                        "<gold>Total Refunded: <green>"
                                                                + formatCurrency(finalTotal));
                                                TextUtil.send(
                                                        sender,
                                                        "<gold>=====================================</gold>");
                                            });
                        });
    }

    private double calculateTotalCost(int chunks, double basePrice, double growthRate) {
        double total = 0;
        for (int i = 1; i <= chunks; i++) {
            total += basePrice * Math.pow(growthRate, i - 1);
        }
        return total;
    }

    private String formatCurrency(double amount) {
        return currencyFormat.format(amount);
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            if ("preview".startsWith(partial)) completions.add("preview");
            if ("execute".startsWith(partial)) completions.add("execute");
        }
        return completions;
    }
}
