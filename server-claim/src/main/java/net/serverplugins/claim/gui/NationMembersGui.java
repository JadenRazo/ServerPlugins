package net.serverplugins.claim.gui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.Nation;
import net.serverplugins.claim.models.NationMember;
import net.serverplugins.claim.models.PlayerClaimData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class NationMembersGui extends Gui {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy");

    private final ServerClaim plugin;
    private final Nation nation;
    private final Claim playerClaim;
    private final int page;
    private static final int ITEMS_PER_PAGE = 28;

    public NationMembersGui(ServerClaim plugin, Player player, Nation nation, Claim playerClaim) {
        this(plugin, player, nation, playerClaim, 0);
    }

    public NationMembersGui(
            ServerClaim plugin, Player player, Nation nation, Claim playerClaim, int page) {
        super(
                plugin,
                player,
                (nation != null ? nation.getColoredTag() : "<white>")
                        + " <white>Members - Page "
                        + (page + 1),
                54);
        this.plugin = plugin;

        // Validate nation exists
        if (!GuiValidator.validateNation(plugin, player, nation, "Nation Members")) {
            this.nation = null;
            this.playerClaim = playerClaim;
            this.page = page;
            return;
        }

        this.nation = nation;
        this.playerClaim = playerClaim;
        this.page = page;
    }

    @Override
    protected void initializeItems() {
        // Early return if nation is null
        if (nation == null) {
            plugin.getLogger().warning("NationMembersGui: Nation is null, cannot initialize");
            return;
        }

        // Fill border
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) setItem(i, new GuiItem(filler));
        for (int i = 45; i < 54; i++) setItem(i, new GuiItem(filler));
        for (int i = 9; i < 45; i += 9) {
            setItem(i, new GuiItem(filler));
            setItem(i + 8, new GuiItem(filler));
        }

        List<NationMember> members = null;
        try {
            members = plugin.getNationManager().getMembers(nation.getId());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get nation members: " + e.getMessage());
            members = new ArrayList<>();
        }
        int totalPages = (int) Math.ceil((double) members.size() / ITEMS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, members.size());

        NationMember myMembership = null;
        try {
            myMembership =
                    playerClaim != null
                            ? plugin.getNationManager().getMember(playerClaim.getId())
                            : null;
        } catch (Exception e) {
            plugin.getLogger()
                    .warning("Failed to get player's nation membership: " + e.getMessage());
        }

        boolean canManage =
                nation.isLeader(viewer.getUniqueId())
                        || (myMembership != null && myMembership.isOfficer());

        // Display members
        int[] slots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        for (int i = start; i < end; i++) {
            int slotIndex = i - start;
            if (slotIndex >= slots.length) break;

            NationMember member = members.get(i);
            if (member == null) continue; // Skip null members

            GuiItem item = createMemberItem(member, canManage);
            if (item != null) {
                setItem(slots[slotIndex], item);
            }
        }

        // Previous page (slot 48)
        if (page > 0) {
            ItemStack prevItem =
                    new ItemBuilder(Material.ARROW).name("<yellow>Previous Page").build();
            setItem(
                    48,
                    new GuiItem(
                            prevItem,
                            e -> {
                                viewer.closeInventory();
                                new NationMembersGui(plugin, viewer, nation, playerClaim, page - 1)
                                        .open();
                            }));
        }

        // Page info (slot 49)
        ItemStack pageInfo =
                new ItemBuilder(Material.PAPER)
                        .name("<gold>Page " + (page + 1) + " of " + totalPages)
                        .lore("<gray>Total members: <white>" + members.size())
                        .build();
        setItem(49, new GuiItem(pageInfo));

        // Next page (slot 50)
        if (page < totalPages - 1) {
            ItemStack nextItem = new ItemBuilder(Material.ARROW).name("<yellow>Next Page").build();
            setItem(
                    50,
                    new GuiItem(
                            nextItem,
                            e -> {
                                viewer.closeInventory();
                                new NationMembersGui(plugin, viewer, nation, playerClaim, page + 1)
                                        .open();
                            }));
        }

        // Back button (slot 45)
        ItemStack backItem =
                new ItemBuilder(Material.DARK_OAK_DOOR)
                        .name("<gray>Back")
                        .lore("<gray>Return to nation menu")
                        .build();
        setItem(
                45,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new NationMenuGui(plugin, viewer, nation, playerClaim).open();
                        }));

        // Close (slot 53)
        ItemStack closeItem = new ItemBuilder(Material.BARRIER).name("<red>Close").build();
        setItem(53, new GuiItem(closeItem, e -> viewer.closeInventory()));
    }

    private GuiItem createMemberItem(NationMember member, boolean canManage) {
        if (member == null) {
            plugin.getLogger().warning("Cannot create member item for null member");
            return null;
        }

        Claim claim = null;
        try {
            claim = plugin.getClaimManager().getClaimById(member.getClaimId());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get claim for member: " + e.getMessage());
        }

        if (claim == null) {
            return new GuiItem(
                    new ItemBuilder(Material.BARRIER).name("<red>Unknown Claim").build());
        }

        PlayerClaimData data = null;
        try {
            data = plugin.getRepository().getPlayerData(claim.getOwnerUuid());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get player data for member: " + e.getMessage());
        }
        String ownerName =
                data != null && data.getUsername() != null ? data.getUsername() : "Unknown";

        Material material =
                switch (member.getRole()) {
                    case LEADER -> Material.GOLDEN_HELMET;
                    case OFFICER -> Material.IRON_HELMET;
                    case MEMBER -> Material.LEATHER_HELMET;
                };

        String roleColor =
                switch (member.getRole()) {
                    case LEADER -> "<gold>";
                    case OFFICER -> "<aqua>";
                    case MEMBER -> "<gray>";
                };

        String joinedDate =
                member.getJoinedAt() != null
                        ? member.getJoinedAt().atZone(ZoneId.systemDefault()).format(DATE_FORMAT)
                        : "Unknown";
        String claimName = claim.getName() != null ? claim.getName() : "Unknown";
        int chunkCount = claim.getChunks() != null ? claim.getChunks().size() : 0;

        ItemBuilder builder =
                new ItemBuilder(material)
                        .name(roleColor + ownerName)
                        .lore(
                                "",
                                "<gray>Claim: <white>" + claimName,
                                "<gray>Role: " + roleColor + member.getRole().getDisplayName(),
                                "<gray>Chunks: <white>" + chunkCount,
                                "<gray>Contributed: <gold>$"
                                        + String.format("%.2f", member.getContributedAmount()),
                                "<gray>Joined: <white>" + joinedDate);

        if (member.isLeader()) {
            builder.glow(true);
        }

        if (canManage && !member.isLeader() && member.getClaimId() != playerClaim.getId()) {
            builder.lore("", "<yellow>Left-click to promote/demote", "<red>Right-click to kick");
        }

        return GuiItem.withContext(
                builder.build(),
                ctx -> {
                    if (!canManage
                            || member.isLeader()
                            || member.getClaimId() == playerClaim.getId()) return;

                    if (ctx.isLeftClick()) {
                        if (member.getRole() == NationMember.NationRole.MEMBER) {
                            plugin.getNationManager()
                                    .promoteMember(
                                            nation,
                                            member.getClaimId(),
                                            success -> {
                                                TextUtil.send(
                                                        viewer,
                                                        plugin.getClaimConfig().getMessage("prefix")
                                                                + (success
                                                                        ? "<green>Promoted to Officer!"
                                                                        : "<red>Could not promote."));
                                                plugin.getServer()
                                                        .getScheduler()
                                                        .runTask(
                                                                plugin,
                                                                () -> {
                                                                    viewer.closeInventory();
                                                                    new NationMembersGui(
                                                                                    plugin,
                                                                                    viewer,
                                                                                    nation,
                                                                                    playerClaim,
                                                                                    page)
                                                                            .open();
                                                                });
                                            });
                        } else if (member.getRole() == NationMember.NationRole.OFFICER) {
                            plugin.getNationManager()
                                    .demoteMember(
                                            nation,
                                            member.getClaimId(),
                                            success -> {
                                                TextUtil.send(
                                                        viewer,
                                                        plugin.getClaimConfig().getMessage("prefix")
                                                                + (success
                                                                        ? "<yellow>Demoted to Member."
                                                                        : "<red>Could not demote."));
                                                plugin.getServer()
                                                        .getScheduler()
                                                        .runTask(
                                                                plugin,
                                                                () -> {
                                                                    viewer.closeInventory();
                                                                    new NationMembersGui(
                                                                                    plugin,
                                                                                    viewer,
                                                                                    nation,
                                                                                    playerClaim,
                                                                                    page)
                                                                            .open();
                                                                });
                                            });
                        }
                    } else if (ctx.isRightClick()) {
                        plugin.getNationManager()
                                .kickClaim(
                                        nation,
                                        member.getClaimId(),
                                        success -> {
                                            TextUtil.send(
                                                    viewer,
                                                    plugin.getClaimConfig().getMessage("prefix")
                                                            + (success
                                                                    ? "<green>Kicked from nation."
                                                                    : "<red>Could not kick."));
                                            plugin.getServer()
                                                    .getScheduler()
                                                    .runTask(
                                                            plugin,
                                                            () -> {
                                                                viewer.closeInventory();
                                                                new NationMembersGui(
                                                                                plugin,
                                                                                viewer,
                                                                                nation,
                                                                                playerClaim,
                                                                                page)
                                                                        .open();
                                                            });
                                        });
                    }
                });
    }
}
