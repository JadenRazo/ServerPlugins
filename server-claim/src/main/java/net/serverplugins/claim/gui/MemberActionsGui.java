package net.serverplugins.claim.gui;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimGroup;
import net.serverplugins.claim.models.CustomGroup;
import net.serverplugins.claim.models.ManagementPermission;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * GUI for managing an individual claim member. Allows promoting, demoting, changing groups, and
 * removing members. Requires MANAGE_MEMBERS permission.
 */
public class MemberActionsGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;
    private final UUID memberUuid;
    private final String memberName;

    public MemberActionsGui(
            ServerClaim plugin, Player player, Claim claim, UUID memberUuid, String memberName) {
        super(plugin, player, "Member Actions", 27);
        this.plugin = plugin;

        // Validate claim at constructor level
        if (!GuiValidator.validateClaim(plugin, player, claim, "MemberActionsGui")) {
            this.claim = null;
            this.memberUuid = null;
            this.memberName = null;
            return;
        }

        // Validate member UUID
        if (memberUuid == null) {
            TextUtil.send(player, "<red>Invalid member!");
            plugin.getLogger()
                    .warning("MemberActionsGui: null member UUID for player " + player.getName());
            this.claim = null;
            this.memberUuid = null;
            this.memberName = null;
            return;
        }

        // Validate member exists in claim
        if (!claim.isMember(memberUuid)) {
            TextUtil.send(player, "<red>Player is not a member of this claim!");
            this.claim = null;
            this.memberUuid = null;
            this.memberName = null;
            return;
        }

        this.claim = claim;
        this.memberUuid = memberUuid;
        this.memberName = memberName != null ? memberName : "Unknown";
    }

    @Override
    protected void initializeItems() {
        // Safety check: claim/member was invalidated in constructor
        if (claim == null || memberUuid == null) {
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

        // Re-validate member still exists (may have been removed)
        if (!validateMemberExists()) {
            showMemberRemovedState();
            return;
        }

        // Get current group info
        CustomGroup currentGroup = claim.getMemberCustomGroup(memberUuid);
        ClaimGroup legacyGroup = claim.getMemberGroup(memberUuid);

        String groupName;
        String groupColorTag;
        int currentPriority;

        if (currentGroup != null) {
            groupName = currentGroup.getName();
            groupColorTag = currentGroup.getColorTag();
            currentPriority = currentGroup.getPriority();
        } else if (legacyGroup != null) {
            groupName = legacyGroup.getDisplayName();
            groupColorTag = legacyGroup.getColorTag();
            currentPriority = legacyGroup.ordinal() * 20;
        } else {
            groupName = "Visitor";
            groupColorTag = "<yellow>";
            currentPriority = 10;
        }

        // Row 0: Title bar with player info
        setupTitleBar(groupName, groupColorTag);

        // Row 1: Action buttons
        setupActionButtons(currentGroup, legacyGroup, currentPriority);

        // Row 2: Navigation
        setupNavigationBar();

        fillEmpty(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
    }

    private void setupTitleBar(String groupName, String groupColorTag) {
        ItemStack blackGlass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {0, 1, 2, 3, 5, 6, 7, 8}) {
            setItem(i, new GuiItem(blackGlass));
        }

        // Player head in center
        ItemStack head =
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name(groupColorTag + memberName)
                        .lore(
                                "",
                                "<gray>Current Group: " + groupColorTag + groupName,
                                "",
                                "<dark_gray>Manage this member's",
                                "<dark_gray>group assignment")
                        .glow(true)
                        .build();

        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberUuid);
            meta.setOwningPlayer(member);
            head.setItemMeta(meta);
        }
        setItem(4, new GuiItem(head));
    }

    private void setupActionButtons(
            CustomGroup currentGroup, ClaimGroup legacyGroup, int currentPriority) {
        List<CustomGroup> groups =
                claim.getCustomGroups().stream()
                        .sorted(Comparator.comparingInt(CustomGroup::getPriority).reversed())
                        .toList();

        // Find higher and lower priority groups
        CustomGroup higherGroup = null;
        CustomGroup lowerGroup = null;

        for (int i = 0; i < groups.size(); i++) {
            CustomGroup g = groups.get(i);
            if (currentGroup != null && g.getId() == currentGroup.getId()) {
                if (i > 0) {
                    higherGroup = groups.get(i - 1);
                }
                if (i < groups.size() - 1) {
                    lowerGroup = groups.get(i + 1);
                }
                break;
            } else if (currentGroup == null) {
                // Legacy group - find based on priority
                if (g.getPriority() > currentPriority
                        && (higherGroup == null || g.getPriority() < higherGroup.getPriority())) {
                    higherGroup = g;
                }
                if (g.getPriority() < currentPriority
                        && (lowerGroup == null || g.getPriority() > lowerGroup.getPriority())) {
                    lowerGroup = g;
                }
            }
        }

        // If no custom groups exist, fall back to showing legacy promotion/demotion
        if (groups.isEmpty() && legacyGroup != null) {
            setupLegacyActionButtons(legacyGroup);
            return;
        }

        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        setItem(9, new GuiItem(divider));
        setItem(17, new GuiItem(divider));

        // Promote button (slot 10)
        final CustomGroup finalHigherGroup = higherGroup;
        if (higherGroup != null && !higherGroup.getName().equalsIgnoreCase("Owner")) {
            ItemStack promoteItem =
                    new ItemBuilder(Material.LIME_DYE)
                            .name("<green>Promote")
                            .lore(
                                    "",
                                    "<gray>Move to: "
                                            + higherGroup.getColorTag()
                                            + higherGroup.getName(),
                                    "",
                                    "<yellow>Click to promote")
                            .glow(true)
                            .build();
            setItem(
                    10,
                    new GuiItem(
                            promoteItem,
                            e -> {
                                changeMemberGroup(finalHigherGroup);
                            }));
        } else {
            ItemStack noPromoteItem =
                    new ItemBuilder(Material.GRAY_DYE)
                            .name("<gray>Promote")
                            .lore(
                                    "",
                                    "<red>Already at highest group",
                                    "<red>or no higher group available")
                            .build();
            setItem(10, new GuiItem(noPromoteItem));
        }

        // Demote button (slot 12)
        final CustomGroup finalLowerGroup = lowerGroup;
        if (lowerGroup != null) {
            ItemStack demoteItem =
                    new ItemBuilder(Material.RED_DYE)
                            .name("<red>Demote")
                            .lore(
                                    "",
                                    "<gray>Move to: "
                                            + lowerGroup.getColorTag()
                                            + lowerGroup.getName(),
                                    "",
                                    "<yellow>Click to demote")
                            .build();
            setItem(
                    12,
                    new GuiItem(
                            demoteItem,
                            e -> {
                                changeMemberGroup(finalLowerGroup);
                            }));
        } else {
            ItemStack noDemoteItem =
                    new ItemBuilder(Material.GRAY_DYE)
                            .name("<gray>Demote")
                            .lore("", "<red>Already at lowest group")
                            .build();
            setItem(12, new GuiItem(noDemoteItem));
        }

        // Change Group button (slot 14)
        ItemStack changeGroupItem =
                new ItemBuilder(Material.WRITABLE_BOOK)
                        .name("<aqua>Change Group")
                        .lore(
                                "",
                                "<gray>Select a specific group",
                                "<gray>for this member",
                                "",
                                "<yellow>Click to choose group")
                        .build();
        setItem(
                14,
                new GuiItem(
                        changeGroupItem,
                        e -> {
                            viewer.closeInventory();
                            new GroupSelectGui(plugin, viewer, claim, memberUuid, memberName)
                                    .open();
                        }));

        // Remove button (slot 16)
        ItemStack removeItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Remove Member")
                        .lore(
                                "",
                                "<gray>Remove <white>" + memberName,
                                "<gray>from this claim",
                                "",
                                "<red>This cannot be undone!",
                                "",
                                "<yellow>Click to remove")
                        .build();
        setItem(
                16,
                new GuiItem(
                        removeItem,
                        e -> {
                            removeMember();
                        }));
    }

    private void setupLegacyActionButtons(ClaimGroup legacyGroup) {
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        setItem(9, new GuiItem(divider));
        setItem(17, new GuiItem(divider));

        ClaimGroup[] groups = ClaimGroup.values();
        int currentIdx = legacyGroup.ordinal();

        // Promote button (slot 10)
        if (currentIdx < groups.length - 1) {
            ClaimGroup higherGroup = groups[currentIdx + 1];
            ItemStack promoteItem =
                    new ItemBuilder(Material.LIME_DYE)
                            .name("<green>Promote")
                            .lore(
                                    "",
                                    "<gray>Move to: "
                                            + higherGroup.getColorTag()
                                            + higherGroup.getDisplayName(),
                                    "",
                                    "<yellow>Click to promote")
                            .glow(true)
                            .build();
            setItem(
                    10,
                    new GuiItem(
                            promoteItem,
                            e -> {
                                changeLegacyMemberGroup(higherGroup);
                            }));
        } else {
            ItemStack noPromoteItem =
                    new ItemBuilder(Material.GRAY_DYE)
                            .name("<gray>Promote")
                            .lore("", "<red>Already at highest group")
                            .build();
            setItem(10, new GuiItem(noPromoteItem));
        }

        // Demote button (slot 12)
        if (currentIdx > 0) {
            ClaimGroup lowerGroup = groups[currentIdx - 1];
            ItemStack demoteItem =
                    new ItemBuilder(Material.RED_DYE)
                            .name("<red>Demote")
                            .lore(
                                    "",
                                    "<gray>Move to: "
                                            + lowerGroup.getColorTag()
                                            + lowerGroup.getDisplayName(),
                                    "",
                                    "<yellow>Click to demote")
                            .build();
            setItem(
                    12,
                    new GuiItem(
                            demoteItem,
                            e -> {
                                changeLegacyMemberGroup(lowerGroup);
                            }));
        } else {
            ItemStack noDemoteItem =
                    new ItemBuilder(Material.GRAY_DYE)
                            .name("<gray>Demote")
                            .lore("", "<red>Already at lowest group")
                            .build();
            setItem(12, new GuiItem(noDemoteItem));
        }

        // Change Group button (slot 14)
        ItemStack changeGroupItem =
                new ItemBuilder(Material.WRITABLE_BOOK)
                        .name("<aqua>Change Group")
                        .lore(
                                "",
                                "<gray>Select a specific group",
                                "<gray>for this member",
                                "",
                                "<yellow>Click to choose group")
                        .build();
        setItem(
                14,
                new GuiItem(
                        changeGroupItem,
                        e -> {
                            viewer.closeInventory();
                            new GroupSelectGui(plugin, viewer, claim, memberUuid, memberName)
                                    .open();
                        }));

        // Remove button (slot 16)
        ItemStack removeItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Remove Member")
                        .lore(
                                "",
                                "<gray>Remove <white>" + memberName,
                                "<gray>from this claim",
                                "",
                                "<red>This cannot be undone!",
                                "",
                                "<yellow>Click to remove")
                        .build();
        setItem(
                16,
                new GuiItem(
                        removeItem,
                        e -> {
                            removeMember();
                        }));
    }

    private void setupNavigationBar() {
        // Back button (slot 18)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to members list")
                        .build();
        setItem(
                18,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ManageMembersGui(plugin, viewer, claim).open();
                        }));

        // Fill rest with dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 19; i <= 26; i++) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void changeMemberGroup(CustomGroup newGroup) {
        // Safety checks
        if (!validateMemberExists() || newGroup == null) {
            TextUtil.send(viewer, "<red>Cannot change group - invalid data!");
            viewer.closeInventory();
            return;
        }

        claim.setMemberCustomGroup(memberUuid, newGroup);
        plugin.getRepository().saveMemberWithGroupId(claim.getId(), memberUuid, newGroup.getId());

        TextUtil.send(
                viewer,
                "<yellow>Changed <white>"
                        + memberName
                        + " <yellow>to "
                        + newGroup.getColorTag()
                        + newGroup.getName());

        reopenMenu();
    }

    private void changeLegacyMemberGroup(ClaimGroup newGroup) {
        // Safety checks
        if (!validateMemberExists() || newGroup == null) {
            TextUtil.send(viewer, "<red>Cannot change group - invalid data!");
            viewer.closeInventory();
            return;
        }

        claim.setMemberGroup(memberUuid, newGroup);
        plugin.getRepository().saveMember(claim.getId(), memberUuid, newGroup);

        TextUtil.send(
                viewer,
                "<yellow>Changed <white>"
                        + memberName
                        + " <yellow>to "
                        + newGroup.getColorTag()
                        + newGroup.getDisplayName());

        reopenMenu();
    }

    private void removeMember() {
        // Safety check before removal
        if (!validateMemberExists()) {
            TextUtil.send(viewer, "<red>Member has already been removed!");
            viewer.closeInventory();
            new ManageMembersGui(plugin, viewer, claim).open();
            return;
        }

        // Prevent removing claim owner
        if (claim.isOwner(memberUuid)) {
            TextUtil.send(viewer, "<red>Cannot remove the claim owner!");
            return;
        }

        claim.removeMember(memberUuid);
        plugin.getRepository().removeMember(claim.getId(), memberUuid);

        TextUtil.send(viewer, "<red>Removed <white>" + memberName + " <red>from claim.");

        viewer.closeInventory();
        new ManageMembersGui(plugin, viewer, claim).open();
    }

    private void reopenMenu() {
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            viewer.closeInventory();
                            new MemberActionsGui(plugin, viewer, claim, memberUuid, memberName)
                                    .open();
                        });
    }

    /**
     * Validate that member still exists in claim.
     *
     * @return true if member exists, false otherwise
     */
    private boolean validateMemberExists() {
        if (claim == null || memberUuid == null) {
            return false;
        }

        return claim.isMember(memberUuid);
    }

    /** Show error state when claim/member is null. */
    private void showErrorState() {
        ItemStack errorItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Error")
                        .lore(
                                "",
                                "<gray>Cannot manage member:",
                                "<gray>Invalid claim or member data",
                                "",
                                "<yellow>Try closing and reopening")
                        .build();
        setItem(13, new GuiItem(errorItem));

        ItemStack backItem = new ItemBuilder(Material.ARROW).name("<red>Back").build();
        setItem(22, new GuiItem(backItem, e -> viewer.closeInventory()));

        fillEmpty(new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build());
    }

    /** Show member removed state. */
    private void showMemberRemovedState() {
        ItemStack removedItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<yellow>Member Removed")
                        .lore(
                                "",
                                "<gray>This player is no longer",
                                "<gray>a member of this claim",
                                "",
                                "<yellow>Click to go back")
                        .build();
        setItem(
                13,
                new GuiItem(
                        removedItem,
                        e -> {
                            viewer.closeInventory();
                            new ManageMembersGui(plugin, viewer, claim).open();
                        }));

        fillEmpty(new ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE).name(" ").build());
    }
}
