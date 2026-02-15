package net.serverplugins.claim.gui;

import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimWarp;
import net.serverplugins.claim.models.WarpVisibility;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class WarpSettingsGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;

    public WarpSettingsGui(ServerClaim plugin, Player player, Claim claim) {
        super(plugin, player, "Warp Settings", 45);
        this.plugin = plugin;

        // Validate claim exists
        if (!GuiValidator.validateClaim(plugin, player, claim, "Warp Settings")) {
            this.claim = null;
            return;
        }

        this.claim = claim;
    }

    @Override
    protected void initializeItems() {
        // Early return if claim is null
        if (claim == null) {
            plugin.getLogger().warning("WarpSettingsGui: Claim is null, cannot initialize");
            return;
        }

        ClaimWarp warp = null;
        try {
            warp = plugin.getVisitationManager().getClaimWarp(claim.getId());
        } catch (Exception e) {
            plugin.getLogger()
                    .warning(
                            "Failed to get claim warp for claim "
                                    + claim.getId()
                                    + ": "
                                    + e.getMessage());
        }

        // Header
        setupHeader(warp);

        // Main buttons
        setupMainButtons(warp);

        // Access control
        setupAccessControl(warp);

        // Back button
        setupBackButton();

        // Fill empty slots
        fillEmpty(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
    }

    private void setupHeader(ClaimWarp warp) {
        String claimName =
                claim != null && claim.getName() != null ? claim.getName() : "Unknown Claim";

        ItemStack headerItem =
                new ItemBuilder(Material.ENDER_PEARL)
                        .name("<gold>Warp Settings")
                        .lore(
                                "",
                                "<gray>Claim: <white>" + claimName,
                                "",
                                warp != null
                                        ? "<green>Warp point is set"
                                        : "<red>No warp point set",
                                "",
                                "<dark_gray>Configure your claim's",
                                "<dark_gray>public warp point")
                        .glow(warp != null)
                        .build();

        setItem(4, new GuiItem(headerItem));

        // Decorative glass
        ItemStack blackGlass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {0, 1, 2, 3, 5, 6, 7, 8}) {
            setItem(i, new GuiItem(blackGlass));
        }
    }

    private void setupMainButtons(ClaimWarp warp) {
        // Set Warp Location (slot 19)
        ItemStack setWarpItem =
                new ItemBuilder(Material.COMPASS)
                        .name(warp == null ? "<green>Set Warp Point" : "<yellow>Update Warp Point")
                        .lore(
                                "",
                                warp == null
                                        ? "<gray>Click to set the warp point"
                                        : "<gray>Current location:",
                                warp == null
                                        ? "<gray>at your current location"
                                        : String.format(
                                                "<white>%.1f, %.1f, %.1f",
                                                warp.getX(), warp.getY(), warp.getZ()),
                                "",
                                "<yellow>Click to " + (warp == null ? "set" : "update"))
                        .glow(warp == null)
                        .build();

        setItem(
                19,
                new GuiItem(
                        setWarpItem,
                        e -> {
                            // Validate player location
                            if (viewer == null || viewer.getLocation() == null) {
                                TextUtil.send(viewer, "<red>Cannot set warp - invalid location!");
                                return;
                            }

                            if (claim == null) {
                                TextUtil.send(viewer, "<red>This claim no longer exists!");
                                viewer.closeInventory();
                                return;
                            }

                            if (warp == null) {
                                plugin.getVisitationManager()
                                        .setClaimWarp(claim, viewer.getLocation());
                                TextUtil.send(
                                        viewer, "<green>Warp point set at your current location!");
                            } else {
                                plugin.getVisitationManager()
                                        .updateWarpLocation(warp, viewer.getLocation());
                                TextUtil.send(
                                        viewer,
                                        "<green>Warp point updated to your current location!");
                            }
                            reopenMenu();
                        }));

        // Visibility Toggle (slot 21)
        if (warp != null) {
            WarpVisibility visibility = warp.getVisibility();
            ItemStack visibilityItem =
                    new ItemBuilder(visibility.getIcon())
                            .name(
                                    visibility.getColorTag()
                                            + "Visibility: "
                                            + visibility.getDisplayName())
                            .lore(
                                    "",
                                    "<gray>Current: "
                                            + visibility.getColorTag()
                                            + visibility.getDisplayName(),
                                    "<dark_gray>" + visibility.getDescription(),
                                    "",
                                    "<yellow>Click to change")
                            .glow(visibility == WarpVisibility.PUBLIC)
                            .build();

            setItem(
                    21,
                    new GuiItem(
                            visibilityItem,
                            e -> {
                                WarpVisibility next = visibility.next();
                                plugin.getVisitationManager().setWarpVisibility(warp, next);
                                TextUtil.send(
                                        viewer,
                                        "<green>Visibility changed to: "
                                                + next.getColorTag()
                                                + next.getDisplayName());
                                reopenMenu();
                            }));
        } else {
            ItemStack lockedItem =
                    new ItemBuilder(Material.BARRIER)
                            .name("<red>Visibility")
                            .lore("", "<gray>Set a warp point first")
                            .build();
            setItem(21, new GuiItem(lockedItem));
        }

        // Description (slot 23)
        if (warp != null) {
            ItemStack descItem =
                    new ItemBuilder(Material.WRITABLE_BOOK)
                            .name("<aqua>Description")
                            .lore(
                                    "",
                                    warp.getDescription() != null
                                            ? "<gray>Current: <white>" + warp.getDescription()
                                            : "<gray>No description set",
                                    "",
                                    "<yellow>Click to change")
                            .build();

            setItem(
                    23,
                    new GuiItem(
                            descItem,
                            e -> {
                                viewer.closeInventory();
                                plugin.getVisitationManager().awaitDescriptionInput(viewer, warp);
                            }));
        } else {
            ItemStack lockedItem =
                    new ItemBuilder(Material.BARRIER)
                            .name("<red>Description")
                            .lore("", "<gray>Set a warp point first")
                            .build();
            setItem(23, new GuiItem(lockedItem));
        }

        // Delete Warp (slot 25)
        if (warp != null) {
            ItemStack deleteItem =
                    new ItemBuilder(Material.TNT)
                            .name("<red>Delete Warp")
                            .lore(
                                    "",
                                    "<gray>Remove the warp point",
                                    "<gray>and all settings",
                                    "",
                                    "<red>Click to delete")
                            .build();

            setItem(
                    25,
                    new GuiItem(
                            deleteItem,
                            e -> {
                                plugin.getVisitationManager().deleteClaimWarp(claim.getId());
                                TextUtil.send(viewer, "<yellow>Warp point deleted.");
                                reopenMenu();
                            }));
        } else {
            ItemStack emptyItem =
                    new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
            setItem(25, new GuiItem(emptyItem));
        }

        // Fill row with dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {18, 20, 22, 24, 26}) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void setupAccessControl(ClaimWarp warp) {
        // Allowlist button (slot 29)
        if (warp != null && warp.getVisibility() == WarpVisibility.ALLOWLIST) {
            int allowlistSize = warp.getAllowlist() != null ? warp.getAllowlist().size() : 0;
            ItemStack allowlistItem =
                    new ItemBuilder(Material.PLAYER_HEAD)
                            .name("<green>Manage Allowlist")
                            .lore(
                                    "",
                                    "<gray>Players: <white>" + allowlistSize,
                                    "",
                                    "<dark_gray>Control who can visit",
                                    "<dark_gray>your claim",
                                    "",
                                    "<yellow>Click to manage")
                            .build();

            setItem(
                    29,
                    new GuiItem(
                            allowlistItem,
                            e -> {
                                viewer.closeInventory();
                                new WarpListGui(
                                                plugin,
                                                viewer,
                                                claim,
                                                warp,
                                                WarpListGui.ListType.ALLOWLIST)
                                        .open();
                            }));
        } else if (warp != null) {
            ItemStack lockedItem =
                    new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                            .name("<dark_gray>Allowlist")
                            .lore("", "<gray>Only available in", "<gray>Allowlist mode")
                            .build();
            setItem(29, new GuiItem(lockedItem));
        } else {
            ItemStack emptyItem =
                    new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
            setItem(29, new GuiItem(emptyItem));
        }

        // Visit Cost (slot 31)
        if (warp != null) {
            double cost = warp.getVisitCost();
            ItemStack costItem =
                    new ItemBuilder(Material.GOLD_INGOT)
                            .name("<yellow>Visit Cost")
                            .lore(
                                    "",
                                    "<gray>Current: <white>"
                                            + (cost == 0 ? "Free" : (int) cost + " coins"),
                                    "",
                                    "<dark_gray>Players pay you to visit",
                                    "",
                                    "<yellow>Click to change")
                            .glow(cost > 0)
                            .build();

            setItem(
                    31,
                    new GuiItem(
                            costItem,
                            e -> {
                                viewer.closeInventory();
                                plugin.getVisitationManager().awaitCostInput(viewer, warp);
                            }));
        } else {
            ItemStack lockedItem =
                    new ItemBuilder(Material.BARRIER)
                            .name("<red>Visit Cost")
                            .lore("", "<gray>Set a warp point first")
                            .build();
            setItem(31, new GuiItem(lockedItem));
        }

        // Blocklist button (slot 33)
        if (warp != null) {
            int blocklistSize = warp.getBlocklist() != null ? warp.getBlocklist().size() : 0;
            ItemStack blocklistItem =
                    new ItemBuilder(Material.BARRIER)
                            .name("<red>Manage Blocklist")
                            .lore(
                                    "",
                                    "<gray>Blocked: <white>" + blocklistSize,
                                    "",
                                    "<dark_gray>Prevent specific players",
                                    "<dark_gray>from visiting",
                                    "",
                                    "<yellow>Click to manage")
                            .build();

            setItem(
                    33,
                    new GuiItem(
                            blocklistItem,
                            e -> {
                                viewer.closeInventory();
                                new WarpListGui(
                                                plugin,
                                                viewer,
                                                claim,
                                                warp,
                                                WarpListGui.ListType.BLOCKLIST)
                                        .open();
                            }));
        } else {
            ItemStack emptyItem =
                    new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
            setItem(33, new GuiItem(emptyItem));
        }

        // Fill row with dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {27, 28, 30, 32, 34, 35}) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void setupBackButton() {
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to claim settings")
                        .build();

        setItem(
                40,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ClaimSettingsGui(plugin, viewer, claim).open();
                        }));

        // Fill footer with dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 36; i < 45; i++) {
            if (i != 40) {
                setItem(i, new GuiItem(divider));
            }
        }
    }

    private void reopenMenu() {
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            viewer.closeInventory();
                            new WarpSettingsGui(plugin, viewer, claim).open();
                        });
    }
}
