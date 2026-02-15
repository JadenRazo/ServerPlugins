package net.serverplugins.claim.gui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.ServerClaimStats;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * GUI for displaying server-wide claim statistics. Admin-only interface showing comprehensive
 * metrics about all claims. Requires serverclaim.admin.stats permission.
 */
public class ServerStatsGui extends Gui {

    private final ServerClaim plugin;
    private ServerClaimStats stats;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, HH:mm").withZone(ZoneId.systemDefault());

    public ServerStatsGui(ServerClaim plugin, Player viewer) {
        super(plugin, viewer, "Server Statistics", 54);
        this.plugin = plugin;
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
                                stats = plugin.getStatsManager().getServerStats();

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
        displayGlobalStats();
        displayTopPlayers();
        displayWorldDistribution();
        displayActivityMetrics();

        // Setup footer
        setupFooter();

        // Fill empty slots
        fillEmpty();
    }

    private void setupHeader() {
        String calculatedTime =
                stats.getCalculatedAt() != null
                        ? DATE_FORMAT.format(stats.getCalculatedAt())
                        : "Unknown";

        ItemStack titleItem =
                new ItemBuilder(Material.NETHER_STAR)
                        .name("<gold>Server Claim Statistics")
                        .lore(
                                "",
                                "<gray>Comprehensive server-wide",
                                "<gray>claim metrics and analytics.",
                                "",
                                "<dark_gray>Calculated: " + calculatedTime,
                                "<dark_gray>Stats cached for 5 minutes")
                        .glow(true)
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

    private void displayGlobalStats() {
        // Total Claims (slot 10)
        ItemStack claimsItem =
                new ItemBuilder(Material.GRASS_BLOCK)
                        .name("<green>Total Claims")
                        .lore(
                                "",
                                "<gray>Claims: <white>" + stats.getTotalClaims(),
                                "<gray>Players: <white>" + stats.getTotalPlayers(),
                                "<gray>Nations: <white>" + stats.getTotalNations())
                        .build();
        setItem(10, new GuiItem(claimsItem));

        // Total Chunks (slot 11)
        ItemStack chunksItem =
                new ItemBuilder(Material.EMERALD)
                        .name("<aqua>Total Chunks")
                        .lore(
                                "",
                                "<gray>Chunks Claimed: <white>" + stats.getTotalChunks(),
                                "",
                                stats.getTotalClaims() > 0
                                        ? "<gray>Average: <white>"
                                                + String.format(
                                                        "%.1f",
                                                        (double) stats.getTotalChunks()
                                                                / stats.getTotalClaims())
                                                + " per claim"
                                        : "")
                        .build();
        setItem(11, new GuiItem(chunksItem));

        // Economy Stats (slot 12)
        ItemStack economyItem =
                new ItemBuilder(Material.GOLD_BLOCK)
                        .name("<gold>Economy Metrics")
                        .lore(
                                "",
                                "<gray>Total Bank Money: <gold>$"
                                        + String.format("%.2f", stats.getTotalBankMoney()),
                                "<gray>Average Balance: <gold>$"
                                        + String.format("%.2f", stats.getAverageBankBalance()),
                                "",
                                "<gray>Upkeep Costs: <red>$"
                                        + String.format("%.2f", stats.getTotalUpkeepCosts()))
                        .build();
        setItem(12, new GuiItem(economyItem));

        // Risk Metrics (slot 14)
        ItemStack riskItem =
                new ItemBuilder(
                                stats.getClaimsAtRisk() > 0
                                        ? Material.RED_CONCRETE
                                        : Material.LIME_CONCRETE)
                        .name(
                                stats.getClaimsAtRisk() > 0
                                        ? "<red>Claims at Risk"
                                        : "<green>All Claims Healthy")
                        .lore(
                                "",
                                "<gray>At Risk: <red>" + stats.getClaimsAtRisk(),
                                "<gray>Grace Period: <yellow>" + stats.getClaimsInGracePeriod(),
                                "",
                                stats.getClaimsAtRisk() > 0
                                        ? "<yellow>Some claims need attention!"
                                        : "<green>All claims are well-funded")
                        .build();
        setItem(14, new GuiItem(riskItem));
    }

    private void displayTopPlayers() {
        // Top Owners section (slots 19-25)
        List<ServerClaimStats.TopOwner> topOwners = stats.getTopOwners();

        if (topOwners != null && !topOwners.isEmpty()) {
            ItemStack topOwnersTitle =
                    new ItemBuilder(Material.PLAYER_HEAD)
                            .name("<yellow>Top Land Owners")
                            .lore("", "<gray>Players with the most", "<gray>claimed chunks")
                            .glow(true)
                            .build();
            setItem(19, new GuiItem(topOwnersTitle));

            // Display top 5 owners
            int slot = 20;
            int rank = 1;
            for (ServerClaimStats.TopOwner owner : topOwners) {
                if (slot > 24 || rank > 5) break;

                Material medal =
                        switch (rank) {
                            case 1 -> Material.GOLD_BLOCK;
                            case 2 -> Material.IRON_BLOCK;
                            case 3 -> Material.COPPER_BLOCK;
                            default -> Material.STONE;
                        };

                ItemStack ownerItem =
                        new ItemBuilder(medal)
                                .name("<gold>#" + rank + " <white>" + owner.getPlayerName())
                                .lore(
                                        "",
                                        "<gray>Chunks: <white>" + owner.getChunkCount(),
                                        "<gray>Claims: <white>" + owner.getClaimCount())
                                .build();
                setItem(slot, new GuiItem(ownerItem));

                slot++;
                rank++;
            }
        }

        // Wealthiest Claims section (slots 28-34)
        List<ServerClaimStats.TopClaim> wealthiestClaims = stats.getWealthiestClaims();

        if (wealthiestClaims != null && !wealthiestClaims.isEmpty()) {
            ItemStack wealthTitle =
                    new ItemBuilder(Material.EMERALD_BLOCK)
                            .name("<green>Wealthiest Claims")
                            .lore("", "<gray>Claims with the highest", "<gray>bank balances")
                            .glow(true)
                            .build();
            setItem(28, new GuiItem(wealthTitle));

            // Display top 5 claims
            int slot = 29;
            int rank = 1;
            for (ServerClaimStats.TopClaim claim : wealthiestClaims) {
                if (slot > 33 || rank > 5) break;

                Material icon =
                        switch (rank) {
                            case 1 -> Material.DIAMOND;
                            case 2 -> Material.EMERALD;
                            case 3 -> Material.GOLD_INGOT;
                            default -> Material.IRON_INGOT;
                        };

                ItemStack claimItem =
                        new ItemBuilder(icon)
                                .name("<gold>#" + rank + " <white>" + claim.getClaimName())
                                .lore(
                                        "",
                                        "<gray>Owner: <white>" + claim.getOwnerName(),
                                        "<gray>Balance: <gold>$"
                                                + String.format("%.2f", claim.getBalance()))
                                .build();
                setItem(slot, new GuiItem(claimItem));

                slot++;
                rank++;
            }
        }
    }

    private void displayWorldDistribution() {
        // World distribution (slot 16)
        Map<String, Integer> claimsByWorld = stats.getClaimsByWorld();
        Map<String, Integer> chunksByWorld = stats.getChunksByWorld();

        if (claimsByWorld != null && !claimsByWorld.isEmpty()) {
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("<gray>Claims per world:");

            for (Map.Entry<String, Integer> entry : claimsByWorld.entrySet()) {
                String world = formatWorldName(entry.getKey());
                int claims = entry.getValue();
                int chunks =
                        chunksByWorld != null ? chunksByWorld.getOrDefault(entry.getKey(), 0) : 0;

                lore.add(
                        "<yellow>"
                                + world
                                + ": <white>"
                                + claims
                                + " claims, "
                                + chunks
                                + " chunks");
            }

            ItemStack worldsItem =
                    new ItemBuilder(Material.MAP)
                            .name("<aqua>World Distribution")
                            .lore(lore.toArray(new String[0]))
                            .build();
            setItem(16, new GuiItem(worldsItem));
        }
    }

    private void displayActivityMetrics() {
        // Activity in last 30 days (slot 34)
        ItemStack activityItem =
                new ItemBuilder(Material.CLOCK)
                        .name("<light_purple>Activity (Last 30 Days)")
                        .lore(
                                "",
                                "<gray>Chunks Claimed: <green>+"
                                        + stats.getChunksClaimedLastMonth(),
                                "<gray>Chunks Unclaimed: <red>-"
                                        + stats.getChunksUnclaimedLastMonth(),
                                "",
                                "<gray>Net Change: "
                                        + (stats.getChunksClaimedLastMonth()
                                                                - stats
                                                                        .getChunksUnclaimedLastMonth()
                                                        > 0
                                                ? "<green>+"
                                                : "<red>")
                                        + (stats.getChunksClaimedLastMonth()
                                                - stats.getChunksUnclaimedLastMonth()))
                        .build();
        setItem(34, new GuiItem(activityItem));
    }

    private String formatWorldName(String worldName) {
        if (worldName == null) return "Unknown";
        return switch (worldName) {
            case "world", "playworld" -> "Overworld";
            case "world_nether", "playworld_nether" -> "Nether";
            case "world_the_end", "playworld_the_end" -> "The End";
            default -> worldName;
        };
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
                            new ServerStatsGui(plugin, viewer).open();
                        }));

        // Fill remaining footer
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 46; i < 54; i++) {
            if (i != 49 && i != 45) {
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
