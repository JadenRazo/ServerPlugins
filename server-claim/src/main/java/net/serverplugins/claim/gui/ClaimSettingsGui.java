package net.serverplugins.claim.gui;

import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimBank;
import net.serverplugins.claim.models.ClaimSettings;
import net.serverplugins.claim.models.ManagementPermission;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * GUI for managing claim settings - now acts as a navigation hub. Layout: - Row 0: Title bar - Row
 * 1: Profile Info | Members | Groups | Flags - Row 2: Chunks | Warp Settings | Chunk Shop - Row 3:
 * Delete | Back | Close
 *
 * <p>Each button checks the player's management permissions before allowing access.
 */
public class ClaimSettingsGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;
    private final boolean isOwner;
    private final boolean isAdmin;

    public ClaimSettingsGui(ServerClaim plugin, Player player, Claim claim) {
        super(plugin, player, "Claim Settings", 36);
        this.plugin = plugin;

        // Validate claim exists before proceeding
        if (!GuiValidator.validateClaim(plugin, player, claim, "ClaimSettingsGui")) {
            this.claim = null;
            this.isOwner = false;
            this.isAdmin = false;
            return;
        }

        this.claim = claim;
        this.isOwner = claim.isOwner(player.getUniqueId());
        this.isAdmin = player.hasPermission("serverclaim.admin");
    }

    @Override
    protected void initializeItems() {
        // Safety check - if claim is null, don't initialize
        if (claim == null) {
            return;
        }

        // Row 0: Title bar
        setupTitleRow();

        // Row 1: Main management categories
        setupManagementRow();

        // Row 2: Additional options
        setupOptionsRow();

        // Row 3: Navigation and danger zone
        setupNavigationRow();

        // Fill with claim-colored glass panes
        Material borderMaterial =
                claim.getColor() != null
                        ? claim.getColor().getGlassPaneMaterial()
                        : Material.WHITE_STAINED_GLASS_PANE;
        fillEmpty(new ItemBuilder(borderMaterial).name(" ").build());
    }

    private void setupTitleRow() {
        ItemStack blackGlass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {0, 1, 2, 3, 5, 6, 7, 8}) {
            setItem(i, new GuiItem(blackGlass));
        }

        int usedChunks = claim.getChunks().size();
        int totalChunks = claim.getTotalChunks();
        int memberCount = claim.getMembers() != null ? claim.getMembers().size() : 0;
        String colorTag = claim.getColor() != null ? claim.getColor().getColorTag() : "<white>";

        // Show owner name if viewing a managed claim
        String ownerInfo = "";
        if (!isOwner) {
            String ownerName = claim.getCachedOwnerName();
            if (ownerName == null || ownerName.isEmpty()) {
                OfflinePlayer owner = Bukkit.getOfflinePlayer(claim.getOwnerUuid());
                ownerName = owner.getName() != null ? owner.getName() : "Unknown";
            }
            ownerInfo = "<gray>Owner: <gold>" + ownerName;
        }

        ItemStack titleItem =
                new ItemBuilder(Material.NETHER_STAR)
                        .name("<gold>Claim Settings")
                        .lore(
                                "",
                                "<gray>Claim: " + colorTag + claim.getName(),
                                "<gray>Chunks: <white>" + usedChunks + "/" + totalChunks,
                                "<gray>Members: <white>" + memberCount,
                                ownerInfo,
                                "",
                                isOwner
                                        ? "<green>You own this claim"
                                        : "<yellow>You are managing this claim")
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(titleItem));
    }

    private void setupManagementRow() {
        // Row 1: Profile Info | Members | Groups | Flags

        // Profile Info (slot 10) - requires MANAGE_PROFILE_INFO
        boolean canManageProfile = hasPermission(ManagementPermission.MANAGE_PROFILE_INFO);
        Material profileIcon = claim.getIcon() != null ? claim.getIcon() : Material.NAME_TAG;
        String profileColorTag =
                claim.getColor() != null ? claim.getColor().getColorTag() : "<white>";

        ItemStack profileItem =
                new ItemBuilder(profileIcon)
                        .name("<gold>Profile Info")
                        .lore(
                                "",
                                "<gray>Name: " + profileColorTag + claim.getName(),
                                "<gray>Color: "
                                        + profileColorTag
                                        + (claim.getColor() != null
                                                ? claim.getColor().getDisplayName()
                                                : "White"),
                                "",
                                canManageProfile
                                        ? "<yellow>Click to customize"
                                        : "<red>No permission")
                        .glow(canManageProfile)
                        .build();
        setItem(
                10,
                new GuiItem(
                        profileItem,
                        e -> {
                            if (canManageProfile) {
                                viewer.closeInventory();
                                new ManageProfileInfoGui(plugin, viewer, claim).open();
                            } else {
                                TextUtil.send(
                                        viewer,
                                        "<red>You don't have permission to manage profile info.");
                            }
                        }));

        // Members (slot 12) - requires MANAGE_MEMBERS
        boolean canManageMembers = hasPermission(ManagementPermission.MANAGE_MEMBERS);
        int memberCount = claim.getMembers() != null ? claim.getMembers().size() : 0;

        ItemStack membersItem =
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name("<aqua>Members")
                        .lore(
                                "",
                                "<gray>Members: <white>" + memberCount,
                                "",
                                "<dark_gray>Add, remove, and manage",
                                "<dark_gray>player access to your claim",
                                "",
                                canManageMembers ? "<yellow>Click to manage" : "<red>No permission")
                        .glow(canManageMembers)
                        .build();
        setItem(
                12,
                new GuiItem(
                        membersItem,
                        e -> {
                            if (canManageMembers) {
                                viewer.closeInventory();
                                new ManageGroupsGui(plugin, viewer, claim).open();
                            } else {
                                TextUtil.send(
                                        viewer,
                                        "<red>You don't have permission to manage members.");
                            }
                        }));

        // Groups (slot 14) - requires MANAGE_GROUPS
        boolean canManageGroups = hasPermission(ManagementPermission.MANAGE_GROUPS);
        int groupCount = claim.getCustomGroups() != null ? claim.getCustomGroups().size() : 0;

        ItemStack groupsItem =
                new ItemBuilder(Material.WRITABLE_BOOK)
                        .name("<light_purple>Groups")
                        .lore(
                                "",
                                "<gray>Groups: <white>" + groupCount,
                                "",
                                "<dark_gray>Edit group permissions",
                                "<dark_gray>and create custom roles",
                                "",
                                canManageGroups ? "<yellow>Click to manage" : "<red>No permission")
                        .glow(canManageGroups)
                        .build();
        setItem(
                14,
                new GuiItem(
                        groupsItem,
                        e -> {
                            if (canManageGroups) {
                                viewer.closeInventory();
                                // For now, redirect to ManageGroupsGui which shows groups
                                new ManageGroupsGui(plugin, viewer, claim).open();
                            } else {
                                TextUtil.send(
                                        viewer, "<red>You don't have permission to manage groups.");
                            }
                        }));

        // Flags (slot 16) - requires MANAGE_FLAGS
        boolean canManageFlags = hasPermission(ManagementPermission.MANAGE_FLAGS);
        ClaimSettings settings = claim.getSettings();

        ItemStack flagsItem =
                new ItemBuilder(Material.COMPARATOR)
                        .name("<yellow>Flags")
                        .lore(
                                "",
                                "<gray>PvP: "
                                        + (settings != null && settings.isPvpEnabled()
                                                ? "<green>On"
                                                : "<red>Off"),
                                "<gray>Explosions: "
                                        + (settings != null && settings.isExplosions()
                                                ? "<green>On"
                                                : "<red>Off"),
                                "<gray>Hostile Mobs: "
                                        + (settings != null && settings.isHostileSpawns()
                                                ? "<green>On"
                                                : "<red>Off"),
                                "",
                                canManageFlags
                                        ? "<yellow>Click to configure"
                                        : "<red>No permission")
                        .glow(canManageFlags)
                        .build();
        setItem(
                16,
                new GuiItem(
                        flagsItem,
                        e -> {
                            if (canManageFlags) {
                                viewer.closeInventory();
                                new ManageFlagsGui(plugin, viewer, claim).open();
                            } else {
                                TextUtil.send(
                                        viewer, "<red>You don't have permission to manage flags.");
                            }
                        }));

        // Particle Toggle (slot 17) - owner or those with MANAGE_PROFILE_INFO permission
        boolean canToggleParticles = hasPermission(ManagementPermission.MANAGE_PROFILE_INFO);
        boolean particlesEnabled = claim.isParticleEnabled();

        ItemStack particleItem =
                new ItemBuilder(particlesEnabled ? Material.GLOWSTONE_DUST : Material.GUNPOWDER)
                        .name(particlesEnabled ? "<green>Particles: ON" : "<red>Particles: OFF")
                        .lore(
                                "",
                                "<gray>Toggle border particles",
                                "<gray>for this claim.",
                                "",
                                particlesEnabled
                                        ? "<yellow>Click to disable"
                                        : "<yellow>Click to enable",
                                "",
                                canToggleParticles ? "" : "<red>No permission")
                        .glow(particlesEnabled && canToggleParticles)
                        .build();
        setItem(
                17,
                new GuiItem(
                        particleItem,
                        e -> {
                            if (canToggleParticles) {
                                claim.setParticleEnabled(!particlesEnabled);
                                plugin.getRepository().updateClaim(claim);
                                TextUtil.send(
                                        viewer,
                                        particlesEnabled
                                                ? "<yellow>Claim particles disabled."
                                                : "<green>Claim particles enabled.");
                                reopenMenu();
                            } else {
                                TextUtil.send(
                                        viewer,
                                        "<red>You don't have permission to toggle particles.");
                            }
                        }));

        // Activity Log (slot 18) - viewable by owner and members
        boolean canViewLog = isOwner || isAdmin || claim.isMember(viewer.getUniqueId());

        ItemStack logItem =
                new ItemBuilder(Material.WRITTEN_BOOK)
                        .name("<aqua>Activity Log")
                        .lore(
                                "",
                                "<gray>View a detailed history",
                                "<gray>of all actions taken",
                                "<gray>in this claim.",
                                "",
                                "<yellow>Click to view log",
                                "",
                                canViewLog ? "" : "<red>No permission")
                        .glow(canViewLog)
                        .build();
        setItem(
                18,
                new GuiItem(
                        logItem,
                        e -> {
                            if (canViewLog) {
                                viewer.closeInventory();
                                new ActivityLogGui(plugin, viewer, claim, 0).open();
                            } else {
                                TextUtil.send(
                                        viewer,
                                        "<red>You don't have permission to view the activity log.");
                            }
                        }));

        // Dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {9, 11, 13, 15}) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void setupOptionsRow() {
        // Row 2: Chunks | Bank | Level | Warp Settings | Chunk Shop

        // Chunks (slot 19) - requires MANAGE_CHUNKS
        boolean canManageChunks = hasPermission(ManagementPermission.MANAGE_CHUNKS);
        int usedChunks = claim.getChunks().size();
        int totalChunks = claim.getTotalChunks();
        int remainingChunks = claim.getRemainingChunks();

        ItemStack chunksItem =
                new ItemBuilder(Material.GRASS_BLOCK)
                        .name("<green>Manage Chunks")
                        .lore(
                                "",
                                "<gray>Used: <white>" + usedChunks + "/" + totalChunks,
                                "<gray>Available: <white>" + remainingChunks,
                                "",
                                canManageChunks
                                        ? "<yellow>Left-click to unclaim"
                                        : "<red>No permission",
                                canManageChunks || isOwner ? "<green>Right-click to allocate" : "")
                        .glow(canManageChunks)
                        .build();
        setItem(
                19,
                GuiItem.withContext(
                        chunksItem,
                        ctx -> {
                            if (ctx.isRightClick() && (isOwner || isAdmin)) {
                                // Open allocation GUI
                                viewer.closeInventory();
                                new ChunkAllocationGui(plugin, viewer, claim).open();
                            } else if (ctx.isLeftClick() && canManageChunks) {
                                // Open manage chunks (unclaim) GUI
                                viewer.closeInventory();
                                new ManageChunksGui(plugin, viewer, claim).open();
                            } else {
                                TextUtil.send(
                                        viewer, "<red>You don't have permission to manage chunks.");
                            }
                        }));

        // Land Bank (slot 20) - owner only
        double bankBalance = plugin.getBankManager().getBalance(claim.getId());
        ClaimBank claimBank = plugin.getBankManager().getBank(claim.getId());
        boolean bankGracePeriod = claimBank != null && claimBank.getGracePeriodStart() != null;

        // Calculate days remaining for health coloring
        String balanceColorTag = "<gold>";
        String daysRemainingLine = "";
        Material bankMaterial = Material.GOLD_INGOT;
        boolean bankCritical = false;

        if (plugin.getUpkeepManager() != null && plugin.getUpkeepManager().isUpkeepEnabled()) {
            double upkeepCost = plugin.getUpkeepManager().getUpkeepCost(claim);
            if (upkeepCost > 0) {
                int daysRemaining = (int) (bankBalance / upkeepCost);
                if (bankGracePeriod || daysRemaining < 3) {
                    balanceColorTag = "<red>";
                    bankMaterial = Material.RAW_GOLD;
                    bankCritical = true;
                } else if (daysRemaining < 7) {
                    balanceColorTag = "<yellow>";
                } else {
                    balanceColorTag = "<green>";
                }
                daysRemainingLine = "<gray>Covers: <white>" + daysRemaining + " day(s) of upkeep";
            }
        }

        java.util.List<String> bankLore = new java.util.ArrayList<>();
        bankLore.add("");
        bankLore.add(
                "<gray>Balance: " + balanceColorTag + "$" + String.format("%.2f", bankBalance));
        if (!daysRemainingLine.isEmpty()) {
            bankLore.add(daysRemainingLine);
        }
        if (bankGracePeriod) {
            bankLore.add("<dark_red>IN GRACE PERIOD");
        }
        bankLore.add("");
        bankLore.add("<dark_gray>Deposit funds to pay");
        bankLore.add("<dark_gray>claim upkeep costs");
        bankLore.add("");
        bankLore.add(isOwner || isAdmin ? "<yellow>Click to manage" : "<red>Owner only");

        ItemStack bankItem =
                new ItemBuilder(bankMaterial)
                        .name("<gold>Land Bank")
                        .lore(bankLore.toArray(new String[0]))
                        .glow((isOwner || isAdmin) && !bankCritical)
                        .build();
        setItem(
                20,
                new GuiItem(
                        bankItem,
                        e -> {
                            if (isOwner || isAdmin) {
                                viewer.closeInventory();
                                new LandBankGui(plugin, viewer, claim).open();
                            } else {
                                TextUtil.send(
                                        viewer, "<red>Only the claim owner can manage the bank.");
                            }
                        }));

        // Claim Level (slot 21) - viewable by all
        var level = plugin.getLevelManager().getLevel(claim.getId());
        var benefits = plugin.getLevelManager().getBenefits(claim.getId());
        ItemStack levelItem =
                new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                        .name("<aqua>Claim Level")
                        .lore(
                                "",
                                "<gray>Level: <white>" + level.getLevel(),
                                "<gray>XP: <white>"
                                        + level.getCurrentXp()
                                        + "/"
                                        + level.getXpForNextLevel(),
                                "",
                                "<dark_gray>View level benefits",
                                "<dark_gray>and XP progress",
                                "",
                                "<yellow>Click to view")
                        .glow(true)
                        .build();
        setItem(
                21,
                new GuiItem(
                        levelItem,
                        e -> {
                            viewer.closeInventory();
                            new ClaimLevelGui(plugin, viewer, claim).open();
                        }));

        // Warp Settings (slot 23) - owner only or trusted
        boolean canManageWarp = isOwner || isAdmin;

        ItemStack warpItem =
                new ItemBuilder(Material.ENDER_PEARL)
                        .name("<light_purple>Warp Settings")
                        .lore(
                                "",
                                "<dark_gray>Configure your claim's",
                                "<dark_gray>public warp point",
                                "",
                                canManageWarp ? "<yellow>Click to manage" : "<red>Owner only")
                        .glow(canManageWarp)
                        .build();
        setItem(
                23,
                new GuiItem(
                        warpItem,
                        e -> {
                            if (canManageWarp) {
                                viewer.closeInventory();
                                new WarpSettingsGui(plugin, viewer, claim).open();
                            } else {
                                TextUtil.send(
                                        viewer,
                                        "<red>Only the claim owner can manage warp settings.");
                            }
                        }));

        // Chunk Shop (slot 25) - owner only
        double nextPrice = plugin.getClaimManager().getNextChunkPriceForClaim(claim);
        String priceStr = nextPrice > 0 ? plugin.getPricing().formatPrice(nextPrice) : "MAX";

        ItemStack shopItem =
                new ItemBuilder(Material.EMERALD)
                        .name("<green>Chunk Shop")
                        .lore(
                                "",
                                "<gray>Next chunk: <gold>$" + priceStr,
                                "",
                                "<dark_gray>Purchase additional",
                                "<dark_gray>chunks for this claim",
                                "",
                                isOwner || isAdmin ? "<yellow>Click to open" : "<red>Owner only")
                        .glow(isOwner || isAdmin)
                        .build();
        setItem(
                25,
                new GuiItem(
                        shopItem,
                        e -> {
                            if (isOwner || isAdmin) {
                                viewer.closeInventory();
                                new ChunkShopGui(plugin, viewer).open();
                            } else {
                                TextUtil.send(
                                        viewer, "<red>Only the claim owner can purchase chunks.");
                            }
                        }));

        // Dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {18, 22, 24, 26}) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void setupNavigationRow() {
        // Row 3: Delete | Back | Close

        // Delete Claim (slot 28) - owner only
        if (isOwner || isAdmin) {
            int chunkCount = claim.getChunks().size();
            ItemStack deleteItem =
                    new ItemBuilder(Material.TNT)
                            .name("<red>Delete Claim")
                            .lore(
                                    "",
                                    "<gray>Permanently delete this claim",
                                    "<gray>and unclaim all <white>" + chunkCount + "<gray> chunks.",
                                    "",
                                    "<red>This cannot be undone!",
                                    "",
                                    "<yellow>Click to confirm")
                            .build();
            setItem(
                    28,
                    new GuiItem(
                            deleteItem,
                            e -> {
                                viewer.closeInventory();
                                new ClaimDeleteConfirmGui(plugin, viewer, claim).open();
                            }));
        } else {
            ItemStack lockedDelete =
                    new ItemBuilder(Material.BARRIER)
                            .name("<dark_gray>Delete Claim")
                            .lore(
                                    "",
                                    "<red>Owner only",
                                    "",
                                    "<gray>Only the claim owner",
                                    "<gray>can delete the claim")
                            .build();
            setItem(28, new GuiItem(lockedDelete));
        }

        // Back button (slot 31)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<yellow>Back")
                        .lore("<gray>Return to My Claims")
                        .build();
        setItem(
                31,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new MyProfilesGui(plugin, viewer).open();
                        }));

        // Close/Main Menu (slot 34)
        ItemStack closeItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Close")
                        .lore("<gray>Close this menu")
                        .build();
        setItem(
                34,
                new GuiItem(
                        closeItem,
                        e -> {
                            viewer.closeInventory();
                        }));

        // Dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {27, 29, 30, 32, 33, 35}) {
            setItem(i, new GuiItem(divider));
        }
    }

    /** Checks if the viewer has a specific management permission on this claim. */
    private boolean hasPermission(ManagementPermission permission) {
        // Admins and owners always have all permissions
        if (isAdmin || isOwner) {
            return true;
        }
        // Check the claim's permission system
        return claim.hasManagementPermission(viewer.getUniqueId(), permission);
    }

    private void reopenMenu() {
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            viewer.closeInventory();
                            new ClaimSettingsGui(plugin, viewer, claim).open();
                        });
    }
}
