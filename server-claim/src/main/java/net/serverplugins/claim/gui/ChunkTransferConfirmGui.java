package net.serverplugins.claim.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.serverplugins.api.gui.Gui;
import net.serverplugins.api.gui.GuiItem;
import net.serverplugins.api.utils.ItemBuilder;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimedChunk;
import net.serverplugins.claim.models.PlayerClaimData;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Confirmation GUI for transferring chunks to another player. */
public class ChunkTransferConfirmGui extends Gui {

    private final ServerClaim plugin;
    private final Claim sourceClaim;
    private final List<ClaimedChunk> chunksToTransfer;
    private final UUID targetUuid;
    private final String targetName;

    public ChunkTransferConfirmGui(
            ServerClaim plugin,
            Player player,
            Claim sourceClaim,
            List<ClaimedChunk> chunksToTransfer,
            UUID targetUuid,
            String targetName) {
        super(plugin, player, "<gold>Confirm Transfer</gold>", 27);
        this.plugin = plugin;

        // Validate source claim exists
        if (!GuiValidator.validateClaimOwnership(
                plugin, player, sourceClaim, "ChunkTransferConfirmGui")) {
            this.sourceClaim = null;
            this.chunksToTransfer = new ArrayList<>();
            this.targetUuid = null;
            this.targetName = "Unknown";
            return;
        }

        // Validate chunks list is not null or empty
        if (chunksToTransfer == null || chunksToTransfer.isEmpty()) {
            player.sendMessage(TextUtil.parse("<red>No chunks selected for transfer!"));
            plugin.getLogger()
                    .warning(
                            "ChunkTransferConfirmGui - Empty chunks list for player "
                                    + player.getName());
            this.sourceClaim = null;
            this.chunksToTransfer = new ArrayList<>();
            this.targetUuid = null;
            this.targetName = "Unknown";
            return;
        }

        // Validate target player
        if (targetUuid == null) {
            player.sendMessage(TextUtil.parse("<red>Invalid target player!"));
            plugin.getLogger()
                    .warning(
                            "ChunkTransferConfirmGui - Null target UUID for player "
                                    + player.getName());
            this.sourceClaim = null;
            this.chunksToTransfer = new ArrayList<>();
            this.targetUuid = null;
            this.targetName = "Unknown";
            return;
        }

        this.sourceClaim = sourceClaim;
        this.chunksToTransfer = new ArrayList<>(chunksToTransfer);
        this.targetUuid = targetUuid;
        this.targetName = targetName != null ? targetName : "Unknown Player";
    }

    @Override
    protected void initializeItems() {
        // Safety check - early return if validation failed in constructor
        if (sourceClaim == null || chunksToTransfer.isEmpty() || targetUuid == null) {
            return;
        }

        // Check permission
        if (!sourceClaim.isOwner(viewer.getUniqueId())
                && !viewer.hasPermission("serverclaim.admin")) {
            TextUtil.send(
                    viewer, "<red>You don't have permission to transfer chunks from this claim!");
            return;
        }

        int chunkCount = chunksToTransfer.size();
        double costPerChunk =
                plugin.getClaimConfig() != null
                        ? plugin.getClaimConfig().getChunkTransferCostPerChunk()
                        : 0.0;
        double totalCost = costPerChunk * chunkCount;

        // Info item (slot 4) - Warning display
        ItemStack infoItem =
                new ItemBuilder(Material.PLAYER_HEAD)
                        .name("<gold><bold>⚠ Transfer Confirmation</bold>")
                        .lore(
                                "",
                                "<gray>Source Claim: <white>"
                                        + (sourceClaim.getName() != null
                                                ? sourceClaim.getName()
                                                : "Unnamed"),
                                "<gray>Recipient: <aqua>" + targetName,
                                "<gray>Chunks: <white>" + chunkCount,
                                "",
                                totalCost > 0
                                        ? "<gray>Total Cost: <gold>$"
                                                + String.format("%.0f", totalCost)
                                        : "<green>Free transfer",
                                "",
                                "<yellow>The recipient will receive",
                                "<yellow>these chunks in a claim.",
                                "",
                                "<red>This action cannot be undone!")
                        .glow(true)
                        .build();
        setItem(4, new GuiItem(infoItem));

        // Confirm button (slot 11)
        boolean canAfford =
                totalCost == 0
                        || (plugin.getEconomy() != null
                                && plugin.getEconomy().has(viewer, totalCost));

        ItemStack confirmItem =
                new ItemBuilder(canAfford ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE)
                        .name(canAfford ? "<green>Confirm Transfer" : "<red>Cannot Afford")
                        .lore(
                                "",
                                canAfford
                                        ? "<gray>Click to transfer " + chunkCount + " chunk(s)"
                                        : "<red>You need $" + String.format("%.0f", totalCost),
                                canAfford
                                        ? "<gray>to <aqua>" + targetName
                                        : "<red>to complete this transfer")
                        .build();

        if (canAfford) {
            setItem(11, new GuiItem(confirmItem, e -> performTransfer(totalCost)));
        } else {
            setItem(11, new GuiItem(confirmItem));
        }

        // Cancel button (slot 15)
        ItemStack cancelItem =
                new ItemBuilder(Material.RED_CONCRETE)
                        .name("<red>Cancel")
                        .lore("", "<gray>Go back without transferring")
                        .build();
        setItem(
                15,
                new GuiItem(
                        cancelItem,
                        e -> {
                            viewer.closeInventory();
                            // Safely return to previous GUI
                            if (sourceClaim != null && !chunksToTransfer.isEmpty()) {
                                if (chunksToTransfer.size() == 1
                                        && chunksToTransfer.get(0) != null) {
                                    new ChunkActionsGui(
                                                    plugin,
                                                    viewer,
                                                    sourceClaim,
                                                    chunksToTransfer.get(0))
                                            .open();
                                } else {
                                    new ManageChunksGui(plugin, viewer, sourceClaim).open();
                                }
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

    private void performTransfer(double totalCost) {
        // Re-validate source claim exists
        if (sourceClaim == null) {
            TextUtil.send(viewer, "<red>Source claim data is missing!");
            viewer.closeInventory();
            new MyProfilesGui(plugin, viewer).open();
            return;
        }

        // Verify source claim still exists in database
        Claim existingClaim = plugin.getClaimManager().getClaimById(sourceClaim.getId());
        if (existingClaim == null) {
            TextUtil.send(viewer, "<red>Source claim no longer exists!");
            viewer.closeInventory();
            new MyProfilesGui(plugin, viewer).open();
            return;
        }

        // Check ownership
        if (!sourceClaim.isOwner(viewer.getUniqueId())
                && !viewer.hasPermission("serverclaim.admin")) {
            TextUtil.send(viewer, "<red>You don't own this claim!");
            viewer.closeInventory();
            return;
        }

        // Validate chunks still exist
        if (chunksToTransfer == null || chunksToTransfer.isEmpty()) {
            TextUtil.send(viewer, "<red>No chunks to transfer!");
            viewer.closeInventory();
            return;
        }

        // Validate target player
        if (targetUuid == null) {
            TextUtil.send(viewer, "<red>Invalid target player!");
            viewer.closeInventory();
            return;
        }

        // Charge cost
        if (totalCost > 0 && plugin.getEconomy() != null) {
            if (!plugin.getEconomy().has(viewer, totalCost)) {
                TextUtil.send(viewer, "<red>You no longer have enough money!");
                viewer.closeInventory();
                return;
            }
            if (!plugin.getEconomy().withdraw(viewer, totalCost)) {
                TextUtil.send(viewer, "<red>Failed to process payment!");
                viewer.closeInventory();
                return;
            }
        }

        viewer.closeInventory();
        TextUtil.send(viewer, "<gray>Processing transfer...");

        // Perform transfer asynchronously
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                int transferredCount = 0;

                                // Get or create the recipient's claim in the same world
                                PlayerClaimData recipientData =
                                        plugin.getRepository().getPlayerData(targetUuid);
                                if (recipientData == null) {
                                    // Create new player data for recipient
                                    recipientData =
                                            new PlayerClaimData(
                                                    targetUuid,
                                                    targetName,
                                                    plugin.getClaimConfig().getStartingChunks());
                                    plugin.getRepository().savePlayerData(recipientData);
                                }

                                // Create a new claim for the recipient if they don't have one in
                                // this world
                                // or if their existing claim doesn't have space
                                ClaimedChunk firstChunk = chunksToTransfer.get(0);
                                if (firstChunk == null || firstChunk.getWorld() == null) {
                                    throw new IllegalStateException("Invalid chunk data");
                                }

                                String world = firstChunk.getWorld();
                                List<Claim> recipientClaims =
                                        plugin.getClaimManager().getPlayerClaims(targetUuid);
                                if (recipientClaims == null) {
                                    recipientClaims = new ArrayList<>();
                                }

                                Claim recipientClaim =
                                        recipientClaims.stream()
                                                .filter(c -> c != null && c.getWorld() != null)
                                                .filter(c -> c.getWorld().equals(world))
                                                .filter(
                                                        c ->
                                                                c.getRemainingChunks()
                                                                        >= chunksToTransfer.size())
                                                .findFirst()
                                                .orElse(null);

                                if (recipientClaim == null) {
                                    // Create new claim for recipient
                                    recipientClaim = new Claim(targetUuid, world);
                                    recipientClaim.setName("Transferred Claim");
                                    recipientClaim.setTotalChunks(
                                            Math.max(
                                                    plugin.getClaimConfig()
                                                            .getStartingChunksPerClaim(),
                                                    chunksToTransfer.size()));
                                    recipientClaim.setClaimOrder(recipientClaims.size() + 1);

                                    // saveClaim will insert and set the ID
                                    plugin.getRepository().saveClaim(recipientClaim);
                                }

                                // Transfer each chunk
                                for (ClaimedChunk chunk : chunksToTransfer) {
                                    // Verify chunk is still in source claim
                                    if (existingClaim.getChunks().stream()
                                            .anyMatch(
                                                    ch ->
                                                            ch.getChunkX() == chunk.getChunkX()
                                                                    && ch.getChunkZ()
                                                                            == chunk.getChunkZ()
                                                                    && ch.getWorld()
                                                                            .equals(
                                                                                    chunk
                                                                                            .getWorld()))) {

                                        // Record transfer for audit
                                        plugin.getRepository()
                                                .recordChunkOwnershipTransfer(
                                                        chunk,
                                                        existingClaim,
                                                        targetUuid,
                                                        recipientClaim.getId());

                                        // Transfer chunk to new claim
                                        if (plugin.getRepository()
                                                .transferChunkToNewClaim(
                                                        chunk, recipientClaim.getId())) {
                                            // Update in-memory models
                                            existingClaim.removeChunk(chunk);
                                            chunk.setClaimId(recipientClaim.getId());
                                            recipientClaim.addChunk(chunk);

                                            // Update cache
                                            plugin.getClaimManager()
                                                    .updateChunkClaimMapping(
                                                            chunk, recipientClaim.getId());

                                            transferredCount++;
                                        }
                                    }
                                }

                                // Update chunk counts
                                plugin.getClaimManager()
                                        .decrementUsedChunkCount(
                                                sourceClaim.getOwnerUuid(), transferredCount);

                                // Check if source claim is now empty
                                if (existingClaim.getChunks().isEmpty()) {
                                    plugin.getRepository().deleteClaim(existingClaim);
                                    plugin.getClaimManager().invalidateClaim(existingClaim.getId());
                                }

                                final int finalCount = transferredCount;
                                final String recipientClaimName = recipientClaim.getName();

                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    viewer.playSound(
                                                            viewer.getLocation(),
                                                            Sound.ENTITY_PLAYER_LEVELUP,
                                                            0.5f,
                                                            1f);
                                                    TextUtil.send(
                                                            viewer,
                                                            "<green>Successfully transferred "
                                                                    + finalCount
                                                                    + " chunk(s) to "
                                                                    + targetName
                                                                    + "!");

                                                    if (totalCost > 0) {
                                                        TextUtil.send(
                                                                viewer,
                                                                "<gray>(-$"
                                                                        + String.format(
                                                                                "%.0f", totalCost)
                                                                        + ")");
                                                    }

                                                    // Notify recipient if online
                                                    Player recipient =
                                                            plugin.getServer()
                                                                    .getPlayer(targetUuid);
                                                    if (recipient != null && recipient.isOnline()) {
                                                        recipient.playSound(
                                                                recipient.getLocation(),
                                                                Sound.ENTITY_PLAYER_LEVELUP,
                                                                0.5f,
                                                                1f);
                                                        TextUtil.send(
                                                                recipient,
                                                                "<green>"
                                                                        + viewer.getName()
                                                                        + " has transferred "
                                                                        + finalCount
                                                                        + " chunk(s) to you!");
                                                        TextUtil.send(
                                                                recipient,
                                                                "<gray>Claim: "
                                                                        + recipientClaimName);
                                                    }

                                                    // Return to claims list if source was deleted,
                                                    // otherwise chunk manager
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
                                        .severe("Error transferring chunks: " + e.getMessage());
                                e.printStackTrace();

                                // Refund on error
                                if (totalCost > 0 && plugin.getEconomy() != null) {
                                    plugin.getEconomy().deposit(viewer, totalCost);
                                }

                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    TextUtil.send(
                                                            viewer,
                                                            "<red>An error occurred while transferring chunks!");
                                                    if (totalCost > 0) {
                                                        TextUtil.send(
                                                                viewer,
                                                                "<gray>Your payment has been refunded.");
                                                    }
                                                });
                            }
                        });
    }
}
