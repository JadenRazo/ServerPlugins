package net.serverplugins.claim.gui;

import java.util.*;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimGroup;
import net.serverplugins.claim.models.ClaimPermission;
import net.serverplugins.claim.models.CustomGroup;
import net.serverplugins.claim.models.ManagementPermission;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * GUI for selecting a group when adding a member or changing their group. Displays all available
 * groups (custom and default) for selection. Requires MANAGE_MEMBERS permission.
 */
public class GroupSelectGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;
    private final UUID targetUuid;
    private final String targetName;

    public GroupSelectGui(
            ServerClaim plugin, Player player, Claim claim, UUID targetUuid, String targetName) {
        super(plugin, player, "Select Group", 45);
        this.plugin = plugin;

        // Validate claim at constructor level
        if (!GuiValidator.validateClaim(plugin, player, claim, "GroupSelectGui")) {
            this.claim = null;
            this.targetUuid = null;
            this.targetName = null;
            return;
        }

        // Validate target UUID
        if (targetUuid == null) {
            TextUtil.send(player, "<red>Invalid target player!");
            plugin.getLogger()
                    .warning("GroupSelectGui: null target UUID for player " + player.getName());
            this.claim = null;
            this.targetUuid = null;
            this.targetName = null;
            return;
        }

        this.claim = claim;
        this.targetUuid = targetUuid;
        this.targetName = targetName != null ? targetName : "Unknown";
    }

    @Override
    protected void initializeItems() {
        // Safety check: claim/target was invalidated in constructor
        if (claim == null || targetUuid == null) {
            showErrorState();
            return;
        }

        // Permission check
        if (!claim.isOwner(viewer.getUniqueId())
                && !claim.hasManagementPermission(
                        viewer.getUniqueId(), ManagementPermission.MANAGE_MEMBERS)) {
            TextUtil.send(viewer, "<red>You don't have permission to manage members!");
            viewer.closeInventory();
            return;
        }

        // Row 0: Title bar with target player info
        setupTitleBar();

        // Rows 1-3: Group selection
        setupGroupSelection();

        // Row 4: Navigation
        setupNavigationBar();

        fillEmpty(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
    }

    private void setupTitleBar() {
        ItemStack blackGlass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {0, 1, 2, 3, 5, 6, 7, 8}) {
            setItem(i, new GuiItem(blackGlass));
        }

        // Target player head in center
        ItemStack head =
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name("<gold>" + targetName)
                        .lore(
                                "",
                                "<gray>Select a group for",
                                "<gray>this player",
                                "",
                                "<dark_gray>Click a group below",
                                "<dark_gray>to assign it")
                        .glow(true)
                        .build();

        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
            meta.setOwningPlayer(target);
            head.setItemMeta(meta);
        }
        setItem(4, new GuiItem(head));
    }

    private void setupGroupSelection() {
        List<CustomGroup> customGroups = claim.getCustomGroups();

        // Side dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {9, 17, 18, 26, 27, 35}) {
            setItem(i, new GuiItem(divider));
        }

        // Use custom groups if available
        if (!customGroups.isEmpty()) {
            setupCustomGroupSelection(customGroups);
        } else {
            // Fall back to legacy groups
            setupLegacyGroupSelection();
        }
    }

    private void setupCustomGroupSelection(List<CustomGroup> groups) {
        // Null-safe group filtering and sorting
        if (groups == null) {
            groups = new ArrayList<>();
        }

        // Sort groups by priority (highest first), excluding Owner
        List<CustomGroup> sortedGroups =
                groups.stream()
                        .filter(g -> g != null && !g.getName().equalsIgnoreCase("Owner"))
                        .sorted(Comparator.comparingInt(CustomGroup::getPriority).reversed())
                        .toList();

        // Handle empty groups list
        if (sortedGroups.isEmpty()) {
            showNoGroupsAvailable();
            return;
        }

        // Slots for groups: rows 1-3 (7 slots per row)
        int[] slots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
        };

        int slotIndex = 0;
        for (CustomGroup group : sortedGroups) {
            if (slotIndex >= slots.length) break;

            // Get current group to highlight if selected
            CustomGroup currentGroup = claim.getMemberCustomGroup(targetUuid);
            boolean isCurrentGroup = currentGroup != null && currentGroup.getId() == group.getId();

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("<gray>Priority: <white>" + group.getPriority());
            lore.add("");
            lore.add("<gray>Permissions:");

            Set<ClaimPermission> perms = group.getPermissions();
            if (perms.isEmpty()) {
                lore.add("<red>  None");
            } else {
                int count = 0;
                for (ClaimPermission perm : perms) {
                    if (count >= 4) {
                        lore.add("<gray>  ... and " + (perms.size() - 4) + " more");
                        break;
                    }
                    lore.add("<white>  - " + perm.getDisplayName());
                    count++;
                }
            }

            lore.add("");
            if (isCurrentGroup) {
                lore.add("<green>Currently assigned");
            } else {
                lore.add("<yellow>Click to assign this group");
            }

            ItemStack groupItem =
                    new ItemBuilder(group.getIcon())
                            .name(group.getColorTag() + group.getName())
                            .lore(lore.toArray(new String[0]))
                            .glow(isCurrentGroup)
                            .build();

            final CustomGroup selectedGroup = group;
            setItem(
                    slots[slotIndex],
                    new GuiItem(
                            groupItem,
                            e -> {
                                selectCustomGroup(selectedGroup);
                            }));

            slotIndex++;
        }
    }

    private void setupLegacyGroupSelection() {
        // Slots for legacy groups (5 groups centered)
        int[] slots = {11, 12, 13, 14, 15};

        int slotIndex = 0;
        for (ClaimGroup group : ClaimGroup.values()) {
            if (slotIndex >= slots.length) break;

            ClaimGroup currentGroup = claim.getMemberGroup(targetUuid);
            boolean isCurrentGroup = currentGroup == group;

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("<gray>Default permissions:");

            Set<ClaimPermission> perms = group.getDefaultPermissions();
            if (perms.isEmpty()) {
                lore.add("<red>  None");
            } else {
                int count = 0;
                for (ClaimPermission perm : perms) {
                    if (count >= 4) {
                        lore.add("<gray>  ... and " + (perms.size() - 4) + " more");
                        break;
                    }
                    lore.add("<white>  - " + perm.getDisplayName());
                    count++;
                }
            }

            lore.add("");
            if (isCurrentGroup) {
                lore.add("<green>Currently assigned");
            } else {
                lore.add("<yellow>Click to assign this group");
            }

            ItemStack groupItem =
                    new ItemBuilder(group.getIcon())
                            .name(group.getColorTag() + group.getDisplayName())
                            .lore(lore.toArray(new String[0]))
                            .glow(isCurrentGroup)
                            .build();

            final ClaimGroup selectedGroup = group;
            setItem(
                    slots[slotIndex],
                    new GuiItem(
                            groupItem,
                            e -> {
                                selectLegacyGroup(selectedGroup);
                            }));

            slotIndex++;
        }
    }

    private void setupNavigationBar() {
        // Back button (slot 36)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Cancel")
                        .lore("<gray>Go back without changing")
                        .build();
        setItem(
                36,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            // Check if member already exists to determine where to go back
                            if (claim.isMember(targetUuid)) {
                                new MemberActionsGui(plugin, viewer, claim, targetUuid, targetName)
                                        .open();
                            } else {
                                new ManageMembersGui(plugin, viewer, claim).open();
                            }
                        }));

        // Fill rest with dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 37; i <= 44; i++) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void selectCustomGroup(CustomGroup group) {
        // Safety checks
        if (claim == null || targetUuid == null || group == null) {
            TextUtil.send(viewer, "<red>Error: Invalid data!");
            viewer.closeInventory();
            return;
        }

        claim.setMemberCustomGroup(targetUuid, group);
        plugin.getRepository().saveMemberWithGroupId(claim.getId(), targetUuid, group.getId());

        String action = claim.isMember(targetUuid) ? "Changed" : "Added";
        TextUtil.send(
                viewer,
                "<green>"
                        + action
                        + " <white>"
                        + targetName
                        + " <green>to "
                        + group.getColorTag()
                        + group.getName()
                        + " <green>group.");

        viewer.closeInventory();
        new ManageMembersGui(plugin, viewer, claim).open();
    }

    private void selectLegacyGroup(ClaimGroup group) {
        // Safety checks
        if (claim == null || targetUuid == null || group == null) {
            TextUtil.send(viewer, "<red>Error: Invalid data!");
            viewer.closeInventory();
            return;
        }

        claim.setMemberGroup(targetUuid, group);
        plugin.getRepository().saveMember(claim.getId(), targetUuid, group);

        String action = claim.isMember(targetUuid) ? "Changed" : "Added";
        TextUtil.send(
                viewer,
                "<green>"
                        + action
                        + " <white>"
                        + targetName
                        + " <green>to "
                        + group.getColorTag()
                        + group.getDisplayName()
                        + " <green>group.");

        viewer.closeInventory();
        new ManageMembersGui(plugin, viewer, claim).open();
    }

    /** Show error state when claim/target is null. */
    private void showErrorState() {
        ItemStack errorItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Error")
                        .lore(
                                "",
                                "<gray>Cannot select group:",
                                "<gray>Invalid claim or player data",
                                "",
                                "<yellow>Try closing and reopening")
                        .build();
        setItem(22, new GuiItem(errorItem));

        ItemStack backItem = new ItemBuilder(Material.ARROW).name("<red>Back").build();
        setItem(36, new GuiItem(backItem, e -> viewer.closeInventory()));

        fillEmpty(new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build());
    }

    /** Show no groups available state. */
    private void showNoGroupsAvailable() {
        ItemStack noGroupsItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<yellow>No Groups Available")
                        .lore(
                                "",
                                "<gray>This claim has no groups",
                                "<gray>to assign members to",
                                "",
                                "<gray>Initialize groups first",
                                "",
                                "<yellow>Click to go back")
                        .build();
        setItem(
                22,
                new GuiItem(
                        noGroupsItem,
                        e -> {
                            viewer.closeInventory();
                            new ManageGroupsGui(plugin, viewer, claim).open();
                        }));

        fillEmpty(new ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE).name(" ").build());
    }
}
