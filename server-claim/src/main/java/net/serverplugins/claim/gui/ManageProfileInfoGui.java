package net.serverplugins.claim.gui;

import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ManagementPermission;
import net.serverplugins.claim.models.ProfileColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * GUI for managing claim profile information: name, icon, and color. Requires MANAGE_PROFILE_INFO
 * permission.
 */
public class ManageProfileInfoGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;

    public ManageProfileInfoGui(ServerClaim plugin, Player player, Claim claim) {
        super(plugin, player, "Profile Info", 27);
        this.plugin = plugin;

        // Validate claim at constructor level
        if (!GuiValidator.validateClaim(plugin, player, claim, "ManageProfileInfoGui")) {
            this.claim = null;
            return;
        }

        this.claim = claim;
    }

    @Override
    protected void initializeItems() {
        // Safety check: claim was invalidated in constructor
        if (claim == null) {
            showErrorState();
            return;
        }

        // Check permission
        if (!claim.hasManagementPermission(
                        viewer.getUniqueId(), ManagementPermission.MANAGE_PROFILE_INFO)
                && !viewer.hasPermission("serverclaim.admin")) {
            showNoPermission();
            return;
        }

        // Row 0: Title bar
        setupTitleBar();

        // Row 1: Profile settings (Name, Icon, Color)
        setupProfileSettings();

        // Row 2: Navigation
        setupNavigationBar();

        // Fill with claim-colored glass
        Material borderMaterial =
                claim.getColor() != null
                        ? claim.getColor().getGlassPaneMaterial()
                        : Material.WHITE_STAINED_GLASS_PANE;
        fillEmpty(new ItemBuilder(borderMaterial).name(" ").build());
    }

    private void showNoPermission() {
        ItemStack noPermItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>No Permission")
                        .lore(
                                "",
                                "<gray>You do not have permission",
                                "<gray>to manage this claim's profile.",
                                "",
                                "<yellow>Required: <white>Manage Profile Info")
                        .build();
        setItem(13, new GuiItem(noPermItem));

        // Back button
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to claim settings")
                        .build();
        setItem(
                22,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ClaimSettingsGui(plugin, viewer, claim).open();
                        }));

        fillEmpty(new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build());
    }

    private void setupTitleBar() {
        ItemStack blackGlass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {0, 1, 2, 3, 5, 6, 7, 8}) {
            setItem(i, new GuiItem(blackGlass));
        }

        String colorTag = claim.getColor() != null ? claim.getColor().getColorTag() : "<white>";
        ItemStack titleItem =
                new ItemBuilder(Material.PAINTING)
                        .name("<gold>Profile Settings")
                        .lore(
                                "",
                                "<gray>Claim: " + colorTag + claim.getName(),
                                "",
                                "<dark_gray>Customize your claim's",
                                "<dark_gray>appearance and identity")
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(titleItem));
    }

    private void setupProfileSettings() {
        // Claim Name (slot 11)
        ItemStack nameItem =
                new ItemBuilder(Material.NAME_TAG)
                        .name("<gold>Claim Name")
                        .lore(
                                "",
                                "<gray>Current: <white>" + claim.getName(),
                                "",
                                "<dark_gray>The display name for",
                                "<dark_gray>your claim",
                                "",
                                "<yellow>Click to rename")
                        .glow(true)
                        .build();
        setItem(
                11,
                new GuiItem(
                        nameItem,
                        e -> {
                            viewer.closeInventory();
                            TextUtil.send(viewer, "<yellow>Type the new claim name in chat:");
                            TextUtil.send(viewer, "<gray>(Type 'cancel' to cancel)");
                            plugin.getClaimManager().awaitClaimRenameInput(viewer, claim);
                        }));

        // Claim Icon (slot 13)
        Material currentIcon = claim.getIcon() != null ? claim.getIcon() : Material.GRASS_BLOCK;
        ItemStack iconItem =
                new ItemBuilder(currentIcon)
                        .name("<aqua>Claim Icon")
                        .lore(
                                "",
                                "<gray>Current: <white>" + formatMaterialName(currentIcon),
                                "",
                                "<dark_gray>The icon displayed in",
                                "<dark_gray>claim lists and menus",
                                "",
                                "<yellow>Click to change")
                        .glow(true)
                        .build();
        setItem(
                13,
                new GuiItem(
                        iconItem,
                        e -> {
                            viewer.closeInventory();
                            new IconPickerGui(plugin, viewer, claim).open();
                        }));

        // Claim Color (slot 15)
        ProfileColor currentColor =
                claim.getColor() != null ? claim.getColor() : ProfileColor.WHITE;
        ItemStack colorItem =
                new ItemBuilder(currentColor.getGlassPaneMaterial())
                        .name("<light_purple>Claim Color")
                        .lore(
                                "",
                                "<gray>Current: "
                                        + currentColor.getColorTag()
                                        + currentColor.getDisplayName(),
                                "",
                                "<dark_gray>Changes the border",
                                "<dark_gray>particle color and",
                                "<dark_gray>menu appearance",
                                "",
                                "<yellow>Click to change")
                        .glow(true)
                        .build();
        setItem(
                15,
                new GuiItem(
                        colorItem,
                        e -> {
                            viewer.closeInventory();
                            new ColorPickerGui(plugin, viewer, claim).open();
                        }));

        // Dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {9, 10, 12, 14, 16, 17}) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void setupNavigationBar() {
        // Back button (slot 18)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to claim settings")
                        .build();
        setItem(
                18,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ClaimSettingsGui(plugin, viewer, claim).open();
                        }));

        // Dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {19, 20, 21, 22, 23, 24, 25, 26}) {
            setItem(i, new GuiItem(divider));
        }
    }

    private String formatMaterialName(Material material) {
        if (material == null) {
            return "Unknown";
        }

        String name = material.name().toLowerCase().replace('_', ' ');
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (c == ' ') {
                capitalizeNext = true;
                result.append(c);
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /** Show error state when claim is null. */
    private void showErrorState() {
        ItemStack errorItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Error")
                        .lore(
                                "",
                                "<gray>Cannot manage profile:",
                                "<gray>Claim no longer exists",
                                "",
                                "<yellow>Try closing and reopening")
                        .build();
        setItem(13, new GuiItem(errorItem));

        ItemStack backItem = new ItemBuilder(Material.ARROW).name("<red>Back").build();
        setItem(22, new GuiItem(backItem, e -> viewer.closeInventory()));

        fillEmpty(new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build());
    }
}
