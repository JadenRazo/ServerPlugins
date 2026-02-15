package net.serverplugins.claim.gui;

import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.managers.RewardsManager;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ProfileColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ColorPickerGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;
    private final long playerPlaytime;

    public ColorPickerGui(ServerClaim plugin, Player player, Claim claim) {
        super(plugin, player, "Pick Color", 45);
        this.plugin = plugin;

        // Validate claim at constructor level
        if (!GuiValidator.validateClaim(plugin, player, claim, "ColorPickerGui")) {
            this.claim = null;
            this.playerPlaytime = 0;
            return;
        }

        this.claim = claim;

        // Null-safe rewards manager access
        RewardsManager rewardsManager = plugin.getRewardsManager();
        this.playerPlaytime =
                rewardsManager != null
                        ? rewardsManager.getPlayerPlaytimeMinutes(player.getUniqueId())
                        : Long.MAX_VALUE;
    }

    @Override
    protected void initializeItems() {
        // Safety check: claim was invalidated in constructor
        if (claim == null) {
            showErrorState();
            return;
        }

        // Row 0: Title bar
        setupTitleBar();

        // Rows 1-2: Color options
        setupColorOptions();

        // Row 3: Current selection info
        setupInfoRow();

        // Row 4: Navigation
        setupNavigationBar();

        fillEmpty(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
    }

    private void setupTitleBar() {
        ItemStack blackGlass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {0, 1, 2, 3, 5, 6, 7, 8}) {
            setItem(i, new GuiItem(blackGlass));
        }

        // Null-safe color access
        ProfileColor currentColor =
                claim.getColor() != null ? claim.getColor() : ProfileColor.WHITE;
        ItemStack titleItem =
                new ItemBuilder(Material.PAINTING)
                        .name("<gold>Select Claim Color")
                        .lore(
                                "",
                                "<gray>Choose a color for your",
                                "<gray>claim border particles",
                                "",
                                "<gray>Current: "
                                        + currentColor.getColorTag()
                                        + currentColor.getDisplayName())
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(titleItem));
    }

    private void setupColorOptions() {
        ProfileColor[] colors = ProfileColor.values();
        // Null-safe color access
        ProfileColor currentColor =
                claim.getColor() != null ? claim.getColor() : ProfileColor.WHITE;

        // Slots for colors: 10-16 (row 1) and 19-25 (row 2)
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

        for (int i = 0; i < colors.length && i < slots.length; i++) {
            ProfileColor color = colors[i];
            boolean isSelected = currentColor == color;
            boolean unlocked = color.isUnlockedFor(playerPlaytime);

            if (unlocked) {
                ItemStack item =
                        new ItemBuilder(color.getGlassPaneMaterial())
                                .name(color.getColorTag() + color.getDisplayName())
                                .lore(
                                        "",
                                        isSelected
                                                ? "<green>Currently Selected"
                                                : "<yellow>Click to select",
                                        "",
                                        isSelected ? "<dark_gray>This is your active color" : "")
                                .glow(isSelected)
                                .build();

                final ProfileColor selectedColor = color;
                setItem(
                        slots[i],
                        new GuiItem(
                                item,
                                event -> {
                                    // Safety check before saving
                                    if (claim == null || selectedColor == null) {
                                        TextUtil.send(viewer, "<red>Error: Cannot update color!");
                                        viewer.closeInventory();
                                        return;
                                    }

                                    claim.setColor(selectedColor);
                                    plugin.getRepository().updateClaimColor(claim);
                                    TextUtil.send(
                                            viewer,
                                            "<green>Color changed to "
                                                    + selectedColor.getColorTag()
                                                    + selectedColor.getDisplayName());
                                    viewer.closeInventory();
                                    new ClaimSettingsGui(plugin, viewer, claim).open();
                                }));
            } else {
                ItemStack item =
                        new ItemBuilder(Material.GRAY_DYE)
                                .name("<gray>" + color.getDisplayName())
                                .lore(
                                        "",
                                        "<red>LOCKED",
                                        "",
                                        "<gray>Required playtime:",
                                        "<white>" + color.formatPlaytimeRequired(),
                                        "",
                                        "<yellow>Use /claim rewards to",
                                        "<yellow>view your progress")
                                .build();

                setItem(
                        slots[i],
                        new GuiItem(
                                item,
                                event -> {
                                    TextUtil.send(
                                            viewer,
                                            "<red>This color requires <yellow>"
                                                    + color.formatPlaytimeRequired()
                                                    + " <red>of playtime to unlock!");
                                }));
            }
        }

        // Dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {9, 17, 18, 26}) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void setupInfoRow() {
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 27; i < 36; i++) {
            if (i == 31) continue;
            setItem(i, new GuiItem(divider));
        }

        // Null-safe color access
        ProfileColor currentColor =
                claim.getColor() != null ? claim.getColor() : ProfileColor.WHITE;

        // Current color preview (slot 31)
        ItemStack previewItem =
                new ItemBuilder(currentColor.getGlassPaneMaterial())
                        .name(currentColor.getColorTag() + "Current Color")
                        .lore(
                                "",
                                "<gray>Claim: <white>" + claim.getName(),
                                "<gray>Color: "
                                        + currentColor.getColorTag()
                                        + currentColor.getDisplayName(),
                                "",
                                "<dark_gray>Border particles will",
                                "<dark_gray>use this color")
                        .glow(true)
                        .build();
        setItem(31, new GuiItem(previewItem));
    }

    private void setupNavigationBar() {
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 36; i < 45; i++) {
            if (i == 40) continue;
            setItem(i, new GuiItem(divider));
        }

        // Back button (slot 40)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to claim settings")
                        .build();
        setItem(
                40,
                new GuiItem(
                        backItem,
                        event -> {
                            viewer.closeInventory();
                            new ClaimSettingsGui(plugin, viewer, claim).open();
                        }));
    }

    /** Show error state when claim is null. */
    private void showErrorState() {
        ItemStack errorItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Error")
                        .lore(
                                "",
                                "<gray>Cannot select color:",
                                "<gray>Claim no longer exists",
                                "",
                                "<yellow>Try closing and reopening")
                        .build();
        setItem(22, new GuiItem(errorItem));

        ItemStack backItem = new ItemBuilder(Material.ARROW).name("<red>Back").build();
        setItem(40, new GuiItem(backItem, e -> viewer.closeInventory()));

        fillEmpty(new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build());
    }
}
