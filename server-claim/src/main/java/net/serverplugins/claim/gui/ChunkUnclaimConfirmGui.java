package net.serverplugins.claim.gui;

import java.util.ArrayList;
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

/** Confirmation GUI for unclaiming a chunk or connected chunks. */
public class ChunkUnclaimConfirmGui extends Gui {

    private final ServerClaim plugin;
    private final Claim claim;
    private final ClaimedChunk chunk;
    private final boolean bulkUnclaim;
    private final List<ClaimedChunk> connectedChunks;

    public ChunkUnclaimConfirmGui(
            ServerClaim plugin,
            Player player,
            Claim claim,
            ClaimedChunk chunk,
            boolean bulkUnclaim) {
        super(plugin, player, "<red>Confirm Unclaim</red>", 27);
        this.plugin = plugin;

        // Validate claim exists and player owns it
        if (!GuiValidator.validateClaimOwnership(plugin, player, claim, "ChunkUnclaimConfirmGui")) {
            this.claim = null;
            this.chunk = null;
            this.bulkUnclaim = false;
            this.connectedChunks = new ArrayList<>();
            return;
        }

        // Validate chunk exists
        if (chunk == null) {
            player.sendMessage(TextUtil.parse("<red>Invalid chunk data!"));
            plugin.getLogger()
                    .warning("ChunkUnclaimConfirmGui - Null chunk for player " + player.getName());
            this.claim = null;
            this.chunk = null;
            this.bulkUnclaim = false;
            this.connectedChunks = new ArrayList<>();
            return;
        }

        // Verify chunk belongs to this claim
        boolean chunkBelongsToClaim =
                claim.getChunks() != null
                        && claim.getChunks().stream()
                                .anyMatch(
                                        c ->
                                                c != null
                                                        && c.getChunkX() == chunk.getChunkX()
                                                        && c.getChunkZ() == chunk.getChunkZ()
                                                        && c.getWorld() != null
                                                        && c.getWorld().equals(chunk.getWorld()));

        if (!chunkBelongsToClaim) {
            player.sendMessage(TextUtil.parse("<red>This chunk doesn't belong to the claim!"));
            plugin.getLogger()
                    .warning(
                            "ChunkUnclaimConfirmGui - Chunk mismatch for player "
                                    + player.getName());
            this.claim = null;
            this.chunk = null;
            this.bulkUnclaim = false;
            this.connectedChunks = new ArrayList<>();
            return;
        }

        this.claim = claim;
        this.chunk = chunk;
        this.bulkUnclaim = bulkUnclaim;

        // Calculate connected chunks safely
        List<ClaimedChunk> chunks;
        if (bulkUnclaim) {
            try {
                chunks = plugin.getClaimManager().getConnectedChunks(claim, chunk);
                if (chunks == null || chunks.isEmpty()) {
                    chunks = List.of(chunk);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to get connected chunks: " + e.getMessage());
                chunks = List.of(chunk);
            }
        } else {
            chunks = List.of(chunk);
        }
        this.connectedChunks = chunks;
    }

    @Override
    protected void initializeItems() {
        // Safety check - early return if validation failed in constructor
        if (claim == null || chunk == null || connectedChunks.isEmpty()) {
            return;
        }

        // Check permission
        if (!claim.isOwner(viewer.getUniqueId()) && !viewer.hasPermission("serverclaim.admin")) {
            TextUtil.send(
                    viewer, "<red>You don't have permission to unclaim chunks from this claim!");
            return;
        }

        int chunkCount = connectedChunks.size();
        int totalChunks = claim.getChunks() != null ? claim.getChunks().size() : 0;
        boolean isLastChunk = chunkCount >= totalChunks;

        // Warning info (slot 4)
        List<String> warningLore = new ArrayList<>();
        warningLore.add("");
        warningLore.add("<gray>You are about to unclaim:");
        warningLore.add("");

        if (bulkUnclaim) {
            warningLore.add("<white>" + chunkCount + " connected chunks");
        } else {
            warningLore.add("<white>Chunk " + chunk.getChunkX() + ", " + chunk.getChunkZ());
        }

        warningLore.add("");
        warningLore.add(
                "<gray>Claim: <white>" + (claim.getName() != null ? claim.getName() : "Unnamed"));
        warningLore.add("<gray>World: <white>" + formatWorldName(chunk.getWorld()));
        warningLore.add("");

        if (isLastChunk) {
            warningLore.add("<dark_red><bold>⚠ WARNING ⚠</bold>");
            warningLore.add("<dark_red>This will DELETE your entire claim!");
            warningLore.add("<dark_red>All data will be lost!");
            warningLore.add("");
        }

        warningLore.add("<red>This action cannot be undone!");
        warningLore.add("<red>Land will become wilderness.");

        ItemStack warningItem =
                new ItemBuilder(isLastChunk ? Material.BARRIER : Material.TNT)
                        .name(
                                isLastChunk
                                        ? "<dark_red><bold>⚠ FINAL CHUNK WARNING ⚠</bold>"
                                        : "<red>Warning!")
                        .lore(warningLore.toArray(new String[0]))
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(warningItem));

        // Confirm button (slot 11)
        ItemStack confirmItem =
                new ItemBuilder(Material.LIME_CONCRETE)
                        .name("<green>Confirm Unclaim")
                        .lore(
                                "",
                                "<gray>Click to unclaim",
                                bulkUnclaim
                                        ? "<white>" + chunkCount + " chunks"
                                        : "<white>this chunk")
                        .build();
        setItem(11, new GuiItem(confirmItem, e -> performUnclaim()));

        // Cancel button (slot 15)
        ItemStack cancelItem =
                new ItemBuilder(Material.RED_CONCRETE)
                        .name("<red>Cancel")
                        .lore("", "<gray>Go back without unclaiming")
                        .build();
        setItem(
                15,
                new GuiItem(
                        cancelItem,
                        e -> {
                            viewer.closeInventory();
                            // Safely return to chunk actions if possible
                            if (claim != null && chunk != null) {
                                new ChunkActionsGui(plugin, viewer, claim, chunk).open();
                            } else if (claim != null) {
                                new ManageChunksGui(plugin, viewer, claim).open();
                            } else {
                                new MyProfilesGui(plugin, viewer).open();
                            }
                        }));

        // Fill empty slots
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < 27; i++) {
            if (i != 4 && i != 11 && i != 15) {
                setItem(i, new GuiItem(filler));
            }
        }
    }

    private void performUnclaim() {
        // Re-validate claim exists
        if (claim == null) {
            TextUtil.send(viewer, "<red>Claim data is missing!");
            viewer.closeInventory();
            new MyProfilesGui(plugin, viewer).open();
            return;
        }

        // Verify claim still exists and chunks are still claimed
        Claim existingClaim = plugin.getClaimManager().getClaimById(claim.getId());
        if (existingClaim == null) {
            TextUtil.send(viewer, "<red>Claim no longer exists!");
            viewer.closeInventory();
            new MyProfilesGui(plugin, viewer).open();
            return;
        }

        // Check ownership
        if (!claim.isOwner(viewer.getUniqueId()) && !viewer.hasPermission("serverclaim.admin")) {
            TextUtil.send(viewer, "<red>You don't own this claim!");
            viewer.closeInventory();
            return;
        }

        // Validate chunks exist
        if (connectedChunks == null || connectedChunks.isEmpty()) {
            TextUtil.send(viewer, "<red>No chunks to unclaim!");
            viewer.closeInventory();
            return;
        }

        viewer.closeInventory();

        // Perform unclaim asynchronously
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                int unclaimedCount = 0;

                                for (ClaimedChunk c : connectedChunks) {
                                    if (c == null) continue;

                                    // Verify chunk is still in claim
                                    if (existingClaim.getChunks() != null
                                            && existingClaim.getChunks().stream()
                                                    .anyMatch(
                                                            ch ->
                                                                    ch != null
                                                                            && ch.getChunkX()
                                                                                    == c.getChunkX()
                                                                            && ch.getChunkZ()
                                                                                    == c.getChunkZ()
                                                                            && ch.getWorld() != null
                                                                            && ch.getWorld()
                                                                                    .equals(
                                                                                            c
                                                                                                    .getWorld()))) {

                                        // Record transfer for audit
                                        plugin.getRepository().recordChunkUnclaim(c, existingClaim);

                                        // Delete chunk from database
                                        plugin.getRepository().deleteChunk(c);
                                        existingClaim.removeChunk(c);

                                        // Update cache
                                        plugin.getClaimManager()
                                                .invalidateChunkCache(
                                                        c.getWorld(), c.getChunkX(), c.getChunkZ());

                                        unclaimedCount++;
                                    }
                                }

                                // Update used chunk count
                                plugin.getClaimManager()
                                        .decrementUsedChunkCount(
                                                claim.getOwnerUuid(), unclaimedCount);

                                // Check if claim is now empty
                                if (existingClaim.getChunks().isEmpty()) {
                                    plugin.getRepository().deleteClaim(existingClaim);
                                    plugin.getClaimManager().invalidateClaim(existingClaim.getId());

                                    final int finalCount = unclaimedCount;
                                    plugin.getServer()
                                            .getScheduler()
                                            .runTask(
                                                    plugin,
                                                    () -> {
                                                        viewer.playSound(
                                                                viewer.getLocation(),
                                                                Sound.BLOCK_ANVIL_DESTROY,
                                                                0.5f,
                                                                1f);
                                                        TextUtil.send(
                                                                viewer,
                                                                "<green>Unclaimed "
                                                                        + finalCount
                                                                        + " chunk(s)!");
                                                        TextUtil.send(
                                                                viewer,
                                                                "<gray>Claim was deleted (no chunks remaining).");
                                                        new MyProfilesGui(plugin, viewer).open();
                                                    });
                                } else {
                                    final int finalCount = unclaimedCount;
                                    plugin.getServer()
                                            .getScheduler()
                                            .runTask(
                                                    plugin,
                                                    () -> {
                                                        viewer.playSound(
                                                                viewer.getLocation(),
                                                                Sound.BLOCK_GRASS_BREAK,
                                                                1f,
                                                                1f);
                                                        TextUtil.send(
                                                                viewer,
                                                                "<green>Unclaimed "
                                                                        + finalCount
                                                                        + " chunk(s)!");
                                                        new ManageChunksGui(
                                                                        plugin,
                                                                        viewer,
                                                                        existingClaim)
                                                                .open();
                                                    });
                                }

                            } catch (Exception e) {
                                plugin.getLogger()
                                        .severe("Error unclaiming chunks: " + e.getMessage());
                                e.printStackTrace();
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    TextUtil.send(
                                                            viewer,
                                                            "<red>An error occurred while unclaiming chunks!");
                                                });
                            }
                        });
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
