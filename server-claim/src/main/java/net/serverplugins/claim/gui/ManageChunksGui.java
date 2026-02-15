package net.serverplugins.claim.gui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiClickContext;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimedChunk;
import net.serverplugins.claim.models.ManagementPermission;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * GUI for viewing and managing all chunks in a claim. Displays a 7x5 grid of chunks (35 per page)
 * with pagination.
 */
public class ManageChunksGui extends Gui {

    private static final int CHUNKS_PER_PAGE = 35; // 7x5 grid
    private static final int GRID_START_SLOT = 10; // Start of the chunk grid
    private static final int GRID_WIDTH = 7;
    private static final int GRID_HEIGHT = 5;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    private final ServerClaim plugin;
    private final Claim claim;
    private final int page;
    private final List<ClaimedChunk> sortedChunks;

    public ManageChunksGui(ServerClaim plugin, Player player, Claim claim) {
        this(plugin, player, claim, 0);
    }

    public ManageChunksGui(ServerClaim plugin, Player player, Claim claim, int page) {
        super(plugin, player, "Manage Chunks", 54);
        this.plugin = plugin;
        this.page = page;

        // Validate claim exists
        if (!GuiValidator.validateClaim(plugin, player, claim, "ManageChunksGui")) {
            this.claim = null;
            this.sortedChunks = new ArrayList<>();
            return;
        }

        this.claim = claim;

        // Sort chunks by claimed date (oldest first), then by coordinates
        this.sortedChunks = new ArrayList<>(claim.getChunks());
        sortedChunks.sort(
                Comparator.comparing(ClaimedChunk::getClaimedAt)
                        .thenComparingInt(ClaimedChunk::getChunkX)
                        .thenComparingInt(ClaimedChunk::getChunkZ));
    }

    @Override
    protected void initializeItems() {
        // Safety check
        if (claim == null) {
            return;
        }

        // Check permission
        boolean hasPermission =
                claim.isOwner(viewer.getUniqueId())
                        || claim.hasManagementPermission(
                                viewer.getUniqueId(), ManagementPermission.MANAGE_CHUNKS)
                        || viewer.hasPermission("serverclaim.admin");

        // Header row (slots 0-8)
        setupHeader();

        // Chunk grid (slots 10-16, 19-25, 28-34, 37-43, 46-52) - 7x5 = 35 slots
        setupChunkGrid(hasPermission);

        // Navigation row (slots 45, 49, 53)
        setupNavigation();

        // Fill borders
        fillBorders();
    }

    private void setupHeader() {
        int totalChunks = claim.getChunks().size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalChunks / CHUNKS_PER_PAGE));
        String colorTag = claim.getColor() != null ? claim.getColor().getColorTag() : "<white>";

        // Title (slot 4)
        ItemStack titleItem =
                new ItemBuilder(Material.GRASS_BLOCK)
                        .name("<gold>Manage Chunks")
                        .lore(
                                "",
                                "<gray>Claim: " + colorTag + claim.getName(),
                                "<gray>Total Chunks: <white>" + totalChunks,
                                "<gray>Page: <white>" + (page + 1) + "/" + totalPages,
                                "",
                                "<dark_gray>Left-click chunk to teleport",
                                "<dark_gray>Right-click for more options")
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(titleItem));

        // Fill header with glass
        ItemStack headerFill = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) {
            if (i != 4) {
                setItem(i, new GuiItem(headerFill));
            }
        }
    }

    private void setupChunkGrid(boolean hasPermission) {
        int startIndex = page * CHUNKS_PER_PAGE;
        int endIndex = Math.min(startIndex + CHUNKS_PER_PAGE, sortedChunks.size());

        int chunkIndex = startIndex;
        for (int row = 0; row < GRID_HEIGHT; row++) {
            for (int col = 0; col < GRID_WIDTH; col++) {
                int slot = getGridSlot(row, col);

                if (chunkIndex < endIndex) {
                    ClaimedChunk chunk = sortedChunks.get(chunkIndex);
                    ItemStack item = createChunkItem(chunk, chunkIndex + 1);

                    if (hasPermission) {
                        final ClaimedChunk finalChunk = chunk;
                        setItem(
                                slot,
                                GuiItem.withContext(
                                        item, ctx -> handleChunkClick(finalChunk, ctx)));
                    } else {
                        setItem(slot, new GuiItem(item));
                    }
                    chunkIndex++;
                } else {
                    // Empty slot
                    ItemStack emptyItem =
                            new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                                    .name("<dark_gray>Empty Slot")
                                    .build();
                    setItem(slot, new GuiItem(emptyItem));
                }
            }
        }
    }

    private int getGridSlot(int row, int col) {
        // Grid starts at slot 10 (row 1, col 1) and spans 7 columns
        // Row 0: slots 10-16
        // Row 1: slots 19-25
        // Row 2: slots 28-34
        // Row 3: slots 37-43
        // Row 4: slots 46-52
        return (row + 1) * 9 + col + 1;
    }

    private ItemStack createChunkItem(ClaimedChunk chunk, int displayNumber) {
        int blockX = chunk.getChunkX() * 16;
        int blockZ = chunk.getChunkZ() * 16;
        String claimedDate = DATE_FORMAT.format(chunk.getClaimedAt());

        // Determine material based on world
        Material material = getMaterialForWorld(chunk.getWorld());

        // Check if player is in this chunk
        boolean isHere =
                viewer.getWorld().getName().equals(chunk.getWorld())
                        && viewer.getLocation().getChunk().getX() == chunk.getChunkX()
                        && viewer.getLocation().getChunk().getZ() == chunk.getChunkZ();

        ItemBuilder builder =
                new ItemBuilder(material)
                        .name(
                                "<aqua>Chunk #"
                                        + displayNumber
                                        + (isHere ? " <yellow>(You are here)" : ""))
                        .lore(
                                "",
                                "<gray>Coordinates: <white>"
                                        + chunk.getChunkX()
                                        + ", "
                                        + chunk.getChunkZ(),
                                "<gray>Block Position: <white>" + blockX + ", " + blockZ,
                                "<gray>World: <white>" + formatWorldName(chunk.getWorld()),
                                "<gray>Claimed: <white>" + claimedDate,
                                "",
                                "<green>Left-click <gray>to teleport",
                                "<yellow>Right-click <gray>for options");

        if (isHere) {
            builder.glow(true);
        }

        return builder.build();
    }

    private Material getMaterialForWorld(String worldName) {
        if (worldName.contains("nether")) {
            return Material.NETHERRACK;
        } else if (worldName.contains("end")) {
            return Material.END_STONE;
        }
        return Material.GRASS_BLOCK;
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

    private void handleChunkClick(ClaimedChunk chunk, GuiClickContext ctx) {
        if (ctx.isLeftClick()) {
            // Teleport to chunk
            teleportToChunk(chunk);
        } else if (ctx.isRightClick()) {
            // Open chunk actions menu
            viewer.closeInventory();
            new ChunkActionsGui(plugin, viewer, claim, chunk).open();
        }
    }

    private void teleportToChunk(ClaimedChunk chunk) {
        World world = plugin.getServer().getWorld(chunk.getWorld());
        if (world == null) {
            TextUtil.send(viewer, "<red>The world for this chunk is not loaded!");
            return;
        }

        // Check teleport cost
        double cost = getOwnerTeleportCost();
        if (cost > 0 && plugin.getEconomy() != null) {
            if (!plugin.getEconomy().has(viewer, cost)) {
                TextUtil.send(
                        viewer,
                        "<red>You need <gold>$"
                                + String.format("%.0f", cost)
                                + "<red> to teleport to this chunk!");
                return;
            }
        }

        // Find safe location in chunk center
        int centerX = (chunk.getChunkX() * 16) + 8;
        int centerZ = (chunk.getChunkZ() * 16) + 8;
        Location safeLoc = findSafeLocation(world, centerX, centerZ);

        if (safeLoc == null) {
            TextUtil.send(viewer, "<red>Could not find a safe location in this chunk!");
            return;
        }

        // Charge cost if applicable
        if (cost > 0 && plugin.getEconomy() != null) {
            if (!plugin.getEconomy().withdraw(viewer, cost)) {
                TextUtil.send(viewer, "<red>Failed to process payment!");
                return;
            }
            TextUtil.send(viewer, "<gray>(-$" + String.format("%.0f", cost) + ")");
        }

        viewer.closeInventory();
        viewer.teleport(safeLoc);
        TextUtil.send(
                viewer,
                "<green>Teleported to chunk " + chunk.getChunkX() + ", " + chunk.getChunkZ());
    }

    private double getOwnerTeleportCost() {
        // Owner teleport is free by default
        if (claim.isOwner(viewer.getUniqueId())) {
            return plugin.getClaimConfig().getChunkTeleportOwnerCost();
        }
        return plugin.getClaimConfig().getChunkTeleportMemberCost();
    }

    /** Finds a safe teleport location at the given coordinates. */
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

                // Never teleport to lava
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
                    score = 10;
                } else if (groundMaterial == Material.WATER) {
                    score = 5;
                } else if (ground.isPassable()) {
                    score = 1;
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

    private void setupNavigation() {
        int totalChunks = sortedChunks.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalChunks / CHUNKS_PER_PAGE));

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

        // Previous page (slot 48) - only if not on first page
        if (page > 0) {
            ItemStack prevItem =
                    new ItemBuilder(Material.SPECTRAL_ARROW)
                            .name("<yellow>Previous Page")
                            .lore("<gray>Go to page " + page)
                            .build();
            setItem(
                    48,
                    new GuiItem(
                            prevItem,
                            e -> {
                                viewer.closeInventory();
                                new ManageChunksGui(plugin, viewer, claim, page - 1).open();
                            }));
        }

        // Page indicator (slot 49)
        ItemStack pageItem =
                new ItemBuilder(Material.PAPER)
                        .name("<white>Page " + (page + 1) + "/" + totalPages)
                        .lore(
                                "",
                                "<gray>Showing chunks "
                                        + (page * CHUNKS_PER_PAGE + 1)
                                        + "-"
                                        + Math.min((page + 1) * CHUNKS_PER_PAGE, totalChunks)
                                        + " of "
                                        + totalChunks)
                        .build();
        setItem(49, new GuiItem(pageItem));

        // Next page (slot 50) - only if not on last page
        if (page < totalPages - 1) {
            ItemStack nextItem =
                    new ItemBuilder(Material.SPECTRAL_ARROW)
                            .name("<yellow>Next Page")
                            .lore("<gray>Go to page " + (page + 2))
                            .build();
            setItem(
                    50,
                    new GuiItem(
                            nextItem,
                            e -> {
                                viewer.closeInventory();
                                new ManageChunksGui(plugin, viewer, claim, page + 1).open();
                            }));
        }

        // Main menu (slot 53)
        ItemStack menuItem =
                new ItemBuilder(Material.COMPASS)
                        .name("<gold>Main Menu")
                        .lore("<gray>Return to claim main menu")
                        .build();
        setItem(
                53,
                new GuiItem(
                        menuItem,
                        e -> {
                            viewer.closeInventory();
                            new ClaimMenuGui(plugin, viewer).open();
                        }));
    }

    private void fillBorders() {
        Material borderMaterial =
                claim.getColor() != null
                        ? claim.getColor().getGlassPaneMaterial()
                        : Material.WHITE_STAINED_GLASS_PANE;
        ItemStack borderItem = new ItemBuilder(borderMaterial).name(" ").build();

        // Left column (slots 9, 18, 27, 36, 45) - except 45 which is back button
        for (int slot : new int[] {9, 18, 27, 36}) {
            setItem(slot, new GuiItem(borderItem));
        }

        // Right column (slots 17, 26, 35, 44, 53) - except 53 which is menu button
        for (int slot : new int[] {17, 26, 35, 44}) {
            setItem(slot, new GuiItem(borderItem));
        }

        // Bottom row fill
        ItemStack bottomFill = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 46; i < 53; i++) {
            if (i != 48 && i != 49 && i != 50) {
                setItem(i, new GuiItem(bottomFill));
            }
        }
    }
}
