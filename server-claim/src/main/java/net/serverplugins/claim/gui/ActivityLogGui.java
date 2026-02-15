package net.serverplugins.claim.gui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.repository.AuditLogRepository.ActivityType;
import net.serverplugins.claim.repository.AuditLogRepository.AuditLogEntry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * GUI for viewing claim activity logs. 54-slot paginated interface with color-coded entries. GREEN
 * = positive actions (purchases, deposits) YELLOW = neutral actions (settings changes, access) RED
 * = negative actions (withdrawals, removals)
 */
public class ActivityLogGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;
    private final int currentPage;
    private final ActivityType filterType;

    private static final int LOGS_PER_PAGE = 36; // 4 rows of 9
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, HH:mm").withZone(ZoneId.systemDefault());

    public ActivityLogGui(ServerClaim plugin, Player player, Claim claim, int page) {
        this(plugin, player, claim, page, null);
    }

    public ActivityLogGui(
            ServerClaim plugin, Player player, Claim claim, int page, ActivityType filterType) {
        super(plugin, player, "Activity Log - " + claim.getName(), 54);
        this.plugin = plugin;
        this.claim = claim;
        this.currentPage = page;
        this.filterType = filterType;
    }

    @Override
    protected void initializeItems() {
        if (claim == null) {
            plugin.getLogger().warning("ActivityLogGui: Claim is null");
            return;
        }

        // Setup header
        setupHeader();

        // Load and display activity logs
        displayActivityLogs();

        // Setup navigation footer
        setupFooter();
    }

    private void setupHeader() {
        // Title item
        ItemStack titleItem =
                new ItemBuilder(Material.WRITTEN_BOOK)
                        .name("<gold>Activity Log")
                        .lore(
                                "",
                                "<gray>Claim: <white>" + claim.getName(),
                                "<gray>Claim ID: <white>" + claim.getId(),
                                "",
                                filterType != null
                                        ? "<yellow>Filter: <white>" + formatActivityType(filterType)
                                        : "<gray>Showing all activities",
                                "",
                                "<gray>Page " + (currentPage + 1))
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

    private void displayActivityLogs() {
        // Fetch logs from database with pagination
        int offset = currentPage * LOGS_PER_PAGE;
        List<AuditLogEntry> logs =
                plugin.getAuditLogRepository()
                        .getLogsFiltered(claim.getId(), filterType, LOGS_PER_PAGE, offset);

        if (logs.isEmpty()) {
            // No logs message
            ItemStack noLogs =
                    new ItemBuilder(Material.BARRIER)
                            .name("<yellow>No Activity Yet")
                            .lore(
                                    "",
                                    currentPage == 0
                                            ? "<gray>This claim has no recorded activity."
                                            : "<gray>No more activity on this page.",
                                    "",
                                    "<gray>Activity is tracked for:",
                                    "<white>• Chunk purchases/unclaims",
                                    "<white>• Bank deposits/withdrawals",
                                    "<white>• Member changes",
                                    "<white>• Settings modifications",
                                    "<white>• Claim access events")
                            .build();
            setItem(22, new GuiItem(noLogs));
            return;
        }

        // Display logs in slots 9-44 (36 slots)
        int slot = 9;
        for (AuditLogEntry log : logs) {
            if (slot >= 45) break; // Safety check

            ItemStack logItem = createLogItem(log);
            setItem(slot, new GuiItem(logItem));
            slot++;
        }

        // Fill remaining slots with empty glass
        ItemStack empty = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = slot; i < 45; i++) {
            setItem(i, new GuiItem(empty));
        }
    }

    private ItemStack createLogItem(AuditLogEntry log) {
        ActivityType activity = log.activityType();

        // Determine color and icon based on activity type
        Material icon;
        String colorTag;

        switch (activity) {
                // Positive actions (GREEN)
            case CHUNK_PURCHASE -> {
                icon = Material.LIME_DYE;
                colorTag = "<green>";
            }
            case BANK_DEPOSIT -> {
                icon = Material.EMERALD;
                colorTag = "<green>";
            }
            case MEMBER_ADDED -> {
                icon = Material.PLAYER_HEAD;
                colorTag = "<green>";
            }
            case GROUP_CREATED -> {
                icon = Material.WRITABLE_BOOK;
                colorTag = "<green>";
            }

                // Negative actions (RED)
            case CHUNK_UNCLAIM -> {
                icon = Material.RED_DYE;
                colorTag = "<red>";
            }
            case BANK_WITHDRAW -> {
                icon = Material.GOLD_NUGGET;
                colorTag = "<red>";
            }

                // Neutral actions (YELLOW)
            case CLAIM_ACCESS -> {
                icon = Material.OAK_DOOR;
                colorTag = "<yellow>";
            }
            case GROUP_MODIFIED -> {
                icon = Material.WRITABLE_BOOK;
                colorTag = "<yellow>";
            }
            case WARP_TELEPORT -> {
                icon = Material.ENDER_PEARL;
                colorTag = "<yellow>";
            }

            default -> {
                icon = Material.PAPER;
                colorTag = "<gray>";
            }
        }

        // Get player name
        String playerName = "Unknown";
        if (log.playerUuid() != null) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(log.playerUuid());
            playerName = player.getName() != null ? player.getName() : "Unknown";
        }

        // Format timestamp
        String timeStr = DATE_FORMAT.format(log.timestamp());

        // Build lore
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Player: <white>" + playerName);
        lore.add("<gray>Time: <white>" + timeStr);
        lore.add("");

        // Add details
        if (log.details() != null && !log.details().isEmpty()) {
            lore.add("<gray>Details:");
            lore.add("<white>" + log.details());
        }

        // Add amount if present
        if (log.amount() != null) {
            lore.add("");
            lore.add("<gray>Amount: <gold>$" + String.format("%.2f", log.amount()));
        }

        // Add change information if present
        if (log.oldValue() != null && log.newValue() != null) {
            lore.add("");
            lore.add("<gray>Changed from: <white>" + log.oldValue());
            lore.add("<gray>Changed to: <white>" + log.newValue());
        }

        return new ItemBuilder(icon)
                .name(colorTag + formatActivityType(activity))
                .lore(lore.toArray(new String[0]))
                .build();
    }

    private String formatActivityType(ActivityType type) {
        return switch (type) {
            case CLAIM_ACCESS -> "Claim Access";
            case CHUNK_PURCHASE -> "Chunk Purchased";
            case CHUNK_UNCLAIM -> "Chunk Unclaimed";
            case MEMBER_ADDED -> "Member Added";
            case GROUP_CREATED -> "Group Created";
            case GROUP_MODIFIED -> "Group Modified";
            case BANK_DEPOSIT -> "Bank Deposit";
            case BANK_WITHDRAW -> "Bank Withdrawal";
            case WARP_TELEPORT -> "Warp Teleport";
        };
    }

    private void setupFooter() {
        // Previous page button (slot 45)
        if (currentPage > 0) {
            ItemStack prevPage =
                    new ItemBuilder(Material.ARROW)
                            .name("<yellow>Previous Page")
                            .lore("<gray>Go to page " + currentPage)
                            .build();
            setItem(
                    45,
                    new GuiItem(
                            prevPage,
                            e -> {
                                viewer.closeInventory();
                                new ActivityLogGui(
                                                plugin, viewer, claim, currentPage - 1, filterType)
                                        .open();
                            }));
        } else {
            setItem(
                    45,
                    new GuiItem(
                            new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build()));
        }

        // Filter button (slot 49)
        ItemStack filterItem =
                new ItemBuilder(Material.HOPPER)
                        .name("<aqua>Filter Activities")
                        .lore(
                                "",
                                filterType != null
                                        ? "<gray>Current: <white>" + formatActivityType(filterType)
                                        : "<gray>Showing all activities",
                                "",
                                "<yellow>Click to change filter",
                                "",
                                "<gray>Available filters:",
                                "<white>• All Activities",
                                "<white>• Chunk Operations",
                                "<white>• Bank Transactions",
                                "<white>• Member Changes")
                        .glow(filterType != null)
                        .build();
        setItem(
                49,
                new GuiItem(
                        filterItem,
                        e -> {
                            // Cycle through filters: null -> CHUNK_PURCHASE -> BANK_DEPOSIT ->
                            // MEMBER_ADDED -> null
                            ActivityType nextFilter = null;
                            if (filterType == null) {
                                nextFilter = ActivityType.CHUNK_PURCHASE;
                            } else if (filterType == ActivityType.CHUNK_PURCHASE) {
                                nextFilter = ActivityType.BANK_DEPOSIT;
                            } else if (filterType == ActivityType.BANK_DEPOSIT) {
                                nextFilter = ActivityType.MEMBER_ADDED;
                            }

                            viewer.closeInventory();
                            new ActivityLogGui(plugin, viewer, claim, 0, nextFilter).open();
                        }));

        // Next page button (slot 53)
        // Check if there are more logs
        int offset = (currentPage + 1) * LOGS_PER_PAGE;
        int nextPageCount = plugin.getAuditLogRepository().getLogCount(claim.getId(), filterType);

        if (offset < nextPageCount) {
            ItemStack nextPage =
                    new ItemBuilder(Material.ARROW)
                            .name("<yellow>Next Page")
                            .lore("<gray>Go to page " + (currentPage + 2))
                            .build();
            setItem(
                    53,
                    new GuiItem(
                            nextPage,
                            e -> {
                                viewer.closeInventory();
                                new ActivityLogGui(
                                                plugin, viewer, claim, currentPage + 1, filterType)
                                        .open();
                            }));
        } else {
            setItem(
                    53,
                    new GuiItem(
                            new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build()));
        }

        // Back button (slot 48)
        ItemStack backItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Back")
                        .lore("<gray>Return to claim settings")
                        .build();
        setItem(
                48,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ClaimSettingsGui(plugin, viewer, claim).open();
                        }));

        // Fill remaining footer slots
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 46; i < 54; i++) {
            if (i != 48 && i != 49 && i != 45 && i != 53) {
                setItem(i, new GuiItem(filler));
            }
        }
    }
}
