package net.serverplugins.bounty.gui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

public class BountyDetailGui extends Gui {

    private final ServerBounty plugin;
    private final Bounty bounty;
    private final BountyBoardGui parentGui;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm").withZone(ZoneId.systemDefault());

    public BountyDetailGui(
            ServerBounty plugin, Player player, Bounty bounty, BountyBoardGui parentGui) {
        super(
                plugin,
                27,
                TextUtil.parse(
                        ColorScheme.ERROR + "<bold>" + bounty.getTargetName() + "'s Bounty"));
        this.plugin = plugin;
        this.viewer = player;
        this.bounty = bounty;
        this.parentGui = parentGui;

        // Load contributions if not already loaded
        if (bounty.getContributions() == null || bounty.getContributions().isEmpty()) {
            bounty.setContributions(plugin.getRepository().getContributions(bounty.getId()));
        }
    }

    @Override
    protected void initializeItems() {
        clearItems();

        ItemStack glass = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        GuiItem glassItem = new GuiItem(glass, false);

        // Row 1: Glass border with target head in center
        for (int i = 0; i < 9; i++) {
            setItem(i, glassItem);
        }
        setItem(4, createTargetHeadItem());

        // Row 2: Contributor items (slots 9-17)
        List<Contribution> contributions = bounty.getContributions();
        for (int i = 0; i < 9; i++) {
            if (i < contributions.size()) {
                setItem(9 + i, createContributorItem(contributions.get(i), i + 1));
            } else {
                setItem(9 + i, glassItem);
            }
        }

        // Row 3: Action buttons
        for (int i = 18; i < 27; i++) {
            setItem(i, glassItem);
        }
        setItem(19, createAddBountyButton());
        setItem(22, createBackButton());
    }

    private GuiItem createTargetHeadItem() {
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
                                    + "Total Bounty: "
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
                lore.add(TextUtil.parse(ColorScheme.WARNING + topContributor.getContributorName()));
                lore.add(
                        TextUtil.parse(
                                ColorScheme.INFO
                                        + "Amount: "
                                        + ColorScheme.SUCCESS
                                        + plugin.formatCurrency(topContributor.getAmount())));
            }

            lore.add(TextUtil.parse(""));
            lore.add(
                    TextUtil.parse(
                            ColorScheme.INFO
                                    + "Created: "
                                    + ColorScheme.HIGHLIGHT
                                    + DATE_FORMAT.format(bounty.getCreatedAt())));

            meta.lore(lore);
            head.setItemMeta(meta);
        }

        return new GuiItem(head, false);
    }

    private GuiItem createContributorItem(Contribution contribution, int rank) {
        Material material =
                switch (rank) {
                    case 1 -> Material.GOLD_INGOT;
                    case 2 -> Material.IRON_INGOT;
                    case 3 -> Material.COPPER_INGOT;
                    default -> Material.PAPER;
                };

        String rankColor =
                switch (rank) {
                    case 1 -> ColorScheme.EMPHASIS;
                    case 2 -> ColorScheme.INFO;
                    case 3 -> "<#b87333>";
                    default -> ColorScheme.HIGHLIGHT;
                };

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(
                ColorScheme.INFO
                        + "Amount: "
                        + ColorScheme.SUCCESS
                        + plugin.formatCurrency(contribution.getAmount()));
        if (contribution.getTaxPaid() > 0) {
            lore.add(
                    ColorScheme.INFO
                            + "Tax Paid: "
                            + ColorScheme.ERROR
                            + plugin.formatCurrency(contribution.getTaxPaid()));
        }
        lore.add("");
        lore.add(
                ColorScheme.INFO
                        + "Contributed: "
                        + ColorScheme.HIGHLIGHT
                        + DATE_FORMAT.format(contribution.getContributedAt()));

        ItemStack item =
                new ItemBuilder(material)
                        .name(rankColor + "#" + rank + " " + contribution.getContributorName())
                        .lore(lore.toArray(new String[0]))
                        .build();

        return new GuiItem(item, false);
    }

    private GuiItem createAddBountyButton() {
        double minAmount = plugin.getBountyConfig().getMinBountyAmount();

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Add to this bounty pool!");
        lore.add("");
        lore.add("<gray>Minimum: <yellow>" + plugin.formatCurrency(minAmount));
        double tax = plugin.getBountyConfig().getTaxPercentage();
        if (tax > 0) {
            lore.add("<gray>Tax: <red>" + tax + "%");
        }
        lore.add("");
        lore.add("<green>Click to add bounty");
        lore.add("<gray>Use: /bounty add " + bounty.getTargetName() + " <amount>");

        ItemStack item =
                new ItemBuilder(Material.EMERALD)
                        .name("<green><bold>Add Bounty")
                        .lore(lore.toArray(new String[0]))
                        .glow()
                        .build();

        return new GuiItem(
                item,
                player -> {
                    player.closeInventory();
                    TextUtil.send(
                            player,
                            "<gray>Use <yellow>/bounty add "
                                    + bounty.getTargetName()
                                    + " <amount> <gray>to add to this bounty.");
                });
    }

    private GuiItem createBackButton() {
        ItemStack item =
                new ItemBuilder(Material.ARROW)
                        .name("<yellow>Back")
                        .lore("", "<gray>Return to Bounty Board")
                        .build();

        return new GuiItem(
                item,
                player -> {
                    if (parentGui != null) {
                        parentGui.refreshBounties();
                        parentGui.open(player);
                    } else {
                        new BountyBoardGui(plugin, player).open(player);
                    }
                });
    }
}
