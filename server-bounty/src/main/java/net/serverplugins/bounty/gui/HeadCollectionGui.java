package net.serverplugins.bounty.gui;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.bounty.ServerBounty;
import net.serverplugins.bounty.models.TrophyHead;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class HeadCollectionGui extends Gui {

    private final ServerBounty plugin;
    private final BountyBoardGui parentGui;
    private List<TrophyHead> unclaimedHeads;
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.systemDefault());

    public HeadCollectionGui(ServerBounty plugin, Player player, BountyBoardGui parentGui) {
        super(plugin, 27, TextUtil.parse("<gold><bold>Trophy Heads"));
        this.plugin = plugin;
        this.viewer = player;
        this.parentGui = parentGui;
        this.unclaimedHeads = plugin.getHeadManager().getUnclaimedHeads(player.getUniqueId());
    }

    @Override
    protected void initializeItems() {
        clearItems();

        ItemStack glass = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        GuiItem glassItem = new GuiItem(glass, false);

        // Row 1: Border with title
        for (int i = 0; i < 9; i++) {
            setItem(i, glassItem);
        }
        setItem(4, createTitleItem());

        // Row 2: Head items (slots 9-17)
        if (unclaimedHeads.isEmpty()) {
            for (int i = 9; i < 18; i++) {
                setItem(i, glassItem);
            }
            setItem(13, createNoHeadsItem());
        } else {
            for (int i = 0; i < 9; i++) {
                if (i < unclaimedHeads.size()) {
                    setItem(9 + i, createHeadItem(unclaimedHeads.get(i)));
                } else {
                    setItem(9 + i, glassItem);
                }
            }
        }

        // Row 3: Action buttons
        for (int i = 18; i < 27; i++) {
            setItem(i, glassItem);
        }
        setItem(21, createClaimAllButton());
        setItem(22, createBackButton());
    }

    private GuiItem createTitleItem() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Unclaimed Heads: <gold>" + unclaimedHeads.size());
        lore.add("");
        lore.add("<gray>Trophy heads are awarded to the");
        lore.add("<gray>top contributor when a bounty");
        lore.add("<gray>is claimed.");
        lore.add("");
        lore.add("<yellow>Click on a head to claim it!");

        ItemStack item =
                new ItemBuilder(Material.SKELETON_SKULL)
                        .name("<gold><bold>Trophy Heads")
                        .lore(lore.toArray(new String[0]))
                        .build();

        return new GuiItem(item, false);
    }

    private GuiItem createNoHeadsItem() {
        ItemStack item =
                new ItemBuilder(Material.STRUCTURE_VOID)
                        .name("<gray>No Trophy Heads")
                        .lore(
                                "",
                                "<gray>You have no unclaimed trophy heads.",
                                "",
                                "<yellow>Be the top contributor on a",
                                "<yellow>bounty to earn trophy heads!")
                        .build();

        return new GuiItem(item, false);
    }

    private GuiItem createHeadItem(TrophyHead trophy) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(trophy.getVictimUuid()));
            meta.displayName(TextUtil.parse("<gold><bold>" + trophy.getVictimName() + "'s Head"));

            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(TextUtil.parse(""));
            lore.add(
                    TextUtil.parse(
                            "<gray>Bounty Value: <green>"
                                    + plugin.formatCurrency(trophy.getBountyAmount())));
            lore.add(
                    TextUtil.parse(
                            "<gray>Claimed: <yellow>" + DATE_FORMAT.format(trophy.getCreatedAt())));
            lore.add(TextUtil.parse(""));
            lore.add(TextUtil.parse("<gray>Expires: <red>" + formatTimeUntilExpiry(trophy)));
            lore.add(TextUtil.parse(""));
            lore.add(TextUtil.parse("<green>Click to claim!"));

            meta.lore(lore);
            head.setItemMeta(meta);
        }

        final int headId = trophy.getId();
        return new GuiItem(
                head,
                player -> {
                    claimSingleHead(player, headId);
                });
    }

    private void claimSingleHead(Player player, int headId) {
        // Find the head in our list
        TrophyHead trophy = null;
        for (TrophyHead t : unclaimedHeads) {
            if (t.getId() == headId) {
                trophy = t;
                break;
            }
        }

        if (trophy == null) {
            TextUtil.send(player, "<red>This head is no longer available.");
            refreshHeads();
            return;
        }

        // Check inventory space
        if (player.getInventory().firstEmpty() == -1) {
            TextUtil.send(player, "<red>Your inventory is full!");
            return;
        }

        // Create the head item and give to player
        ItemStack headItem = plugin.getHeadManager().createTrophyHead(trophy);
        player.getInventory().addItem(headItem);

        // Mark as claimed in database
        plugin.getRepository().markHeadClaimed(headId);

        TextUtil.send(
                player, "<green>You claimed <gold>" + trophy.getVictimName() + "'s Head<green>!");

        // Refresh the GUI
        refreshHeads();
    }

    private GuiItem createClaimAllButton() {
        if (unclaimedHeads.isEmpty()) {
            return new GuiItem(
                    new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build(), false);
        }

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gray>Heads to claim: <gold>" + unclaimedHeads.size());
        lore.add("");
        lore.add("<green>Click to claim all heads!");

        ItemStack item =
                new ItemBuilder(Material.CHEST)
                        .name("<green><bold>Claim All Heads")
                        .lore(lore.toArray(new String[0]))
                        .glow()
                        .build();

        return new GuiItem(
                item,
                player -> {
                    int claimed = plugin.getHeadManager().claimAllHeads(player);
                    if (claimed > 0) {
                        TextUtil.send(
                                player,
                                "<green>You claimed <gold>"
                                        + claimed
                                        + "<green> trophy head"
                                        + (claimed > 1 ? "s" : "")
                                        + "!");
                    } else {
                        TextUtil.send(
                                player, "<red>No heads were claimed. Your inventory may be full.");
                    }
                    refreshHeads();
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
                        parentGui.open(player);
                    } else {
                        new BountyBoardGui(plugin, player).open(player);
                    }
                });
    }

    private String formatTimeUntilExpiry(TrophyHead trophy) {
        Instant now = Instant.now();
        Instant expiresAt = trophy.getExpiresAt();

        if (now.isAfter(expiresAt)) {
            return "Expired";
        }

        Duration duration = Duration.between(now, expiresAt);
        long days = duration.toDays();
        long hours = duration.toHours() % 24;

        if (days > 0) {
            return days
                    + " day"
                    + (days != 1 ? "s" : "")
                    + ", "
                    + hours
                    + " hour"
                    + (hours != 1 ? "s" : "");
        } else if (hours > 0) {
            return hours + " hour" + (hours != 1 ? "s" : "");
        } else {
            long minutes = duration.toMinutes();
            return minutes + " minute" + (minutes != 1 ? "s" : "");
        }
    }

    private void refreshHeads() {
        unclaimedHeads = plugin.getHeadManager().getUnclaimedHeads(viewer.getUniqueId());
        refresh();
    }
}
