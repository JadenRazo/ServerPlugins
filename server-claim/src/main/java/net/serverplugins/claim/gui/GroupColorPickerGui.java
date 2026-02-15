package net.serverplugins.claim.gui;

import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.CustomGroup;
import net.serverplugins.claim.repository.ClaimGroupRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** GUI for selecting a color tag for a custom group. */
public class GroupColorPickerGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;
    private final CustomGroup group;

    private record ColorOption(String tag, String displayName, Material material) {}

    private static final ColorOption[] COLORS = {
        new ColorOption("<black>", "Black", Material.BLACK_CONCRETE),
        new ColorOption("<dark_gray>", "Dark Gray", Material.GRAY_CONCRETE),
        new ColorOption("<gray>", "Gray", Material.LIGHT_GRAY_CONCRETE),
        new ColorOption("<white>", "White", Material.WHITE_CONCRETE),
        new ColorOption("<dark_red>", "Dark Red", Material.RED_TERRACOTTA),
        new ColorOption("<red>", "Red", Material.RED_CONCRETE),
        new ColorOption("<gold>", "Gold", Material.ORANGE_CONCRETE),
        new ColorOption("<yellow>", "Yellow", Material.YELLOW_CONCRETE),
        new ColorOption("<dark_green>", "Dark Green", Material.GREEN_CONCRETE),
        new ColorOption("<green>", "Green", Material.LIME_CONCRETE),
        new ColorOption("<aqua>", "Aqua", Material.CYAN_CONCRETE),
        new ColorOption("<dark_aqua>", "Dark Aqua", Material.CYAN_TERRACOTTA),
        new ColorOption("<light_blue>", "Light Blue", Material.LIGHT_BLUE_CONCRETE),
        new ColorOption("<blue>", "Blue", Material.BLUE_CONCRETE),
        new ColorOption("<dark_blue>", "Dark Blue", Material.BLUE_TERRACOTTA),
        new ColorOption("<light_purple>", "Light Purple", Material.MAGENTA_CONCRETE),
        new ColorOption("<dark_purple>", "Dark Purple", Material.PURPLE_CONCRETE),
        new ColorOption("<pink>", "Pink", Material.PINK_CONCRETE)
    };

    public GroupColorPickerGui(ServerClaim plugin, Player player, Claim claim, CustomGroup group) {
        super(plugin, player, "Group Settings", 36);
        this.plugin = plugin;

        // Validate claim at constructor level
        if (!GuiValidator.validateClaim(plugin, player, claim, "GroupColorPickerGui")) {
            this.claim = null;
            this.group = null;
            return;
        }

        // Validate group
        if (group == null) {
            TextUtil.send(player, "<red>Invalid group!");
            plugin.getLogger()
                    .warning("GroupColorPickerGui: null group for player " + player.getName());
            this.claim = null;
            this.group = null;
            return;
        }

        this.claim = claim;
        this.group = group;
    }

    @Override
    protected void initializeItems() {
        // Safety check: claim/group was invalidated in constructor
        if (claim == null || group == null) {
            showErrorState();
            return;
        }

        // Row 0: Title bar
        ItemStack blackGlass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {0, 1, 2, 3, 5, 6, 7, 8}) {
            setItem(i, new GuiItem(blackGlass));
        }

        ItemStack titleItem =
                new ItemBuilder(group.getIcon())
                        .name(group.getColorTag() + "Select Color")
                        .lore(
                                "",
                                "<gray>Group: " + group.getColorTag() + group.getName(),
                                "<gray>Current color: " + group.getColorTag() + "Sample",
                                "",
                                "<dark_gray>Click a color to select it")
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(titleItem));

        // Rows 1-2: Color options
        int[] slots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
        };

        for (int i = 0; i < Math.min(COLORS.length, slots.length); i++) {
            ColorOption color = COLORS[i];
            boolean isSelected = group.getColorTag().equals(color.tag);

            ItemStack colorItem =
                    new ItemBuilder(color.material)
                            .name(color.tag + color.displayName)
                            .lore(
                                    "",
                                    "<gray>Preview: " + color.tag + group.getName(),
                                    "",
                                    isSelected
                                            ? "<green>Currently selected"
                                            : "<yellow>Click to select")
                            .glow(isSelected)
                            .build();

            setItem(
                    slots[i],
                    new GuiItem(
                            colorItem,
                            e -> {
                                selectColor(color.tag);
                            }));
        }

        // Add remaining colors if any
        if (COLORS.length > slots.length) {
            int[] extraSlots = {28, 29, 30, 31, 32, 33, 34};
            for (int i = slots.length;
                    i < COLORS.length && (i - slots.length) < extraSlots.length;
                    i++) {
                ColorOption color = COLORS[i];
                boolean isSelected = group.getColorTag().equals(color.tag);

                ItemStack colorItem =
                        new ItemBuilder(color.material)
                                .name(color.tag + color.displayName)
                                .lore(
                                        "",
                                        "<gray>Preview: " + color.tag + group.getName(),
                                        "",
                                        isSelected
                                                ? "<green>Currently selected"
                                                : "<yellow>Click to select")
                                .glow(isSelected)
                                .build();

                setItem(
                        extraSlots[i - slots.length],
                        new GuiItem(
                                colorItem,
                                e -> {
                                    selectColor(color.tag);
                                }));
            }
        }

        // Side dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {9, 17, 18, 26}) {
            setItem(i, new GuiItem(divider));
        }

        // Row 3: Navigation
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to group settings")
                        .build();
        setItem(
                27,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new GroupSettingsGui(plugin, viewer, claim, group).open();
                        }));

        for (int i = 28; i <= 35; i++) {
            if (getItem(i) == null) {
                setItem(i, new GuiItem(divider));
            }
        }

        fillEmpty(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
    }

    private void selectColor(String colorTag) {
        // Safety checks
        if (group == null || claim == null || colorTag == null) {
            TextUtil.send(viewer, "<red>Error: Invalid color selection!");
            viewer.closeInventory();
            return;
        }

        group.setColorTag(colorTag);
        new ClaimGroupRepository(plugin.getDatabase()).saveGroup(group);

        TextUtil.send(viewer, "<green>Changed group color to " + colorTag + "this color");
        viewer.closeInventory();
        new GroupSettingsGui(plugin, viewer, claim, group).open();
    }

    /** Show error state when claim/group is null. */
    private void showErrorState() {
        ItemStack errorItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Error")
                        .lore(
                                "",
                                "<gray>Cannot change color:",
                                "<gray>Invalid claim or group data",
                                "",
                                "<yellow>Try closing and reopening")
                        .build();
        setItem(13, new GuiItem(errorItem));

        ItemStack backItem = new ItemBuilder(Material.ARROW).name("<red>Back").build();
        setItem(27, new GuiItem(backItem, e -> viewer.closeInventory()));

        fillEmpty(new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build());
    }
}
