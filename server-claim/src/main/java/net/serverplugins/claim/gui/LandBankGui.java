package net.serverplugins.claim.gui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimBank;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class LandBankGui extends Gui {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, HH:mm");

    private final ServerClaim plugin;
    private final Claim claim;

    public LandBankGui(ServerClaim plugin, Player player, Claim claim) {
        super(
                plugin,
                player,
                "<dark_green>Land Bank - " + (claim != null ? claim.getName() : "Unknown"),
                54);
        this.plugin = plugin;

        // Validate claim and bank exist
        if (!GuiValidator.validateClaimBank(plugin, player, claim, "LandBankGui")) {
            this.claim = null;
            return;
        }

        this.claim = claim;
    }

    @Override
    protected void initializeItems() {
        // Safety check
        if (claim == null) {
            return;
        }

        ClaimBank bank = plugin.getBankManager().getBank(claim.getId());

        // Fill background
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            setItem(i, new GuiItem(filler));
        }

        // Calculate upkeep cost first (needed for balance item and forecast)
        double costPerChunk = plugin.getClaimConfig().getUpkeepCostPerChunk();
        int chunkCount = claim.getChunks().size();
        double totalUpkeep = costPerChunk * chunkCount;
        double discount = plugin.getLevelManager().getUpkeepDiscount(claim.getId());
        double discountedUpkeep = totalUpkeep * (1 - discount / 100);

        // Bank balance display (slot 13)
        double balance = bank != null ? bank.getBalance() : 0;
        double warning = bank != null ? bank.getMinimumBalanceWarning() : 100;
        String balanceColor = balance < warning ? "<red>" : "<green>";

        // Calculate balance runway
        String runwayLine;
        boolean dbGracePeriod = bank != null && bank.getGracePeriodStart() != null;
        boolean inGracePeriod =
                dbGracePeriod && (discountedUpkeep <= 0 || balance < discountedUpkeep);
        if (discountedUpkeep <= 0) {
            runwayLine = "<green>No upkeep due";
        } else {
            int daysRemaining = (int) (balance / discountedUpkeep);
            String runwayColor;
            if (inGracePeriod) {
                runwayColor = "<dark_red>";
            } else if (daysRemaining < 3) {
                runwayColor = "<red>";
            } else if (daysRemaining < 7) {
                runwayColor = "<yellow>";
            } else {
                runwayColor = "<green>";
            }
            runwayLine = runwayColor + "Covers: " + daysRemaining + " day(s)";
        }

        List<String> balanceLore = new ArrayList<>();
        balanceLore.add("");
        balanceLore.add(
                "<gray>Current Balance: " + balanceColor + "$" + String.format("%.2f", balance));
        balanceLore.add("<gray>Warning Threshold: <yellow>$" + String.format("%.2f", warning));
        balanceLore.add("<gray>" + runwayLine);
        if (inGracePeriod) {
            if (balance >= discountedUpkeep && discountedUpkeep > 0) {
                balanceLore.add("<yellow>Upkeep recovery pending...");
            } else if (discountedUpkeep > 0) {
                double needed = discountedUpkeep - balance;
                balanceLore.add("<dark_red>IN GRACE PERIOD");
                balanceLore.add("<red>Need $" + String.format("%.2f", needed) + " more for upkeep");
            } else {
                balanceLore.add("<dark_red>IN GRACE PERIOD");
            }
        }
        balanceLore.add("");
        balanceLore.add(
                bank != null && bank.getNextUpkeepDue() != null
                        ? "<gray>Next Upkeep Due: <white>" + formatInstant(bank.getNextUpkeepDue())
                        : "<gray>Upkeep: <green>Paid");

        ItemStack balanceItem =
                new ItemBuilder(Material.GOLD_INGOT)
                        .name("<gold>Bank Balance")
                        .lore(balanceLore.toArray(new String[0]))
                        .glow(true)
                        .build();
        setItem(13, new GuiItem(balanceItem));

        // Deposit buttons (row 3)
        createDepositButton(28, 100);
        createDepositButton(29, 500);
        createDepositButton(30, 1000);
        createDepositButton(31, 5000);
        createDepositButton(32, 10000);
        createDepositButton(33, "All");

        // Withdraw buttons (row 4)
        createWithdrawButton(37, 100, balance);
        createWithdrawButton(38, 500, balance);
        createWithdrawButton(39, 1000, balance);
        createWithdrawButton(40, 5000, balance);
        createWithdrawButton(41, 10000, balance);
        createWithdrawButton(42, "All", balance);

        // Labels
        ItemStack depositLabel =
                new ItemBuilder(Material.LIME_STAINED_GLASS_PANE).name("<green>Deposit").build();
        setItem(19, new GuiItem(depositLabel));

        ItemStack withdrawLabel =
                new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name("<red>Withdraw").build();
        setItem(46, new GuiItem(withdrawLabel));

        // Transaction history button (slot 22)
        ItemStack historyItem =
                new ItemBuilder(Material.BOOK)
                        .name("<yellow>Transaction History")
                        .lore(
                                "",
                                "<gray>View recent deposits,",
                                "<gray>withdrawals, and upkeep",
                                "",
                                "<yellow>Click to view history")
                        .build();
        setItem(
                22,
                new GuiItem(
                        historyItem,
                        e -> {
                            viewer.closeInventory();
                            new BankTransactionHistoryGui(plugin, viewer, claim).open();
                        }));

        // Upkeep forecast (slot 23)
        if (discountedUpkeep > 0) {
            int forecastDays = (int) (balance / discountedUpkeep);
            double thirtyCost = discountedUpkeep * 30;
            double toFundThirty = Math.max(0, thirtyCost - balance);

            Material forecastMaterial;
            if (forecastDays >= 30) {
                forecastMaterial = Material.LIME_DYE;
            } else if (forecastDays >= 7) {
                forecastMaterial = Material.SUNFLOWER;
            } else {
                forecastMaterial = Material.REDSTONE_TORCH;
            }

            List<String> forecastLore = new ArrayList<>();
            forecastLore.add("");
            forecastLore.add(
                    "<gray>Daily Cost: <white>$" + String.format("%.2f", discountedUpkeep));
            forecastLore.add("<gray>Balance Covers: <white>" + forecastDays + " day(s)");
            forecastLore.add("");
            forecastLore.add("<gray>30-Day Cost: <white>$" + String.format("%.2f", thirtyCost));
            if (toFundThirty > 0) {
                forecastLore.add(
                        "<yellow>To Fund 30 Days: <white>Deposit $"
                                + String.format("%.2f", toFundThirty)
                                + " more");
            } else {
                forecastLore.add("<green>30 days fully funded!");
            }

            ItemStack forecastItem =
                    new ItemBuilder(forecastMaterial)
                            .name("<gold>Upkeep Forecast")
                            .lore(forecastLore.toArray(new String[0]))
                            .build();
            setItem(23, new GuiItem(forecastItem));
        }

        // Upkeep info (slot 4)
        List<String> upkeepLore = new ArrayList<>();
        upkeepLore.add("");
        upkeepLore.add("<gray>Cost per chunk: <white>$" + String.format("%.2f", costPerChunk));
        upkeepLore.add("<gray>Your chunks: <white>" + chunkCount);
        upkeepLore.add("<gray>Base total: <white>$" + String.format("%.2f", totalUpkeep));
        if (discount > 0) {
            upkeepLore.add(
                    "<gray>Level discount: <green>-" + String.format("%.0f", discount) + "%");
            upkeepLore.add("<gray>Final cost: <green>$" + String.format("%.2f", discountedUpkeep));
        }

        ItemStack upkeepItem =
                new ItemBuilder(Material.CLOCK)
                        .name("<yellow>Upkeep Information")
                        .lore(upkeepLore.toArray(new String[0]))
                        .build();
        setItem(4, new GuiItem(upkeepItem));

        // Back button (slot 49)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<gray>Back")
                        .lore("<gray>Return to claim settings")
                        .build();
        setItem(
                49,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ClaimSettingsGui(plugin, viewer, claim).open();
                        }));

        // Close button (slot 53)
        ItemStack closeItem = new ItemBuilder(Material.BARRIER).name("<red>Close").build();
        setItem(53, new GuiItem(closeItem, e -> viewer.closeInventory()));
    }

    private void createDepositButton(int slot, int amount) {
        ItemStack item =
                new ItemBuilder(Material.EMERALD)
                        .name("<green>Deposit $" + amount)
                        .lore(
                                "",
                                "<gray>Click to deposit",
                                "<green>$" + amount + "</green> into bank")
                        .build();
        setItem(
                slot,
                new GuiItem(
                        item,
                        e -> {
                            plugin.getBankManager()
                                    .deposit(
                                            viewer,
                                            claim,
                                            amount,
                                            result -> {
                                                handleDepositResult(result, amount);
                                                plugin.getServer()
                                                        .getScheduler()
                                                        .runTask(
                                                                plugin,
                                                                () -> {
                                                                    viewer.closeInventory();
                                                                    new LandBankGui(
                                                                                    plugin, viewer,
                                                                                    claim)
                                                                            .open();
                                                                });
                                            });
                        }));
    }

    private void createDepositButton(int slot, String label) {
        ItemStack item =
                new ItemBuilder(Material.EMERALD_BLOCK)
                        .name("<green>Deposit " + label)
                        .lore("", "<gray>Click to deposit", "<gray>all available funds")
                        .build();
        setItem(
                slot,
                new GuiItem(
                        item,
                        e -> {
                            double playerBalance =
                                    plugin.getEconomy() != null
                                            ? plugin.getEconomy().getBalance(viewer)
                                            : 0;
                            if (playerBalance > 0) {
                                plugin.getBankManager()
                                        .deposit(
                                                viewer,
                                                claim,
                                                playerBalance,
                                                result -> {
                                                    handleDepositResult(result, playerBalance);
                                                    plugin.getServer()
                                                            .getScheduler()
                                                            .runTask(
                                                                    plugin,
                                                                    () -> {
                                                                        viewer.closeInventory();
                                                                        new LandBankGui(
                                                                                        plugin,
                                                                                        viewer,
                                                                                        claim)
                                                                                .open();
                                                                    });
                                                });
                            } else {
                                TextUtil.send(
                                        viewer,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<red>You have no money to deposit!");
                            }
                        }));
    }

    private void createWithdrawButton(int slot, int amount, double balance) {
        boolean canWithdraw = balance >= amount;
        Material material = canWithdraw ? Material.REDSTONE : Material.GRAY_DYE;

        ItemStack item =
                new ItemBuilder(material)
                        .name(
                                canWithdraw
                                        ? "<red>Withdraw $" + amount
                                        : "<gray>Withdraw $" + amount)
                        .lore(
                                "",
                                canWithdraw
                                        ? "<gray>Click to withdraw"
                                        : "<red>Insufficient bank balance",
                                canWithdraw
                                        ? "<red>$" + amount + "</red> from bank"
                                        : "<gray>Need: $" + amount)
                        .build();
        setItem(
                slot,
                new GuiItem(
                        item,
                        e -> {
                            if (!canWithdraw) return;
                            plugin.getBankManager()
                                    .withdraw(
                                            viewer,
                                            claim,
                                            amount,
                                            result -> {
                                                handleWithdrawResult(result, amount);
                                                plugin.getServer()
                                                        .getScheduler()
                                                        .runTask(
                                                                plugin,
                                                                () -> {
                                                                    viewer.closeInventory();
                                                                    new LandBankGui(
                                                                                    plugin, viewer,
                                                                                    claim)
                                                                            .open();
                                                                });
                                            });
                        }));
    }

    private void createWithdrawButton(int slot, String label, double balance) {
        boolean canWithdraw = balance > 0;
        Material material =
                canWithdraw ? Material.REDSTONE_BLOCK : Material.GRAY_STAINED_GLASS_PANE;

        ItemStack item =
                new ItemBuilder(material)
                        .name(canWithdraw ? "<red>Withdraw " + label : "<gray>Withdraw " + label)
                        .lore(
                                "",
                                canWithdraw ? "<gray>Click to withdraw all" : "<red>Bank is empty",
                                canWithdraw
                                        ? "<red>$"
                                                + String.format("%.2f", balance)
                                                + "</red> from bank"
                                        : "")
                        .build();
        setItem(
                slot,
                new GuiItem(
                        item,
                        e -> {
                            if (!canWithdraw) return;
                            // Fetch fresh balance to avoid stale data
                            double freshBalance = plugin.getBankManager().getBalance(claim.getId());
                            if (freshBalance <= 0) {
                                TextUtil.send(
                                        viewer,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<red>Bank is empty!");
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    viewer.closeInventory();
                                                    new LandBankGui(plugin, viewer, claim).open();
                                                });
                                return;
                            }
                            plugin.getBankManager()
                                    .withdraw(
                                            viewer,
                                            claim,
                                            freshBalance,
                                            result -> {
                                                handleWithdrawResult(result, freshBalance);
                                                plugin.getServer()
                                                        .getScheduler()
                                                        .runTask(
                                                                plugin,
                                                                () -> {
                                                                    viewer.closeInventory();
                                                                    new LandBankGui(
                                                                                    plugin, viewer,
                                                                                    claim)
                                                                            .open();
                                                                });
                                            });
                        }));
    }

    private void handleDepositResult(
            net.serverplugins.claim.managers.BankManager.DepositResult result, double amount) {
        String prefix = plugin.getClaimConfig().getMessage("prefix");
        switch (result) {
            case SUCCESS ->
                    TextUtil.send(
                            viewer,
                            prefix
                                    + "<green>Deposited $"
                                    + String.format("%.2f", amount)
                                    + " into land bank!");
            case INSUFFICIENT_FUNDS -> TextUtil.send(viewer, prefix + "<red>Insufficient funds!");
            case INVALID_AMOUNT -> TextUtil.send(viewer, prefix + "<red>Invalid deposit amount!");
            case ECONOMY_ERROR ->
                    TextUtil.send(viewer, prefix + "<red>Deposit failed! Please try again.");
        }
    }

    private void handleWithdrawResult(
            net.serverplugins.claim.managers.BankManager.WithdrawResult result, double amount) {
        String prefix = plugin.getClaimConfig().getMessage("prefix");
        switch (result) {
            case SUCCESS ->
                    TextUtil.send(
                            viewer,
                            prefix
                                    + "<green>Withdrew $"
                                    + String.format("%.2f", amount)
                                    + " from land bank!");
            case INSUFFICIENT_FUNDS ->
                    TextUtil.send(viewer, prefix + "<red>Insufficient bank balance!");
            case NO_PERMISSION ->
                    TextUtil.send(viewer, prefix + "<red>Only the claim owner can withdraw funds!");
            case INVALID_AMOUNT ->
                    TextUtil.send(viewer, prefix + "<red>Invalid withdrawal amount!");
            case ECONOMY_ERROR ->
                    TextUtil.send(viewer, prefix + "<red>Withdrawal failed! Please try again.");
        }
    }

    private String formatInstant(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).format(DATE_FORMAT);
    }
}
