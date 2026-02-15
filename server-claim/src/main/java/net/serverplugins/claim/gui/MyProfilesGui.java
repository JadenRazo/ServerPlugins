package net.serverplugins.claim.gui;

import java.util.List;
import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.economy.EconomyProvider;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimBank;
import net.serverplugins.claim.models.ClaimedChunk;
import net.serverplugins.claim.models.ManagementPermission;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * GUI for managing all of a player's profiles from anywhere. Allows viewing settings and warping to
 * profiles.
 */
public class MyProfilesGui extends Gui {

    private final ServerClaim plugin;
    private static final double WARP_COST = 25.0;

    public MyProfilesGui(ServerClaim plugin, Player player) {
        super(plugin, player, "My Profiles", 54);
        this.plugin = plugin;
    }

    @Override
    protected void initializeItems() {
        // Null safety check
        if (viewer == null) {
            plugin.getLogger().warning("MyProfilesGui: Viewer is null");
            return;
        }

        // Header
        setupHeader();

        // List owned profiles (slots 9-26)
        int nextSlot = setupOwnedProfilesList();

        // List managed profiles (slots after owned profiles, up to 44)
        setupManagedProfilesList(nextSlot);

        // Footer
        setupFooter();
    }

    private void setupHeader() {
        // Get profile count and max profiles
        int currentProfiles = plugin.getClaimManager().getClaimCount(viewer.getUniqueId());
        int maxProfiles = plugin.getClaimManager().getMaxClaims(viewer);
        boolean canCreateMore = currentProfiles < maxProfiles;

        // Title item with profile count
        ItemStack titleItem =
                new ItemBuilder(Material.GRASS_BLOCK)
                        .name(
                                "<gold>My Profiles <gray>("
                                        + currentProfiles
                                        + "/"
                                        + maxProfiles
                                        + ")")
                        .lore(
                                "",
                                "<gray>View and manage all your",
                                "<gray>profiles from anywhere!",
                                "",
                                canCreateMore
                                        ? "<green>You can create "
                                                + (maxProfiles - currentProfiles)
                                                + " more profile(s)!"
                                        : "<yellow>Max profiles reached",
                                "",
                                "<yellow>Warp Cost: <white>" + (int) WARP_COST + " coins")
                        .glow(canCreateMore)
                        .build();
        setItem(4, new GuiItem(titleItem));

        // Fill header with glass
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) {
            if (i != 4) {
                setItem(i, new GuiItem(filler));
            }
        }
    }

    /**
     * Sets up the list of profiles owned by the player.
     *
     * @return The next available slot after owned profiles
     */
    private int setupOwnedProfilesList() {
        List<Claim> claims = null;
        try {
            claims = plugin.getClaimManager().getPlayerClaims(viewer.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger()
                    .warning(
                            "Failed to get player profiles for "
                                    + viewer.getName()
                                    + ": "
                                    + e.getMessage());
        }

        if (claims == null || claims.isEmpty()) {
            // No profiles message
            int maxProfiles = plugin.getClaimManager().getMaxClaims(viewer);
            ItemStack noProfiles =
                    new ItemBuilder(Material.BARRIER)
                            .name("<red>No Profiles Yet")
                            .lore(
                                    "",
                                    "<gray>You don't have any profiles.",
                                    "",
                                    "<yellow>Use /claim to create your first profile!",
                                    "",
                                    "<gray>You can have up to <white>"
                                            + maxProfiles
                                            + "<gray> profiles.")
                            .build();
            setItem(13, new GuiItem(noProfiles));
            return 18; // Return slot after first row section
        }

        int slot = 9;
        int profileNumber = 1;
        for (Claim claim : claims) {
            if (claim == null) continue; // Skip null claims
            if (slot >= 27) break; // Max 18 owned profiles displayed (2 rows)

            ItemStack profileItem = createOwnedProfileItem(claim, profileNumber);
            if (profileItem == null) continue; // Skip if item creation failed
            final Claim c = claim;

            setItem(
                    slot,
                    GuiItem.withContext(
                            profileItem,
                            ctx -> {
                                if (ctx.isShiftClick()) {
                                    // Quick actions menu
                                    viewer.closeInventory();
                                    new ProfileQuickActionsGui(plugin, viewer, c).open();
                                } else if (ctx.isLeftClick()) {
                                    // Open settings
                                    viewer.closeInventory();
                                    new ClaimSettingsGui(plugin, viewer, c).open();
                                } else if (ctx.isRightClick()) {
                                    // Warp to profile
                                    warpToProfile(c);
                                }
                            }));

            slot++;
            profileNumber++;
        }

        return slot;
    }

    /** Sets up the list of profiles the player can manage but doesn't own. */
    private void setupManagedProfilesList(int startSlot) {
        List<Claim> managedClaims = null;
        try {
            managedClaims = plugin.getClaimManager().getManagedClaims(viewer.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger()
                    .warning(
                            "Failed to get managed profiles for "
                                    + viewer.getName()
                                    + ": "
                                    + e.getMessage());
        }

        // If there are managed profiles, add a separator and header
        if (managedClaims != null && !managedClaims.isEmpty()) {
            // Add a divider row if needed
            int dividerStart = startSlot;
            if (dividerStart < 27) {
                dividerStart = 27; // Start managed profiles on row 3
            }

            // Section header for managed profiles (using colored glass to indicate different
            // section)
            ItemStack divider =
                    new ItemBuilder(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build();
            for (int i = dividerStart; i < dividerStart + 9 && i < 45; i++) {
                setItem(i, new GuiItem(divider));
            }

            // Title item in center of divider row
            ItemStack managedTitle =
                    new ItemBuilder(Material.ENDER_CHEST)
                            .name("<light_purple>Profiles You Manage")
                            .lore(
                                    "",
                                    "<gray>Profiles where you have",
                                    "<gray>management permissions",
                                    "<gray>but are not the owner.")
                            .glow(true)
                            .build();
            setItem(dividerStart + 4, new GuiItem(managedTitle));

            // List managed profiles starting after divider
            int slot = dividerStart + 9;
            for (Claim claim : managedClaims) {
                if (claim == null) continue; // Skip null claims
                if (slot >= 45) break; // Max display

                ItemStack profileItem = createManagedProfileItem(claim);
                if (profileItem == null) continue; // Skip if item creation failed
                final Claim c = claim;

                setItem(
                        slot,
                        GuiItem.withContext(
                                profileItem,
                                ctx -> {
                                    if (ctx.isLeftClick()) {
                                        // Open settings (permission checks happen in
                                        // ClaimSettingsGui)
                                        viewer.closeInventory();
                                        new ClaimSettingsGui(plugin, viewer, c).open();
                                    } else if (ctx.isRightClick()) {
                                        // Warp to profile
                                        warpToProfile(c);
                                    }
                                }));

                slot++;
            }

            // Fill remaining slots
            ItemStack empty = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
            for (int i = slot; i < 45; i++) {
                setItem(i, new GuiItem(empty));
            }
        } else {
            // No managed profiles - just fill with empty slots
            ItemStack empty = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
            for (int i = startSlot; i < 45; i++) {
                setItem(i, new GuiItem(empty));
            }
        }
    }

    private ItemStack createOwnedProfileItem(Claim claim, int profileNumber) {
        if (claim == null) {
            plugin.getLogger().warning("Cannot create profile item for null claim");
            return null;
        }

        int usedChunks = claim.getChunks() != null ? claim.getChunks().size() : 0;
        int totalChunks = claim.getTotalChunks();
        String worldName = formatWorldName(claim.getWorld());

        // Use claim's color and icon directly
        Material icon = claim.getIcon() != null ? claim.getIcon() : Material.GRASS_BLOCK;
        String colorTag = claim.getColor() != null ? claim.getColor().getColorTag() : "<white>";

        // Get first chunk location for reference
        String locationInfo = "<gray>Unknown location";
        if (claim.getChunks() != null && !claim.getChunks().isEmpty()) {
            ClaimedChunk firstChunk = claim.getChunks().get(0);
            if (firstChunk != null) {
                int x = firstChunk.getChunkX() * 16;
                int z = firstChunk.getChunkZ() * 16;
                locationInfo = "<gray>Near: <white>" + x + ", " + z;
            }
        }

        String displayName = claim.getName();
        // If claim name is "Default Claim" or generic, show numbered name
        if (displayName == null
                || displayName.isEmpty()
                || displayName.equals("Default Claim")
                || displayName.equals("My Claim")) {
            displayName = "Profile #" + profileNumber;
        }

        // Get pricing multiplier
        double multiplier = plugin.getPricing().getClaimOrderMultiplier(claim.getClaimOrder());
        String multiplierStr =
                multiplier > 1.0
                        ? "<gray>Price Multiplier: <gold>"
                                + plugin.getPricing().formatMultiplier(claim.getClaimOrder())
                        : "";

        // Bank health indicator
        String bankHealthLine = "";
        if (plugin.getUpkeepManager() != null && plugin.getUpkeepManager().isUpkeepEnabled()) {
            ClaimBank bank = plugin.getBankManager().getBank(claim.getId());
            if (bank != null && bank.getGracePeriodStart() != null) {
                bankHealthLine = "<dark_red>\u25cf Grace Period!";
            } else {
                double upkeepCost = plugin.getUpkeepManager().getUpkeepCost(claim);
                if (upkeepCost <= 0) {
                    bankHealthLine = "<green>\u25cf No upkeep";
                } else {
                    double bankBalance = bank != null ? bank.getBalance() : 0;
                    int daysRemaining = (int) (bankBalance / upkeepCost);
                    if (daysRemaining < 3) {
                        bankHealthLine = "<red>\u25cf Bank: " + daysRemaining + " day(s)";
                    } else if (daysRemaining < 7) {
                        bankHealthLine = "<yellow>\u25cf Bank: " + daysRemaining + " day(s)";
                    } else {
                        bankHealthLine = "<green>\u25cf Bank: " + daysRemaining + " day(s)";
                    }
                }
            }
        }

        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("");
        lore.add("<dark_gray>Profile #" + claim.getClaimOrder());
        lore.add("");
        lore.add("<gray>World: <white>" + worldName);
        lore.add("<gray>Chunks: <white>" + usedChunks + "/" + totalChunks);
        if (!bankHealthLine.isEmpty()) {
            lore.add(bankHealthLine);
        }
        lore.add(locationInfo);
        if (!multiplierStr.isEmpty()) {
            lore.add(multiplierStr);
        }
        lore.add("");
        lore.add("<green>Left-click <gray>to manage settings");
        lore.add("<yellow>Right-click <gray>to warp <white>(" + (int) WARP_COST + " coins)");
        lore.add("<aqua>Shift-click <gray>for quick actions");

        return new ItemBuilder(icon)
                .name(colorTag + displayName)
                .lore(lore.toArray(new String[0]))
                .glow(true)
                .build();
    }

    /**
     * Creates an item for a profile the player manages but doesn't own. Has a distinct visual style
     * (purple glow, shows owner name).
     */
    private ItemStack createManagedProfileItem(Claim claim) {
        if (claim == null) {
            plugin.getLogger().warning("Cannot create managed profile item for null claim");
            return null;
        }

        int usedChunks = claim.getChunks() != null ? claim.getChunks().size() : 0;
        String worldName = formatWorldName(claim.getWorld());

        // Use claim's color and icon
        Material icon = claim.getIcon() != null ? claim.getIcon() : Material.CHEST;
        String colorTag =
                claim.getColor() != null ? claim.getColor().getColorTag() : "<light_purple>";

        // Get first chunk location for reference
        String locationInfo = "<gray>Unknown location";
        if (claim.getChunks() != null && !claim.getChunks().isEmpty()) {
            ClaimedChunk firstChunk = claim.getChunks().get(0);
            if (firstChunk != null) {
                int x = firstChunk.getChunkX() * 16;
                int z = firstChunk.getChunkZ() * 16;
                locationInfo = "<gray>Near: <white>" + x + ", " + z;
            }
        }

        // Get owner name
        String ownerName = claim.getCachedOwnerName();
        if (ownerName == null || ownerName.isEmpty()) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(claim.getOwnerUuid());
            ownerName = owner.getName() != null ? owner.getName() : "Unknown";
        }

        String displayName = claim.getName();
        if (displayName == null || displayName.isEmpty() || displayName.equals("My Claim")) {
            displayName = ownerName + "'s Profile";
        }

        // Determine what permissions the viewer has on this profile
        StringBuilder permList = new StringBuilder();
        for (ManagementPermission perm : ManagementPermission.values()) {
            if (claim.hasManagementPermission(viewer.getUniqueId(), perm)) {
                if (permList.length() > 0) permList.append(", ");
                permList.append(perm.getDisplayName());
            }
        }
        String permissionsLine =
                permList.length() > 0 ? "<gray>Permissions: <aqua>" + permList : "";

        return new ItemBuilder(icon)
                .name(colorTag + displayName + " <dark_gray>(Managed)")
                .lore(
                        "",
                        "<gray>Owner: <gold>" + ownerName,
                        "",
                        "<gray>World: <white>" + worldName,
                        "<gray>Chunks: <white>" + usedChunks,
                        locationInfo,
                        "",
                        permissionsLine,
                        "",
                        "<green>Left-click <gray>to manage",
                        "<yellow>Right-click <gray>to warp <white>(" + (int) WARP_COST + " coins)")
                .build();
    }

    private String formatWorldName(String worldName) {
        if (worldName == null) return "Unknown";
        return switch (worldName) {
            case "world", "playworld" -> "Overworld";
            case "world_nether", "playworld_nether" -> "Nether";
            case "world_the_end", "playworld_the_end" -> "The End";
            default -> worldName;
        };
    }

    private void warpToProfile(Claim claim) {
        if (claim == null) {
            TextUtil.send(viewer, "<red>This profile no longer exists!");
            return;
        }

        if (claim.getChunks() == null || claim.getChunks().isEmpty()) {
            TextUtil.send(viewer, "<red>This profile has no chunks to warp to!");
            return;
        }

        // Check economy
        EconomyProvider economy = ServerAPI.getInstance().getEconomyProvider();
        if (economy == null || !economy.isAvailable()) {
            TextUtil.send(viewer, "<red>Economy system is not available!");
            return;
        }

        // Check balance
        if (!economy.has(viewer, WARP_COST)) {
            TextUtil.send(
                    viewer,
                    "<red>You need <yellow>"
                            + (int) WARP_COST
                            + " coins<red> to warp to your profile!");
            TextUtil.send(
                    viewer,
                    "<gray>Your balance: <white>" + economy.format(economy.getBalance(viewer)));
            return;
        }

        // Get warp location (center of first chunk)
        ClaimedChunk chunk = claim.getChunks().get(0);
        World world = plugin.getServer().getWorld(claim.getWorld());

        if (world == null) {
            TextUtil.send(viewer, "<red>The world for this profile is not loaded!");
            return;
        }

        // Calculate center of chunk and find safe location
        int x = (chunk.getChunkX() * 16) + 8;
        int z = (chunk.getChunkZ() * 16) + 8;

        Location warpLoc = findSafeLocation(world, x, z);

        if (warpLoc == null) {
            TextUtil.send(viewer, "<red>Could not find a safe location to warp to!");
            TextUtil.send(viewer, "<gray>The area may be covered in lava or other hazards.");
            return;
        }

        // Withdraw money
        if (economy.withdraw(viewer, WARP_COST)) {
            viewer.closeInventory();
            viewer.teleport(warpLoc);
            TextUtil.send(
                    viewer,
                    "<green>Warped to your profile! <gray>(-" + (int) WARP_COST + " coins)");
        } else {
            TextUtil.send(viewer, "<red>Failed to process payment!");
        }
    }

    /**
     * Finds a safe teleport location near the given coordinates. Prioritizes: flat solid ground >
     * water > any non-lava surface
     *
     * @return Safe location or null if none found
     */
    private Location findSafeLocation(World world, int centerX, int centerZ) {
        int searchRadius = 5;

        Location bestLocation = null;
        int bestScore = -1;

        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                int x = centerX + dx;
                int z = centerZ + dz;
                int y = world.getHighestBlockYAt(x, z);

                Block ground = world.getBlockAt(x, y, z);
                Block above = world.getBlockAt(x, y + 1, z);
                Block aboveTwo = world.getBlockAt(x, y + 2, z);

                // Skip if standing blocks are not passable
                if (!above.isPassable() || !aboveTwo.isPassable()) {
                    continue;
                }

                Material groundMaterial = ground.getType();

                // NEVER teleport to lava or near lava
                if (groundMaterial == Material.LAVA) {
                    continue;
                }

                int score = 0;

                // Check for safe solid ground
                if (ground.isSolid()
                        && groundMaterial != Material.CACTUS
                        && groundMaterial != Material.MAGMA_BLOCK
                        && groundMaterial != Material.CAMPFIRE
                        && groundMaterial != Material.SOUL_CAMPFIRE
                        && groundMaterial != Material.FIRE
                        && groundMaterial != Material.SWEET_BERRY_BUSH
                        && groundMaterial != Material.POWDER_SNOW
                        && groundMaterial != Material.POINTED_DRIPSTONE
                        && groundMaterial != Material.WITHER_ROSE) {
                    score = 10; // Best: safe solid ground
                } else if (groundMaterial == Material.WATER) {
                    score = 5; // Acceptable: water
                } else if (ground.isPassable()) {
                    score = 1; // Fallback: passable block (air above something)
                }

                // Prefer center of chunk
                if (dx == 0 && dz == 0) {
                    score += 2;
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestLocation = new Location(world, x + 0.5, y + 1, z + 0.5);
                }
            }
        }

        return bestLocation;
    }

    private void setupFooter() {
        // Back button (slot 45 - far left of bottom row)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to claim menu")
                        .build();
        setItem(
                45,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ClaimMenuGui(plugin, viewer).open();
                        }));

        // Check if player can create more profiles
        int currentProfiles = plugin.getClaimManager().getClaimCount(viewer.getUniqueId());
        int maxProfiles = plugin.getClaimManager().getMaxClaims(viewer);
        boolean canCreateMore = currentProfiles < maxProfiles;

        // Info/Create profile hint (slot 49 - center)
        if (canCreateMore) {
            ItemStack createHint =
                    new ItemBuilder(Material.LIME_CONCRETE)
                            .name("<green>Create New Profile")
                            .lore(
                                    "",
                                    "<gray>To create a new profile:",
                                    "<white>1. Go to any unclaimed area",
                                    "<white>2. Use <yellow>/claim<white> to claim it",
                                    "",
                                    "<gray>Profiles remaining: <green>"
                                            + (maxProfiles - currentProfiles),
                                    "",
                                    "<dark_gray>Tip: Claim non-adjacent chunks",
                                    "<dark_gray>to start a separate profile!")
                            .glow(true)
                            .build();
            setItem(
                    49,
                    new GuiItem(
                            createHint,
                            e -> {
                                viewer.closeInventory();
                                TextUtil.send(
                                        viewer,
                                        "<green>Go to any unclaimed area and use <yellow>/claim<green> to create a new profile!");
                            }));
        } else {
            ItemStack maxReached =
                    new ItemBuilder(Material.BARRIER)
                            .name("<yellow>Max Profiles Reached")
                            .lore(
                                    "",
                                    "<gray>You have <white>"
                                            + currentProfiles
                                            + "/"
                                            + maxProfiles
                                            + "<gray> profiles.",
                                    "",
                                    "<dark_gray>Upgrade your rank for more!")
                            .build();
            setItem(49, new GuiItem(maxReached));
        }

        // Fill remaining footer with glass
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 46; i < 54; i++) {
            if (i != 49) {
                setItem(i, new GuiItem(filler));
            }
        }
    }
}
