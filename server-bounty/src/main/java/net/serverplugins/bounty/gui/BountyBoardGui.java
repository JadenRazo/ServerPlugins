package net.serverplugins.bounty.gui;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.bounty.ServerBounty;
import net.serverplugins.bounty.models.Bounty;
import net.serverplugins.bounty.models.Contribution;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class BountyBoardGui extends Gui {

    private final ServerBounty plugin;
    private final List<Bounty> bounties;
    private int page = 0;
    private static final int ITEMS_PER_PAGE = 36;

    public BountyBoardGui(ServerBounty plugin, Player player) {
        super(
                plugin,
                54,
                TextUtil.parse("<gradient:#e74c3c:#c0392b><bold>Bounty Board</bold></gradient>"));
        this.plugin = plugin;
        this.viewer = player;
        this.bounties = plugin.getRepository().getActiveBounties();
    }

    @Override
    protected void initializeItems() {
        clearItems();

        ItemStack glass = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        GuiItem glassItem = new GuiItem(glass, false);

        // Row 1: Border with title in center
        for (int i = 0; i < 9; i++) {
            setItem(i, glassItem);
        }
        setItem(4, createTitleItem());

        // Row 6: Navigation bar
        for (int i = 45; i < 54; i++) {
            setItem(i, glassItem);
        }

        // Navigation buttons
        setItem(45, createMyHeadsButton());
        setItem(47, createPreviousPageButton());
        setItem(49, createPageIndicator());
        setItem(51, createNextPageButton());
        setItem(53, createTopListButton());

        // Populate bounty items
        if (bounties.isEmpty()) {
            setItem(31, createNoBountiesItem());
            return;
        }

        int totalPages = (int) Math.ceil((double) bounties.size() / ITEMS_PER_PAGE);
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, bounties.size());

        // Slots for bounty items (rows 2-5)
        int[] slots = new int[36];
        int index = 0;
        for (int row = 1; row < 5; row++) {
            for (int col = 0; col < 9; col++) {
                slots[index++] = row * 9 + col;
            }
        }

        for (int i = startIndex; i < endIndex; i++) {
            int slotIndex = i - startIndex;
            if (slotIndex >= slots.length) break;

            Bounty bounty = bounties.get(i);
            loadBountyContributions(bounty);
            setItem(slots[slotIndex], createBountyItem(bounty));
        }
    }

    private void loadBountyContributions(Bounty bounty) {
        if (bounty.getContributions() == null || bounty.getContributions().isEmpty()) {
            List<Contribution> contributions =
                    plugin.getRepository().getContributions(bounty.getId());
            bounty.setContributions(contributions);
        }
    }

    private GuiItem createTitleItem() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ColorScheme.INFO + "Active bounties: " + ColorScheme.HIGHLIGHT + bounties.size());
        lore.add("");
        lore.add(ColorScheme.INFO + "Click on a bounty to view details");
        lore.add(ColorScheme.INFO + "or add to the bounty pool.");

        ItemStack item =
                new ItemBuilder(Material.SKELETON_SKULL)
                        .name("<gradient:#e74c3c:#c0392b><bold>Bounty Board</bold></gradient>")
                        .lore(lore.toArray(new String[0]))
                        .build();

        return new GuiItem(item, false);
    }

    private GuiItem createNoBountiesItem() {
        ItemStack item =
                new ItemBuilder(Material.STRUCTURE_VOID)
                        .name(ColorScheme.INFO + "No Active Bounties")
                        .lore(
                                "",
                                ColorScheme.INFO + "There are no active bounties",
                                ColorScheme.INFO + "at this time.",
                                "",
                                ColorScheme.WARNING + "Use /bounty set <player> <amount>",
                                ColorScheme.WARNING + "to place a bounty!")
                        .build();

        return new GuiItem(item, false);
    }

    private GuiItem createBountyItem(Bounty bounty) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(bounty.getTargetUuid()));
            meta.displayName(TextUtil.parse(ColorScheme.ERROR + "<bold>" + bounty.getTargetName()));

            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse(""));
            lore.add(
                    TextUtil.parse(
                            ColorScheme.INFO
                                    + "Bounty: "
                                    + ColorScheme.SUCCESS
                                    + plugin.formatCurrency(bounty.getTotalAmount())));
            lore.add(
                    TextUtil.parse(
                            ColorScheme.INFO
                                    + "Contributors: "
                                    + ColorScheme.HIGHLIGHT
                                    + bounty.getContributorCount()));
            lore.add(TextUtil.parse(""));

            Contribution topContributor = bounty.getTopContributor();
            if (topContributor != null) {
                lore.add(TextUtil.parse(ColorScheme.INFO + "Top Contributor:"));
                lore.add(
                        TextUtil.parse(
                                ColorScheme.WARNING
                                        + topContributor.getContributorName()
                                        + ": "
                                        + ColorScheme.SUCCESS
                                        + plugin.formatCurrency(topContributor.getAmount())));
            } else {
                lore.add(
                        TextUtil.parse(
                                ColorScheme.INFO
                                        + "Top Contributor: "
                                        + ColorScheme.HIGHLIGHT
                                        + "None"));
            }

            lore.add(TextUtil.parse(""));
            lore.add(TextUtil.parse(ColorScheme.SUCCESS + "Click to view details"));

            meta.lore(lore);
            head.setItemMeta(meta);
        }

        return new GuiItem(
                head,
                player -> {
                    new BountyDetailGui(plugin, player, bounty, this).open(player);
                });
    }

    private GuiItem createMyHeadsButton() {
        int unclaimedCount = plugin.getHeadManager().getUnclaimedCount(viewer.getUniqueId());

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ColorScheme.INFO + "Unclaimed Heads: " + ColorScheme.EMPHASIS + unclaimedCount);
        lore.add("");
        lore.add(ColorScheme.WARNING + "Click to view your trophy heads");

        ItemStack item =
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name(ColorScheme.EMPHASIS + "<bold>My Trophy Heads")
                        .lore(lore.toArray(new String[0]))
                        .glow(unclaimedCount > 0)
                        .build();

        return new GuiItem(
                item,
                player -> {
                    new HeadCollectionGui(plugin, player, this).open(player);
                });
    }

    private GuiItem createPreviousPageButton() {
        if (page <= 0) {
            return new GuiItem(
                    new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build(), false);
        }

        ItemStack item =
                new ItemBuilder(Material.ARROW)
                        .name(ColorScheme.WARNING + "Previous Page")
                        .lore("", ColorScheme.INFO + "Click to go back")
                        .build();

        return new GuiItem(
                item,
                player -> {
                    page--;
                    refresh();
                });
    }

    private GuiItem createNextPageButton() {
        int totalPages = Math.max(1, (int) Math.ceil((double) bounties.size() / ITEMS_PER_PAGE));
        if (page >= totalPages - 1) {
            return new GuiItem(
                    new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build(), false);
        }

        ItemStack item =
                new ItemBuilder(Material.ARROW)
                        .name(ColorScheme.WARNING + "Next Page")
                        .lore("", ColorScheme.INFO + "Click to go forward")
                        .build();

        return new GuiItem(
                item,
                player -> {
                    page++;
                    refresh();
                });
    }

    private GuiItem createPageIndicator() {
        int totalPages = Math.max(1, (int) Math.ceil((double) bounties.size() / ITEMS_PER_PAGE));
        int currentPage = page + 1;

        ItemStack item =
                new ItemBuilder(Material.PAPER)
                        .name(ColorScheme.INFO + "Page " + currentPage + "/" + totalPages)
                        .amount(Math.min(currentPage, 64))
                        .build();

        return new GuiItem(item, false);
    }

    private GuiItem createTopListButton() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        List<Bounty> topBounties = plugin.getRepository().getTopBounties(5);
        if (topBounties.isEmpty()) {
            lore.add(ColorScheme.INFO + "No bounties to display");
        } else {
            int rank = 1;
            for (Bounty b : topBounties) {
                lore.add(
                        ColorScheme.WARNING
                                + rank
                                + ". "
                                + ColorScheme.HIGHLIGHT
                                + b.getTargetName()
                                + " "
                                + ColorScheme.INFO
                                + "- "
                                + ColorScheme.SUCCESS
                                + plugin.formatCurrency(b.getTotalAmount()));
                rank++;
            }
        }
        lore.add("");
        lore.add(ColorScheme.INFO + "Top 5 bounties by value");

        ItemStack item =
                new ItemBuilder(Material.GOLD_INGOT)
                        .name(ColorScheme.EMPHASIS + "<bold>Top Bounties")
                        .lore(lore.toArray(new String[0]))
                        .build();

        return new GuiItem(item, false);
    }

    public void refreshBounties() {
        bounties.clear();
        bounties.addAll(plugin.getRepository().getActiveBounties());
        refresh();
    }
}
