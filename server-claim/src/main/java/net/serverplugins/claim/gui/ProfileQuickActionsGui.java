package net.serverplugins.claim.gui;

import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.economy.EconomyProvider;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimedChunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Quick actions GUI for managing a single claim profile. Accessible via shift+click from
 * MyProfilesGui.
 */
public class ProfileQuickActionsGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;
    private static final double WARP_COST = 25.0;

    public ProfileQuickActionsGui(ServerClaim plugin, Player player, Claim claim) {
        super(plugin, player, "Quick Actions", 27);
        this.plugin = plugin;

        // Validate claim exists
        if (!GuiValidator.validateClaim(plugin, player, claim, "Quick Actions")) {
            this.claim = null;
            return;
        }

        this.claim = claim;
    }

    @Override
    protected void initializeItems() {
        // Early return if claim is null
        if (claim == null) {
            plugin.getLogger().warning("ProfileQuickActionsGui: Claim is null, cannot initialize");
            return;
        }

        // Row 0: Title bar with claim icon
        setupTitleBar();

        // Row 1: Action buttons
        setupActionButtons();

        // Row 2: Navigation
        setupNavigationRow();

        fillEmpty(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build());
    }

    private void setupTitleBar() {
        ItemStack blackGlass = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) {
            if (i == 4) continue;
            setItem(i, new GuiItem(blackGlass));
        }

        // Claim icon in center
        Material icon = claim.getIcon() != null ? claim.getIcon() : Material.GRASS_BLOCK;
        String colorTag = claim.getColor() != null ? claim.getColor().getColorTag() : "<white>";
        int usedChunks = claim.getChunks() != null ? claim.getChunks().size() : 0;
        int totalChunks = claim.getTotalChunks();
        double multiplier = plugin.getPricing().getClaimOrderMultiplier(claim.getClaimOrder());
        String multiplierStr =
                multiplier > 1.0
                        ? plugin.getPricing().formatMultiplier(claim.getClaimOrder())
                        : "1x";

        ItemStack titleItem =
                new ItemBuilder(icon)
                        .name(colorTag + claim.getName())
                        .lore(
                                "",
                                "<gray>Profile #" + claim.getClaimOrder(),
                                "<gray>Chunks: <white>" + usedChunks + "/" + totalChunks,
                                "<gray>Multiplier: <gold>" + multiplierStr,
                                "",
                                "<dark_gray>Quick actions menu")
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(titleItem));
    }

    private void setupActionButtons() {
        // Rename (slot 10)
        ItemStack renameItem =
                new ItemBuilder(Material.NAME_TAG)
                        .name("<yellow>Rename Profile")
                        .lore(
                                "",
                                "<gray>Current: <white>" + claim.getName(),
                                "",
                                "<green>Click to rename")
                        .build();
        setItem(
                10,
                new GuiItem(
                        renameItem,
                        e -> {
                            viewer.closeInventory();
                            TextUtil.send(viewer, "<yellow>Type the new profile name in chat:");
                            TextUtil.send(viewer, "<gray>(Type 'cancel' to cancel)");
                            plugin.getClaimManager().awaitClaimRenameInput(viewer, claim);
                        }));

        // Color (slot 12)
        String colorTag = claim.getColor() != null ? claim.getColor().getColorTag() : "<white>";
        String colorName = claim.getColor() != null ? claim.getColor().getDisplayName() : "White";
        Material colorMaterial =
                claim.getColor() != null
                        ? claim.getColor().getGlassPaneMaterial()
                        : Material.WHITE_STAINED_GLASS_PANE;

        ItemStack colorItem =
                new ItemBuilder(colorMaterial)
                        .name("<light_purple>Change Color")
                        .lore(
                                "",
                                "<gray>Current: " + colorTag + colorName,
                                "",
                                "<green>Click to change")
                        .build();
        setItem(
                12,
                new GuiItem(
                        colorItem,
                        e -> {
                            viewer.closeInventory();
                            new ColorPickerGui(plugin, viewer, claim).open();
                        }));

        // Teleport (slot 14)
        ItemStack teleportItem =
                new ItemBuilder(Material.ENDER_PEARL)
                        .name("<aqua>Teleport to Profile")
                        .lore(
                                "",
                                "<gray>Warp to this profile's",
                                "<gray>claimed territory",
                                "",
                                "<yellow>Cost: <white>" + (int) WARP_COST + " coins",
                                "",
                                "<green>Click to teleport")
                        .glow(true)
                        .build();
        setItem(14, new GuiItem(teleportItem, e -> warpToClaim()));

        // Settings (slot 16)
        ItemStack settingsItem =
                new ItemBuilder(Material.CHEST)
                        .name("<gold>Full Settings")
                        .lore(
                                "",
                                "<gray>Open the complete",
                                "<gray>settings menu",
                                "",
                                "<green>Click to open")
                        .build();
        setItem(
                16,
                new GuiItem(
                        settingsItem,
                        e -> {
                            viewer.closeInventory();
                            new ClaimSettingsGui(plugin, viewer, claim).open();
                        }));

        // Dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {9, 11, 13, 15, 17}) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void setupNavigationRow() {
        // Back button (slot 18)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to profile list")
                        .build();
        setItem(
                18,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new MyProfilesGui(plugin, viewer).open();
                        }));

        // Delete button (slot 22)
        ItemStack deleteItem =
                new ItemBuilder(Material.TNT)
                        .name("<red>Delete Profile")
                        .lore(
                                "",
                                "<gray>Permanently delete this",
                                "<gray>profile and all its chunks",
                                "",
                                "<red>This cannot be undone!",
                                "",
                                "<dark_red>Click to delete")
                        .build();
        setItem(
                22,
                new GuiItem(
                        deleteItem,
                        e -> {
                            viewer.closeInventory();
                            new ClaimDeleteConfirmGui(plugin, viewer, claim).open();
                        }));

        // Close button (slot 26)
        ItemStack closeItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<red>Close")
                        .lore("<gray>Close this menu")
                        .build();
        setItem(26, new GuiItem(closeItem, e -> viewer.closeInventory()));

        // Fill rest with dividers
        ItemStack divider = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i : new int[] {19, 20, 21, 23, 24, 25}) {
            setItem(i, new GuiItem(divider));
        }
    }

    private void warpToClaim() {
        // Revalidate claim before warping
        if (claim == null) {
            TextUtil.send(viewer, "<red>This claim no longer exists!");
            return;
        }

        if (claim.getChunks() == null || claim.getChunks().isEmpty()) {
            TextUtil.send(viewer, "<red>This profile has no chunks to warp to!");
            return;
        }

        EconomyProvider economy = ServerAPI.getInstance().getEconomyProvider();
        if (economy == null || !economy.isAvailable()) {
            TextUtil.send(viewer, "<red>Economy system is not available!");
            return;
        }

        if (!economy.has(viewer, WARP_COST)) {
            TextUtil.send(
                    viewer, "<red>You need <yellow>" + (int) WARP_COST + " coins<red> to warp!");
            TextUtil.send(
                    viewer,
                    "<gray>Your balance: <white>" + economy.format(economy.getBalance(viewer)));
            return;
        }

        ClaimedChunk chunk = claim.getChunks().get(0);
        World world = plugin.getServer().getWorld(claim.getWorld());

        if (world == null) {
            TextUtil.send(viewer, "<red>The world for this profile is not loaded!");
            return;
        }

        int x = (chunk.getChunkX() * 16) + 8;
        int z = (chunk.getChunkZ() * 16) + 8;
        Location warpLoc = findSafeLocation(world, x, z);

        if (warpLoc == null) {
            TextUtil.send(viewer, "<red>Could not find a safe location to warp to!");
            return;
        }

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

                if (!above.isPassable() || !aboveTwo.isPassable()) {
                    continue;
                }

                Material groundMaterial = ground.getType();
                if (groundMaterial == Material.LAVA) {
                    continue;
                }

                int score = 0;
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
                    score = 10;
                } else if (groundMaterial == Material.WATER) {
                    score = 5;
                } else if (ground.isPassable()) {
                    score = 1;
                }

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
}
