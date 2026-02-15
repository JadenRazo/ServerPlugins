package net.serverplugins.claim.gui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Nation;
import net.serverplugins.claim.models.PlayerClaimData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class NationListGui extends Gui {

    private final ServerClaim plugin;
    private final int page;
    private static final int ITEMS_PER_PAGE = 28;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy");

    public NationListGui(ServerClaim plugin, Player player) {
        this(plugin, player, 0);
    }

    public NationListGui(ServerClaim plugin, Player player, int page) {
        super(plugin, player, "<gold>Nations - Page " + (page + 1), 54);
        this.plugin = plugin;
        this.page = page;
    }

    @Override
    protected void initializeItems() {
        // Null safety check
        if (viewer == null) {
            plugin.getLogger().warning("NationListGui: Viewer is null");
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

        List<Nation> allNations = null;
        try {
            allNations = plugin.getNationManager().getAllNations();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get all nations: " + e.getMessage());
            allNations = new ArrayList<>();
        }

        // Sort by member count descending
        allNations.sort(Comparator.comparingInt(Nation::getMemberCount).reversed());

        int totalPages = (int) Math.ceil((double) allNations.size() / ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, allNations.size());

        int[] slots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        for (int i = start; i < end; i++) {
            int slotIndex = i - start;
            if (slotIndex >= slots.length) break;

            Nation nation = allNations.get(i);
            if (nation == null) continue; // Skip null nations
            int rank = i + 1;

            GuiItem item = createNationItem(nation, rank);
            if (item != null) {
                setItem(slots[slotIndex], item);
            }
        }

        // Stats (slot 4)
        ItemStack statsItem =
                new ItemBuilder(Material.COMPASS)
                        .name("<gold>Nation Statistics")
                        .lore(
                                "",
                                "<gray>Total Nations: <white>" + allNations.size(),
                                "<gray>Total Members: <white>"
                                        + allNations.stream()
                                                .mapToInt(Nation::getMemberCount)
                                                .sum(),
                                "<gray>Total Chunks: <white>"
                                        + allNations.stream()
                                                .mapToInt(Nation::getTotalChunks)
                                                .sum())
                        .build();
        setItem(4, new GuiItem(statsItem));

        // Previous page (slot 48)
        if (page > 0) {
            ItemStack prevItem =
                    new ItemBuilder(Material.ARROW).name("<yellow>Previous Page").build();
            setItem(
                    48,
                    new GuiItem(
                            prevItem,
                            e -> {
                                viewer.closeInventory();
                                new NationListGui(plugin, viewer, page - 1).open();
                            }));
        }

        // Page info (slot 49)
        ItemStack pageInfo =
                new ItemBuilder(Material.PAPER)
                        .name("<gold>Page " + (page + 1) + " of " + totalPages)
                        .build();
        setItem(49, new GuiItem(pageInfo));

        // Next page (slot 50)
        if (page < totalPages - 1) {
            ItemStack nextItem = new ItemBuilder(Material.ARROW).name("<yellow>Next Page").build();
            setItem(
                    50,
                    new GuiItem(
                            nextItem,
                            e -> {
                                viewer.closeInventory();
                                new NationListGui(plugin, viewer, page + 1).open();
                            }));
        }

        // Close (slot 53)
        ItemStack closeItem = new ItemBuilder(Material.BARRIER).name("<red>Close").build();
        setItem(53, new GuiItem(closeItem, e -> viewer.closeInventory()));
    }

    private GuiItem createNationItem(Nation nation, int rank) {
        if (nation == null) {
            plugin.getLogger().warning("Cannot create nation item for null nation");
            return null;
        }

        PlayerClaimData leaderData = null;
        try {
            leaderData = plugin.getRepository().getPlayerData(nation.getLeaderUuid());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get leader data for nation: " + e.getMessage());
        }
        String leaderName =
                leaderData != null && leaderData.getUsername() != null
                        ? leaderData.getUsername()
                        : "Unknown";

        Material material =
                switch (rank) {
                    case 1 -> Material.GOLDEN_HELMET;
                    case 2 -> Material.IRON_HELMET;
                    case 3 -> Material.CHAINMAIL_HELMET;
                    default -> Material.LEATHER_HELMET;
                };

        String rankPrefix = rank <= 3 ? "<gold>#" + rank + " " : "<gray>#" + rank + " ";

        String nationName = nation.getName() != null ? nation.getName() : "Unknown";
        String nationTag = nation.getColoredTag() != null ? nation.getColoredTag() : "<white>";
        String foundedDate =
                nation.getFoundedAt() != null
                        ? nation.getFoundedAt().atZone(ZoneId.systemDefault()).format(DATE_FORMAT)
                        : "Unknown";

        ItemBuilder builder =
                new ItemBuilder(material)
                        .name(rankPrefix + nationTag + " " + nationName)
                        .lore(
                                "",
                                "<gray>Leader: <white>" + leaderName,
                                "<gray>Members: <white>" + nation.getMemberCount(),
                                "<gray>Chunks: <white>" + nation.getTotalChunks(),
                                "<gray>Level: <yellow>" + nation.getLevel(),
                                "<gray>Founded: <white>" + foundedDate,
                                "",
                                nation.getDescription() != null
                                                && !nation.getDescription().isEmpty()
                                        ? "<dark_gray>\"" + nation.getDescription() + "\""
                                        : "");

        if (rank <= 3) {
            builder.glow(true);
        }

        return new GuiItem(builder.build());
    }
}
