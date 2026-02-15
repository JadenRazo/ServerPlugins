package net.serverplugins.claim.listeners;

import java.util.List;
import java.util.UUID;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
// import net.serverplugins.claim.gui.AdminClaimSearchGui; // Commented out - feature not implemented
import net.serverplugins.claim.gui.AddMemberGui;
import net.serverplugins.claim.gui.ChunkTransferConfirmGui;
import net.serverplugins.claim.gui.ClaimSettingsGui;
import net.serverplugins.claim.gui.GroupSettingsGui;
import net.serverplugins.claim.gui.WarpSettingsGui;
import net.serverplugins.claim.managers.ClaimManager;
import net.serverplugins.claim.managers.VisitationManager;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimProfile;
import net.serverplugins.claim.models.ClaimWarp;
import net.serverplugins.claim.models.ClaimedChunk;
import net.serverplugins.claim.models.CustomGroup;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatInputListener implements Listener {

    private final ServerClaim plugin;

    public ChatInputListener(ServerClaim plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().trim();

        // Check for pending trust input
        if (plugin.getClaimManager().hasPendingTrustInput(player.getUniqueId())) {
            event.setCancelled(true);

            if (message.equalsIgnoreCase("cancel")) {
                plugin.getClaimManager().getPendingTrustInput(player.getUniqueId()); // clear it
                TextUtil.send(player, "<yellow>Cancelled.");
                return;
            }

            Claim claim = plugin.getClaimManager().getPendingTrustInput(player.getUniqueId());
            if (claim == null) return;

            // Find player by name - use cached lookup to avoid blocking Mojang API calls
            Player target = Bukkit.getPlayer(message);
            UUID targetUuid;
            String targetName;

            if (target != null) {
                targetUuid = target.getUniqueId();
                targetName = target.getName();
            } else {
                // Try cached offline player to avoid blocking Mojang API lookup
                org.bukkit.OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(message);
                if (cached != null && cached.hasPlayedBefore()) {
                    targetUuid = cached.getUniqueId();
                    targetName = cached.getName() != null ? cached.getName() : message;
                } else {
                    TextUtil.send(player, "<red>Player not found or never joined: " + message);
                    return;
                }
            }

            // Open AddMemberGui for group selection
            final UUID finalTargetUuid = targetUuid;
            final String finalTargetName = targetName;
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                new AddMemberGui(
                                                plugin,
                                                player,
                                                claim,
                                                finalTargetUuid,
                                                finalTargetName)
                                        .open();
                            });
            return;
        }

        // Check for pending claim rename input
        if (plugin.getClaimManager().hasPendingClaimRename(player.getUniqueId())) {
            event.setCancelled(true);

            if (message.equalsIgnoreCase("cancel")) {
                plugin.getClaimManager().getPendingClaimRename(player.getUniqueId()); // clear it
                TextUtil.send(player, "<yellow>Cancelled.");
                return;
            }

            Claim claim = plugin.getClaimManager().getPendingClaimRename(player.getUniqueId());
            if (claim == null) return;

            // Validate name length
            if (message.length() > 64) {
                TextUtil.send(player, "<red>Claim name must be 64 characters or less!");
                return;
            }

            plugin.getClaimManager().renameClaim(claim, message);
            TextUtil.send(player, "<green>Claim renamed to: <white>" + message);

            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                new ClaimSettingsGui(plugin, player, claim).open();
                            });
            return;
        }

        // Check for pending profile rename input (legacy - kept for backwards compatibility)
        if (plugin.getProfileManager().hasPendingRenameInput(player.getUniqueId())) {
            event.setCancelled(true);

            if (message.equalsIgnoreCase("cancel")) {
                plugin.getProfileManager().getPendingRenameInput(player.getUniqueId()); // clear it
                TextUtil.send(player, "<yellow>Cancelled.");
                return;
            }

            ClaimProfile profile =
                    plugin.getProfileManager().getPendingRenameInput(player.getUniqueId());
            if (profile == null) return;

            // Validate name length
            if (message.length() > 32) {
                TextUtil.send(player, "<red>Profile name must be 32 characters or less!");
                return;
            }

            plugin.getProfileManager().renameProfile(profile, message);
            TextUtil.send(player, "<green>Profile renamed to: <white>" + message);

            // Get the claim for the profile to reopen the menu
            Claim claim = plugin.getClaimManager().getClaimAt(player.getLocation().getChunk());
            if (claim != null) {
                Bukkit.getScheduler()
                        .runTask(
                                plugin,
                                () -> {
                                    new ClaimSettingsGui(plugin, player, claim).open();
                                });
            }
        }

        // Check for pending warp input
        if (plugin.getVisitationManager().hasPendingInput(player.getUniqueId())) {
            event.setCancelled(true);

            if (message.equalsIgnoreCase("cancel")) {
                plugin.getVisitationManager().getPendingInput(player.getUniqueId());
                TextUtil.send(player, "<yellow>Cancelled.");
                return;
            }

            VisitationManager.PendingInput pendingInput =
                    plugin.getVisitationManager().getPendingInput(player.getUniqueId());
            if (pendingInput == null) return;

            ClaimWarp warp = pendingInput.getWarp();
            Claim claim =
                    plugin.getClaimManager().getPlayerClaims(player.getUniqueId()).stream()
                            .filter(c -> c.getId() == warp.getClaimId())
                            .findFirst()
                            .orElse(null);

            if (claim == null) {
                TextUtil.send(player, "<red>Could not find claim!");
                return;
            }

            switch (pendingInput.getType()) {
                case DESCRIPTION -> {
                    if (message.length() > 256) {
                        TextUtil.send(player, "<red>Description must be 256 characters or less!");
                        return;
                    }
                    plugin.getVisitationManager().setWarpDescription(warp, message);
                    TextUtil.send(player, "<green>Warp description updated!");

                    Bukkit.getScheduler()
                            .runTask(
                                    plugin,
                                    () -> {
                                        new WarpSettingsGui(plugin, player, claim).open();
                                    });
                }
                case ALLOWLIST -> {
                    Player target = Bukkit.getPlayer(message);
                    UUID targetUuid;
                    String targetName;

                    if (target != null) {
                        targetUuid = target.getUniqueId();
                        targetName = target.getName();
                    } else {
                        // Use cached lookup to avoid blocking Mojang API
                        org.bukkit.OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(message);
                        if (cached == null || !cached.hasPlayedBefore()) {
                            TextUtil.send(
                                    player, "<red>Player not found or never joined: " + message);
                            return;
                        }
                        targetUuid = cached.getUniqueId();
                        targetName = cached.getName() != null ? cached.getName() : message;
                    }

                    plugin.getVisitationManager()
                            .addToAllowlist(
                                    warp,
                                    targetUuid,
                                    () -> {
                                        TextUtil.send(
                                                player,
                                                "<green>Added <white>"
                                                        + targetName
                                                        + " <green>to allowlist!");
                                        new WarpSettingsGui(plugin, player, claim).open();
                                    });
                }
                case BLOCKLIST -> {
                    Player target = Bukkit.getPlayer(message);
                    UUID targetUuid;
                    String targetName;

                    if (target != null) {
                        targetUuid = target.getUniqueId();
                        targetName = target.getName();
                    } else {
                        // Use cached lookup to avoid blocking Mojang API
                        org.bukkit.OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(message);
                        if (cached == null || !cached.hasPlayedBefore()) {
                            TextUtil.send(
                                    player, "<red>Player not found or never joined: " + message);
                            return;
                        }
                        targetUuid = cached.getUniqueId();
                        targetName = cached.getName() != null ? cached.getName() : message;
                    }

                    plugin.getVisitationManager()
                            .addToBlocklist(
                                    warp,
                                    targetUuid,
                                    () -> {
                                        TextUtil.send(
                                                player,
                                                "<green>Added <white>"
                                                        + targetName
                                                        + " <green>to blocklist!");
                                        new WarpSettingsGui(plugin, player, claim).open();
                                    });
                }
                case COST -> {
                    try {
                        double cost = Double.parseDouble(message);
                        if (cost < 0) {
                            TextUtil.send(player, "<red>Cost cannot be negative!");
                            return;
                        }
                        if (cost > 10000) {
                            TextUtil.send(player, "<red>Cost cannot exceed 10,000 coins!");
                            return;
                        }
                        plugin.getVisitationManager().setWarpCost(warp, cost);
                        TextUtil.send(
                                player,
                                "<green>Visit cost set to: <white>"
                                        + (cost == 0 ? "Free" : (int) cost + " coins"));

                        Bukkit.getScheduler()
                                .runTask(
                                        plugin,
                                        () -> {
                                            new WarpSettingsGui(plugin, player, claim).open();
                                        });
                    } catch (NumberFormatException e) {
                        TextUtil.send(player, "<red>Invalid number! Please enter a valid cost.");
                    }
                }
            }
            return;
        }

        // Check for pending group rename input
        if (plugin.getClaimManager().hasPendingGroupRename(player.getUniqueId())) {
            event.setCancelled(true);

            if (message.equalsIgnoreCase("cancel")) {
                plugin.getClaimManager().getPendingGroupRename(player.getUniqueId()); // clear it
                TextUtil.send(player, "<yellow>Cancelled.");
                return;
            }

            ClaimManager.GroupRenameContext context =
                    plugin.getClaimManager().getPendingGroupRename(player.getUniqueId());
            if (context == null) return;

            Claim claim = context.claim();
            CustomGroup group = context.group();

            // Validate name length
            if (message.length() > 32) {
                TextUtil.send(player, "<red>Group name must be 32 characters or less!");
                return;
            }

            // Check for reserved names
            String lowerMessage = message.toLowerCase();
            if (lowerMessage.equals("owner") && !group.getName().equalsIgnoreCase("owner")) {
                TextUtil.send(player, "<red>Cannot use 'Owner' as a group name!");
                return;
            }

            // Update the group name asynchronously
            group.setName(message);
            plugin.getServer()
                    .getScheduler()
                    .runTaskAsynchronously(
                            plugin,
                            () -> {
                                plugin.getGroupRepository().saveGroup(group);
                            });
            TextUtil.send(player, "<green>Group renamed to: <white>" + message);

            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                new GroupSettingsGui(plugin, player, claim, group).open();
                            });
            return;
        }

        // Check for pending chunk transfer input
        if (plugin.getClaimManager().hasPendingChunkTransfer(player.getUniqueId())) {
            event.setCancelled(true);

            if (message.equalsIgnoreCase("cancel")) {
                plugin.getClaimManager().cancelPendingChunkTransfer(player.getUniqueId());
                TextUtil.send(player, "<yellow>Cancelled chunk transfer.");
                return;
            }

            ClaimManager.ChunkTransferRequest request =
                    plugin.getClaimManager().getPendingChunkTransfer(player.getUniqueId());
            if (request == null) return;

            // Find player by name - use cached lookup to avoid blocking Mojang API calls
            Player target = Bukkit.getPlayer(message);
            UUID targetUuid;
            String targetName;

            if (target != null) {
                targetUuid = target.getUniqueId();
                targetName = target.getName();
            } else {
                // Check if offline transfers are allowed
                if (!plugin.getClaimConfig().isChunkTransferAllowOffline()) {
                    TextUtil.send(
                            player, "<red>The player must be online to receive chunk transfers!");
                    return;
                }

                // Use cached lookup to avoid blocking Mojang API
                org.bukkit.OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(message);
                if (cached == null || !cached.hasPlayedBefore()) {
                    TextUtil.send(player, "<red>Player not found or never joined: " + message);
                    return;
                }
                targetUuid = cached.getUniqueId();
                targetName = cached.getName() != null ? cached.getName() : message;
            }

            // Check if target is the same as the owner
            if (targetUuid.equals(request.getClaim().getOwnerUuid())) {
                TextUtil.send(player, "<red>You cannot transfer chunks to yourself!");
                return;
            }

            // Get chunks to transfer
            List<ClaimedChunk> chunksToTransfer =
                    request.isBulkTransfer()
                            ? plugin.getClaimManager()
                                    .getConnectedChunks(request.getClaim(), request.getChunk())
                            : List.of(request.getChunk());

            // Open confirmation GUI
            final UUID finalTargetUuid = targetUuid;
            final String finalTargetName = targetName;
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                new ChunkTransferConfirmGui(
                                                plugin,
                                                player,
                                                request.getClaim(),
                                                chunksToTransfer,
                                                finalTargetUuid,
                                                finalTargetName)
                                        .open();
                            });
            return;
        }

        // Admin search feature disabled - AdminClaimSearchGui not implemented
        /*
        // Check for pending admin search input
        if (plugin.getClaimManager().hasPendingAdminSearch(player.getUniqueId())) {
            event.setCancelled(true);

            if (message.equalsIgnoreCase("cancel")) {
                plugin.getClaimManager().clearAdminSearch(player.getUniqueId());
                TextUtil.send(player, "<yellow>Admin search cancelled.");
                return;
            }

            plugin.getClaimManager().clearAdminSearch(player.getUniqueId());

            // Search for player by username
            PlayerClaimData targetData = plugin.getRepository().getPlayerDataByExactUsername(message);

            // Fallback to partial match if exact not found
            if (targetData == null) {
                targetData = plugin.getRepository().getPlayerDataByUsername(message);
            }

            if (targetData == null) {
                TextUtil.send(player, "<red>No player found with username: <white>" + message);
                TextUtil.send(player, "<gray>Try a different name or check spelling.");
                return;
            }

            // Get all claims for this player
            List<Claim> claims = plugin.getClaimManager().getPlayerClaims(targetData.getUuid());

            if (claims.isEmpty()) {
                TextUtil.send(player, "<yellow>Player <white>" + targetData.getUsername() + " <yellow>has no claims.");
                return;
            }

            // Open admin claims GUI
            Bukkit.getScheduler().runTask(plugin, () -> {
                new AdminClaimSearchGui(plugin, player, targetData, claims).open();
            });

            return;
        }
        */
    }
}
