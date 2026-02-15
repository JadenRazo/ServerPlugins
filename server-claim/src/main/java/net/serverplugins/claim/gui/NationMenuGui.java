package net.serverplugins.claim.gui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.Nation;
import net.serverplugins.claim.models.NationMember;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class NationMenuGui extends Gui {

    private final ServerClaim plugin;
    private final Nation nation;
    private final Claim playerClaim;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy");

    public NationMenuGui(ServerClaim plugin, Player player, Nation nation, Claim playerClaim) {
        super(
                plugin,
                player,
                (nation != null
                        ? nation.getColoredTag() + " <white>" + nation.getName()
                        : "Nation Menu"),
                54);
        this.plugin = plugin;

        // Validate nation exists
        if (!GuiValidator.validateNation(plugin, player, nation, "NationMenuGui")) {
            this.nation = null;
            this.playerClaim = null;
            return;
        }

        // Validate player claim if provided
        if (playerClaim != null
                && !GuiValidator.validateClaim(plugin, player, playerClaim, "NationMenuGui")) {
            this.nation = nation;
            this.playerClaim = null;
            return;
        }

        this.nation = nation;
        this.playerClaim = playerClaim;
    }

    @Override
    protected void initializeItems() {
        // Safety check
        if (nation == null) {
            return;
        }

        // Fill background
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            setItem(i, new GuiItem(filler));
        }

        NationMember membership =
                playerClaim != null
                        ? plugin.getNationManager().getMember(playerClaim.getId())
                        : null;
        boolean isLeader = nation.isLeader(viewer.getUniqueId());
        boolean isOfficer = membership != null && membership.isOfficer();

        // Nation info (slot 4)
        ItemStack infoItem =
                new ItemBuilder(Material.GOLDEN_HELMET)
                        .name(nation.getColoredTag() + " " + nation.getName())
                        .lore(
                                "",
                                "<gray>Leader: <white>" + getLeaderName(),
                                "<gray>Members: <white>" + nation.getMemberCount(),
                                "<gray>Total Chunks: <white>" + nation.getTotalChunks(),
                                "<gray>Level: <yellow>" + nation.getLevel(),
                                "<gray>Founded: <white>"
                                        + nation.getFoundedAt()
                                                .atZone(ZoneId.systemDefault())
                                                .format(DATE_FORMAT),
                                "",
                                nation.getDescription() != null
                                        ? "<dark_gray>\"" + nation.getDescription() + "\""
                                        : "")
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(infoItem));

        // Members (slot 20)
        ItemStack membersItem =
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name("<green>Members")
                        .lore(
                                "",
                                "<gray>View and manage nation",
                                "<gray>members and their roles",
                                "",
                                "<gray>Members: <white>" + nation.getMemberCount(),
                                "",
                                "<yellow>Click to view members")
                        .build();
        setItem(
                20,
                new GuiItem(
                        membersItem,
                        e -> {
                            viewer.closeInventory();
                            new NationMembersGui(plugin, viewer, nation, playerClaim).open();
                        }));

        // Nation Bank (slot 22)
        double balance = plugin.getNationManager().getNationBalance(nation.getId());
        ItemStack bankItem =
                new ItemBuilder(Material.GOLD_INGOT)
                        .name("<gold>Nation Treasury")
                        .lore(
                                "",
                                "<gray>Balance: <gold>$" + String.format("%.2f", balance),
                                "",
                                "<gray>Deposit funds to the",
                                "<gray>nation treasury",
                                "",
                                "<yellow>Click to manage")
                        .build();
        setItem(
                22,
                new GuiItem(
                        bankItem,
                        e -> {
                            viewer.closeInventory();
                            new NationBankGui(plugin, viewer, nation, playerClaim).open();
                        }));

        // Relations (slot 24)
        ItemStack relationsItem =
                new ItemBuilder(Material.SHIELD)
                        .name("<aqua>Diplomacy")
                        .lore(
                                "",
                                "<gray>Manage relationships with",
                                "<gray>other nations",
                                "",
                                "<green>Allies <gray>| <yellow>Neutral <gray>| <red>Enemies",
                                "",
                                isLeader || isOfficer
                                        ? "<yellow>Click to manage"
                                        : "<red>Officers only")
                        .build();
        setItem(
                24,
                new GuiItem(
                        relationsItem,
                        e -> {
                            if (isLeader || isOfficer) {
                                viewer.closeInventory();
                                new NationRelationsGui(plugin, viewer, nation, playerClaim).open();
                            } else {
                                TextUtil.send(
                                        viewer,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<red>Only officers can manage diplomacy!");
                            }
                        }));

        // Invite (slot 29) - Officers only
        if (isLeader || isOfficer) {
            ItemStack inviteItem =
                    new ItemBuilder(Material.WRITABLE_BOOK)
                            .name("<green>Invite Claim")
                            .lore(
                                    "",
                                    "<gray>Invite another claim",
                                    "<gray>to join the nation",
                                    "",
                                    "<yellow>Click to invite")
                            .build();
            setItem(
                    29,
                    new GuiItem(
                            inviteItem,
                            e -> {
                                viewer.closeInventory();
                                TextUtil.send(
                                        viewer,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<yellow>Use <white>/nation invite <claim> <yellow>to invite a claim.");
                            }));
        }

        // Settings (slot 31) - Leader only
        if (isLeader) {
            ItemStack settingsItem =
                    new ItemBuilder(Material.COMPARATOR)
                            .name("<yellow>Nation Settings")
                            .lore(
                                    "",
                                    "<gray>Modify nation name,",
                                    "<gray>description, and color",
                                    "",
                                    "<yellow>Click to edit")
                            .build();
            setItem(
                    31,
                    new GuiItem(
                            settingsItem,
                            e -> {
                                viewer.closeInventory();
                                TextUtil.send(
                                        viewer,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<yellow>Use commands to modify nation settings.");
                            }));
        }

        // Leave/Disband (slot 33)
        if (isLeader) {
            ItemStack disbandItem =
                    new ItemBuilder(Material.TNT)
                            .name("<dark_red>Disband Nation")
                            .lore(
                                    "",
                                    "<red>WARNING: This will permanently",
                                    "<red>delete the nation!",
                                    "",
                                    "<gray>All members will be removed.",
                                    "",
                                    "<dark_red>Click to disband")
                            .build();
            setItem(
                    33,
                    new GuiItem(
                            disbandItem,
                            e -> {
                                viewer.closeInventory();
                                TextUtil.send(
                                        viewer,
                                        plugin.getClaimConfig().getMessage("prefix")
                                                + "<red>Use <white>/nation disband <red>to confirm disbanding.");
                            }));
        } else {
            ItemStack leaveItem =
                    new ItemBuilder(Material.IRON_DOOR)
                            .name("<red>Leave Nation")
                            .lore("", "<gray>Leave this nation", "", "<yellow>Click to leave")
                            .build();
            setItem(
                    33,
                    new GuiItem(
                            leaveItem,
                            e -> {
                                plugin.getNationManager()
                                        .leaveNation(
                                                playerClaim,
                                                success -> {
                                                    if (success) {
                                                        TextUtil.send(
                                                                viewer,
                                                                plugin.getClaimConfig()
                                                                                .getMessage(
                                                                                        "prefix")
                                                                        + "<green>You have left the nation.");
                                                        viewer.closeInventory();
                                                    } else {
                                                        TextUtil.send(
                                                                viewer,
                                                                plugin.getClaimConfig()
                                                                                .getMessage(
                                                                                        "prefix")
                                                                        + "<red>Could not leave the nation.");
                                                    }
                                                });
                            }));
        }

        // War status (slot 40)
        List<?> wars = plugin.getWarManager().getActiveWarsForNation(nation.getId());
        Material warMat = wars.isEmpty() ? Material.WHITE_BANNER : Material.RED_BANNER;
        String warStatus = wars.isEmpty() ? "<green>At Peace" : "<red>At War!";

        ItemStack warItem =
                new ItemBuilder(warMat)
                        .name(warStatus)
                        .lore(
                                "",
                                wars.isEmpty()
                                        ? "<gray>Your nation is not at war"
                                        : "<gray>Active wars: <red>" + wars.size(),
                                "",
                                "<yellow>Click for war info")
                        .build();
        setItem(
                40,
                new GuiItem(
                        warItem,
                        e -> {
                            viewer.closeInventory();
                            viewer.performCommand("war status");
                        }));

        // Close button (slot 49)
        ItemStack closeItem = new ItemBuilder(Material.BARRIER).name("<red>Close").build();
        setItem(49, new GuiItem(closeItem, e -> viewer.closeInventory()));
    }

    private String getLeaderName() {
        return plugin.getRepository().getPlayerData(nation.getLeaderUuid()) != null
                ? plugin.getRepository().getPlayerData(nation.getLeaderUuid()).getUsername()
                : "Unknown";
    }
}
