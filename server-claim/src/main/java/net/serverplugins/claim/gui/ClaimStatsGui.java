package net.serverplugins.claim.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.PlayerClaimStats;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * GUI for displaying player claim statistics. Shows comprehensive metrics about a player's claiming
 * activity.
 */
public class ClaimStatsGui extends Gui {

    private final ServerClaim plugin;
    private final UUID targetPlayerUuid;
    private PlayerClaimStats stats;

    public ClaimStatsGui(ServerClaim plugin, Player viewer, UUID targetPlayerUuid) {
        super(plugin, viewer, "Claim Statistics", 54);
        this.plugin = plugin;
        this.targetPlayerUuid = targetPlayerUuid;
    }

    @Override
    protected void initializeItems() {
        // Load stats asynchronously if needed
        if (stats == null) {
            plugin.getServer()
                    .getScheduler()
                    .runTaskAsynchronously(
                            plugin,
                            () -> {
                                stats = plugin.getStatsManager().getPlayerStats(targetPlayerUuid);

                                // Update GUI on main thread
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    if (viewer != null && viewer.isOnline()) {
                                                        refresh();
                                                    }
                                                });
                            });

            // Show loading message
            ItemStack loading =
                    new ItemBuilder(Material.HOPPER)
                            .name("<yellow>Loading Statistics...")
                            .lore("<gray>Please wait...")
                            .build();
            setItem(22, new GuiItem(loading));
            return;
        }

        // Setup header
        setupHeader();

        // Display stats sections
        displayOwnershipStats();
        displayClaimDetails();
        displayNationInfo();

        // Setup footer
        setupFooter();

        // Fill empty slots
        fillEmpty();
    }

    private void setupHeader() {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetPlayerUuid);
        String playerName = target.getName() != null ? target.getName() : stats.getPlayerName();

        ItemStack titleItem =
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name("<gold>" + playerName + "'s Statistics")
                        .lore(
                                "",
                                "<gray>Comprehensive claim statistics",
                                "<gray>for this player.",
                                "",
                                stats.getTotalClaims() > 0
                                        ? "<green>" + stats.getTotalClaims() + " claim(s) owned"
                                        : "<yellow>No claims yet")
                        .glow(stats.getTotalClaims() > 0)
                        .build();
        setItem(4, new GuiItem(titleItem));

        // Fill header with glass
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) {
            if (i != 4) {
                setItem(i, new GuiItem(filler));
            }
        }
    }

    private void displayOwnershipStats() {
        // Total Claims (slot 10)
        ItemStack claimsItem =
                new ItemBuilder(Material.GRASS_BLOCK)
                        .name("<green>Total Claims")
                        .lore(
                                "",
                                "<gray>Claims Owned: <white>" + stats.getTotalClaims(),
                                "",
                                stats.getTotalClaims() > 0
                                        ? "<gray>Oldest: <white>"
                                                + (stats.getOldestClaimName() != null
                                                        ? stats.getOldestClaimName()
                                                        : "N/A")
                                        : "<yellow>No claims created yet",
                                stats.getTotalClaims() > 0
                                        ? "<gray>Newest: <white>"
                                                + (stats.getNewestClaimName() != null
                                                        ? stats.getNewestClaimName()
                                                        : "N/A")
                                        : "")
                        .build();
        setItem(10, new GuiItem(claimsItem));

        // Total Chunks (slot 12)
        ItemStack chunksItem =
                new ItemBuilder(Material.EMERALD)
                        .name("<aqua>Total Chunks")
                        .lore(
                                "",
                                "<gray>Chunks Claimed: <white>" + stats.getTotalChunks(),
                                "<gray>Purchased: <white>" + stats.getTotalPurchasedChunks(),
                                "",
                                stats.getTotalClaims() > 0
                                        ? "<gray>Average Size: <white>"
                                                + String.format("%.1f", stats.getAverageClaimSize())
                                                + " chunks/claim"
                                        : "")
                        .build();
        setItem(12, new GuiItem(chunksItem));

        // Total Bank Money (slot 14)
        ItemStack bankItem =
                new ItemBuilder(Material.GOLD_INGOT)
                        .name("<gold>Total Bank Balance")
                        .lore(
                                "",
                                "<gray>All Claims Combined: <gold>$"
                                        + String.format("%.2f", stats.getTotalBankMoney()),
                                "",
                                stats.getTotalClaims() > 0
                                        ? "<gray>Average per claim: <gold>$"
                                                + String.format(
                                                        "%.2f",
                                                        stats.getTotalBankMoney()
                                                                / stats.getTotalClaims())
                                        : "")
                        .build();
        setItem(14, new GuiItem(bankItem));
    }

    private void displayClaimDetails() {
        // Largest Claim (slot 20)
        if (stats.getLargestClaimName() != null) {
            ItemStack largestItem =
                    new ItemBuilder(Material.DIAMOND)
                            .name("<blue>Largest Claim")
                            .lore(
                                    "",
                                    "<gray>Name: <white>" + stats.getLargestClaimName(),
                                    "<gray>Size: <white>"
                                            + stats.getLargestClaimChunks()
                                            + " chunks",
                                    "<gray>Claim ID: <white>" + stats.getLargestClaimId(),
                                    "",
                                    "<yellow>Click to view this claim")
                            .glow(true)
                            .build();

            setItem(
                    20,
                    new GuiItem(
                            largestItem,
                            e -> {
                                // Open claim settings for this claim
                                var claim =
                                        plugin.getClaimManager()
                                                .getClaimById(stats.getLargestClaimId());
                                if (claim != null) {
                                    viewer.closeInventory();
                                    new ClaimSettingsGui(plugin, viewer, claim).open();
                                }
                            }));
        } else {
            ItemStack noLargest =
                    new ItemBuilder(Material.BARRIER)
                            .name("<gray>No Claims")
                            .lore("", "<yellow>No claims to display")
                            .build();
            setItem(20, new GuiItem(noLargest));
        }

        // Most Valuable Claim (slot 22)
        if (stats.getMostValuableClaimName() != null && stats.getMostValuableClaimBalance() > 0) {
            ItemStack valuableItem =
                    new ItemBuilder(Material.EMERALD_BLOCK)
                            .name("<green>Wealthiest Claim")
                            .lore(
                                    "",
                                    "<gray>Name: <white>" + stats.getMostValuableClaimName(),
                                    "<gray>Balance: <gold>$"
                                            + String.format(
                                                    "%.2f", stats.getMostValuableClaimBalance()),
                                    "<gray>Claim ID: <white>" + stats.getMostValuableClaimId(),
                                    "",
                                    "<yellow>Click to view this claim")
                            .glow(true)
                            .build();

            setItem(
                    22,
                    new GuiItem(
                            valuableItem,
                            e -> {
                                // Open claim settings for this claim
                                var claim =
                                        plugin.getClaimManager()
                                                .getClaimById(stats.getMostValuableClaimId());
                                if (claim != null) {
                                    viewer.closeInventory();
                                    new ClaimSettingsGui(plugin, viewer, claim).open();
                                }
                            }));
        } else {
            ItemStack noValuable =
                    new ItemBuilder(Material.BARRIER)
                            .name("<gray>No Wealthy Claims")
                            .lore("", "<yellow>No claims with bank balance")
                            .build();
            setItem(22, new GuiItem(noValuable));
        }
    }

    private void displayNationInfo() {
        // Nation Membership (slot 24)
        List<String> nationNames = stats.getNationNames();

        if (nationNames != null && !nationNames.isEmpty()) {
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("<gray>Nations: <white>" + stats.getNationsJoined());
            lore.add("");

            for (String nationName : nationNames) {
                lore.add("<yellow>• <white>" + nationName);
            }

            ItemStack nationsItem =
                    new ItemBuilder(Material.WHITE_BANNER)
                            .name("<light_purple>Nation Membership")
                            .lore(lore.toArray(new String[0]))
                            .build();
            setItem(24, new GuiItem(nationsItem));
        } else {
            ItemStack noNations =
                    new ItemBuilder(Material.WHITE_BANNER)
                            .name("<gray>No Nations")
                            .lore("", "<gray>This player is not part", "<gray>of any nations.")
                            .build();
            setItem(24, new GuiItem(noNations));
        }
    }

    private void setupFooter() {
        // Back button (slot 45)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<yellow>Back")
                        .lore("<gray>Close statistics")
                        .build();
        setItem(45, new GuiItem(backItem, e -> viewer.closeInventory()));

        // Refresh button (slot 49)
        ItemStack refreshItem =
                new ItemBuilder(Material.COMPASS)
                        .name("<aqua>Refresh Statistics")
                        .lore(
                                "",
                                "<gray>Click to reload statistics",
                                "<gray>from the database.",
                                "",
                                "<dark_gray>Stats are cached for 5 minutes")
                        .build();
        setItem(
                49,
                new GuiItem(
                        refreshItem,
                        e -> {
                            stats = null; // Clear cache
                            plugin.getStatsManager().refreshServerStats(); // Force refresh
                            viewer.closeInventory();
                            new ClaimStatsGui(plugin, viewer, targetPlayerUuid).open();
                        }));

        // View Own Claims (if viewing self) (slot 53)
        if (viewer.getUniqueId().equals(targetPlayerUuid)) {
            ItemStack myClaimsItem =
                    new ItemBuilder(Material.CHEST)
                            .name("<green>View My Claims")
                            .lore("<gray>Open claims menu")
                            .build();
            setItem(
                    53,
                    new GuiItem(
                            myClaimsItem,
                            e -> {
                                viewer.closeInventory();
                                new MyProfilesGui(plugin, viewer).open();
                            }));
        }

        // Fill remaining footer
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 46; i < 54; i++) {
            if (i != 49 && i != 45 && i != 53) {
                setItem(i, new GuiItem(filler));
            }
        }
    }

    private void fillEmpty() {
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 9; i < 45; i++) {
            if (getItem(i) == null) {
                setItem(i, new GuiItem(filler));
            }
        }
    }
}
