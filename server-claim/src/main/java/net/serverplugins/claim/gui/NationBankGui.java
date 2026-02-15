package net.serverplugins.claim.gui;

import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.Nation;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class NationBankGui extends Gui {

    private final ServerClaim plugin;
    private final Nation nation;
    private final Claim playerClaim;

    public NationBankGui(ServerClaim plugin, Player player, Nation nation, Claim playerClaim) {
        super(
                plugin,
                player,
                (nation != null ? nation.getColoredTag() : "<gray>Unknown") + " <gold>Treasury",
                54);
        this.plugin = plugin;
        this.playerClaim = playerClaim;

        // Validate nation exists
        if (!GuiValidator.validateNation(plugin, player, nation, "NationBankGui")) {
            this.nation = null;
            return;
        }

        this.nation = nation;
    }

    @Override
    protected void initializeItems() {
        // Safety check - return early if nation is null
        if (nation == null) {
            return;
        }

        // Fill background
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            setItem(i, new GuiItem(filler));
        }

        // Get nation balance with null safety
        double balance = 0.0;
        try {
            balance = plugin.getNationManager().getNationBalance(nation.getId());
        } catch (Exception e) {
            plugin.getLogger()
                    .severe(
                            "Failed to get nation balance for "
                                    + nation.getId()
                                    + ": "
                                    + e.getMessage());
            TextUtil.send(viewer, "<red>Failed to load nation treasury data!");
            viewer.closeInventory();
            return;
        }

        boolean isLeader = nation.isLeader(viewer.getUniqueId());
        boolean hasAdminPerm = viewer.hasPermission("serverclaim.admin");

        // Balance display (slot 13)
        ItemStack balanceItem =
                new ItemBuilder(Material.GOLD_BLOCK)
                        .name("<gold>Nation Treasury")
                        .lore(
                                "",
                                "<gray>Balance: <gold>$" + String.format("%.2f", balance),
                                "",
                                "<gray>The nation treasury is used",
                                "<gray>for war tributes, nation",
                                "<gray>upgrades, and more.")
                        .glow(true)
                        .build();
        setItem(13, new GuiItem(balanceItem));

        // Deposit buttons (row 3) - All members can deposit
        createDepositButton(28, 100);
        createDepositButton(29, 500);
        createDepositButton(30, 1000);
        createDepositButton(31, 5000);
        createDepositButton(32, 10000);
        createDepositButton(33, "All");

        // Withdraw buttons (row 4) - Leader only (or admin)
        if (isLeader || hasAdminPerm) {
            createWithdrawButton(37, 100, balance);
            createWithdrawButton(38, 500, balance);
            createWithdrawButton(39, 1000, balance);
            createWithdrawButton(40, 5000, balance);
            createWithdrawButton(41, 10000, balance);
            createWithdrawButton(42, "All", balance);
        } else {
            // Show locked indicators for non-leaders
            ItemStack leaderOnly =
                    new ItemBuilder(Material.BARRIER)
                            .name("<red>Leader Only")
                            .lore(
                                    "",
                                    "<gray>Only the nation leader",
                                    "<gray>can withdraw funds",
                                    "",
                                    "<dark_gray>You lack permission")
                            .build();
            for (int slot : new int[] {37, 38, 39, 40, 41, 42}) {
                setItem(slot, new GuiItem(leaderOnly));
            }
        }

        // Labels
        ItemStack depositLabel =
                new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                        .name("<green>Deposit")
                        .lore("", "<gray>All nation members", "<gray>can deposit funds")
                        .build();
        setItem(19, new GuiItem(depositLabel));

        ItemStack withdrawLabel =
                new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
                        .name(
                                (isLeader || hasAdminPerm)
                                        ? "<red>Withdraw"
                                        : "<dark_gray>Withdraw (Leader)")
                        .lore(
                                "",
                                (isLeader || hasAdminPerm)
                                        ? "<gray>Withdraw funds from treasury"
                                        : "<gray>Requires leader permission")
                        .build();
        setItem(46, new GuiItem(withdrawLabel));

        // Tax info (slot 22)
        ItemStack taxItem =
                new ItemBuilder(Material.PAPER)
                        .name("<yellow>Tax Information")
                        .lore(
                                "",
                                "<gray>Nation leaders can set",
                                "<gray>a tax rate on member",
                                "<gray>claim upkeep payments.",
                                "",
                                "<gray>Use: <white>/nation tax <rate>")
                        .build();
        setItem(22, new GuiItem(taxItem));

        // Back button (slot 49)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<gray>Back")
                        .lore("<gray>Return to nation menu")
                        .build();
        setItem(
                49,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new NationMenuGui(plugin, viewer, nation, playerClaim).open();
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
                                "<green>$" + amount + "</green> to treasury")
                        .build();
        setItem(
                slot,
                new GuiItem(
                        item,
                        e -> {
                            // Null safety check
                            if (nation == null) {
                                TextUtil.send(viewer, "<red>Nation no longer exists!");
                                viewer.closeInventory();
                                return;
                            }

                            // Check if player has enough money
                            double playerBalance =
                                    plugin.getEconomy() != null
                                            ? plugin.getEconomy().getBalance(viewer)
                                            : 0;
                            if (playerBalance < amount) {
                                TextUtil.send(
                                        viewer,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<red>Insufficient funds! You need $"
                                                + amount
                                                + " but have $"
                                                + String.format("%.2f", playerBalance));
                                return;
                            }

                            plugin.getNationManager()
                                    .depositToNationBank(
                                            viewer,
                                            nation,
                                            amount,
                                            success -> {
                                                if (success) {
                                                    TextUtil.send(
                                                            viewer,
                                                            plugin.getClaimConfig()
                                                                            .getMessage("prefix")
                                                                    + "<green>Deposited $"
                                                                    + amount
                                                                    + " to nation treasury!");
                                                } else {
                                                    TextUtil.send(
                                                            viewer,
                                                            plugin.getClaimConfig()
                                                                            .getMessage("prefix")
                                                                    + "<red>Deposit failed!");
                                                }
                                                plugin.getServer()
                                                        .getScheduler()
                                                        .runTask(
                                                                plugin,
                                                                () -> {
                                                                    viewer.closeInventory();
                                                                    new NationBankGui(
                                                                                    plugin,
                                                                                    viewer,
                                                                                    nation,
                                                                                    playerClaim)
                                                                            .open();
                                                                });
                                            });
                        }));
    }

    private void createDepositButton(int slot, String label) {
        ItemStack item =
                new ItemBuilder(Material.EMERALD_BLOCK)
                        .name("<green>Deposit " + label)
                        .lore(
                                "",
                                "<gray>Click to deposit all",
                                "<gray>available funds",
                                "",
                                "<yellow>⚠ This will deposit",
                                "<yellow>your entire balance!")
                        .build();
        setItem(
                slot,
                new GuiItem(
                        item,
                        e -> {
                            // Null safety check
                            if (nation == null) {
                                TextUtil.send(viewer, "<red>Nation no longer exists!");
                                viewer.closeInventory();
                                return;
                            }

                            double playerBalance =
                                    plugin.getEconomy() != null
                                            ? plugin.getEconomy().getBalance(viewer)
                                            : 0;

                            if (playerBalance <= 0) {
                                TextUtil.send(
                                        viewer,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<red>You have no money to deposit!");
                                return;
                            }

                            plugin.getNationManager()
                                    .depositToNationBank(
                                            viewer,
                                            nation,
                                            playerBalance,
                                            success -> {
                                                if (success) {
                                                    TextUtil.send(
                                                            viewer,
                                                            plugin.getClaimConfig()
                                                                            .getMessage("prefix")
                                                                    + "<green>Deposited $"
                                                                    + String.format(
                                                                            "%.2f", playerBalance)
                                                                    + " to nation treasury!");
                                                } else {
                                                    TextUtil.send(
                                                            viewer,
                                                            plugin.getClaimConfig()
                                                                            .getMessage("prefix")
                                                                    + "<red>Deposit failed!");
                                                }
                                                plugin.getServer()
                                                        .getScheduler()
                                                        .runTask(
                                                                plugin,
                                                                () -> {
                                                                    viewer.closeInventory();
                                                                    new NationBankGui(
                                                                                    plugin,
                                                                                    viewer,
                                                                                    nation,
                                                                                    playerClaim)
                                                                            .open();
                                                                });
                                            });
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
                                        : "<red>Insufficient treasury balance",
                                canWithdraw
                                        ? "<red>$" + amount + "</red> from treasury"
                                        : "<gray>Need: $" + amount)
                        .build();
        setItem(
                slot,
                new GuiItem(
                        item,
                        e -> {
                            if (!canWithdraw) {
                                TextUtil.send(
                                        viewer,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<red>Insufficient treasury balance!");
                                return;
                            }

                            // Null safety check
                            if (nation == null) {
                                TextUtil.send(viewer, "<red>Nation no longer exists!");
                                viewer.closeInventory();
                                return;
                            }

                            // Permission check (belt-and-suspenders approach)
                            if (!nation.isLeader(viewer.getUniqueId())
                                    && !viewer.hasPermission("serverclaim.admin")) {
                                TextUtil.send(
                                        viewer,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<red>Only the nation leader can withdraw funds!");
                                return;
                            }

                            plugin.getNationManager()
                                    .withdrawFromNationBank(
                                            viewer,
                                            nation,
                                            amount,
                                            success -> {
                                                if (success) {
                                                    TextUtil.send(
                                                            viewer,
                                                            plugin.getClaimConfig()
                                                                            .getMessage("prefix")
                                                                    + "<green>Withdrew $"
                                                                    + amount
                                                                    + " from nation treasury!");
                                                } else {
                                                    TextUtil.send(
                                                            viewer,
                                                            plugin.getClaimConfig()
                                                                            .getMessage("prefix")
                                                                    + "<red>Could not withdraw funds!");
                                                }
                                                plugin.getServer()
                                                        .getScheduler()
                                                        .runTask(
                                                                plugin,
                                                                () -> {
                                                                    viewer.closeInventory();
                                                                    new NationBankGui(
                                                                                    plugin,
                                                                                    viewer,
                                                                                    nation,
                                                                                    playerClaim)
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
                                canWithdraw
                                        ? "<yellow>⚠ Withdraw ALL funds"
                                        : "<red>Treasury is empty",
                                canWithdraw ? "<red>$" + String.format("%.2f", balance) : "",
                                "",
                                canWithdraw ? "<yellow>Click to confirm" : "")
                        .build();
        setItem(
                slot,
                new GuiItem(
                        item,
                        e -> {
                            if (!canWithdraw) {
                                TextUtil.send(
                                        viewer,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<red>Treasury is empty!");
                                return;
                            }

                            // Null safety check
                            if (nation == null) {
                                TextUtil.send(viewer, "<red>Nation no longer exists!");
                                viewer.closeInventory();
                                return;
                            }

                            // Permission check
                            if (!nation.isLeader(viewer.getUniqueId())
                                    && !viewer.hasPermission("serverclaim.admin")) {
                                TextUtil.send(
                                        viewer,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<red>Only the nation leader can withdraw funds!");
                                return;
                            }

                            plugin.getNationManager()
                                    .withdrawFromNationBank(
                                            viewer,
                                            nation,
                                            balance,
                                            success -> {
                                                if (success) {
                                                    TextUtil.send(
                                                            viewer,
                                                            plugin.getClaimConfig()
                                                                            .getMessage("prefix")
                                                                    + "<green>Withdrew $"
                                                                    + String.format("%.2f", balance)
                                                                    + " from nation treasury!");
                                                } else {
                                                    TextUtil.send(
                                                            viewer,
                                                            plugin.getClaimConfig()
                                                                            .getMessage("prefix")
                                                                    + "<red>Could not withdraw funds!");
                                                }
                                                plugin.getServer()
                                                        .getScheduler()
                                                        .runTask(
                                                                plugin,
                                                                () -> {
                                                                    viewer.closeInventory();
                                                                    new NationBankGui(
                                                                                    plugin,
                                                                                    viewer,
                                                                                    nation,
                                                                                    playerClaim)
                                                                            .open();
                                                                });
                                            });
                        }));
    }
}
