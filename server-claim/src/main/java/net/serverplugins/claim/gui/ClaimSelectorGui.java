package net.serverplugins.claim.gui;

import java.util.List;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimedChunk;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** GUI for selecting a claim to move a chunk to. */
public class ClaimSelectorGui extends Gui {

    private final ServerClaim plugin;
    private final Claim sourceClaim;
    private final ClaimedChunk chunk;
    private final List<Claim> eligibleClaims;

    public ClaimSelectorGui(
            ServerClaim plugin,
            Player player,
            Claim sourceClaim,
            ClaimedChunk chunk,
            List<Claim> eligibleClaims) {
        super(plugin, player, "<aqua>Select Destination Claim</aqua>", 54);
        this.plugin = plugin;
        this.sourceClaim = sourceClaim;
        this.chunk = chunk;
        this.eligibleClaims = eligibleClaims != null ? eligibleClaims : List.of();

        // Validate required parameters
        if (!GuiValidator.validateClaim(plugin, player, sourceClaim, "ClaimSelectorGui")) {
            return;
        }

        if (chunk == null) {
            player.sendMessage(
                    net.kyori.adventure.text.Component.text(
                            "This chunk no longer exists!",
                            net.kyori.adventure.text.format.NamedTextColor.RED));
            plugin.getLogger()
                    .warning(
                            "GUI validation failed: ClaimSelectorGui - Chunk is null for player "
                                    + player.getName());
            return;
        }

        // Check if list is empty
        if (this.eligibleClaims.isEmpty()) {
            player.sendMessage(
                    net.kyori.adventure.text.Component.text(
                            "No eligible claims found!",
                            net.kyori.adventure.text.format.NamedTextColor.RED));
            plugin.getLogger()
                    .warning(
                            "GUI validation failed: ClaimSelectorGui - No eligible claims for player "
                                    + player.getName());
            return;
        }
    }

    @Override
    protected void initializeItems() {
        // Safety checks
        if (sourceClaim == null || chunk == null) {
            plugin.getLogger()
                    .warning("Cannot initialize ClaimSelectorGui: source claim or chunk is null");
            return;
        }

        if (eligibleClaims == null || eligibleClaims.isEmpty()) {
            displayEmptyState();
            setupFooter();
            return;
        }

        // Header
        setupHeader();

        // Claim list
        setupClaimList();

        // Footer with back button
        setupFooter();
    }

    private void setupHeader() {
        // Safe null checks
        String chunkPos = chunk != null ? chunk.getChunkX() + ", " + chunk.getChunkZ() : "Unknown";
        String sourceClaimName =
                sourceClaim != null && sourceClaim.getName() != null
                        ? sourceClaim.getName()
                        : "Unnamed Claim";
        int claimCount = eligibleClaims != null ? eligibleClaims.size() : 0;

        // Title (slot 4)
        ItemStack titleItem =
                new ItemBuilder(Material.ENDER_CHEST)
                        .name("<gold>Move Chunk to Claim")
                        .lore(
                                "",
                                "<gray>Moving chunk: <white>" + chunkPos,
                                "<gray>From: <white>" + sourceClaimName,
                                "",
                                "<gray>Available claims: <green>" + claimCount,
                                "",
                                "<dark_gray>Select a destination claim below")
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(titleItem));

        // Fill header
        ItemStack headerFill = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 9; i++) {
            if (i != 4) {
                setItem(i, new GuiItem(headerFill));
            }
        }
    }

    private void setupClaimList() {
        if (eligibleClaims == null || eligibleClaims.isEmpty()) {
            displayEmptyState();
            return;
        }

        int slot = 9;
        for (Claim claim : eligibleClaims) {
            if (claim == null) {
                plugin.getLogger().warning("Skipping null claim in ClaimSelectorGui");
                continue;
            }

            if (slot >= 45) {
                plugin.getLogger().warning("Too many claims to display (max 36)");
                break;
            }

            ItemStack claimItem = createClaimItem(claim);
            final Claim targetClaim = claim;

            setItem(slot, new GuiItem(claimItem, e -> moveChunkToClaim(targetClaim)));
            slot++;
        }

        // Fill remaining slots
        ItemStack empty = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = slot; i < 45; i++) {
            setItem(i, new GuiItem(empty));
        }
    }

    private ItemStack createClaimItem(Claim claim) {
        if (claim == null) {
            return new ItemBuilder(Material.BARRIER)
                    .name("<red>Invalid Claim")
                    .lore("<gray>This claim is no longer valid")
                    .build();
        }

        int usedChunks = claim.getChunks() != null ? claim.getChunks().size() : 0;
        int totalChunks = claim.getTotalChunks();
        int available = totalChunks - usedChunks;

        Material icon = claim.getIcon() != null ? claim.getIcon() : Material.GRASS_BLOCK;
        String colorTag = claim.getColor() != null ? claim.getColor().getColorTag() : "<white>";
        String claimName = claim.getName() != null ? claim.getName() : "Unnamed Claim";

        // Check if chunk would be adjacent to this claim
        boolean wouldBeAdjacent = false;
        try {
            if (chunk != null) {
                wouldBeAdjacent = claim.isAdjacentTo(chunk.getChunkX(), chunk.getChunkZ());
            }
        } catch (Exception e) {
            plugin.getLogger()
                    .warning(
                            "Failed to check adjacency for claim "
                                    + claim.getId()
                                    + ": "
                                    + e.getMessage());
        }

        // Check if claim has space
        boolean hasSpace = available > 0;
        Material material = hasSpace ? icon : Material.BARRIER;
        String availabilityText =
                hasSpace
                        ? "<gray>Available: <green>" + available
                        : "<gray>Available: <red>0 (FULL)";

        return new ItemBuilder(material)
                .name(colorTag + claimName)
                .lore(
                        "",
                        "<gray>Chunks: <white>" + usedChunks + "/" + totalChunks,
                        availabilityText,
                        "",
                        wouldBeAdjacent
                                ? "<green>✓ Adjacent to this claim"
                                : "<yellow>⚠ Not adjacent (separate region)",
                        "",
                        hasSpace ? "<yellow>Click to move chunk here" : "<red>This claim is full!")
                .glow(wouldBeAdjacent && hasSpace)
                .build();
    }

    private void moveChunkToClaim(Claim targetClaim) {
        // Validate inputs
        if (targetClaim == null) {
            TextUtil.send(viewer, "<red>Invalid target claim!");
            viewer.closeInventory();
            return;
        }

        if (chunk == null) {
            TextUtil.send(viewer, "<red>This chunk no longer exists!");
            viewer.closeInventory();
            return;
        }

        if (sourceClaim == null) {
            TextUtil.send(viewer, "<red>Source claim no longer exists!");
            viewer.closeInventory();
            return;
        }

        // Verify source claim still exists
        Claim existingSource = null;
        try {
            existingSource = plugin.getClaimManager().getClaimById(sourceClaim.getId());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get source claim: " + e.getMessage());
        }

        if (existingSource == null) {
            TextUtil.send(viewer, "<red>Source claim no longer exists!");
            viewer.closeInventory();
            new MyProfilesGui(plugin, viewer).open();
            return;
        }

        // Verify chunk still belongs to source claim
        if (chunk.getClaimId() != existingSource.getId()) {
            TextUtil.send(viewer, "<red>This chunk no longer belongs to the source claim!");
            viewer.closeInventory();
            return;
        }

        // Verify target claim still has space
        Claim existingTarget = null;
        try {
            existingTarget = plugin.getClaimManager().getClaimById(targetClaim.getId());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get target claim: " + e.getMessage());
        }

        if (existingTarget == null) {
            TextUtil.send(viewer, "<red>Target claim no longer exists!");
            viewer.closeInventory();
            return;
        }

        if (!existingTarget.hasAvailableChunks()) {
            TextUtil.send(viewer, "<red>Target claim no longer has available chunk slots!");
            TextUtil.send(
                    viewer,
                    "<gray>Chunks: "
                            + existingTarget.getChunks().size()
                            + "/"
                            + existingTarget.getTotalChunks());
            viewer.closeInventory();
            return;
        }

        // Check ownership
        if (!existingSource.isOwner(viewer.getUniqueId())
                && !viewer.hasPermission("serverclaim.admin")) {
            TextUtil.send(viewer, "<red>You don't own the source claim!");
            viewer.closeInventory();
            return;
        }

        if (!existingTarget.isOwner(viewer.getUniqueId())
                && !viewer.hasPermission("serverclaim.admin")) {
            TextUtil.send(viewer, "<red>You don't own the target claim!");
            viewer.closeInventory();
            return;
        }

        // Check if this would leave source claim with 0 chunks
        if (existingSource.getChunks() != null && existingSource.getChunks().size() <= 1) {
            TextUtil.send(viewer, "<red>Cannot move the last chunk from a claim!");
            TextUtil.send(viewer, "<gray>This would delete the claim. Use /claim delete instead.");
            viewer.closeInventory();
            return;
        }

        viewer.closeInventory();

        // Make variables effectively final for lambda
        final Claim finalSource = existingSource;
        final Claim finalTarget = existingTarget;

        // Perform move asynchronously
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                // Record transfer for audit
                                plugin.getRepository()
                                        .recordChunkReassignment(chunk, finalSource, finalTarget);

                                // Update chunk's claim ID in database
                                plugin.getRepository()
                                        .reassignChunkToClaim(chunk, finalTarget.getId());

                                // Update in-memory models
                                finalSource.removeChunk(chunk);
                                chunk.setClaimId(finalTarget.getId());
                                finalTarget.addChunk(chunk);

                                // Update cache
                                plugin.getClaimManager()
                                        .updateChunkClaimMapping(chunk, finalTarget.getId());

                                // Check if source claim is now empty
                                if (finalSource.getChunks().isEmpty()) {
                                    plugin.getRepository().deleteClaim(finalSource);
                                    plugin.getClaimManager().invalidateClaim(finalSource.getId());
                                }

                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    viewer.playSound(
                                                            viewer.getLocation(),
                                                            Sound.ENTITY_ENDERMAN_TELEPORT,
                                                            0.5f,
                                                            1f);
                                                    TextUtil.send(
                                                            viewer,
                                                            "<green>Chunk moved to '"
                                                                    + finalTarget.getName()
                                                                    + "'!");

                                                    // If source claim still exists, go back to it,
                                                    // otherwise go to claims list
                                                    Claim sourceCheck =
                                                            plugin.getClaimManager()
                                                                    .getClaimById(
                                                                            sourceClaim.getId());
                                                    if (sourceCheck != null) {
                                                        new ManageChunksGui(
                                                                        plugin, viewer, sourceCheck)
                                                                .open();
                                                    } else {
                                                        new MyProfilesGui(plugin, viewer).open();
                                                    }
                                                });

                            } catch (Exception e) {
                                plugin.getLogger()
                                        .severe("Error moving chunk to claim: " + e.getMessage());
                                e.printStackTrace();
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    TextUtil.send(
                                                            viewer,
                                                            "<red>An error occurred while moving the chunk!");
                                                });
                            }
                        });
    }

    private void displayEmptyState() {
        ItemStack noClaimsItem =
                new ItemBuilder(Material.BARRIER)
                        .name("<yellow>No Eligible Claims")
                        .lore(
                                "",
                                "<gray>You don't have any other claims",
                                "<gray>in this world that have space",
                                "<gray>for this chunk.",
                                "",
                                "<gray>Requirements:",
                                "<white>• Must be in same world",
                                "<white>• Must have available chunk slots",
                                "<white>• Cannot be the current claim",
                                "",
                                "<green>Click to close")
                        .build();
        setItem(22, new GuiItem(noClaimsItem, e -> viewer.closeInventory()));

        // Fill remaining slots
        ItemStack empty = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 54; i++) {
            if (i != 22 && getInventory().getItem(i) == null) {
                setItem(i, new GuiItem(empty));
            }
        }
    }

    private void setupFooter() {
        // Back button (slot 45)
        ItemStack backItem =
                new ItemBuilder(Material.ARROW)
                        .name("<red>Back")
                        .lore("<gray>Return to chunk actions")
                        .build();
        setItem(
                45,
                new GuiItem(
                        backItem,
                        e -> {
                            viewer.closeInventory();
                            if (sourceClaim != null && chunk != null) {
                                new ChunkActionsGui(plugin, viewer, sourceClaim, chunk).open();
                            }
                        }));

        // Fill footer
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 46; i < 54; i++) {
            setItem(i, new GuiItem(filler));
        }
    }
}
