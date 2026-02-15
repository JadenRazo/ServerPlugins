package net.serverplugins.claim.gui;

import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** GUI for selecting a claim icon from a curated list of materials. */
public class IconPickerGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;

    // Curated list of icons suitable for claims
    private static final Material[] AVAILABLE_ICONS = {
        // Nature / Terrain
        Material.GRASS_BLOCK,
        Material.DIRT,
        Material.STONE,
        Material.COBBLESTONE,
        Material.OAK_LOG,
        Material.SPRUCE_LOG,
        Material.BIRCH_LOG,
        Material.DARK_OAK_LOG,
        Material.OAK_LEAVES,
        Material.SAND,
        Material.GRAVEL,
        Material.SNOW_BLOCK,
        Material.ICE,
        Material.PACKED_ICE,

        // Ores / Valuables
        Material.COAL_ORE,
        Material.IRON_ORE,
        Material.GOLD_ORE,
        Material.DIAMOND_ORE,
        Material.EMERALD_ORE,
        Material.REDSTONE_ORE,
        Material.LAPIS_ORE,
        Material.COPPER_ORE,

        // Building Blocks
        Material.BRICKS,
        Material.STONE_BRICKS,
        Material.MOSSY_STONE_BRICKS,
        Material.DEEPSLATE_BRICKS,
        Material.QUARTZ_BLOCK,
        Material.PRISMARINE,
        Material.DARK_PRISMARINE,

        // Storage / Functional
        Material.CHEST,
        Material.BARREL,
        Material.ENDER_CHEST,
        Material.CRAFTING_TABLE,
        Material.FURNACE,
        Material.ANVIL,
        Material.ENCHANTING_TABLE,
        Material.BREWING_STAND,
        Material.BEACON,
        Material.CONDUIT,

        // Decorative
        Material.BOOKSHELF,
        Material.LANTERN,
        Material.SOUL_LANTERN,
        Material.CAMPFIRE,
        Material.FLOWER_POT,
        Material.CAKE,
        Material.BELL,
        Material.JUKEBOX,
        Material.NOTE_BLOCK,

        // Nether / End
        Material.NETHERRACK,
        Material.NETHER_BRICKS,
        Material.SOUL_SAND,
        Material.GLOWSTONE,
        Material.END_STONE,
        Material.PURPUR_BLOCK,
        Material.OBSIDIAN,
        Material.CRYING_OBSIDIAN,

        // Special
        Material.DRAGON_EGG,
        Material.NETHER_STAR,
        Material.HEART_OF_THE_SEA,
        Material.TOTEM_OF_UNDYING,
        Material.ELYTRA,
        Material.TRIDENT,
        Material.DIAMOND,
        Material.EMERALD,
        Material.AMETHYST_SHARD
    };

    public IconPickerGui(ServerClaim plugin, Player player, Claim claim) {
        super(plugin, player, "Pick Icon", 54);
        this.plugin = plugin;

        // Validate claim at constructor level
        if (!GuiValidator.validateClaim(plugin, player, claim, "IconPickerGui")) {
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

        // Row 0: Title bar
        setupTitleBar();

        // Rows 1-4: Icon options
        setupIconOptions();

        // Row 5: Navigation
        setupNavigationBar();

        fillEmpty(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
    }

    private void setupTitleBar() {
        ItemStack blackGlass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {0, 1, 2, 3, 5, 6, 7, 8}) {
            setItem(i, new GuiItem(blackGlass));
        }

        Material currentIcon = claim.getIcon() != null ? claim.getIcon() : Material.GRASS_BLOCK;
        ItemStack titleItem =
                new ItemBuilder(currentIcon)
                        .name("<gold>Select Claim Icon")
                        .lore(
                                "",
                                "<gray>Choose an icon for your claim",
                                "<gray>to display in menus and lists",
                                "",
                                "<gray>Current: <white>" + formatMaterialName(currentIcon))
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(titleItem));
    }

    private void setupIconOptions() {
        // Null-safe icon access
        Material currentIcon = claim.getIcon() != null ? claim.getIcon() : Material.GRASS_BLOCK;
        int slot = 9;

        for (Material icon : AVAILABLE_ICONS) {
            if (slot >= 45) break;

            boolean isSelected = icon == currentIcon;

            ItemStack iconItem =
                    new ItemBuilder(icon)
                            .name("<yellow>" + formatMaterialName(icon))
                            .lore(
                                    "",
                                    isSelected
                                            ? "<green>Currently Selected"
                                            : "<gray>Click to select",
                                    "",
                                    isSelected ? "<dark_gray>This is your current icon" : "")
                            .glow(isSelected)
                            .build();

            final Material selectedIcon = icon;
            setItem(
                    slot,
                    new GuiItem(
                            iconItem,
                            e -> {
                                // Safety check before saving
                                if (claim == null || selectedIcon == null) {
                                    TextUtil.send(viewer, "<red>Error: Cannot update icon!");
                                    viewer.closeInventory();
                                    return;
                                }

                                claim.setIcon(selectedIcon);
                                plugin.getRepository().saveClaimSettings(claim);
                                TextUtil.send(
                                        viewer,
                                        "<green>Claim icon changed to <yellow>"
                                                + formatMaterialName(selectedIcon));
                                viewer.closeInventory();
                                new ManageProfileInfoGui(plugin, viewer, claim).open();
                            }));

            slot++;
        }
    }

    private void setupNavigationBar() {
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) {
            if (i == 49) continue;
            setItem(i, new GuiItem(divider));
        }

        // Back button (slot 49)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to profile settings")
                        .build();
        setItem(
                49,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ManageProfileInfoGui(plugin, viewer, claim).open();
                        }));
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
                                "<gray>Cannot select icon:",
                                "<gray>Claim no longer exists",
                                "",
                                "<yellow>Try closing and reopening")
                        .build();
        setItem(22, new GuiItem(errorItem));

        ItemStack backItem = new ItemBuilder(Material.ARROW).name("<red>Back").build();
        setItem(49, new GuiItem(backItem, e -> viewer.closeInventory()));

        fillEmpty(new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build());
    }
}
