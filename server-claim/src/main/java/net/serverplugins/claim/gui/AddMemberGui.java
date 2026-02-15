package net.serverplugins.claim.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimGroup;
import net.serverplugins.claim.models.ClaimPermission;
import net.serverplugins.claim.models.XpSource;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class AddMemberGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;
    private final UUID targetUuid;
    private final String targetName;

    public AddMemberGui(
            ServerClaim plugin, Player player, Claim claim, UUID targetUuid, String targetName) {
        super(
                plugin,
                player,
                "Add " + (targetName != null ? targetName : "Player") + " to Group",
                27);
        this.plugin = plugin;

        // Validate claim and member management permission at constructor level
        if (!GuiValidator.validateClaim(plugin, player, claim, "AddMemberGui")) {
            this.claim = null;
            this.targetUuid = null;
            this.targetName = null;
            return;
        }

        if (!claim.isOwner(player.getUniqueId()) && !player.hasPermission("serverclaim.admin")) {
            TextUtil.send(player, "<red>You don't have permission to manage members!");
            this.claim = null;
            this.targetUuid = null;
            this.targetName = null;
            return;
        }

        // Validate target UUID
        if (targetUuid == null) {
            TextUtil.send(player, "<red>Invalid target player!");
            plugin.getLogger()
                    .warning("AddMemberGui: null target UUID for player " + player.getName());
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
        // Safety check: claim was invalidated in constructor
        if (claim == null || targetUuid == null) {
            showErrorState();
            return;
        }

        // Check if we can add more members
        if (!canAddMoreMembers()) {
            showMemberLimitReached();
            return;
        }

        // Check if player is already a member
        if (claim.isMember(targetUuid)) {
            showAlreadyMember();
            return;
        }

        // Target player head in center top
        ItemStack targetHead =
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name("<gold>" + targetName)
                        .lore("", "<gray>Select a group to add", "<gray>this player to")
                        .build();

        SkullMeta meta = (SkullMeta) targetHead.getItemMeta();
        if (meta != null) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);
            if (target != null) {
                meta.setOwningPlayer(target);
                targetHead.setItemMeta(meta);
            }
        }
        setItem(4, new GuiItem(targetHead));

        // Group buttons (slots 10-14)
        int slot = 10;
        for (ClaimGroup group : ClaimGroup.values()) {
            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("<gray>Default permissions:");

            Set<ClaimPermission> perms = group.getDefaultPermissions();
            if (perms.isEmpty()) {
                lore.add("<red>  None");
            } else {
                int count = 0;
                for (ClaimPermission perm : perms) {
                    if (count >= 5) {
                        lore.add("<gray>  ... and " + (perms.size() - 5) + " more");
                        break;
                    }
                    lore.add("<white>  - " + perm.getDisplayName());
                    count++;
                }
            }

            lore.add("");
            lore.add("<yellow>Click to add to this group");

            ItemStack groupItem =
                    new ItemBuilder(group.getIcon())
                            .name(group.getColorTag() + group.getDisplayName())
                            .lore(lore.toArray(new String[0]))
                            .build();

            final ClaimGroup selectedGroup = group;
            setItem(
                    slot,
                    new GuiItem(
                            groupItem,
                            e -> {
                                addMemberToGroup(selectedGroup);
                            }));
            slot++;
        }

        // Cancel button
        ItemStack cancelItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Cancel")
                        .lore("", "<gray>Go back without adding")
                        .build();
        setItem(
                22,
                new GuiItem(
                        cancelItem,
                        e -> {
                            viewer.closeInventory();
                            new ManageGroupsGui(plugin, viewer, claim).open();
                        }));

        fillEmpty(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
    }

    private void addMemberToGroup(ClaimGroup group) {
        // Final safety check before adding
        if (claim == null || targetUuid == null) {
            TextUtil.send(viewer, "<red>Error: Claim or player data is invalid!");
            viewer.closeInventory();
            return;
        }

        if (group == null) {
            TextUtil.send(viewer, "<red>Error: Invalid group selected!");
            return;
        }

        // Check member limit one more time
        if (!canAddMoreMembers()) {
            TextUtil.send(viewer, "<red>Cannot add more members - limit reached!");
            viewer.closeInventory();
            return;
        }

        claim.setMemberGroup(targetUuid, group);
        plugin.getRepository().saveMember(claim.getId(), targetUuid, group);

        // Grant XP for adding a new member
        if (plugin.getLevelManager() != null) {
            plugin.getLevelManager()
                    .grantXp(claim.getId(), viewer.getUniqueId(), XpSource.MEMBER_ADDED);
        }

        TextUtil.send(
                viewer,
                "<green>Added <white>"
                        + targetName
                        + " <green>to "
                        + group.getColorTag()
                        + group.getDisplayName()
                        + " <green>group.");

        viewer.closeInventory();
        new ManageGroupsGui(plugin, viewer, claim).open();
    }

    /**
     * Check if claim can accept more members.
     *
     * @return true if can add more members, false if limit reached
     */
    private boolean canAddMoreMembers() {
        if (claim == null) {
            return false;
        }

        // Get current member count - getTrustedPlayers returns Set<UUID>
        var members = claim.getTrustedPlayers();
        int currentCount = members != null ? members.size() : 0;

        // Check against config limit (default 20)
        int maxMembers = plugin.getConfig().getInt("claims.max-members", 20);

        return currentCount < maxMembers;
    }

    /** Show error state when claim/target is null. */
    private void showErrorState() {
        ItemStack errorItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Error")
                        .lore(
                                "",
                                "<gray>Cannot add member:",
                                "<gray>Invalid claim or player data",
                                "",
                                "<yellow>Try closing and reopening")
                        .build();
        setItem(13, new GuiItem(errorItem));

        ItemStack backItem = new ItemBuilder(Material.ARROW).name("<red>Back").build();
        setItem(22, new GuiItem(backItem, e -> viewer.closeInventory()));

        fillEmpty(new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name(" ").build());
    }

    /** Show member limit reached state. */
    private void showMemberLimitReached() {
        var members = claim.getTrustedPlayers();
        int currentCount = members != null ? members.size() : 0;
        int maxMembers = plugin.getConfig().getInt("claims.max-members", 20);

        ItemStack limitItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Member Limit Reached")
                        .lore(
                                "",
                                "<gray>Current members: <white>" + currentCount + "/" + maxMembers,
                                "",
                                "<gray>Remove a member before",
                                "<gray>adding new ones",
                                "",
                                "<yellow>Click to go back")
                        .build();
        setItem(
                13,
                new GuiItem(
                        limitItem,
                        e -> {
                            viewer.closeInventory();
                            new ManageMembersGui(plugin, viewer, claim).open();
                        }));

        fillEmpty(new ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE).name(" ").build());
    }

    /** Show already a member state. */
    private void showAlreadyMember() {
        ItemStack alreadyItem =
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name("<yellow>" + targetName)
                        .lore(
                                "",
                                "<gray>This player is already",
                                "<gray>a member of this claim",
                                "",
                                "<yellow>Click to manage instead")
                        .build();
        setItem(
                13,
                new GuiItem(
                        alreadyItem,
                        e -> {
                            viewer.closeInventory();
                            new MemberActionsGui(plugin, viewer, claim, targetUuid, targetName)
                                    .open();
                        }));

        fillEmpty(new ItemBuilder(Material.YELLOW_STAINED_GLASS_PANE).name(" ").build());
    }
}
