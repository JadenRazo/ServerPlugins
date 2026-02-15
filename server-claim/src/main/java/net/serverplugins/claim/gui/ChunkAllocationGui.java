package net.serverplugins.claim.gui;

import java.sql.SQLException;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.managers.ClaimManager.AllocationResult;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.PlayerChunkPool;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** GUI for allocating chunks from global pool to a specific profile. */
public class ChunkAllocationGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;

    public ChunkAllocationGui(ServerClaim plugin, Player player, Claim claim) {
        super(plugin, player, "Allocate Chunks", 45);
        this.plugin = plugin;
        this.claim = claim;
    }

    @Override
    protected void initializeItems() {
        if (claim == null) {
            plugin.getLogger().warning("ChunkAllocationGui: Claim is null");
            return;
        }

        // Get pool data
        PlayerChunkPool pool = getOrCreatePool();
        int totalPurchased = pool.getPurchasedChunks();
        int totalAllocated = getTotalAllocated();
        int available = Math.max(0, totalPurchased - totalAllocated);
        int currentAllocated = claim.getPurchasedChunks();
        int maxPerProfile = plugin.getClaimManager().getMaxChunksPerProfile(viewer);

        // Setup rows
        setupTitleRow(totalPurchased, totalAllocated, available, currentAllocated, maxPerProfile);
        fillRow(1, Material.BLACK_STAINED_GLASS_PANE);
        setupAllocationRow(available, currentAllocated, maxPerProfile);
        setupNavigationRow();
    }

    private void setupTitleRow(
            int totalPurchased,
            int totalAllocated,
            int available,
            int currentAllocated,
            int maxPerProfile) {
        // Fill with black glass
        for (int i = 0; i < 9; i++) {
            if (i == 4) continue;
            ItemStack blackGlass =
                    new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
            setItem(i, new GuiItem(blackGlass));
        }

        // Center title
        String availableColor = available > 0 ? "<green>" : "<yellow>";
        String claimName =
                claim.getName() != null && !claim.getName().isEmpty()
                        ? claim.getName()
                        : "Profile #" + claim.getClaimOrder();

        ItemStack titleItem =
                new ItemBuilder(Material.EMERALD)
                        .name("<gold><bold>Chunk Allocation</bold></gold>")
                        .lore(
                                "",
                                "<gray>Profile: <white>" + claimName,
                                "",
                                "<yellow>Global Pool:",
                                "<gray>  Total Purchased: <white>" + totalPurchased,
                                "<gray>  Allocated to All Profiles: <white>" + totalAllocated,
                                "<gray>  Available: " + availableColor + available,
                                "",
                                "<yellow>This Profile:",
                                "<gray>  Currently Allocated: <white>"
                                        + currentAllocated
                                        + "/"
                                        + maxPerProfile,
                                "<gray>  Can Use: <white>" + (currentAllocated + 2) + " chunks",
                                "",
                                "<dark_gray>Allocate chunks to expand this profile!")
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

    private void setupAllocationRow(int available, int currentAllocated, int maxPerProfile) {
        // Fill row with gray glass
        for (int i = 18; i < 27; i++) {
            ItemStack grayGlass =
                    new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
            setItem(i, new GuiItem(grayGlass));
        }

        int[] amounts = {1, 5, 10, 25, 50};
        int[] slots = {20, 21, 22, 23, 24};

        for (int i = 0; i < amounts.length; i++) {
            createAllocationButton(
                    amounts[i], slots[i], available, currentAllocated, maxPerProfile);
        }
    }

    private void createAllocationButton(
            int amount, int slot, int available, int currentAllocated, int maxPerProfile) {
        boolean canAllocate = available >= amount && (currentAllocated + amount) <= maxPerProfile;
        boolean wouldExceedLimit = (currentAllocated + amount) > maxPerProfile;

        Material material;
        String nameColor;

        if (canAllocate) {
            material = Material.LIME_DYE;
            nameColor = "<green>";
        } else if (wouldExceedLimit) {
            material = Material.RED_DYE;
            nameColor = "<red>";
        } else {
            material = Material.GRAY_DYE;
            nameColor = "<gray>";
        }

        ItemStack item =
                new ItemBuilder(material)
                        .name(nameColor + "Allocate " + amount + " Chunk" + (amount > 1 ? "s" : ""))
                        .lore(
                                "",
                                "<gray>Amount: <white>"
                                        + amount
                                        + " chunk"
                                        + (amount > 1 ? "s" : ""),
                                "<gray>Available in Pool: <white>" + available,
                                "",
                                "<gray>Current: <white>" + currentAllocated + "/" + maxPerProfile,
                                "<gray>After: <white>"
                                        + (currentAllocated + amount)
                                        + "/"
                                        + maxPerProfile,
                                "",
                                wouldExceedLimit
                                        ? "<red>Would exceed profile limit!"
                                        : available < amount
                                                ? "<red>Not enough chunks in pool!"
                                                : "<green>Click to allocate")
                        .glow(canAllocate)
                        .build();

        setItem(
                slot,
                new GuiItem(
                        item,
                        e -> {
                            if (!canAllocate) {
                                if (wouldExceedLimit) {
                                    TextUtil.send(
                                            viewer,
                                            "<red>This would exceed your profile chunk limit ("
                                                    + maxPerProfile
                                                    + " chunks max)");
                                } else {
                                    TextUtil.send(
                                            viewer,
                                            "<red>You don't have enough chunks in your global pool!");
                                    TextUtil.send(
                                            viewer,
                                            "<gray>Available: <white>"
                                                    + available
                                                    + " <gray>| Needed: <white>"
                                                    + amount);
                                    TextUtil.send(
                                            viewer,
                                            "<yellow>Use /claim shop to purchase more chunks");
                                }
                                return;
                            }

                            // Allocate chunks
                            viewer.closeInventory();
                            plugin.getClaimManager()
                                    .allocateChunksToProfile(viewer, claim, amount)
                                    .thenAccept(
                                            result -> {
                                                plugin.getServer()
                                                        .getScheduler()
                                                        .runTask(
                                                                plugin,
                                                                () -> {
                                                                    if (result
                                                                            == AllocationResult
                                                                                    .SUCCESS) {
                                                                        TextUtil.send(
                                                                                viewer,
                                                                                "<green>Successfully allocated "
                                                                                        + amount
                                                                                        + " chunk"
                                                                                        + (amount
                                                                                                        > 1
                                                                                                ? "s"
                                                                                                : "")
                                                                                        + " to this profile!");
                                                                        TextUtil.send(
                                                                                viewer,
                                                                                "<gray>Profile now has <white>"
                                                                                        + (currentAllocated
                                                                                                + amount)
                                                                                        + " <gray>allocated chunks");
                                                                        // Reopen GUI to show
                                                                        // updated values
                                                                        new ChunkAllocationGui(
                                                                                        plugin,
                                                                                        viewer,
                                                                                        claim)
                                                                                .open();
                                                                    } else {
                                                                        String message =
                                                                                switch (result) {
                                                                                    case INSUFFICIENT_CHUNKS ->
                                                                                            "<red>Not enough chunks in your global pool!";
                                                                                    case PROFILE_CAPACITY_EXCEEDED ->
                                                                                            "<red>Profile chunk limit reached!";
                                                                                    case NOT_OWNER ->
                                                                                            "<red>You don't own this profile!";
                                                                                    case DATABASE_ERROR ->
                                                                                            "<red>Database error occurred!";
                                                                                    default ->
                                                                                            "<red>Allocation failed!";
                                                                                };
                                                                        TextUtil.send(
                                                                                viewer, message);
                                                                        // Reopen GUI
                                                                        new ChunkAllocationGui(
                                                                                        plugin,
                                                                                        viewer,
                                                                                        claim)
                                                                                .open();
                                                                    }
                                                                });
                                            });
                        }));
    }

    private void setupNavigationRow() {
        // Back button
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to claim settings")
                        .build();
        setItem(
                36,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ClaimSettingsGui(plugin, viewer, claim).open();
                        }));

        // Info about chunk shop
        ItemStack shopItem =
                new ItemBuilder(Material.EMERALD)
                        .name("<green>Need More Chunks?")
                        .lore(
                                "",
                                "<gray>Purchase chunks from",
                                "<gray>the chunk shop!",
                                "",
                                "<yellow>Use /claim shop")
                        .glow(true)
                        .build();
        setItem(
                40,
                new GuiItem(
                        shopItem,
                        e -> {
                            viewer.closeInventory();
                            new ChunkShopGui(plugin, viewer).open();
                        }));

        // Fill footer
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 36; i < 45; i++) {
            if (i != 36 && i != 40) {
                setItem(i, new GuiItem(filler));
            }
        }
    }

    private PlayerChunkPool getOrCreatePool() {
        PlayerChunkPool pool = plugin.getClaimManager().getPlayerChunkPool(viewer.getUniqueId());
        if (pool == null) {
            try {
                pool = plugin.getRepository().getOrCreatePlayerChunkPool(viewer.getUniqueId());
            } catch (SQLException e) {
                plugin.getLogger()
                        .severe(
                                "Failed to load chunk pool for "
                                        + viewer.getName()
                                        + ": "
                                        + e.getMessage());
                return new PlayerChunkPool(viewer.getUniqueId()); // Return empty pool
            }
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
}
