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
import net.serverplugins.claim.repository.ClaimGroupRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * GUI for managing claim groups. Displays all groups (6 defaults + custom) with options to edit
 * permissions and settings. Members management has been moved to ManageMembersGui. Requires
 * MANAGE_GROUPS permission to edit groups.
 */
public class ManageGroupsGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;
    private int page;

    private static final int GROUPS_PER_PAGE = 14; // Slots 10-16 and 19-25

    public ManageGroupsGui(ServerClaim plugin, Player player, Claim claim) {
        this(plugin, player, claim, 0);
    }

    public ManageGroupsGui(ServerClaim plugin, Player player, Claim claim, int page) {
        super(plugin, player, "Manage Groups", 45);
        this.plugin = plugin;

        // Validate claim at constructor level
        if (!GuiValidator.validateClaim(plugin, player, claim, "ManageGroupsGui")) {
            this.claim = null;
            this.page = 0;
            return;
        }

        this.claim = claim;
        this.page = Math.max(0, page);
    }

    @Override
    protected void initializeItems() {
        // Safety check: claim was invalidated in constructor
        if (claim == null) {
            showErrorState();
            return;
        }

        // Permission check for viewing (less restrictive than editing)
        boolean canManage =
                claim.isOwner(viewer.getUniqueId())
                        || claim.hasManagementPermission(
                                viewer.getUniqueId(), ManagementPermission.MANAGE_GROUPS);

        // Row 0: Title bar
        setupTitleBar(canManage);

        // Rows 1-2: Group list
        setupGroupsList(canManage);

        // Row 3: Quick navigation to members
        setupQuickNavRow();

        // Row 4: Navigation and actions
        setupNavigationBar(canManage);

        fillEmpty(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
    }

    private void setupTitleBar(boolean canManage) {
        ItemStack blackGlass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {0, 1, 2, 3, 5, 6, 7, 8}) {
            setItem(i, new GuiItem(blackGlass));
        }

        // Null-safe group access
        List<CustomGroup> groups = claim.getCustomGroups();
        if (groups == null) {
            groups = new ArrayList<>();
        }
        int groupCount = groups.isEmpty() ? ClaimGroup.values().length : groups.size();
        int customGroupCount =
                groups.isEmpty()
                        ? 0
                        : (int) groups.stream().filter(g -> g != null && !g.isDefault()).count();

        ItemStack titleItem =
                new ItemBuilder(Material.WRITABLE_BOOK)
                        .name("<gold>Group Management")
                        .lore(
                                "",
                                "<gray>Claim: <white>" + claim.getName(),
                                "<gray>Total Groups: <white>" + groupCount,
                                customGroupCount > 0
                                        ? "<gray>Custom Groups: <white>" + customGroupCount
                                        : "",
                                "",
                                "<dark_gray>Configure group permissions",
                                "<dark_gray>and settings",
                                "",
                                canManage ? "<green>You can edit groups" : "<red>View only")
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(titleItem));
    }

    private void setupGroupsList(boolean canManage) {
        List<CustomGroup> customGroups = claim.getCustomGroups();

        // Side dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {9, 17, 18, 26}) {
            setItem(i, new GuiItem(divider));
        }

        // Use custom groups if available, otherwise fall back to legacy groups
        if (!customGroups.isEmpty()) {
            setupCustomGroupsList(customGroups, canManage);
        } else {
            setupLegacyGroupsList(canManage);
        }
    }

    private void setupCustomGroupsList(List<CustomGroup> groups, boolean canManage) {
        // Sort by priority (highest first), Owner always first
        List<CustomGroup> sortedGroups =
                groups.stream()
                        .sorted(Comparator.comparingInt(CustomGroup::getPriority).reversed())
                        .toList();

        int totalPages =
                Math.max(1, (int) Math.ceil((double) sortedGroups.size() / GROUPS_PER_PAGE));
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int startIndex = page * GROUPS_PER_PAGE;
        int endIndex = Math.min(startIndex + GROUPS_PER_PAGE, sortedGroups.size());

        // Slots for groups
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

        int slotIndex = 0;
        for (int i = startIndex; i < endIndex && slotIndex < slots.length; i++) {
            CustomGroup group = sortedGroups.get(i);
            int memberCount = countMembersInCustomGroup(group);

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("<gray>Members: <white>" + memberCount);
            lore.add("<gray>Priority: <white>" + group.getPriority());
            lore.add("");

            // Show a few permissions
            Set<ClaimPermission> perms = group.getPermissions();
            if (perms.isEmpty()) {
                lore.add("<red>No permissions");
            } else {
                lore.add("<gray>Permissions: <white>" + perms.size());
            }

            // Show management permissions if any
            Set<ManagementPermission> mgmtPerms = group.getManagementPermissions();
            if (!mgmtPerms.isEmpty()) {
                lore.add("<gray>Management: <white>" + mgmtPerms.size());
            }

            lore.add("");
            if (canManage) {
                lore.add("<green>Left-click <dark_gray>to edit permissions");
                lore.add("<yellow>Right-click <dark_gray>to edit settings");
            } else {
                lore.add("<gray>View permissions only");
            }

            ItemStack groupItem =
                    new ItemBuilder(group.getIcon())
                            .name(group.getColorTag() + group.getName())
                            .lore(lore.toArray(new String[0]))
                            .glow(memberCount > 0)
                            .build();

            final CustomGroup selectedGroup = group;
            setItem(
                    slots[slotIndex],
                    GuiItem.withContext(
                            groupItem,
                            ctx -> {
                                if (!canManage) {
                                    TextUtil.send(
                                            viewer,
                                            "<red>You don't have permission to edit groups!");
                                    return;
                                }
                                if (ctx.isRightClick()) {
                                    // Open group settings
                                    viewer.closeInventory();
                                    new GroupSettingsGui(plugin, viewer, claim, selectedGroup)
                                            .open();
                                } else {
                                    // Open permissions editor
                                    viewer.closeInventory();
                                    new GroupPermissionsGui(plugin, viewer, claim, selectedGroup)
                                            .open();
                                }
                            }));

            slotIndex++;
        }
    }

    private void setupLegacyGroupsList(boolean canManage) {
        ClaimGroup[] groups = ClaimGroup.values();
        int[] slots = {10, 11, 12, 13, 14, 15, 16};

        for (int i = 0; i < Math.min(groups.length, slots.length); i++) {
            ClaimGroup group = groups[groups.length - 1 - i]; // Reverse order: Admin first

            int memberCount = countMembersInLegacyGroup(group);

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("<gray>Members: <white>" + memberCount);
            lore.add("");
            lore.add("<dark_gray>" + getGroupDescription(group));
            lore.add("");

            if (canManage) {
                lore.add("<yellow>Click to edit permissions");
            } else {
                lore.add("<gray>View permissions only");
            }

            ItemStack groupItem =
                    new ItemBuilder(group.getIcon())
                            .name(group.getColorTag() + group.getDisplayName())
                            .lore(lore.toArray(new String[0]))
                            .glow(memberCount > 0)
                            .build();

            final ClaimGroup selectedGroup = group;
            setItem(
                    slots[i],
                    new GuiItem(
                            groupItem,
                            e -> {
                                if (!canManage) {
                                    TextUtil.send(
                                            viewer,
                                            "<red>You don't have permission to edit groups!");
                                    return;
                                }
                                viewer.closeInventory();
                                new GroupPermissionsGui(plugin, viewer, claim, selectedGroup)
                                        .open();
                            }));
        }
    }

    private void setupQuickNavRow() {
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {27, 35}) {
            setItem(i, new GuiItem(divider));
        }

        // Manage Members button (slot 29)
        int memberCount = claim.getMembers().size() + claim.getMemberGroupIds().size();
        // Dedupe count
        Set<UUID> allMembers = new HashSet<>();
        allMembers.addAll(claim.getMembers().keySet());
        allMembers.addAll(claim.getMemberGroupIds().keySet());
        int totalMembers = allMembers.size();

        ItemStack membersItem =
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name("<aqua>Manage Members")
                        .lore(
                                "",
                                "<gray>Total Members: <white>" + totalMembers,
                                "",
                                "<dark_gray>Add, remove, or change",
                                "<dark_gray>member group assignments",
                                "",
                                "<yellow>Click to manage")
                        .build();
        setItem(
                29,
                new GuiItem(
                        membersItem,
                        e -> {
                            viewer.closeInventory();
                            new ManageMembersGui(plugin, viewer, claim).open();
                        }));

        // Initialize Groups button (slot 31) - for claims without custom groups
        if (claim.getCustomGroups().isEmpty()) {
            ItemStack initItem =
                    new ItemBuilder(Material.EMERALD)
                            .name("<green>Initialize Custom Groups")
                            .lore(
                                    "",
                                    "<gray>Create the default group set",
                                    "<gray>with customizable permissions",
                                    "",
                                    "<dark_gray>This enables advanced features",
                                    "<dark_gray>like management permissions",
                                    "",
                                    "<yellow>Click to initialize")
                            .glow(true)
                            .build();
            setItem(
                    31,
                    new GuiItem(
                            initItem,
                            e -> {
                                initializeCustomGroups();
                            }));
        } else {
            // Placeholder when groups already exist
            setItem(31, new GuiItem(divider));
        }

        // Group info (slot 33)
        ItemStack infoItem =
                new ItemBuilder(Material.OAK_SIGN)
                        .name("<yellow>Group Info")
                        .lore(
                                "",
                                "<gray>Groups control what members",
                                "<gray>can do in your claim.",
                                "",
                                "<white>Default groups:",
                                "<gold>  Owner <dark_gray>- Full control",
                                "<light_purple>  Admin <dark_gray>- Most permissions",
                                "<aqua>  Friend <dark_gray>- Build access",
                                "<green>  Acquaintance <dark_gray>- Basic access",
                                "<yellow>  Visitor <dark_gray>- Entry only",
                                "<red>  Enemy <dark_gray>- No access")
                        .build();
        setItem(33, new GuiItem(infoItem));

        // Fill gaps
        for (int i : new int[] {28, 30, 32, 34}) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void setupNavigationBar(boolean canManage) {
        List<CustomGroup> groups = claim.getCustomGroups();
        int totalPages =
                groups.isEmpty()
                        ? 1
                        : Math.max(1, (int) Math.ceil((double) groups.size() / GROUPS_PER_PAGE));

        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();

        // Back button (slot 36)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to claim settings")
                        .build();
        setItem(
                36,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ClaimSettingsGui(plugin, viewer, claim).open();
                        }));

        // Previous page (slot 38)
        if (page > 0) {
            ItemStack prevItem =
                    new ItemBuilder(Material.ARROW)
                            .name("<yellow>Previous Page")
                            .lore("<gray>Page " + page + "/" + totalPages)
                            .build();
            setItem(
                    38,
                    new GuiItem(
                            prevItem,
                            e -> {
                                page--;
                                reopenMenu();
                            }));
        } else {
            setItem(38, new GuiItem(divider));
        }

        // Add Group button (slot 40) - only if custom groups exist and user can manage
        if (!groups.isEmpty() && canManage) {
            ItemStack addItem =
                    new ItemBuilder(Material.LIME_DYE)
                            .name("<green>Create Custom Group")
                            .lore(
                                    "",
                                    "<gray>Create a new custom group",
                                    "<gray>with custom permissions",
                                    "",
                                    "<yellow>Click to create")
                            .glow(true)
                            .build();
            setItem(
                    40,
                    new GuiItem(
                            addItem,
                            e -> {
                                createCustomGroup();
                            }));
        } else {
            setItem(40, new GuiItem(divider));
        }

        // Next page (slot 42)
        if (page < totalPages - 1) {
            ItemStack nextItem =
                    new ItemBuilder(Material.ARROW)
                            .name("<yellow>Next Page")
                            .lore("<gray>Page " + (page + 2) + "/" + totalPages)
                            .build();
            setItem(
                    42,
                    new GuiItem(
                            nextItem,
                            e -> {
                                page++;
                                reopenMenu();
                            }));
        } else {
            setItem(42, new GuiItem(divider));
        }

        // Page indicator (slot 44)
        ItemStack pageItem =
                new ItemBuilder(Material.PAPER)
                        .name("<gold>Page " + (page + 1) + "/" + totalPages)
                        .lore(
                                "",
                                "<gray>Groups: <white>"
                                        + (groups.isEmpty()
                                                ? ClaimGroup.values().length
                                                : groups.size()))
                        .build();
        setItem(44, new GuiItem(pageItem));

        // Fill gaps
        for (int i : new int[] {37, 39, 41, 43}) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void initializeCustomGroups() {
        if (!claim.isOwner(viewer.getUniqueId())) {
            TextUtil.send(viewer, "<red>Only the claim owner can initialize custom groups!");
            return;
        }

        ClaimGroupRepository groupRepo = new ClaimGroupRepository(plugin.getDatabase());

        // Check if already initialized
        if (groupRepo.hasCustomGroups(claim.getId())) {
            // Reload groups from database
            List<CustomGroup> groups = groupRepo.getGroupsForClaim(claim.getId());
            claim.setCustomGroups(groups);
            TextUtil.send(viewer, "<yellow>Custom groups already exist. Reloaded from database.");
        } else {
            // Create default groups
            boolean success = groupRepo.createDefaultGroups(claim.getId());
            if (success) {
                List<CustomGroup> groups = groupRepo.getGroupsForClaim(claim.getId());
                claim.setCustomGroups(groups);
                TextUtil.send(viewer, "<green>Initialized custom groups for this claim!");
            } else {
                TextUtil.send(viewer, "<red>Failed to create custom groups. Please try again.");
                return;
            }
        }

        reopenMenu();
    }

    private void createCustomGroup() {
        // Find the next available priority (between lowest default and custom)
        List<CustomGroup> existing = claim.getCustomGroups();
        int newPriority = 5; // Default to low priority

        // Find a gap in priorities
        Set<Integer> usedPriorities = new HashSet<>();
        for (CustomGroup g : existing) {
            usedPriorities.add(g.getPriority());
        }

        // Find an unused priority between 1 and 99 (avoiding 0, 10, 25, 50, 100, 1000)
        for (int p = 15; p < 100; p += 5) {
            if (!usedPriorities.contains(p)) {
                newPriority = p;
                break;
            }
        }

        // Create new group
        CustomGroup newGroup = new CustomGroup();
        newGroup.setClaimId(claim.getId());
        newGroup.setName("New Group");
        newGroup.setColorTag("<white>");
        newGroup.setIcon(Material.WHITE_STAINED_GLASS_PANE);
        newGroup.setPriority(newPriority);
        newGroup.setDefault(false);
        // Start with visitor-like permissions
        newGroup.setPermissions(EnumSet.of(ClaimPermission.ENTER_CLAIM));

        ClaimGroupRepository groupRepo = new ClaimGroupRepository(plugin.getDatabase());
        boolean success = groupRepo.saveGroup(newGroup);

        if (success) {
            claim.addCustomGroup(newGroup);
            TextUtil.send(viewer, "<green>Created new custom group! Click it to configure.");
            reopenMenu();
        } else {
            TextUtil.send(viewer, "<red>Failed to create group. Please try again.");
        }
    }

    private String getGroupDescription(ClaimGroup group) {
        return switch (group) {
            case ADMIN -> "Full access to claim";
            case FRIEND -> "Can build and interact";
            case ACQUAINTANCE -> "Limited access";
            case VISITOR -> "Can enter claim only";
            case ENEMY -> "Hostile, blocked access";
        };
    }

    private int countMembersInCustomGroup(CustomGroup group) {
        return (int)
                claim.getMemberGroupIds().values().stream()
                        .filter(id -> id == group.getId())
                        .count();
    }

    private int countMembersInLegacyGroup(ClaimGroup group) {
        return (int) claim.getMembers().values().stream().filter(g -> g == group).count();
    }

    private void reopenMenu() {
        final int currentPage = this.page;
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            viewer.closeInventory();
                            new ManageGroupsGui(plugin, viewer, claim, currentPage).open();
                        });
    }

    /** Show error state when claim is null. */
    private void showErrorState() {
        ItemStack errorItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Error")
                        .lore(
                                "",
                                "<gray>Cannot manage groups:",
                                "<gray>Claim no longer exists",
                                "",
                                "<yellow>Try closing and reopening")
                        .build();
        setItem(22, new GuiItem(errorItem));

        ItemStack backItem = new ItemBuilder(Material.ARROW).name("<red>Back").build();
        setItem(36, new GuiItem(backItem, e -> viewer.closeInventory()));

        fillEmpty(new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build());
    }
}
