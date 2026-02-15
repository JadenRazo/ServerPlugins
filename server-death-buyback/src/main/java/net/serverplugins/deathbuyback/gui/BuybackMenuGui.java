package net.serverplugins.deathbuyback.gui;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.deathbuyback.ServerDeathBuyback;
import net.serverplugins.deathbuyback.models.DeathInventory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class BuybackMenuGui extends Gui {

    private final ServerDeathBuyback plugin;
    private final List<DeathInventory> deaths;
    private int page = 0;
    private static final int ITEMS_PER_PAGE = 28; // 4 rows of 7 items

    public BuybackMenuGui(ServerDeathBuyback plugin, Player player) {
        super(plugin, 54, net.serverplugins.api.utils.TextUtil.parse("<dark_red>Death Buyback"));
        this.plugin = plugin;
        this.viewer = player;
        this.deaths = plugin.getDeathInventoryManager().getActiveInventories(player.getUniqueId());
    }

    @Override
    protected void initializeItems() {
        clearItems();

        // Fill border with glass
        ItemStack glass = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        fillBorder(new GuiItem(glass, false));

        // Header items
        setItem(4, createInfoItem());
        setItem(8, createCloseButton());

        // Check if player has any deaths
        if (deaths.isEmpty()) {
            setItem(22, createNoDeathsItem());
            return;
        }

        // Calculate pagination
        int totalPages = (int) Math.ceil((double) deaths.size() / ITEMS_PER_PAGE);
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, deaths.size());

        // Display death items (slots 10-16, 19-25, 28-34, 37-43)
        int[] slots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        for (int i = startIndex; i < endIndex; i++) {
            int slotIndex = i - startIndex;
            if (slotIndex >= slots.length) break;

            DeathInventory death = deaths.get(i);
            setItem(slots[slotIndex], createDeathItem(death, i + 1));
        }

        // Pagination buttons
        if (page > 0) {
            setItem(45, createPreviousPageButton());
        }

        if (page < totalPages - 1) {
            setItem(53, createNextPageButton());
        }

        // Page indicator
        setItem(49, createPageIndicator(page + 1, totalPages));
    }

    private GuiItem createInfoItem() {
        int maxSlots = plugin.getDeathInventoryManager().getMaxSlots(viewer);
        int usedSlots = deaths.size();

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Stored Deaths: <white>" + usedSlots + "/" + maxSlots);
        lore.add("");
        lore.add("<gray>Click on a death to preview");
        lore.add("<gray>and buy back your items.");
        lore.add("");
        lore.add(
                "<yellow>Deaths expire after "
                        + (plugin.getDeathBuybackConfig().getExpirationHours() / 24)
                        + " days");

        ItemStack item =
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name("<gradient:#e74c3c:#9b59b6>Death Buyback")
                        .lore(lore.toArray(new String[0]))
                        .build();

        return new GuiItem(item, false);
    }

    private GuiItem createCloseButton() {
        ItemStack item = new ItemBuilder(Material.BARRIER).name("<red>Close").build();

        return GuiItem.closeButton(item);
    }

    private GuiItem createNoDeathsItem() {
        ItemStack item =
                new ItemBuilder(Material.STRUCTURE_VOID)
                        .name("<gray>No Deaths Stored")
                        .lore(
                                "",
                                "<gray>You have no stored death inventories.",
                                "",
                                "<yellow>When you die, your inventory will",
                                "<yellow>be stored here for buyback.")
                        .build();

        return new GuiItem(item, false);
    }

    private GuiItem createDeathItem(DeathInventory death, int number) {
        String priceFormatted = plugin.getPricingManager().formatPrice(death.getBuybackPrice());

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Location: <white>" + death.getFormattedLocation());
        lore.add("<gray>Died: <white>" + death.getTimeAgo());
        lore.add("<gray>Items: <white>" + death.getItemCount());
        lore.add("<gray>XP Levels: <white>" + death.getXpLevels());
        lore.add("");
        lore.add("<gray>Expires in: <yellow>" + death.getTimeUntilExpiry());
        lore.add("");
        lore.add("<gold>Price: <yellow>" + priceFormatted);
        lore.add("");
        lore.add("<green>Click to preview and purchase");

        ItemStack item =
                new ItemBuilder(Material.SKELETON_SKULL)
                        .name("<red>Death #" + number)
                        .lore(lore.toArray(new String[0]))
                        .build();

        return new GuiItem(
                item,
                player -> {
                    new InventoryPreviewGui(plugin, player, death, this).open(player);
                });
    }

    private GuiItem createPreviousPageButton() {
        ItemStack item = new ItemBuilder(Material.ARROW).name("<yellow>Previous Page").build();

        return new GuiItem(
                item,
                player -> {
                    page--;
                    refresh();
                });
    }

    private GuiItem createNextPageButton() {
        ItemStack item = new ItemBuilder(Material.ARROW).name("<yellow>Next Page").build();

        return new GuiItem(
                item,
                player -> {
                    page++;
                    refresh();
                });
    }

    private GuiItem createPageIndicator(int current, int total) {
        ItemStack item =
                new ItemBuilder(Material.PAPER)
                        .name("<gray>Page " + current + "/" + total)
                        .amount(current)
                        .build();

        return new GuiItem(item, false);
    }
}
