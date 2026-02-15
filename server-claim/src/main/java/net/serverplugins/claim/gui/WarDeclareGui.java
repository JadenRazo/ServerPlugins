package net.serverplugins.claim.gui;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.Nation;
import net.serverplugins.claim.models.NationRelation;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class WarDeclareGui extends Gui {

    private final ServerClaim plugin;
    private final Nation nation;
    private final Claim playerClaim;
    private final int page;
    private static final int ITEMS_PER_PAGE = 28;

    public WarDeclareGui(ServerClaim plugin, Player player, Nation nation, Claim playerClaim) {
        this(plugin, player, nation, playerClaim, 0);
    }

    public WarDeclareGui(
            ServerClaim plugin, Player player, Nation nation, Claim playerClaim, int page) {
        super(plugin, player, "<dark_red>Declare War - Select Target", 54);
        this.plugin = plugin;

        // Validate nation exists and player owns it
        if (!GuiValidator.validateNationOwnership(plugin, player, nation, "War Declare")) {
            this.nation = null;
            this.playerClaim = playerClaim;
            this.page = page;
            return;
        }

        this.nation = nation;
        this.playerClaim = playerClaim;
        this.page = page;
    }

    @Override
    protected void initializeItems() {
        // Early return if nation is null
        if (nation == null) {
            plugin.getLogger().warning("WarDeclareGui: Nation is null, cannot initialize");
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

        // Get eligible targets (other nations, not already at war, not shielded)
        List<Nation> allNations = null;
        try {
            allNations = plugin.getNationManager().getAllNations();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get all nations: " + e.getMessage());
            allNations = new ArrayList<>();
        }

        allNations.removeIf(n -> n == null || n.getId() == nation.getId());
        allNations.removeIf(
                n -> {
                    try {
                        return plugin.getWarManager().getWarBetween(nation.getId(), n.getId())
                                != null;
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to check war status: " + e.getMessage());
                        return false;
                    }
                });

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

            Nation target = allNations.get(i);
            if (target == null) continue; // Skip null nations

            GuiItem item = createTargetItem(target);
            if (item != null) {
                setItem(slots[slotIndex], item);
            }
        }

        // Warning (slot 4)
        ItemStack warningItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<dark_red>⚠ Warning")
                        .lore(
                                "",
                                "<gray>Declaring war will:",
                                "<red>• Enable PvP in your territory",
                                "<red>• Allow enemy territory capture",
                                "<red>• Last until surrender or victory",
                                "",
                                "<gray>There is a <yellow>24-hour notice",
                                "<gray>period before combat begins.")
                        .build();
        setItem(4, new GuiItem(warningItem));

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
                                new WarDeclareGui(plugin, viewer, nation, playerClaim, page - 1)
                                        .open();
                            }));
        }

        // Page info (slot 49)
        ItemStack pageInfo =
                new ItemBuilder(Material.PAPER)
                        .name("<gold>Page " + (page + 1) + " of " + totalPages)
                        .lore("<gray>Targets: <white>" + allNations.size())
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
                                new WarDeclareGui(plugin, viewer, nation, playerClaim, page + 1)
                                        .open();
                            }));
        }

        // Back (slot 45)
        ItemStack backItem =
                new ItemBuilder(Material.DARK_OAK_DOOR)
                        .name("<gray>Back")
                        .lore("<gray>Return to war room")
                        .build();
        setItem(
                45,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new WarMenuGui(plugin, viewer, nation, playerClaim).open();
                        }));

        // Close (slot 53)
        ItemStack closeItem = new ItemBuilder(Material.BARRIER).name("<red>Cancel").build();
        setItem(53, new GuiItem(closeItem, e -> viewer.closeInventory()));
    }

    private GuiItem createTargetItem(Nation target) {
        if (target == null) {
            plugin.getLogger().warning("Cannot create target item for null nation");
            return null;
        }

        boolean hasShield = false;
        try {
            hasShield = plugin.getWarManager().hasActiveShield(target.getId());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check shield status: " + e.getMessage());
        }

        NationRelation.RelationType relation =
                plugin.getNationManager().getRelation(nation.getId(), target.getId());

        Material mat;
        String statusLine;

        if (hasShield) {
            mat = Material.GRAY_DYE;
            statusLine = "<yellow>⚠ War Shield Active";
        } else if (relation == NationRelation.RelationType.ALLY) {
            mat = Material.EMERALD;
            statusLine = "<green>Ally - Are you sure?";
        } else if (relation == NationRelation.RelationType.ENEMY) {
            mat = Material.REDSTONE;
            statusLine = "<red>Enemy - Ready to attack";
        } else {
            mat = Material.IRON_SWORD;
            statusLine = "<gray>Click to declare war";
        }

        String targetName = target.getName() != null ? target.getName() : "Unknown";
        String targetTag = target.getColoredTag() != null ? target.getColoredTag() : "<white>";

        ItemStack item =
                new ItemBuilder(mat)
                        .name(targetTag + " " + targetName)
                        .lore(
                                "",
                                "<gray>Members: <white>" + target.getMemberCount(),
                                "<gray>Chunks: <white>" + target.getTotalChunks(),
                                "<gray>Relation: " + relation.getDisplayName(),
                                "",
                                statusLine)
                        .build();

        final boolean finalHasShield = hasShield;
        return new GuiItem(
                item,
                e -> {
                    if (finalHasShield) {
                        TextUtil.send(
                                viewer,
                                plugin.getClaimConfig().getMessage("prefix")
                                        + "<red>This nation has a war shield and cannot be attacked!");
                        return;
                    }

                    // Confirm declaration
                    viewer.closeInventory();
                    new WarDeclareConfirmGui(plugin, viewer, nation, target, playerClaim).open();
                });
    }

    // Inner confirmation GUI
    public static class WarDeclareConfirmGui extends Gui {
        private final ServerClaim plugin;
        private final Nation nation;
        private final Nation target;
        private final Claim playerClaim;

        public WarDeclareConfirmGui(
                ServerClaim plugin,
                Player player,
                Nation nation,
                Nation target,
                Claim playerClaim) {
            super(plugin, player, "<dark_red>Confirm War Declaration", 27);
            this.plugin = plugin;
            this.nation = nation;
            this.target = target;
            this.playerClaim = playerClaim;
        }

        @Override
        protected void initializeItems() {
            ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
            for (int i = 0; i < 27; i++) setItem(i, new GuiItem(filler));

            // Target info (slot 4)
            ItemStack targetInfo =
                    new ItemBuilder(Material.PLAYER_HEAD)
                            .name(target.getColoredTag() + " " + target.getName())
                            .lore(
                                    "",
                                    "<gray>You are about to declare",
                                    "<gray>war on this nation.",
                                    "",
                                    "<gray>Members: <white>" + target.getMemberCount(),
                                    "<gray>Chunks: <white>" + target.getTotalChunks())
                            .build();
            setItem(4, new GuiItem(targetInfo));

            // Confirm (slot 11)
            ItemStack confirmItem =
                    new ItemBuilder(Material.LIME_CONCRETE)
                            .name("<green>Confirm Declaration")
                            .lore(
                                    "",
                                    "<gray>Click to officially",
                                    "<gray>declare war on " + target.getName())
                            .build();
            setItem(
                    11,
                    new GuiItem(
                            confirmItem,
                            e -> {
                                plugin.getWarManager()
                                        .declareWar(
                                                nation,
                                                target,
                                                null,
                                                result -> {
                                                    String message =
                                                            switch (result) {
                                                                case SUCCESS ->
                                                                        "<green>War declared on "
                                                                                + target.getName()
                                                                                + "!";
                                                                case ALREADY_AT_WAR ->
                                                                        "<red>Already at war with "
                                                                                + target.getName()
                                                                                + ".";
                                                                case TARGET_SHIELDED ->
                                                                        "<red>"
                                                                                + target.getName()
                                                                                + " has a war shield.";
                                                                default ->
                                                                        "<red>Could not declare war.";
                                                            };
                                                    TextUtil.send(
                                                            viewer,
                                                            plugin.getClaimConfig()
                                                                            .getMessage("prefix")
                                                                    + message);
                                                    viewer.closeInventory();
                                                });
                            }));

            // Cancel (slot 15)
            ItemStack cancelItem =
                    new ItemBuilder(Material.RED_CONCRETE)
                            .name("<red>Cancel")
                            .lore("", "<gray>Cancel war declaration")
                            .build();
            setItem(
                    15,
                    new GuiItem(
                            cancelItem,
                            e -> {
                                viewer.closeInventory();
                                new WarDeclareGui(plugin, viewer, nation, playerClaim).open();
                            }));
        }
    }
}
