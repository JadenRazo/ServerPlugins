package net.serverplugins.claim.gui;

import java.util.*;
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
 * GUI for managing claim members. Displays a paginated list of all members with their assigned
 * groups. Requires MANAGE_MEMBERS permission to access.
 */
public class ManageMembersGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;
    private int page;

    private static final int MEMBERS_PER_PAGE = 28; // Slots 10-16 and 19-25 and 28-34 and 37-43

    public ManageMembersGui(ServerClaim plugin, Player player, Claim claim) {
        this(plugin, player, claim, 0);
    }

    public ManageMembersGui(ServerClaim plugin, Player player, Claim claim, int page) {
        super(plugin, player, "Manage Members", 54);
        this.plugin = plugin;
        this.page = page;

        // Validate claim exists
        if (!GuiValidator.validateClaim(plugin, player, claim, "ManageMembersGui")) {
            this.claim = null;
            return;
        }

        this.claim = claim;
    }

    @Override
    protected void initializeItems() {
        // Safety check
        if (claim == null) {
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

        // Row 0: Title bar
        setupTitleBar();

        // Rows 1-4: Member list (slots 10-16, 19-25, 28-34, 37-43)
        setupMembersList();

        // Row 5: Navigation and actions
        setupNavigationBar();

        fillEmpty(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
    }

    private void setupTitleBar() {
        ItemStack blackGlass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {0, 1, 2, 3, 5, 6, 7, 8}) {
            setItem(i, new GuiItem(blackGlass));
        }

        int memberCount = getAllMembers().size();
        ItemStack titleItem =
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name("<gold>Members")
                        .lore(
                                "",
                                "<gray>Claim: <white>" + claim.getName(),
                                "<gray>Total Members: <white>" + memberCount,
                                "",
                                "<dark_gray>Click a member to manage",
                                "<dark_gray>their group and permissions")
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(titleItem));
    }

    private void setupMembersList() {
        List<MemberEntry> members = getAllMembers();
        int totalPages = Math.max(1, (int) Math.ceil((double) members.size() / MEMBERS_PER_PAGE));

        // Clamp page to valid range
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int startIndex = page * MEMBERS_PER_PAGE;
        int endIndex = Math.min(startIndex + MEMBERS_PER_PAGE, members.size());

        // Slots for members (4 rows of 7)
        int[] slots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        // Side dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {9, 17, 18, 26, 27, 35, 36, 44}) {
            setItem(i, new GuiItem(divider));
        }

        int slotIndex = 0;
        for (int i = startIndex; i < endIndex && slotIndex < slots.length; i++) {
            MemberEntry entry = members.get(i);
            OfflinePlayer member = Bukkit.getOfflinePlayer(entry.uuid);
            String name = member.getName() != null ? member.getName() : "Unknown";

            String groupName;
            String groupColorTag;

            if (entry.customGroup != null) {
                groupName = entry.customGroup.getName();
                groupColorTag = entry.customGroup.getColorTag();
            } else if (entry.legacyGroup != null) {
                groupName = entry.legacyGroup.getDisplayName();
                groupColorTag = entry.legacyGroup.getColorTag();
            } else {
                groupName = "Visitor";
                groupColorTag = "<yellow>";
            }

            ItemStack head =
                    new ItemBuilder(Material.PLAYER_HEAD)
                            .name(groupColorTag + name)
                            .lore(
                                    "",
                                    "<gray>Group: " + groupColorTag + groupName,
                                    "",
                                    "<yellow>Click to manage")
                            .build();

            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(member);
                head.setItemMeta(meta);
            }

            final UUID memberUuid = entry.uuid;
            final String memberName = name;
            setItem(
                    slots[slotIndex],
                    new GuiItem(
                            head,
                            e -> {
                                viewer.closeInventory();
                                new MemberActionsGui(plugin, viewer, claim, memberUuid, memberName)
                                        .open();
                            }));

            slotIndex++;
        }
    }

    private void setupNavigationBar() {
        List<MemberEntry> members = getAllMembers();
        int totalPages = Math.max(1, (int) Math.ceil((double) members.size() / MEMBERS_PER_PAGE));

        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();

        // Back button (slot 45)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to claim settings")
                        .build();
        setItem(
                45,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ClaimSettingsGui(plugin, viewer, claim).open();
                        }));

        // Previous page (slot 47)
        if (page > 0) {
            ItemStack prevItem =
                    new ItemBuilder(Material.ARROW)
                            .name("<yellow>Previous Page")
                            .lore("", "<gray>Page " + page + "/" + totalPages)
                            .build();
            setItem(
                    47,
                    new GuiItem(
                            prevItem,
                            e -> {
                                page--;
                                reopenMenu();
                            }));
        } else {
            setItem(47, new GuiItem(divider));
        }

        // Add member button (slot 49)
        ItemStack addItem =
                new ItemBuilder(Material.LIME_DYE)
                        .name("<green>Add Member")
                        .lore("", "<gray>Add a player to your claim", "", "<yellow>Click to add")
                        .glow(true)
                        .build();
        setItem(
                49,
                new GuiItem(
                        addItem,
                        e -> {
                            viewer.closeInventory();
                            TextUtil.send(
                                    viewer, "<yellow>Type the player name in chat to add them:");
                            TextUtil.send(viewer, "<gray>(Type 'cancel' to cancel)");
                            plugin.getClaimManager().awaitTrustInput(viewer, claim);
                        }));

        // Next page (slot 51)
        if (page < totalPages - 1) {
            ItemStack nextItem =
                    new ItemBuilder(Material.ARROW)
                            .name("<yellow>Next Page")
                            .lore("", "<gray>Page " + (page + 2) + "/" + totalPages)
                            .build();
            setItem(
                    51,
                    new GuiItem(
                            nextItem,
                            e -> {
                                page++;
                                reopenMenu();
                            }));
        } else {
            setItem(51, new GuiItem(divider));
        }

        // Page info (slot 53)
        ItemStack pageItem =
                new ItemBuilder(Material.PAPER)
                        .name("<gold>Page " + (page + 1) + "/" + totalPages)
                        .lore("", "<gray>Total members: <white>" + members.size())
                        .build();
        setItem(53, new GuiItem(pageItem));

        // Fill remaining bottom row
        for (int i : new int[] {46, 48, 50, 52}) {
            setItem(i, new GuiItem(divider));
        }
    }

    /** Get all members from both legacy and custom group systems. */
    private List<MemberEntry> getAllMembers() {
        List<MemberEntry> entries = new ArrayList<>();
        Set<UUID> processedUuids = new HashSet<>();

        // Process custom group members first (v2.1 system)
        for (Map.Entry<UUID, Integer> entry : claim.getMemberGroupIds().entrySet()) {
            UUID uuid = entry.getKey();
            Integer groupId = entry.getValue();
            CustomGroup group = claim.getCustomGroupById(groupId);
            entries.add(new MemberEntry(uuid, group, null));
            processedUuids.add(uuid);
        }

        // Process legacy members that aren't already in custom groups
        for (Map.Entry<UUID, ClaimGroup> entry : claim.getMembers().entrySet()) {
            UUID uuid = entry.getKey();
            if (!processedUuids.contains(uuid)) {
                entries.add(new MemberEntry(uuid, null, entry.getValue()));
            }
        }

        // Sort by group priority (highest first), then by name
        entries.sort(
                (a, b) -> {
                    int priorityA =
                            a.customGroup != null
                                    ? a.customGroup.getPriority()
                                    : (a.legacyGroup != null ? a.legacyGroup.ordinal() * 20 : 0);
                    int priorityB =
                            b.customGroup != null
                                    ? b.customGroup.getPriority()
                                    : (b.legacyGroup != null ? b.legacyGroup.ordinal() * 20 : 0);

                    if (priorityA != priorityB) {
                        return Integer.compare(priorityB, priorityA); // Higher priority first
                    }

                    // Same priority, sort by name
                    OfflinePlayer playerA = Bukkit.getOfflinePlayer(a.uuid);
                    OfflinePlayer playerB = Bukkit.getOfflinePlayer(b.uuid);
                    String nameA = playerA.getName() != null ? playerA.getName() : "";
                    String nameB = playerB.getName() != null ? playerB.getName() : "";
                    return nameA.compareToIgnoreCase(nameB);
                });

        return entries;
    }

    private void reopenMenu() {
        final int currentPage = this.page;
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            viewer.closeInventory();
                            new ManageMembersGui(plugin, viewer, claim, currentPage).open();
                        });
    }

    /** Helper class to hold member data from either system. */
    private record MemberEntry(UUID uuid, CustomGroup customGroup, ClaimGroup legacyGroup) {}
}
