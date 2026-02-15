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

/** GUI for selecting an icon for a custom group. Provides a selection of common icons. */
public class GroupIconPickerGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;
    private final CustomGroup group;

    private static final Material[] ICONS = {
        // Colored glass panes (standard group icons)
        Material.WHITE_STAINED_GLASS_PANE,
        Material.LIGHT_GRAY_STAINED_GLASS_PANE,
        Material.GRAY_STAINED_GLASS_PANE,
        Material.BLACK_STAINED_GLASS_PANE,
        Material.RED_STAINED_GLASS_PANE,
        Material.ORANGE_STAINED_GLASS_PANE,
        Material.YELLOW_STAINED_GLASS_PANE,
        Material.LIME_STAINED_GLASS_PANE,
        Material.GREEN_STAINED_GLASS_PANE,
        Material.CYAN_STAINED_GLASS_PANE,
        Material.LIGHT_BLUE_STAINED_GLASS_PANE,
        Material.BLUE_STAINED_GLASS_PANE,
        Material.PURPLE_STAINED_GLASS_PANE,
        Material.MAGENTA_STAINED_GLASS_PANE,
        Material.PINK_STAINED_GLASS_PANE,
        Material.BROWN_STAINED_GLASS_PANE,

        // Special items
        Material.GOLDEN_HELMET,
        Material.IRON_HELMET,
        Material.DIAMOND_HELMET,
        Material.NETHERITE_HELMET,
        Material.LEATHER_HELMET,
        Material.CHAINMAIL_HELMET,

        // Tools/weapons
        Material.DIAMOND_SWORD,
        Material.GOLDEN_SWORD,
        Material.IRON_PICKAXE,
        Material.DIAMOND_AXE,
        Material.BOW,
        Material.SHIELD,

        // Decorative
        Material.NETHER_STAR,
        Material.BEACON,
        Material.TOTEM_OF_UNDYING,
        Material.DRAGON_EGG,
        Material.ELYTRA,
        Material.TRIDENT,
        Material.END_CRYSTAL,
        Material.HEART_OF_THE_SEA,

        // Misc
        Material.BOOK,
        Material.WRITABLE_BOOK,
        Material.ENCHANTED_BOOK,
        Material.NAME_TAG,
        Material.COMPASS,
        Material.CLOCK,
        Material.EMERALD,
        Material.DIAMOND,
        Material.GOLD_INGOT,
        Material.IRON_INGOT,
        Material.REDSTONE
    };

    public GroupIconPickerGui(ServerClaim plugin, Player player, Claim claim, CustomGroup group) {
        super(plugin, player, "Pick Icon", 54);
        this.plugin = plugin;

        // Validate claim at constructor level
        if (!GuiValidator.validateClaim(plugin, player, claim, "GroupIconPickerGui")) {
            this.claim = null;
            this.group = null;
            return;
        }

        // Validate group
        if (group == null) {
            TextUtil.send(player, "<red>Invalid group!");
            plugin.getLogger()
                    .warning("GroupIconPickerGui: null group for player " + player.getName());
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
                        .name(group.getColorTag() + "Select Icon")
                        .lore(
                                "",
                                "<gray>Group: " + group.getColorTag() + group.getName(),
                                "",
                                "<dark_gray>Click an icon to select it",
                                "<dark_gray>or hold an item and click")
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(titleItem));

        // Rows 1-4: Icons
        int[] slots = new int[45 - 9]; // Slots 9-44 (excluding bottom row)
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i + 9;
        }

        for (int i = 0; i < Math.min(ICONS.length, slots.length); i++) {
            Material iconMaterial = ICONS[i];
            boolean isSelected = group.getIcon() == iconMaterial;

            ItemStack iconItem =
                    new ItemBuilder(iconMaterial)
                            .name("<white>" + formatMaterialName(iconMaterial))
                            .lore(
                                    "",
                                    isSelected
                                            ? "<green>Currently selected"
                                            : "<yellow>Click to select")
                            .glow(isSelected)
                            .build();

            setItem(
                    slots[i],
                    new GuiItem(
                            iconItem,
                            e -> {
                                selectIcon(iconMaterial);
                            }));
        }

        // Row 5: Navigation
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to group settings")
                        .build();
        setItem(
                45,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new GroupSettingsGui(plugin, viewer, claim, group).open();
                        }));

        // Custom item hint
        ItemStack hintItem =
                new ItemBuilder(Material.PAPER)
                        .name("<aqua>Custom Icon")
                        .lore(
                                "",
                                "<gray>Hold any item on your cursor",
                                "<gray>and click here to use it",
                                "<gray>as the group icon")
                        .build();
        setItem(
                49,
                new GuiItem(
                        hintItem,
                        e -> {
                            ItemStack cursorItem = viewer.getItemOnCursor();
                            if (cursorItem != null && cursorItem.getType() != Material.AIR) {
                                selectIcon(cursorItem.getType());
                            }
                        }));

        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {46, 47, 48, 50, 51, 52, 53}) {
            setItem(i, new GuiItem(divider));
        }

        fillEmpty(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
    }

    private void selectIcon(Material icon) {
        // Safety checks
        if (group == null || claim == null || icon == null) {
            TextUtil.send(viewer, "<red>Error: Invalid icon selection!");
            viewer.closeInventory();
            return;
        }

        group.setIcon(icon);
        new ClaimGroupRepository(plugin.getDatabase()).saveGroup(group);

        TextUtil.send(viewer, "<green>Changed group icon to <white>" + formatMaterialName(icon));
        viewer.closeInventory();
        new GroupSettingsGui(plugin, viewer, claim, group).open();
    }

    private String formatMaterialName(Material material) {
        if (material == null) {
            return "Unknown";
        }

        String name = material.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                formatted
                        .append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }
        return formatted.toString().trim();
    }

    /** Show error state when claim/group is null. */
    private void showErrorState() {
        ItemStack errorItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Error")
                        .lore(
                                "",
                                "<gray>Cannot change icon:",
                                "<gray>Invalid claim or group data",
                                "",
                                "<yellow>Try closing and reopening")
                        .build();
        setItem(22, new GuiItem(errorItem));

        ItemStack backItem = new ItemBuilder(Material.ARROW).name("<red>Back").build();
        setItem(45, new GuiItem(backItem, e -> viewer.closeInventory()));

        fillEmpty(new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build());
    }
}
