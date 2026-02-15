package net.serverplugins.claim.gui;

import java.time.format.DateTimeFormatter;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.BankTransaction;
import net.serverplugins.claim.models.Claim;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class BankTransactionHistoryGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;
    private final int page;
    private static final int ITEMS_PER_PAGE = 28;
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("MM/dd HH:mm");

    public BankTransactionHistoryGui(ServerClaim plugin, Player player, Claim claim) {
        this(plugin, player, claim, 0);
    }

    public BankTransactionHistoryGui(ServerClaim plugin, Player player, Claim claim, int page) {
        super(plugin, player, "<dark_aqua>Bank History - Page " + (page + 1), 54);
        this.plugin = plugin;
        this.page = page;

        // Validate claim and bank exist
        if (!GuiValidator.validateClaimBank(plugin, player, claim, "BankTransactionHistoryGui")) {
            this.claim = null;
            return;
        }

        this.claim = claim;
    }

    @Override
    protected void initializeItems() {
        // Safety check - return early if claim is null
        if (claim == null) {
            return;
        }

        // Fill border
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) setItem(i, new GuiItem(filler));
        for (int i = 45; i < 54; i++) setItem(i, new GuiItem(filler));
        for (int i = 9; i < 45; i += 9) {
            setItem(i, new GuiItem(filler));
            setItem(i + 8, new GuiItem(filler));
        }

        // Show loading indicator while transactions load asynchronously
        ItemStack loadingItem =
                new ItemBuilder(Material.CLOCK)
                        .name("<yellow>Loading...")
                        .lore("", "<gray>Fetching transaction history...")
                        .build();
        setItem(22, new GuiItem(loadingItem));

        // Always show back button
        setupBackButton();

        // Load transactions asynchronously to avoid blocking the main thread
        final int claimId = claim.getId();
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            List<BankTransaction> transactions = null;
                            try {
                                transactions =
                                        plugin.getBankRepository()
                                                .getTransactionHistory(claimId, 200);
                            } catch (Exception e) {
                                plugin.getLogger()
                                        .severe(
                                                "Failed to load transaction history for claim "
                                                        + claimId
                                                        + ": "
                                                        + e.getMessage());
                            }

                            final List<BankTransaction> finalTransactions = transactions;

                            // Update GUI on main thread
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                // Check if player still has this inventory open
                                                if (!viewer.isOnline()) return;

                                                // Clear loading indicator
                                                ItemStack clearFiller =
                                                        new ItemBuilder(
                                                                        Material
                                                                                .GRAY_STAINED_GLASS_PANE)
                                                                .name(" ")
                                                                .build();
                                                setItem(22, new GuiItem(clearFiller));

                                                if (finalTransactions == null
                                                        || finalTransactions.isEmpty()) {
                                                    displayNoTransactionsMessage();
                                                } else {
                                                    displayTransactions(finalTransactions);
                                                }
                                            });
                        });
    }

    /** Display transaction list with pagination. */
    private void displayTransactions(List<BankTransaction> allTransactions) {
        int totalPages = (int) Math.ceil((double) allTransactions.size() / ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, allTransactions.size());

        // Display transactions in slots 10-16, 19-25, 28-34, 37-43
        int[] slots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        for (int i = start; i < end; i++) {
            int slotIndex = i - start;
            if (slotIndex >= slots.length) break;

            BankTransaction tx = allTransactions.get(i);
            // Null safety check for individual transaction
            if (tx == null) {
                plugin.getLogger()
                        .warning(
                                "Null transaction found at index "
                                        + i
                                        + " for claim "
                                        + claim.getId());
                continue;
            }
            setItem(slots[slotIndex], createTransactionItem(tx));
        }

        // Navigation buttons
        setupNavigationButtons(totalPages, allTransactions.size());
    }

    /** Display "no transactions" message when bank is empty. */
    private void displayNoTransactionsMessage() {
        ItemStack noTransactionsItem =
                new ItemBuilder(Material.PAPER)
                        .name("<yellow>No Transactions Yet")
                        .lore(
                                "",
                                "<gray>This claim bank has no",
                                "<gray>transaction history yet.",
                                "",
                                "<gray>Transactions will appear here",
                                "<gray>when you deposit, withdraw,",
                                "<gray>or pay upkeep costs.")
                        .build();
        setItem(22, new GuiItem(noTransactionsItem));
    }

    /** Setup navigation buttons (previous/next page, back, close). */
    private void setupNavigationButtons(int totalPages, int transactionCount) {
        // Previous page button (slot 48)
        if (page > 0) {
            ItemStack prevItem =
                    new ItemBuilder(Material.ARROW)
                            .name("<yellow>Previous Page")
                            .lore("<gray>Go to page " + page)
                            .build();
            setItem(
                    48,
                    new GuiItem(
                            prevItem,
                            e -> {
                                viewer.closeInventory();
                                new BankTransactionHistoryGui(plugin, viewer, claim, page - 1)
                                        .open();
                            }));
        }

        // Page info (slot 49)
        ItemStack pageInfo =
                new ItemBuilder(Material.PAPER)
                        .name("<gold>Page " + (page + 1) + " of " + totalPages)
                        .lore("", "<gray>Total transactions: <white>" + transactionCount)
                        .build();
        setItem(49, new GuiItem(pageInfo));

        // Next page button (slot 50)
        if (page < totalPages - 1) {
            ItemStack nextItem =
                    new ItemBuilder(Material.ARROW)
                            .name("<yellow>Next Page")
                            .lore("<gray>Go to page " + (page + 2))
                            .build();
            setItem(
                    50,
                    new GuiItem(
                            nextItem,
                            e -> {
                                viewer.closeInventory();
                                new BankTransactionHistoryGui(plugin, viewer, claim, page + 1)
                                        .open();
                            }));
        }
    }

    /** Setup back and close buttons. */
    private void setupBackButton() {
        // Back button (slot 45)
        ItemStack backItem =
                new ItemBuilder(Material.DARK_OAK_DOOR)
                        .name("<gray>Back")
                        .lore("<gray>Return to land bank")
                        .build();
        setItem(
                45,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new LandBankGui(plugin, viewer, claim).open();
                        }));

        // Close button (slot 53)
        ItemStack closeItem = new ItemBuilder(Material.BARRIER).name("<red>Close").build();
        setItem(53, new GuiItem(closeItem, e -> viewer.closeInventory()));
    }

    /** Create a transaction item with null safety checks. */
    private GuiItem createTransactionItem(BankTransaction tx) {
        // Null safety for transaction type
        if (tx.getType() == null) {
            plugin.getLogger().warning("Transaction has null type for claim " + claim.getId());
            return createErrorTransactionItem();
        }

        Material material =
                switch (tx.getType()) {
                    case DEPOSIT -> Material.EMERALD;
                    case WITHDRAW -> Material.REDSTONE;
                    case UPKEEP -> Material.CLOCK;
                    case TAX -> Material.GOLD_NUGGET;
                    case REFUND -> Material.DIAMOND;
                    case NATION_TAX -> Material.GOLD_BLOCK;
                };

        String colorPrefix =
                switch (tx.getType()) {
                    case DEPOSIT, REFUND -> "<green>+";
                    case WITHDRAW, UPKEEP, TAX, NATION_TAX -> "<red>-";
                };

        String playerName = "System";
        if (tx.getPlayerUuid() != null) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(tx.getPlayerUuid());
            playerName = player.getName() != null ? player.getName() : "Unknown";
        }

        // Get transaction properties (primitives don't need null checks)
        double amount = tx.getAmount();
        double balanceAfter = tx.getBalanceAfter();
        String displayName =
                tx.getType().getDisplayName() != null
                        ? tx.getType().getDisplayName()
                        : "Transaction";

        // Format date with null safety
        String dateStr = "Unknown";
        if (tx.getCreatedAt() != null) {
            try {
                dateStr = tx.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).format(FORMAT);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to format transaction date: " + e.getMessage());
            }
        }

        ItemStack item =
                new ItemBuilder(material)
                        .name(displayName)
                        .lore(
                                "",
                                "<gray>Amount: "
                                        + colorPrefix
                                        + "$"
                                        + String.format("%.2f", amount),
                                "<gray>Balance After: <white>$"
                                        + String.format("%.2f", balanceAfter),
                                "<gray>By: <white>" + playerName,
                                "<gray>Date: <white>" + dateStr,
                                tx.getDescription() != null ? "" : null,
                                tx.getDescription() != null
                                        ? "<dark_gray>" + tx.getDescription()
                                        : null)
                        .build();

        return new GuiItem(item);
    }

    /** Create error item for malformed transactions. */
    private GuiItem createErrorTransactionItem() {
        ItemStack item =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Invalid Transaction")
                        .lore("", "<gray>This transaction has", "<gray>incomplete data.")
                        .build();
        return new GuiItem(item);
    }
}
