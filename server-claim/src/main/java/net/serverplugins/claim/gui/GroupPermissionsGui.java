package net.serverplugins.claim.gui;

import java.util.EnumSet;
import java.util.Set;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimGroup;
import net.serverplugins.claim.models.ClaimPermission;
import net.serverplugins.claim.models.ClaimProfile;
import net.serverplugins.claim.models.CustomGroup;
import net.serverplugins.claim.models.GroupPermissions;
import net.serverplugins.claim.models.ManagementPermission;
import net.serverplugins.claim.repository.ClaimGroupRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * GUI for editing group permissions. Supports both legacy ClaimGroup and new CustomGroup systems.
 * Shows ClaimPermissions and ManagementPermissions for CustomGroups.
 */
public class GroupPermissionsGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;
    private final ClaimGroup legacyGroup;
    private final CustomGroup customGroup;
    private int page;
    private boolean showManagementPerms; // Toggle between claim and management permissions

    private static final int PERMISSIONS_PER_PAGE = 14;

    // Constructor for legacy ClaimGroup
    public GroupPermissionsGui(ServerClaim plugin, Player player, Claim claim, ClaimGroup group) {
        this(plugin, player, claim, group, null, 0, false);
    }

    public GroupPermissionsGui(
            ServerClaim plugin, Player player, Claim claim, ClaimGroup group, int page) {
        this(plugin, player, claim, group, null, page, false);
    }

    // Constructor for CustomGroup
    public GroupPermissionsGui(ServerClaim plugin, Player player, Claim claim, CustomGroup group) {
        this(plugin, player, claim, null, group, 0, false);
    }

    public GroupPermissionsGui(
            ServerClaim plugin, Player player, Claim claim, CustomGroup group, int page) {
        this(plugin, player, claim, null, group, page, false);
    }

    public GroupPermissionsGui(
            ServerClaim plugin,
            Player player,
            Claim claim,
            CustomGroup group,
            int page,
            boolean showManagement) {
        this(plugin, player, claim, null, group, page, showManagement);
    }

    private GroupPermissionsGui(
            ServerClaim plugin,
            Player player,
            Claim claim,
            ClaimGroup legacyGroup,
            CustomGroup customGroup,
            int page,
            boolean showManagementPerms) {
        super(plugin, player, "Group Permissions", 54);
        this.plugin = plugin;

        // Validate claim at constructor level
        if (!GuiValidator.validateClaim(plugin, player, claim, "GroupPermissionsGui")) {
            this.claim = null;
            this.legacyGroup = null;
            this.customGroup = null;
            this.page = 0;
            this.showManagementPerms = false;
            return;
        }

        // Validate at least one group is provided
        if (legacyGroup == null && customGroup == null) {
            TextUtil.send(player, "<red>No group specified!");
            plugin.getLogger()
                    .warning(
                            "GroupPermissionsGui: no group provided for player "
                                    + player.getName());
            this.claim = null;
            this.legacyGroup = null;
            this.customGroup = null;
            this.page = 0;
            this.showManagementPerms = false;
            return;
        }

        this.claim = claim;
        this.legacyGroup = legacyGroup;
        this.customGroup = customGroup;
        this.page = Math.max(0, page);
        this.showManagementPerms = showManagementPerms;
    }

    @Override
    protected void initializeItems() {
        // Safety check: claim was invalidated in constructor
        if (claim == null || (customGroup == null && legacyGroup == null)) {
            showErrorState();
            return;
        }

        // Permission check
        if (!claim.isOwner(viewer.getUniqueId())
                && !claim.hasManagementPermission(
                        viewer.getUniqueId(), ManagementPermission.MANAGE_GROUPS)) {
            TextUtil.send(viewer, "<red>You don't have permission to edit group permissions!");
            viewer.closeInventory();
            return;
        }

        if (customGroup != null) {
            setupCustomGroupPermissions();
        } else if (legacyGroup != null) {
            setupLegacyGroupPermissions();
        } else {
            showErrorState();
        }
    }

    /** Show error state when claim/group is null. */
    private void showErrorState() {
        ItemStack errorItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Error")
                        .lore(
                                "",
                                "<gray>Cannot manage permissions:",
                                "<gray>Invalid claim or group data",
                                "",
                                "<yellow>Try closing and reopening")
                        .build();
        setItem(22, new GuiItem(errorItem));

        ItemStack backItem = new ItemBuilder(Material.ARROW).name("<red>Back").build();
        setItem(45, new GuiItem(backItem, e -> viewer.closeInventory()));

        fillEmpty(new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build());
    }

    private void setupCustomGroupPermissions() {
        // Row 0: Title bar
        setupCustomGroupTitleBar();

        // Rows 1-3: Permission toggles (or management permissions)
        if (showManagementPerms) {
            setupManagementPermissions();
        } else {
            setupClaimPermissions();
        }

        // Row 4: Toggle and quick actions
        setupQuickActionsForCustomGroup();

        // Row 5: Navigation
        setupNavigationBarForCustomGroup();

        fillEmpty(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
    }

    private void setupLegacyGroupPermissions() {
        ClaimProfile activeProfile = claim.getActiveProfile();
        if (activeProfile == null) {
            TextUtil.send(viewer, "<red>No active profile found!");
            viewer.closeInventory();
            return;
        }

        GroupPermissions perms = activeProfile.getGroupPermissions();
        if (perms == null) {
            perms = new GroupPermissions(activeProfile.getId());
            activeProfile.setGroupPermissions(perms);
        }

        // Row 0: Title bar
        setupLegacyTitleBar();

        // Rows 1-3: Permission toggles
        setupLegacyPermissions(activeProfile, perms);

        // Row 4: Quick actions
        setupLegacyQuickActions(activeProfile, perms);

        // Row 5: Navigation
        setupLegacyNavigationBar();

        fillEmpty(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
    }

    private void setupCustomGroupTitleBar() {
        ItemStack blackGlass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {0, 1, 2, 3, 5, 6, 7, 8}) {
            setItem(i, new GuiItem(blackGlass));
        }

        int memberCount = countMembersInCustomGroup();
        String permType = showManagementPerms ? "Management" : "Claim";

        ItemStack titleItem =
                new ItemBuilder(customGroup.getIcon())
                        .name(
                                customGroup.getColorTag()
                                        + customGroup.getName()
                                        + " "
                                        + permType
                                        + " Permissions")
                        .lore(
                                "",
                                "<gray>Members in group: <white>" + memberCount,
                                "<gray>Priority: <white>" + customGroup.getPriority(),
                                "",
                                "<green>Green <dark_gray>= Allowed",
                                "<red>Red <dark_gray>= Denied",
                                "",
                                showManagementPerms
                                        ? "<gold>Showing: Management Permissions"
                                        : "<aqua>Showing: Claim Permissions",
                                "<dark_gray>Click toggle below to switch")
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(titleItem));
    }

    private void setupClaimPermissions() {
        ClaimPermission[] allPerms = ClaimPermission.values();
        int totalPerms = allPerms.length;
        int totalPages = Math.max(1, (int) Math.ceil((double) totalPerms / PERMISSIONS_PER_PAGE));

        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int startIndex = page * PERMISSIONS_PER_PAGE;
        int endIndex = Math.min(startIndex + PERMISSIONS_PER_PAGE, totalPerms);

        // Slots for permissions: rows 1-2 (7 slots each)
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

        Set<ClaimPermission> groupPerms = customGroup.getPermissions();

        int slotIndex = 0;
        for (int i = startIndex; i < endIndex && slotIndex < slots.length; i++) {
            ClaimPermission perm = allPerms[i];
            boolean enabled = groupPerms.contains(perm);

            ItemStack item =
                    new ItemBuilder(perm.getIcon())
                            .name((enabled ? "<green>" : "<red>") + perm.getDisplayName())
                            .lore(
                                    "",
                                    "<dark_gray>" + perm.getDescription(),
                                    "",
                                    "<gray>Status: " + (enabled ? "<green>ALLOWED" : "<red>DENIED"),
                                    "",
                                    "<yellow>Click to toggle")
                            .glow(enabled)
                            .build();

            final ClaimPermission permission = perm;
            setItem(
                    slots[slotIndex],
                    new GuiItem(
                            item,
                            e -> {
                                toggleClaimPermission(permission);
                            }));

            slotIndex++;
        }

        // Side dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {9, 17, 18, 26, 27, 35}) {
            setItem(i, new GuiItem(divider));
        }

        // Row 3: additional permissions or empty
        int[] row3Slots = {28, 29, 30, 31, 32, 33, 34};
        int remaining = endIndex - startIndex - 14;
        if (remaining > 0) {
            int startRow3 = startIndex + 14;
            for (int i = 0; i < Math.min(remaining, 7); i++) {
                int permIndex = startRow3 + i;
                if (permIndex < allPerms.length) {
                    ClaimPermission perm = allPerms[permIndex];
                    boolean enabled = groupPerms.contains(perm);

                    ItemStack item =
                            new ItemBuilder(perm.getIcon())
                                    .name((enabled ? "<green>" : "<red>") + perm.getDisplayName())
                                    .lore(
                                            "",
                                            "<dark_gray>" + perm.getDescription(),
                                            "",
                                            "<gray>Status: "
                                                    + (enabled ? "<green>ALLOWED" : "<red>DENIED"),
                                            "",
                                            "<yellow>Click to toggle")
                                    .glow(enabled)
                                    .build();

                    final ClaimPermission permission = perm;
                    setItem(
                            row3Slots[i],
                            new GuiItem(
                                    item,
                                    e -> {
                                        toggleClaimPermission(permission);
                                    }));
                }
            }
        }
    }

    private void setupManagementPermissions() {
        ManagementPermission[] allPerms = ManagementPermission.values();

        // Slots for permissions: rows 1-2
        int[] slots = {10, 11, 12, 13, 14, 19, 20, 21, 22, 23};

        Set<ManagementPermission> groupPerms = customGroup.getManagementPermissions();

        for (int i = 0; i < Math.min(allPerms.length, slots.length); i++) {
            ManagementPermission perm = allPerms[i];
            boolean enabled = groupPerms.contains(perm);

            ItemStack item =
                    new ItemBuilder(perm.getIcon())
                            .name((enabled ? "<green>" : "<red>") + perm.getDisplayName())
                            .lore(
                                    "",
                                    "<dark_gray>" + perm.getDescription(),
                                    "",
                                    "<gray>Status: " + (enabled ? "<green>ALLOWED" : "<red>DENIED"),
                                    "",
                                    "<yellow>Click to toggle")
                            .glow(enabled)
                            .build();

            final ManagementPermission permission = perm;
            setItem(
                    slots[i],
                    new GuiItem(
                            item,
                            e -> {
                                toggleManagementPermission(permission);
                            }));
        }

        // Side dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {9, 15, 16, 17, 18, 24, 25, 26, 27, 35}) {
            setItem(i, new GuiItem(divider));
        }

        // Fill row 3 with dividers
        for (int i = 28; i <= 34; i++) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void setupQuickActionsForCustomGroup() {
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {36, 44}) {
            setItem(i, new GuiItem(divider));
        }

        // Toggle permission type (slot 37)
        String otherType = showManagementPerms ? "Claim" : "Management";
        Material toggleMaterial = showManagementPerms ? Material.CHEST : Material.COMPARATOR;
        ItemStack toggleItem =
                new ItemBuilder(toggleMaterial)
                        .name("<gold>Switch to " + otherType + " Permissions")
                        .lore(
                                "",
                                "<gray>Currently viewing: "
                                        + (showManagementPerms
                                                ? "<gold>Management"
                                                : "<aqua>Claim"),
                                "",
                                "<yellow>Click to switch")
                        .build();
        setItem(
                37,
                new GuiItem(
                        toggleItem,
                        e -> {
                            showManagementPerms = !showManagementPerms;
                            page = 0;
                            reopenCustomGroupMenu();
                        }));

        // Enable All (slot 39)
        ItemStack enableAllItem =
                new ItemBuilder(Material.LIME_DYE)
                        .name("<green>Enable All")
                        .lore(
                                "",
                                "<gray>Allow all "
                                        + (showManagementPerms ? "management" : "claim")
                                        + " permissions",
                                "",
                                "<yellow>Click to enable all")
                        .build();
        setItem(
                39,
                new GuiItem(
                        enableAllItem,
                        e -> {
                            if (showManagementPerms) {
                                customGroup.setManagementPermissions(
                                        EnumSet.allOf(ManagementPermission.class));
                            } else {
                                customGroup.setPermissions(EnumSet.allOf(ClaimPermission.class));
                            }
                            saveCustomGroup();
                            String type = showManagementPerms ? "management" : "claim";
                            TextUtil.send(
                                    viewer,
                                    "<green>Enabled all "
                                            + type
                                            + " permissions for "
                                            + customGroup.getColorTag()
                                            + customGroup.getName());
                            reopenCustomGroupMenu();
                        }));

        // Reset to Defaults (slot 41)
        ItemStack resetItem =
                new ItemBuilder(Material.CLOCK)
                        .name("<yellow>Reset to Defaults")
                        .lore(
                                "",
                                "<gray>Reset to default permissions",
                                "<gray>for this group type",
                                "",
                                "<yellow>Click to reset")
                        .build();
        setItem(
                41,
                new GuiItem(
                        resetItem,
                        e -> {
                            if (showManagementPerms) {
                                // Default management: none for most groups
                                customGroup.setManagementPermissions(
                                        EnumSet.noneOf(ManagementPermission.class));
                            } else {
                                // Get default claim permissions based on group name
                                customGroup.setPermissions(getDefaultClaimPermissions());
                            }
                            saveCustomGroup();
                            TextUtil.send(
                                    viewer,
                                    "<yellow>Reset permissions to defaults for "
                                            + customGroup.getColorTag()
                                            + customGroup.getName());
                            reopenCustomGroupMenu();
                        }));

        // Disable All (slot 43)
        ItemStack disableAllItem =
                new ItemBuilder(Material.RED_DYE)
                        .name("<red>Disable All")
                        .lore(
                                "",
                                "<gray>Deny all "
                                        + (showManagementPerms ? "management" : "claim")
                                        + " permissions",
                                "",
                                "<yellow>Click to disable all")
                        .build();
        setItem(
                43,
                new GuiItem(
                        disableAllItem,
                        e -> {
                            if (showManagementPerms) {
                                customGroup.setManagementPermissions(
                                        EnumSet.noneOf(ManagementPermission.class));
                            } else {
                                customGroup.setPermissions(EnumSet.noneOf(ClaimPermission.class));
                            }
                            saveCustomGroup();
                            String type = showManagementPerms ? "management" : "claim";
                            TextUtil.send(
                                    viewer,
                                    "<red>Disabled all "
                                            + type
                                            + " permissions for "
                                            + customGroup.getColorTag()
                                            + customGroup.getName());
                            reopenCustomGroupMenu();
                        }));

        // Fill gaps
        for (int i : new int[] {38, 40, 42}) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void setupNavigationBarForCustomGroup() {
        int totalItems =
                showManagementPerms
                        ? ManagementPermission.values().length
                        : ClaimPermission.values().length;
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / PERMISSIONS_PER_PAGE));

        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();

        // Back button (slot 45)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to groups")
                        .build();
        setItem(
                45,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ManageGroupsGui(plugin, viewer, claim).open();
                        }));

        // Previous page (slot 47)
        if (page > 0 && !showManagementPerms) {
            ItemStack prevItem =
                    new ItemBuilder(Material.ARROW)
                            .name("<yellow>Previous Page")
                            .lore("<gray>Page " + page + "/" + totalPages)
                            .build();
            setItem(
                    47,
                    new GuiItem(
                            prevItem,
                            e -> {
                                page--;
                                reopenCustomGroupMenu();
                            }));
        } else {
            setItem(47, new GuiItem(divider));
        }

        // Page info (slot 49)
        ItemStack pageItem =
                new ItemBuilder(Material.PAPER)
                        .name("<gold>Page " + (page + 1) + "/" + totalPages)
                        .lore(
                                "",
                                "<gray>Total permissions: <white>" + totalItems,
                                "<gray>Type: <white>"
                                        + (showManagementPerms ? "Management" : "Claim"))
                        .build();
        setItem(49, new GuiItem(pageItem));

        // Next page (slot 51)
        if (page < totalPages - 1 && !showManagementPerms) {
            ItemStack nextItem =
                    new ItemBuilder(Material.ARROW)
                            .name("<yellow>Next Page")
                            .lore("<gray>Page " + (page + 2) + "/" + totalPages)
                            .build();
            setItem(
                    51,
                    new GuiItem(
                            nextItem,
                            e -> {
                                page++;
                                reopenCustomGroupMenu();
                            }));
        } else {
            setItem(51, new GuiItem(divider));
        }

        // Group Settings (slot 53)
        ItemStack settingsItem =
                new ItemBuilder(Material.COMPARATOR)
                        .name("<aqua>Group Settings")
                        .lore(
                                "",
                                "<gray>Edit group name, icon,",
                                "<gray>and color",
                                "",
                                "<yellow>Click to open")
                        .build();
        setItem(
                53,
                new GuiItem(
                        settingsItem,
                        e -> {
                            viewer.closeInventory();
                            new GroupSettingsGui(plugin, viewer, claim, customGroup).open();
                        }));

        // Fill remaining
        for (int i : new int[] {46, 48, 50, 52}) {
            setItem(i, new GuiItem(divider));
        }
    }

    // === Legacy ClaimGroup methods ===

    private void setupLegacyTitleBar() {
        ItemStack blackGlass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {0, 1, 2, 3, 5, 6, 7, 8}) {
            setItem(i, new GuiItem(blackGlass));
        }

        int memberCount =
                (int) claim.getMembers().values().stream().filter(g -> g == legacyGroup).count();

        ItemStack titleItem =
                new ItemBuilder(legacyGroup.getIcon())
                        .name(
                                legacyGroup.getColorTag()
                                        + legacyGroup.getDisplayName()
                                        + " Permissions")
                        .lore(
                                "",
                                "<gray>Members in group: <white>" + memberCount,
                                "",
                                "<green>Green <dark_gray>= Allowed",
                                "<red>Red <dark_gray>= Denied",
                                "",
                                "<dark_gray>Click permissions to toggle")
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(titleItem));
    }

    private void setupLegacyPermissions(ClaimProfile activeProfile, GroupPermissions perms) {
        ClaimPermission[] allPerms = ClaimPermission.values();
        int startIndex = page * PERMISSIONS_PER_PAGE;
        int endIndex = Math.min(startIndex + PERMISSIONS_PER_PAGE, allPerms.length);

        // Slots for permissions: rows 1-2
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

        int slotIndex = 0;
        for (int i = startIndex; i < endIndex && slotIndex < slots.length; i++) {
            ClaimPermission perm = allPerms[i];
            boolean enabled = perms.hasPermission(legacyGroup, perm);

            ItemStack item =
                    new ItemBuilder(perm.getIcon())
                            .name((enabled ? "<green>" : "<red>") + perm.getDisplayName())
                            .lore(
                                    "",
                                    "<dark_gray>" + perm.getDescription(),
                                    "",
                                    "<gray>Status: " + (enabled ? "<green>ALLOWED" : "<red>DENIED"),
                                    "",
                                    "<yellow>Click to toggle")
                            .glow(enabled)
                            .build();

            final ClaimPermission permission = perm;
            final GroupPermissions finalPerms = perms;
            setItem(
                    slots[slotIndex],
                    new GuiItem(
                            item,
                            e -> {
                                finalPerms.togglePermission(legacyGroup, permission);
                                activeProfile.setGroupPermissions(finalPerms);
                                plugin.getRepository().saveGroupPermissions(activeProfile);
                                reopenLegacyMenu();
                            }));

            slotIndex++;
        }

        // Dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {9, 17, 18, 26, 27, 35}) {
            setItem(i, new GuiItem(divider));
        }

        // Fill row 3
        for (int i = 28; i <= 34; i++) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void setupLegacyQuickActions(ClaimProfile activeProfile, GroupPermissions perms) {
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {36, 44}) {
            setItem(i, new GuiItem(divider));
        }

        // Enable All (slot 38)
        ItemStack enableAllItem =
                new ItemBuilder(Material.LIME_DYE)
                        .name("<green>Enable All")
                        .lore("", "<gray>Allow all permissions", "", "<yellow>Click to enable all")
                        .build();
        final GroupPermissions permsForEnable = perms;
        setItem(
                38,
                new GuiItem(
                        enableAllItem,
                        e -> {
                            for (ClaimPermission p : ClaimPermission.values()) {
                                permsForEnable.setPermission(legacyGroup, p, true);
                            }
                            activeProfile.setGroupPermissions(permsForEnable);
                            plugin.getRepository().saveGroupPermissions(activeProfile);
                            TextUtil.send(
                                    viewer,
                                    "<green>Enabled all permissions for "
                                            + legacyGroup.getColorTag()
                                            + legacyGroup.getDisplayName());
                            reopenLegacyMenu();
                        }));

        // Reset to Defaults (slot 40)
        ItemStack resetItem =
                new ItemBuilder(Material.CLOCK)
                        .name("<yellow>Reset to Defaults")
                        .lore(
                                "",
                                "<gray>Reset to default permissions",
                                "",
                                "<yellow>Click to reset")
                        .build();
        final GroupPermissions permsForReset = perms;
        setItem(
                40,
                new GuiItem(
                        resetItem,
                        e -> {
                            permsForReset.setPermissions(
                                    legacyGroup, legacyGroup.getDefaultPermissions());
                            activeProfile.setGroupPermissions(permsForReset);
                            plugin.getRepository().saveGroupPermissions(activeProfile);
                            TextUtil.send(
                                    viewer,
                                    "<yellow>Reset permissions for "
                                            + legacyGroup.getColorTag()
                                            + legacyGroup.getDisplayName());
                            reopenLegacyMenu();
                        }));

        // Disable All (slot 42)
        ItemStack disableAllItem =
                new ItemBuilder(Material.RED_DYE)
                        .name("<red>Disable All")
                        .lore("", "<gray>Deny all permissions", "", "<yellow>Click to disable all")
                        .build();
        final GroupPermissions permsForDisable = perms;
        setItem(
                42,
                new GuiItem(
                        disableAllItem,
                        e -> {
                            for (ClaimPermission p : ClaimPermission.values()) {
                                permsForDisable.setPermission(legacyGroup, p, false);
                            }
                            activeProfile.setGroupPermissions(permsForDisable);
                            plugin.getRepository().saveGroupPermissions(activeProfile);
                            TextUtil.send(
                                    viewer,
                                    "<red>Disabled all permissions for "
                                            + legacyGroup.getColorTag()
                                            + legacyGroup.getDisplayName());
                            reopenLegacyMenu();
                        }));

        // Fill gaps
        for (int i : new int[] {37, 39, 41, 43}) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void setupLegacyNavigationBar() {
        ClaimPermission[] allPerms = ClaimPermission.values();
        int totalPages = (int) Math.ceil((double) allPerms.length / PERMISSIONS_PER_PAGE);

        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();

        // Back button (slot 45)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to groups")
                        .build();
        setItem(
                45,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ManageGroupsGui(plugin, viewer, claim).open();
                        }));

        // Previous page (slot 47)
        if (page > 0) {
            ItemStack prevItem =
                    new ItemBuilder(Material.ARROW)
                            .name("<yellow>Previous Page")
                            .lore("<gray>Page " + page + "/" + totalPages)
                            .build();
            setItem(
                    47,
                    new GuiItem(
                            prevItem,
                            e -> {
                                page--;
                                reopenLegacyMenu();
                            }));
        } else {
            setItem(47, new GuiItem(divider));
        }

        // Page info (slot 49)
        ItemStack pageItem =
                new ItemBuilder(Material.PAPER)
                        .name("<gold>Page " + (page + 1) + "/" + totalPages)
                        .lore("", "<gray>Total permissions: <white>" + allPerms.length)
                        .build();
        setItem(49, new GuiItem(pageItem));

        // Next page (slot 51)
        if (page < totalPages - 1) {
            ItemStack nextItem =
                    new ItemBuilder(Material.ARROW)
                            .name("<yellow>Next Page")
                            .lore("<gray>Page " + (page + 2) + "/" + totalPages)
                            .build();
            setItem(
                    51,
                    new GuiItem(
                            nextItem,
                            e -> {
                                page++;
                                reopenLegacyMenu();
                            }));
        } else {
            setItem(51, new GuiItem(divider));
        }

        // Fill remaining
        for (int i : new int[] {46, 48, 50, 52, 53}) {
            setItem(i, new GuiItem(divider));
        }
    }

    // === Helper methods ===

    private void toggleClaimPermission(ClaimPermission perm) {
        Set<ClaimPermission> perms = customGroup.getPermissions();
        if (perms.contains(perm)) {
            perms.remove(perm);
        } else {
            perms.add(perm);
        }
        customGroup.setPermissions(perms);
        saveCustomGroup();
        reopenCustomGroupMenu();
    }

    private void toggleManagementPermission(ManagementPermission perm) {
        Set<ManagementPermission> perms = customGroup.getManagementPermissions();
        if (perms.contains(perm)) {
            perms.remove(perm);
        } else {
            perms.add(perm);
        }
        customGroup.setManagementPermissions(perms);
        saveCustomGroup();
        reopenCustomGroupMenu();
    }

    private void saveCustomGroup() {
        new ClaimGroupRepository(plugin.getDatabase()).saveGroup(customGroup);
    }

    private int countMembersInCustomGroup() {
        return (int)
                claim.getMemberGroupIds().values().stream()
                        .filter(id -> id == customGroup.getId())
                        .count();
    }

    private Set<ClaimPermission> getDefaultClaimPermissions() {
        String groupName = customGroup.getName().toLowerCase();
        return switch (groupName) {
            case "owner" -> EnumSet.allOf(ClaimPermission.class);
            case "admin" -> EnumSet.allOf(ClaimPermission.class);
            case "friend" ->
                    EnumSet.of(
                            ClaimPermission.ENTER_CLAIM,
                            ClaimPermission.USE_DOORS,
                            ClaimPermission.USE_FENCE_GATES,
                            ClaimPermission.DAMAGE_HOSTILE,
                            ClaimPermission.OPEN_CONTAINERS,
                            ClaimPermission.INTERACT_ENTITIES,
                            ClaimPermission.DAMAGE_PASSIVE,
                            ClaimPermission.USE_REDSTONE,
                            ClaimPermission.PICKUP_ITEMS,
                            ClaimPermission.DROP_ITEMS,
                            ClaimPermission.USE_BREWING_STANDS,
                            ClaimPermission.USE_ANVILS,
                            ClaimPermission.RIDE_VEHICLES);
            case "acquaintance" ->
                    EnumSet.of(
                            ClaimPermission.ENTER_CLAIM,
                            ClaimPermission.USE_DOORS,
                            ClaimPermission.USE_FENCE_GATES,
                            ClaimPermission.DAMAGE_HOSTILE,
                            ClaimPermission.RIDE_VEHICLES);
            case "visitor" -> EnumSet.of(ClaimPermission.ENTER_CLAIM);
            case "enemy" -> EnumSet.noneOf(ClaimPermission.class);
            default -> EnumSet.of(ClaimPermission.ENTER_CLAIM);
        };
    }

    private void reopenCustomGroupMenu() {
        final int currentPage = this.page;
        final boolean currentShowMgmt = this.showManagementPerms;
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            viewer.closeInventory();
                            new GroupPermissionsGui(
                                            plugin,
                                            viewer,
                                            claim,
                                            customGroup,
                                            currentPage,
                                            currentShowMgmt)
                                    .open();
                        });
    }

    private void reopenLegacyMenu() {
        final int currentPage = this.page;
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            viewer.closeInventory();
                            new GroupPermissionsGui(plugin, viewer, claim, legacyGroup, currentPage)
                                    .open();
                        });
    }
}
