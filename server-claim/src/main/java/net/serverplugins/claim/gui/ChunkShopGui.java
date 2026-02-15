package net.serverplugins.claim.gui;

import java.sql.SQLException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.managers.ClaimManager.PurchaseResult;
import net.serverplugins.claim.models.PlayerChunkPool;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ChunkShopGui extends Gui {

    private final ServerClaim plugin;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");

    public ChunkShopGui(ServerClaim plugin, Player player) {
        super(plugin, player, "Chunk Shop", 45);
        this.plugin = plugin;
    }

    @Override
    protected void initializeItems() {
        // Get data
        PlayerChunkPool pool = getOrCreatePool();
        int totalPurchased = pool.getPurchasedChunks();
        int totalAllocated = getTotalAllocated();
        int available = Math.max(0, totalPurchased - totalAllocated); // Never show negative
        int profileCount = getProfileCount();
        double balance = getBalance();

        // Setup rows
        setupTitleRow(totalPurchased, totalAllocated, available, profileCount, balance);
        fillRow(1, Material.BLACK_STAINED_GLASS_PANE);
        setupPurchaseRow(balance);
        setupInfoRow(pool);
        setupNavigationRow();
    }

    /** Refresh the GUI in-place without closing/reopening */
    private void refreshGUI() {
        this.refresh();
    }

    private void setupTitleRow(
            int totalPurchased,
            int totalAllocated,
            int available,
            int profileCount,
            double balance) {
        // Fill row with black glass
        for (int i = 0; i < 9; i++) {
            if (i == 4) continue; // Skip center slot
            ItemStack blackGlass =
                    new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
            setItem(i, new GuiItem(blackGlass));
        }

        // Build lore with conditional warning
        String availableColor = available > 0 ? "<green>" : "<yellow>";
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("");
        lore.add("<gray>Global Chunks Purchased: <white>" + totalPurchased);
        lore.add("<gray>Allocated to Profiles: <white>" + totalAllocated);
        lore.add("<gray>Available to Allocate: " + availableColor + available);

        // Add warning if over-allocated
        if (totalAllocated > totalPurchased) {
            int deficit = totalAllocated - totalPurchased;
            lore.add("");
            lore.add("<red>⚠ Warning: You need " + deficit + " more chunks");
            lore.add("<red>  to cover your current allocations!");
        }

        lore.add("");
        lore.add("<gray>Active Profiles: <white>" + profileCount);
        lore.add("");
        lore.add("<gray>Your Balance: <gold>$" + String.format("%,.0f", balance));
        lore.add("");
        lore.add("<dark_gray>Purchase chunks to expand your claims!");

        // Center title item (slot 4)
        ItemStack titleItem =
                new ItemBuilder(Material.EMERALD)
                        .name("<gold><bold>Chunk Shop</bold></gold>")
                        .lore(lore.toArray(new String[0]))
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(titleItem));
    }

    private void fillRow(int row, Material material) {
        ItemStack filler = new ItemBuilder(material).name(" ").build();
        for (int i = row * 9; i < (row + 1) * 9; i++) {
            setItem(i, new GuiItem(filler));
        }
    }

    private void setupPurchaseRow(double balance) {
        // Fill row with gray glass
        for (int i = 18; i < 27; i++) {
            ItemStack grayGlass =
                    new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
            setItem(i, new GuiItem(grayGlass));
        }

        int[] amounts = {1, 5, 10, 50, 100};
        int[] slots = {20, 21, 22, 23, 24};

        for (int i = 0; i < amounts.length; i++) {
            createPurchaseButton(amounts[i], slots[i], balance);
        }
    }

    private void createPurchaseButton(int amount, int slot, double balance) {
        // Get player's current global chunks for exponential pricing
        PlayerChunkPool pool = getOrCreatePool();
        int currentGlobalChunks = pool.getPurchasedChunks();

        // Calculate total price using exponential pricing
        double totalPrice =
                plugin.getClaimConfig().calculateGlobalChunkPrice(currentGlobalChunks, amount);
        double pricePerChunk = totalPrice / amount;
        boolean canAfford = balance >= totalPrice;

        // Calculate price for first and last chunk in this purchase (with cap)
        double basePrice = plugin.getClaimConfig().getGlobalChunkBasePrice();
        double growthRate = plugin.getClaimConfig().getGlobalChunkGrowthRate();
        double maxPerChunk = plugin.getClaimConfig().getMaxPricePerChunk();
        double firstChunkPrice =
                Math.min(basePrice * Math.pow(growthRate, currentGlobalChunks), maxPerChunk);
        double lastChunkPrice =
                Math.min(
                        basePrice * Math.pow(growthRate, currentGlobalChunks + amount - 1),
                        maxPerChunk);

        Material material = canAfford ? Material.LIME_DYE : Material.GRAY_DYE;
        String nameColor = canAfford ? "<green>" : "<gray>";

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Amount: <white>" + amount + " chunk" + (amount > 1 ? "s" : ""));
        lore.add(
                "<gray>Chunk range: <white>#"
                        + (currentGlobalChunks + 1)
                        + " - #"
                        + (currentGlobalChunks + amount));
        lore.add("");

        if (amount > 1) {
            lore.add("<gray>First chunk: <gold>$" + String.format("%,.0f", firstChunkPrice));
            lore.add("<gray>Last chunk: <gold>$" + String.format("%,.0f", lastChunkPrice));
            lore.add(
                    "<gray>Average: <gold>$"
                            + String.format("%,.0f", pricePerChunk)
                            + " <gray>per chunk");
        } else {
            lore.add("<gray>Price: <gold>$" + String.format("%,.0f", totalPrice));
        }

        lore.add("");
        lore.add("<gray>Total cost: <gold>$" + String.format("%,.0f", totalPrice));

        lore.add("");
        if (canAfford) {
            lore.add("<green><bold>Click to purchase!</bold>");
        } else {
            double needed = totalPrice - balance;
            lore.add("<red>Need $" + String.format("%,.0f", needed) + " more");
        }

        ItemStack purchaseItem =
                new ItemBuilder(material)
                        .name(nameColor + "<bold>" + amount + " Chunk" + (amount > 1 ? "s" : ""))
                        .lore(lore.toArray(new String[0]))
                        .glow(canAfford)
                        .build();

        if (canAfford) {
            setItem(slot, new GuiItem(purchaseItem, e -> handlePurchase(amount, totalPrice)));
        } else {
            setItem(slot, new GuiItem(purchaseItem));
        }
    }

    private void handlePurchase(int amount, double totalPrice) {
        plugin.getClaimManager()
                .purchaseChunksBulk(
                        viewer,
                        amount,
                        result -> {
                            if (result == PurchaseResult.SUCCESS) {
                                String formattedPrice = String.format("$%,.0f", totalPrice);
                                TextUtil.send(
                                        viewer,
                                        "<green>✓ Successfully purchased <white>"
                                                + amount
                                                + " chunk"
                                                + (amount > 1 ? "s" : "")
                                                + "<green> for <gold>"
                                                + formattedPrice);
                                TextUtil.send(
                                        viewer,
                                        "<gray>→ Use <white>/claim <gray>to allocate chunks to your profiles");
                                refreshGUI();
                            } else {
                                sendErrorMessage(result);
                            }
                        });
    }

    private void sendErrorMessage(PurchaseResult result) {
        switch (result) {
            case INSUFFICIENT_FUNDS ->
                    TextUtil.send(viewer, "<red>✗ You don't have enough money for this purchase!");
            case MAX_CHUNKS_REACHED ->
                    TextUtil.send(viewer, "<red>✗ You've reached the maximum chunk limit!");
            case ECONOMY_ERROR ->
                    TextUtil.send(viewer, "<red>✗ Economy system error! Please contact staff.");
            case DATABASE_ERROR ->
                    TextUtil.send(viewer, "<red>✗ Database error! Please try again.");
            case NO_PLAYER_DATA ->
                    TextUtil.send(viewer, "<red>✗ Player data error! Please contact staff.");
            default -> TextUtil.send(viewer, "<red>✗ Purchase failed! Please try again.");
        }
    }

    private void setupInfoRow(PlayerChunkPool pool) {
        // Fill row with gray glass
        for (int i = 27; i < 36; i++) {
            if (i == 29 || i == 31 || i == 33) continue;
            ItemStack grayGlass =
                    new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
            setItem(i, new GuiItem(grayGlass));
        }

        // Purchase history (slot 29)
        setupHistoryPanel(pool);

        // How it works (slot 31)
        setupHowItWorksPanel();

        // Bulk discounts (slot 33)
        setupBulkDiscountsPanel();
    }

    private void setupHistoryPanel(PlayerChunkPool pool) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Total Purchased: <white>" + pool.getPurchasedChunks());
        lore.add("<gray>Total Spent: <gold>$" + String.format("%,.0f", pool.getTotalSpent()));

        if (pool.getLastPurchase() != null) {
            String dateStr =
                    DATE_FORMAT.format(pool.getLastPurchase().atZone(ZoneId.systemDefault()));
            lore.add("");
            lore.add("<gray>Last Purchase: <white>" + dateStr);
        } else {
            lore.add("");
            lore.add("<gray>Last Purchase: <dark_gray>Never");
        }

        ItemStack historyItem =
                new ItemBuilder(Material.BOOK)
                        .name("<aqua>Purchase History")
                        .lore(lore.toArray(new String[0]))
                        .build();

        setItem(29, new GuiItem(historyItem));
    }

    private void setupHowItWorksPanel() {
        ItemStack howItWorksItem =
                new ItemBuilder(Material.COMPASS)
                        .name("<yellow>How It Works")
                        .lore(
                                "",
                                "<gray>1. <white>Purchase chunks here",
                                "<gray>   → Chunks go into your pool",
                                "",
                                "<gray>2. <white>Use <yellow>/claim <white>in-game",
                                "<gray>   → Allocate chunks to profiles",
                                "",
                                "<gray>3. <white>Expand your territory",
                                "<gray>   → Build and protect your land!",
                                "",
                                "<dark_gray>Chunks are global across profiles")
                        .build();

        setItem(31, new GuiItem(howItWorksItem));
    }

    private void setupBulkDiscountsPanel() {
        PlayerChunkPool pool = getOrCreatePool();
        int currentChunks = pool.getPurchasedChunks();
        double basePrice = plugin.getClaimConfig().getGlobalChunkBasePrice();
        double growthRate = plugin.getClaimConfig().getGlobalChunkGrowthRate();
        double maxPerChunk = plugin.getClaimConfig().getMaxPricePerChunk();

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Exponential pricing curve");
        lore.add("");

        // Show pricing for key milestones
        int[] milestones = {1, 10, 25, 50, 75, 100};
        for (int milestone : milestones) {
            if (milestone > currentChunks) {
                double uncappedPrice = basePrice * Math.pow(growthRate, milestone - 1);
                double price = Math.min(uncappedPrice, maxPerChunk);
                String indicator = "";

                if (milestone == currentChunks + 1) {
                    indicator = " <yellow>← Next";
                } else if (uncappedPrice > maxPerChunk) {
                    indicator = " <red>(capped)";
                }

                lore.add(formatPriceLine(milestone, price, indicator));
            }
        }

        lore.add("");
        lore.add(
                "<dark_gray>+"
                        + String.format("%.0f%%", (growthRate - 1) * 100)
                        + " per chunk (max $"
                        + String.format("%,.0f", maxPerChunk)
                        + ")");

        ItemStack pricingItem =
                new ItemBuilder(Material.GOLD_INGOT)
                        .name("<gold>Pricing Info")
                        .lore(lore.toArray(new String[0]))
                        .glow(true)
                        .build();

        setItem(33, new GuiItem(pricingItem));
    }

    private String formatPriceLine(int chunkNumber, double price, String indicator) {
        String chunkStr = String.format("Chunk #%-3d", chunkNumber);
        String priceStr = String.format("$%,10.0f", price);

        return "<white>" + chunkStr + " <gold>" + priceStr + indicator;
    }

    private void setupNavigationRow() {
        // Fill row with black glass
        for (int i = 36; i < 45; i++) {
            if (i == 40) continue; // Skip back button slot
            ItemStack blackGlass =
                    new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
            setItem(i, new GuiItem(blackGlass));
        }

        // Back button (slot 40)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to claim menu")
                        .build();

        setItem(
                40,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ClaimMenuGui(plugin, viewer).open();
                        }));
    }

    // ==================== HELPER METHODS ====================

    private PlayerChunkPool getOrCreatePool() {
        PlayerChunkPool pool = null;
        try {
            pool = plugin.getClaimManager().getPlayerChunkPool(viewer.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger()
                    .severe(
                            "Failed to load chunk pool for "
                                    + viewer.getName()
                                    + ": "
                                    + e.getMessage());
        }

        // If pool is null (not cached on main thread), return empty pool as fallback
        if (pool == null) {
            plugin.getLogger()
                    .warning(
                            "Chunk pool not cached for " + viewer.getName() + ", using empty pool");
            return new PlayerChunkPool(viewer.getUniqueId());
        }

        return pool;
    }

    private int getTotalAllocated() {
        try {
            return plugin.getRepository().getTotalAllocatedChunks(viewer.getUniqueId());
        } catch (SQLException e) {
            plugin.getLogger()
                    .severe(
                            "Failed to get total allocated chunks for "
                                    + viewer.getName()
                                    + ": "
                                    + e.getMessage());
            return 0;
        }
    }

    private int getProfileCount() {
        try {
            List<net.serverplugins.claim.models.Claim> claims =
                    plugin.getClaimManager().getPlayerClaims(viewer.getUniqueId());
            return claims != null ? claims.size() : 0;
        } catch (Exception e) {
            plugin.getLogger()
                    .severe(
                            "Failed to get profile count for "
                                    + viewer.getName()
                                    + ": "
                                    + e.getMessage());
            return 0;
        }
    }

    private double getBalance() {
        try {
            if (plugin.getEconomy() != null) {
                return plugin.getEconomy().getBalance(viewer);
            }
        } catch (Exception e) {
            plugin.getLogger()
                    .warning(
                            "Failed to get balance for "
                                    + viewer.getName()
                                    + ": "
                                    + e.getMessage());
        }
        return 0;
    }
}
