package net.serverplugins.claim.gui;

import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.CustomGroup;
import net.serverplugins.claim.models.ManagementPermission;
import net.serverplugins.claim.repository.ClaimGroupRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * GUI for editing custom group settings. Allows renaming, changing icon, changing color tag, and
 * deleting groups. Requires MANAGE_GROUPS permission.
 */
public class GroupSettingsGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;
    private final CustomGroup group;

    public GroupSettingsGui(ServerClaim plugin, Player player, Claim claim, CustomGroup group) {
        super(plugin, player, "Group Settings", 27);
        this.plugin = plugin;

        // Validate claim at constructor level
        if (!GuiValidator.validateClaim(plugin, player, claim, "GroupSettingsGui")) {
            this.claim = null;
            this.group = null;
            return;
        }

        // Validate group
        if (group == null) {
            TextUtil.send(player, "<red>Invalid group!");
            plugin.getLogger()
                    .warning("GroupSettingsGui: null group for player " + player.getName());
            this.claim = null;
            this.group = null;
            return;
        }

        // Validate group belongs to claim
        boolean groupExists =
                claim.getCustomGroups() != null
                        && claim.getCustomGroups().stream()
                                .anyMatch(g -> g != null && g.getId() == group.getId());

        if (!groupExists) {
            TextUtil.send(player, "<red>Group does not belong to this claim!");
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

        // Permission check
        if (!claim.isOwner(viewer.getUniqueId())
                && !claim.hasManagementPermission(
                        viewer.getUniqueId(), ManagementPermission.MANAGE_GROUPS)) {
            TextUtil.send(viewer, "<red>You don't have permission to manage groups!");
            viewer.closeInventory();
            return;
        }

        // Row 0: Title bar
        setupTitleBar();

        // Row 1: Settings options
        setupSettingsOptions();

        // Row 2: Navigation
        setupNavigationBar();

        fillEmpty(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
    }

    private void setupTitleBar() {
        ItemStack blackGlass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {0, 1, 2, 3, 5, 6, 7, 8}) {
            setItem(i, new GuiItem(blackGlass));
        }

        int memberCount = countMembersInGroup();
        ItemStack titleItem =
                new ItemBuilder(group.getIcon())
                        .name(group.getColorTag() + group.getName() + " Settings")
                        .lore(
                                "",
                                "<gray>Priority: <white>" + group.getPriority(),
                                "<gray>Members: <white>" + memberCount,
                                "<gray>Default: <white>" + (group.isDefault() ? "Yes" : "No"),
                                "",
                                "<dark_gray>Customize this group's",
                                "<dark_gray>appearance and settings")
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(titleItem));
    }

    private void setupSettingsOptions() {
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        setItem(9, new GuiItem(divider));
        setItem(17, new GuiItem(divider));

        // Rename button (slot 10)
        ItemStack renameItem =
                new ItemBuilder(Material.NAME_TAG)
                        .name("<yellow>Rename Group")
                        .lore(
                                "",
                                "<gray>Current: " + group.getColorTag() + group.getName(),
                                "",
                                "<yellow>Click to rename")
                        .build();
        setItem(
                10,
                new GuiItem(
                        renameItem,
                        e -> {
                            if (group.isDefault() && group.getName().equalsIgnoreCase("Owner")) {
                                TextUtil.send(viewer, "<red>Cannot rename the Owner group!");
                                return;
                            }
                            viewer.closeInventory();
                            TextUtil.send(viewer, "<yellow>Type the new group name in chat:");
                            TextUtil.send(viewer, "<gray>(Type 'cancel' to cancel)");
                            plugin.getClaimManager().awaitGroupRenameInput(viewer, claim, group);
                        }));

        // Change Icon button (slot 12)
        ItemStack iconItem =
                new ItemBuilder(group.getIcon())
                        .name("<aqua>Change Icon")
                        .lore(
                                "",
                                "<gray>Current: <white>" + formatMaterialName(group.getIcon()),
                                "",
                                "<dark_gray>Hold an item and click",
                                "<dark_gray>to use it as the icon",
                                "",
                                "<yellow>Click to change")
                        .build();
        setItem(
                12,
                new GuiItem(
                        iconItem,
                        e -> {
                            ItemStack cursorItem = viewer.getItemOnCursor();
                            if (cursorItem != null && cursorItem.getType() != Material.AIR) {
                                changeIcon(cursorItem.getType());
                            } else {
                                // Open icon picker
                                viewer.closeInventory();
                                new GroupIconPickerGui(plugin, viewer, claim, group).open();
                            }
                        }));

        // Change Color button (slot 14)
        ItemStack colorItem =
                new ItemBuilder(getColorMaterial(group.getColorTag()))
                        .name("<light_purple>Change Color Tag")
                        .lore(
                                "",
                                "<gray>Current: " + group.getColorTag() + "Sample Text",
                                "",
                                "<yellow>Click to change")
                        .build();
        setItem(
                14,
                new GuiItem(
                        colorItem,
                        e -> {
                            viewer.closeInventory();
                            new GroupColorPickerGui(plugin, viewer, claim, group).open();
                        }));

        // Delete button (slot 16) - only for non-default groups
        if (!group.isDefault()) {
            ItemStack deleteItem =
                    new ItemBuilder(Material.BARRIER)
                            .name("<red>Delete Group")
                            .lore(
                                    "",
                                    "<gray>Permanently delete this group",
                                    "",
                                    "<red>Members will be moved to Visitor!",
                                    "",
                                    "<yellow>Click to delete")
                            .build();
            setItem(
                    16,
                    new GuiItem(
                            deleteItem,
                            e -> {
                                deleteGroup();
                            }));
        } else {
            ItemStack cannotDeleteItem =
                    new ItemBuilder(Material.GRAY_DYE)
                            .name("<gray>Delete Group")
                            .lore("", "<red>Cannot delete default groups")
                            .build();
            setItem(16, new GuiItem(cannotDeleteItem));
        }
    }

    private void setupNavigationBar() {
        // Back button (slot 18)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to group management")
                        .build();
        setItem(
                18,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ManageGroupsGui(plugin, viewer, claim).open();
                        }));

        // Edit Permissions button (slot 22)
        ItemStack permsItem =
                new ItemBuilder(Material.COMPARATOR)
                        .name("<gold>Edit Permissions")
                        .lore(
                                "",
                                "<gray>Configure what this group",
                                "<gray>can and cannot do",
                                "",
                                "<yellow>Click to edit")
                        .build();
        setItem(
                22,
                new GuiItem(
                        permsItem,
                        e -> {
                            viewer.closeInventory();
                            new GroupPermissionsGui(plugin, viewer, claim, group).open();
                        }));

        // Fill rest with dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {19, 20, 21, 23, 24, 25, 26}) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void changeIcon(Material newIcon) {
        // Safety checks
        if (group == null || newIcon == null) {
            TextUtil.send(viewer, "<red>Error: Invalid icon!");
            return;
        }

        group.setIcon(newIcon);
        getGroupRepository().saveGroup(group);

        TextUtil.send(viewer, "<green>Changed group icon to <white>" + formatMaterialName(newIcon));
        reopenMenu();
    }

    private void deleteGroup() {
        // Safety checks
        if (group == null || claim == null) {
            TextUtil.send(viewer, "<red>Error: Invalid group or claim!");
            viewer.closeInventory();
            return;
        }

        // Prevent deleting default groups
        if (group.isDefault()) {
            TextUtil.send(viewer, "<red>Cannot delete default groups!");
            return;
        }

        // Move all members to Visitor group first
        CustomGroup visitorGroup = claim.getVisitorGroup();
        if (visitorGroup == null) {
            TextUtil.send(viewer, "<red>Cannot delete - no Visitor group found!");
            return;
        }

        // Null-safe member migration
        var memberGroupIds = claim.getMemberGroupIds();
        if (memberGroupIds != null) {
            memberGroupIds.entrySet().stream()
                    .filter(
                            entry ->
                                    entry != null
                                            && entry.getValue() != null
                                            && entry.getValue() == group.getId())
                    .map(java.util.Map.Entry::getKey)
                    .toList()
                    .forEach(
                            uuid -> {
                                if (uuid != null) {
                                    claim.setMemberCustomGroup(uuid, visitorGroup);
                                    plugin.getRepository()
                                            .saveMemberWithGroupId(
                                                    claim.getId(), uuid, visitorGroup.getId());
                                }
                            });
        }

        // Delete the group
        claim.removeCustomGroup(group);
        getGroupRepository().deleteGroup(group.getId());

        TextUtil.send(viewer, "<red>Deleted group <white>" + group.getName());
        viewer.closeInventory();
        new ManageGroupsGui(plugin, viewer, claim).open();
    }

    private int countMembersInGroup() {
        return (int)
                claim.getMemberGroupIds().values().stream()
                        .filter(id -> id == group.getId())
                        .count();
    }

    private ClaimGroupRepository getGroupRepository() {
        return new ClaimGroupRepository(plugin.getDatabase());
    }

    private Material getColorMaterial(String colorTag) {
        return switch (colorTag) {
            case "<red>" -> Material.RED_STAINED_GLASS_PANE;
            case "<gold>", "<orange>" -> Material.ORANGE_STAINED_GLASS_PANE;
            case "<yellow>" -> Material.YELLOW_STAINED_GLASS_PANE;
            case "<green>", "<lime>" -> Material.LIME_STAINED_GLASS_PANE;
            case "<dark_green>" -> Material.GREEN_STAINED_GLASS_PANE;
            case "<aqua>", "<cyan>" -> Material.CYAN_STAINED_GLASS_PANE;
            case "<blue>", "<dark_blue>" -> Material.BLUE_STAINED_GLASS_PANE;
            case "<light_blue>" -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case "<light_purple>", "<pink>" -> Material.PINK_STAINED_GLASS_PANE;
            case "<dark_purple>", "<purple>" -> Material.PURPLE_STAINED_GLASS_PANE;
            case "<white>" -> Material.WHITE_STAINED_GLASS_PANE;
            case "<gray>", "<grey>" -> Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            case "<dark_gray>", "<dark_grey>" -> Material.GRAY_STAINED_GLASS_PANE;
            case "<black>" -> Material.BLACK_STAINED_GLASS_PANE;
            default -> Material.WHITE_STAINED_GLASS_PANE;
        };
    }

    private String formatMaterialName(Material material) {
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

    private void reopenMenu() {
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            viewer.closeInventory();
                            new GroupSettingsGui(plugin, viewer, claim, group).open();
                        });
    }

    /** Show error state when claim/group is null. */
    private void showErrorState() {
        ItemStack errorItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Error")
                        .lore(
                                "",
                                "<gray>Cannot manage group settings:",
                                "<gray>Invalid claim or group data",
                                "",
                                "<yellow>Try closing and reopening")
                        .build();
        setItem(13, new GuiItem(errorItem));

        ItemStack backItem = new ItemBuilder(Material.ARROW).name("<red>Back").build();
        setItem(18, new GuiItem(backItem, e -> viewer.closeInventory()));

        fillEmpty(new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build());
    }
}
