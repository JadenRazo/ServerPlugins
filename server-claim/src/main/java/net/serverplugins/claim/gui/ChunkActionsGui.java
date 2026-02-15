package net.serverplugins.claim.gui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.serverplugins.api.gui.Gui;
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

/** GUI for single chunk operations including teleport, unclaim, transfer, and bulk operations. */
public class ChunkActionsGui extends Gui {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final ServerClaim plugin;
    private final Claim claim;
    private final ClaimedChunk chunk;

    public ChunkActionsGui(ServerClaim plugin, Player player, Claim claim, ClaimedChunk chunk) {
        super(plugin, player, "<gold>Chunk Actions</gold>", 27);
        this.plugin = plugin;
        this.claim = claim;
        this.chunk = chunk;

        // Validate required parameters
        if (!GuiValidator.validateClaim(plugin, player, claim, "ChunkActionsGui")) {
            // Claim is null - player will be notified by validator
            return;
        }

        if (chunk == null) {
            player.sendMessage(
                    net.kyori.adventure.text.Component.text(
                            "This chunk no longer exists!",
                            net.kyori.adventure.text.format.NamedTextColor.RED));
            plugin.getLogger()
                    .warning(
                            "GUI validation failed: ChunkActionsGui - Chunk is null for player "
                                    + player.getName()
                                    + " in claim "
                                    + claim.getId());
            return;
        }

        // Verify chunk belongs to claim
        if (chunk.getClaimId() != claim.getId()) {
            player.sendMessage(
                    net.kyori.adventure.text.Component.text(
                            "This chunk doesn't belong to this claim!",
                            net.kyori.adventure.text.format.NamedTextColor.RED));
            plugin.getLogger()
                    .warning(
                            "GUI validation failed: ChunkActionsGui - Chunk "
                                    + chunk.getChunkX()
                                    + ","
                                    + chunk.getChunkZ()
                                    + " doesn't belong to claim "
                                    + claim.getId());
            return;
        }
    }

    @Override
    protected void initializeItems() {
        // Safety check: ensure claim and chunk are still valid
        if (claim == null || chunk == null) {
            plugin.getLogger().warning("Cannot initialize ChunkActionsGui: claim or chunk is null");
            return;
        }

        // Verify claim still exists in manager
        Claim freshClaim = plugin.getClaimManager().getClaimById(claim.getId());
        if (freshClaim == null) {
            plugin.getLogger()
                    .warning(
                            "Cannot initialize ChunkActionsGui: claim "
                                    + claim.getId()
                                    + " no longer exists");
            TextUtil.send(viewer, "<red>This claim no longer exists!");
            viewer.closeInventory();
            return;
        }

        boolean hasPermission =
                claim.isOwner(viewer.getUniqueId())
                        || claim.hasManagementPermission(
                                viewer.getUniqueId(), ManagementPermission.MANAGE_CHUNKS)
                        || viewer.hasPermission("serverclaim.admin");

        boolean transferEnabled = plugin.getClaimConfig().isChunkTransferEnabled();

        // Safe check for connected chunks
        List<ClaimedChunk> connected = null;
        try {
            connected = plugin.getClaimManager().getConnectedChunks(claim, chunk);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get connected chunks: " + e.getMessage());
            connected = List.of(chunk); // Fallback to just this chunk
        }

        if (connected == null || connected.isEmpty()) {
            connected = List.of(chunk); // Safety fallback
        }

        // Chunk info (slot 4)
        setupChunkInfo();

        // Main actions row
        if (hasPermission) {
            setupTeleportButton(10);
            setupUnclaimButton(11);

            if (transferEnabled) {
                setupTransferButton(12);
                setupMoveToClaimButton(13);
            }

            // Bulk operations
            if (connected.size() > 1) {
                setupBulkTransferButton(15, connected);
                setupBulkUnclaimButton(16, connected);
            }
        } else {
            // View-only mode - just teleport
            setupTeleportButton(13);
        }

        // Back button (slot 22)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to chunk list")
                        .build();
        setItem(
                22,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            new ManageChunksGui(plugin, viewer, claim).open();
                        }));

        // Fill empty slots
        fillEmpty(new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
    }

    private void setupChunkInfo() {
        if (chunk == null || claim == null) {
            return; // Safety check
        }

        int blockX = chunk.getChunkX() * 16;
        int blockZ = chunk.getChunkZ() * 16;
        String claimedDate =
                chunk.getClaimedAt() != null ? DATE_FORMAT.format(chunk.getClaimedAt()) : "Unknown";

        // Safe check for connected chunks
        List<ClaimedChunk> connected = null;
        try {
            connected = plugin.getClaimManager().getConnectedChunks(claim, chunk);
        } catch (Exception e) {
            plugin.getLogger()
                    .warning("Failed to get connected chunks in info display: " + e.getMessage());
            connected = List.of(chunk);
        }

        if (connected == null) {
            connected = List.of(chunk);
        }

        String colorTag = claim.getColor() != null ? claim.getColor().getColorTag() : "<white>";
        String claimName = claim.getName() != null ? claim.getName() : "Unnamed Claim";
        String worldName = chunk.getWorld() != null ? formatWorldName(chunk.getWorld()) : "Unknown";

        ItemStack infoItem =
                new ItemBuilder(Material.FILLED_MAP)
                        .name("<gold>Chunk Information")
                        .lore(
                                "",
                                "<gray>Claim: " + colorTag + claimName,
                                "",
                                "<gray>Chunk Position: <white>"
                                        + chunk.getChunkX()
                                        + ", "
                                        + chunk.getChunkZ(),
                                "<gray>Block Position: <white>" + blockX + ", " + blockZ,
                                "<gray>World: <white>" + worldName,
                                "<gray>Claimed: <white>" + claimedDate,
                                "",
                                "<gray>Connected Chunks: <aqua>" + connected.size())
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(infoItem));
    }

    private void setupTeleportButton(int slot) {
        double cost = getTeleportCost();
        String costStr =
                cost > 0 ? "<gray>Cost: <gold>$" + String.format("%.0f", cost) : "<green>Free";

        ItemStack teleportItem =
                new ItemBuilder(Material.ENDER_PEARL)
                        .name("<green>Teleport")
                        .lore(
                                "",
                                "<gray>Teleport to the center",
                                "<gray>of this chunk.",
                                "",
                                costStr,
                                "",
                                "<yellow>Click to teleport")
                        .build();
        setItem(slot, new GuiItem(teleportItem, e -> teleportToChunk()));
    }

    private void setupUnclaimButton(int slot) {
        // Check if this is the last chunk
        boolean isLastChunk = claim.getChunks() != null && claim.getChunks().size() <= 1;

        if (isLastChunk) {
            // Last chunk - show locked button
            ItemStack lockedItem =
                    new ItemBuilder(Material.GRAY_CONCRETE)
                            .name("<red>Cannot Unclaim")
                            .lore(
                                    "",
                                    "<gray>This is the last chunk",
                                    "<gray>in your claim.",
                                    "",
                                    "<red>Unclaiming would delete the claim!",
                                    "",
                                    "<gray>Use /claim delete instead")
                            .build();
            setItem(slot, new GuiItem(lockedItem));
        } else {
            // Normal unclaim button
            ItemStack unclaimItem =
                    new ItemBuilder(Material.BARRIER)
                            .name("<red>Unclaim Chunk")
                            .lore(
                                    "",
                                    "<gray>Remove this chunk from",
                                    "<gray>your claim.",
                                    "",
                                    "<yellow>⚠ Warning icon",
                                    "<red>This cannot be undone!",
                                    "",
                                    "<yellow>Click to unclaim")
                            .build();
            setItem(
                    slot,
                    new GuiItem(
                            unclaimItem,
                            e -> {
                                viewer.closeInventory();
                                new ChunkUnclaimConfirmGui(plugin, viewer, claim, chunk, false)
                                        .open();
                            }));
        }
    }

    private void setupTransferButton(int slot) {
        double costPerChunk = plugin.getClaimConfig().getChunkTransferCostPerChunk();
        String costStr =
                costPerChunk > 0
                        ? "<gray>Cost: <gold>$" + String.format("%.0f", costPerChunk)
                        : "<green>Free";

        ItemStack transferItem =
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name("<light_purple>Transfer Ownership")
                        .lore(
                                "",
                                "<gray>Give this chunk to",
                                "<gray>another player.",
                                "",
                                costStr,
                                "",
                                "<yellow>Click to transfer")
                        .build();
        setItem(
                slot,
                new GuiItem(
                        transferItem,
                        e -> {
                            viewer.closeInventory();
                            TextUtil.send(
                                    viewer,
                                    "<yellow>Type the player name to transfer this chunk to:");
                            TextUtil.send(viewer, "<gray>(Type 'cancel' to cancel)");
                            plugin.getClaimManager()
                                    .awaitChunkTransferInput(viewer, claim, chunk, false);
                        }));
    }

    private void setupMoveToClaimButton(int slot) {
        // Safe get player claims
        List<Claim> playerClaims = null;
        try {
            playerClaims = plugin.getClaimManager().getPlayerClaims(viewer.getUniqueId());
        } catch (Exception e) {
            plugin.getLogger()
                    .warning("Failed to get player claims for move button: " + e.getMessage());
            playerClaims = List.of();
        }

        if (playerClaims == null) {
            playerClaims = List.of();
        }

        // Filter out current claim and claims in different worlds
        String chunkWorld = chunk.getWorld() != null ? chunk.getWorld() : "";
        List<Claim> eligibleClaims =
                playerClaims.stream()
                        .filter(c -> c != null)
                        .filter(c -> c.getId() != claim.getId())
                        .filter(c -> c.getWorld() != null && c.getWorld().equals(chunkWorld))
                        .filter(Claim::hasAvailableChunks)
                        .toList();

        Material material =
                eligibleClaims.isEmpty() ? Material.GRAY_SHULKER_BOX : Material.ENDER_CHEST;
        String status =
                eligibleClaims.isEmpty()
                        ? "<red>No eligible claims"
                        : "<green>" + eligibleClaims.size() + " claim(s) available";

        ItemStack moveItem =
                new ItemBuilder(material)
                        .name("<aqua>Move to Different Claim")
                        .lore(
                                "",
                                "<gray>Reassign this chunk to",
                                "<gray>another claim you own.",
                                "",
                                status,
                                "",
                                eligibleClaims.isEmpty()
                                        ? "<dark_gray>Not available"
                                        : "<yellow>Click to choose claim")
                        .build();

        if (!eligibleClaims.isEmpty()) {
            setItem(
                    slot,
                    new GuiItem(
                            moveItem,
                            e -> {
                                viewer.closeInventory();
                                new ClaimSelectorGui(plugin, viewer, claim, chunk, eligibleClaims)
                                        .open();
                            }));
        } else {
            setItem(slot, new GuiItem(moveItem));
        }
    }

    private void setupBulkTransferButton(int slot, List<ClaimedChunk> connected) {
        double costPerChunk = plugin.getClaimConfig().getChunkTransferCostPerChunk();
        double totalCost = costPerChunk * connected.size();
        String costStr =
                totalCost > 0
                        ? "<gray>Total Cost: <gold>$" + String.format("%.0f", totalCost)
                        : "<green>Free";

        ItemStack bulkTransferItem =
                new ItemBuilder(Material.CHEST_MINECART)
                        .name("<light_purple>Transfer Connected Chunks")
                        .lore(
                                "",
                                "<gray>Transfer all <aqua>" + connected.size() + "<gray> connected",
                                "<gray>chunks to another player.",
                                "",
                                costStr,
                                "",
                                "<yellow>Click to transfer all")
                        .glow(true)
                        .build();
        setItem(
                slot,
                new GuiItem(
                        bulkTransferItem,
                        e -> {
                            viewer.closeInventory();
                            TextUtil.send(
                                    viewer,
                                    "<yellow>Type the player name to transfer "
                                            + connected.size()
                                            + " connected chunks to:");
                            TextUtil.send(viewer, "<gray>(Type 'cancel' to cancel)");
                            plugin.getClaimManager()
                                    .awaitChunkTransferInput(viewer, claim, chunk, true);
                        }));
    }

    private void setupBulkUnclaimButton(int slot, List<ClaimedChunk> connected) {
        ItemStack bulkUnclaimItem =
                new ItemBuilder(Material.TNT_MINECART)
                        .name("<red>Unclaim Connected Chunks")
                        .lore(
                                "",
                                "<gray>Unclaim all <aqua>" + connected.size() + "<gray> connected",
                                "<gray>chunks at once.",
                                "",
                                "<red>This cannot be undone!",
                                "",
                                "<yellow>Click to unclaim all")
                        .glow(true)
                        .build();
        setItem(
                slot,
                new GuiItem(
                        bulkUnclaimItem,
                        e -> {
                            viewer.closeInventory();
                            new ChunkUnclaimConfirmGui(plugin, viewer, claim, chunk, true).open();
                        }));
    }

    private double getTeleportCost() {
        if (claim.isOwner(viewer.getUniqueId())) {
            return plugin.getClaimConfig().getChunkTeleportOwnerCost();
        }
        return plugin.getClaimConfig().getChunkTeleportMemberCost();
    }

    private void teleportToChunk() {
        // Validate chunk still exists
        if (chunk == null || chunk.getWorld() == null) {
            TextUtil.send(viewer, "<red>This chunk is no longer valid!");
            return;
        }

        World world = plugin.getServer().getWorld(chunk.getWorld());
        if (world == null) {
            TextUtil.send(viewer, "<red>The world for this chunk is not loaded!");
            return;
        }

        double cost = getTeleportCost();
        if (cost > 0) {
            if (plugin.getEconomy() == null) {
                TextUtil.send(viewer, "<red>Economy system is unavailable!");
                return;
            }

            if (!plugin.getEconomy().has(viewer, cost)) {
                TextUtil.send(
                        viewer,
                        "<red>You need <gold>$"
                                + String.format("%.0f", cost)
                                + "<red> to teleport!");
                TextUtil.send(
                        viewer,
                        "<gray>Your balance: <gold>$"
                                + String.format("%.0f", plugin.getEconomy().getBalance(viewer)));
                return;
            }
        }

        int centerX = (chunk.getChunkX() * 16) + 8;
        int centerZ = (chunk.getChunkZ() * 16) + 8;
        Location safeLoc = findSafeLocation(world, centerX, centerZ);

        if (safeLoc == null) {
            TextUtil.send(viewer, "<red>Could not find a safe location in this chunk!");
            return;
        }

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

    private String formatWorldName(String worldName) {
        if (worldName == null) return "Unknown";
        return switch (worldName) {
            case "world", "playworld" -> "Overworld";
            case "world_nether", "playworld_nether" -> "Nether";
            case "world_the_end", "playworld_the_end" -> "The End";
            default -> worldName;
        };
    }
}
