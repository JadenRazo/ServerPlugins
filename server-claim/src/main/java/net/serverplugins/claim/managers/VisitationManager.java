package net.serverplugins.claim.managers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.economy.EconomyProvider;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimWarp;
import net.serverplugins.claim.models.WarpVisibility;
import net.serverplugins.claim.repository.ClaimRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class VisitationManager {

    private final ServerClaim plugin;
    private final ClaimRepository repository;

    private final Map<Integer, ClaimWarp> warpCache = new ConcurrentHashMap<>();
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();

    public VisitationManager(ServerClaim plugin, ClaimRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    /** Gets the warp point for a claim, caching for performance. */
    public ClaimWarp getClaimWarp(int claimId) {
        return warpCache.computeIfAbsent(claimId, id -> repository.getClaimWarp(id));
    }

    /** Clears the warp cache for a specific claim. */
    public void invalidateWarpCache(int claimId) {
        warpCache.remove(claimId);
    }

    /** Sets the warp point for a claim. */
    public void setClaimWarp(Claim claim, Location location) {
        ClaimWarp warp = new ClaimWarp(claim.getId(), location);
        repository.saveClaimWarp(warp);
        warpCache.put(claim.getId(), warp);
    }

    /** Updates an existing warp's location. */
    public void updateWarpLocation(ClaimWarp warp, Location location) {
        warp.setLocation(location);
        repository.saveClaimWarp(warp);
        warpCache.put(warp.getClaimId(), warp);
    }

    /** Deletes a claim's warp point. */
    public void deleteClaimWarp(int claimId) {
        repository.deleteClaimWarp(claimId);
        warpCache.remove(claimId);
    }

    /** Sets the visibility of a claim's warp. */
    public void setWarpVisibility(ClaimWarp warp, WarpVisibility visibility) {
        warp.setVisibility(visibility);
        repository.saveClaimWarp(warp);
        warpCache.put(warp.getClaimId(), warp);
    }

    /** Sets the visit cost for a claim's warp. */
    public void setWarpCost(ClaimWarp warp, double cost) {
        warp.setVisitCost(cost);
        repository.saveClaimWarp(warp);
        warpCache.put(warp.getClaimId(), warp);
    }

    /** Sets the description for a claim's warp. */
    public void setWarpDescription(ClaimWarp warp, String description) {
        warp.setDescription(description);
        repository.saveClaimWarp(warp);
        warpCache.put(warp.getClaimId(), warp);
    }

    /** Adds a player to the warp allowlist. */
    public void addToAllowlist(ClaimWarp warp, UUID playerUuid, Runnable callback) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            warp.addToAllowlist(playerUuid);
                            repository.saveClaimWarp(warp);

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                warpCache.put(warp.getClaimId(), warp);
                                                if (callback != null) {
                                                    callback.run();
                                                }
                                            });
                        });
    }

    /** Removes a player from the warp allowlist. */
    public void removeFromAllowlist(ClaimWarp warp, UUID playerUuid, Runnable callback) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            warp.removeFromAllowlist(playerUuid);
                            repository.saveClaimWarp(warp);

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                warpCache.put(warp.getClaimId(), warp);
                                                if (callback != null) {
                                                    callback.run();
                                                }
                                            });
                        });
    }

    /** Adds a player to the warp blocklist. */
    public void addToBlocklist(ClaimWarp warp, UUID playerUuid, Runnable callback) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            warp.addToBlocklist(playerUuid);
                            repository.saveClaimWarp(warp);

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                warpCache.put(warp.getClaimId(), warp);
                                                if (callback != null) {
                                                    callback.run();
                                                }
                                            });
                        });
    }

    /** Removes a player from the warp blocklist. */
    public void removeFromBlocklist(ClaimWarp warp, UUID playerUuid, Runnable callback) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            warp.removeFromBlocklist(playerUuid);
                            repository.saveClaimWarp(warp);

                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                warpCache.put(warp.getClaimId(), warp);
                                                if (callback != null) {
                                                    callback.run();
                                                }
                                            });
                        });
    }

    /** Gets all claims that the visitor can access. */
    public CompletableFuture<List<Claim>> getVisitableClaims(UUID visitorUuid) {
        return CompletableFuture.supplyAsync(() -> repository.getVisitableClaims(visitorUuid));
    }

    /** Teleports a player to a claim warp with permission and payment handling. */
    public void visitClaim(Player visitor, Claim claim) {
        ClaimWarp warp = getClaimWarp(claim.getId());

        if (warp == null) {
            TextUtil.send(visitor, "<red>This claim does not have a warp point set up.");
            return;
        }

        // Check permission to visit
        if (!warp.canVisit(
                visitor.getUniqueId(), claim.getOwnerUuid(), claim.getTrustedPlayers())) {
            TextUtil.send(visitor, "<red>You do not have permission to visit this claim.");
            return;
        }

        // Get world
        World world = plugin.getServer().getWorld(claim.getWorld());
        if (world == null) {
            TextUtil.send(visitor, "<red>The world for this claim is not loaded!");
            return;
        }

        // Check payment if required
        double cost = warp.getVisitCost();
        if (cost > 0 && !visitor.getUniqueId().equals(claim.getOwnerUuid())) {
            EconomyProvider economy = ServerAPI.getInstance().getEconomyProvider();
            if (economy == null || !economy.isAvailable()) {
                TextUtil.send(visitor, "<red>Economy system is not available!");
                return;
            }

            if (!economy.has(visitor, cost)) {
                TextUtil.send(
                        visitor,
                        "<red>You need <yellow>" + (int) cost + " coins<red> to visit this claim!");
                TextUtil.send(
                        visitor,
                        "<gray>Your balance: <white>"
                                + economy.format(economy.getBalance(visitor)));
                return;
            }

            // Withdraw payment
            if (!economy.withdraw(visitor, cost)) {
                TextUtil.send(visitor, "<red>Failed to process payment!");
                return;
            }

            // Pay the owner
            Player owner = Bukkit.getPlayer(claim.getOwnerUuid());
            if (owner != null && owner.isOnline()) {
                economy.deposit(owner, cost);
                TextUtil.send(
                        owner,
                        "<green>+"
                                + (int) cost
                                + " coins <gray>from <white>"
                                + visitor.getName()
                                + " <gray>visiting your claim!");
            }
        }

        // Teleport to warp location
        Location warpLocation = warp.toLocation(world);

        // Verify location is safe
        if (!isSafeLocation(warpLocation)) {
            Location safeLoc =
                    findSafeLocation(world, (int) warpLocation.getX(), (int) warpLocation.getZ());
            if (safeLoc != null) {
                warpLocation = safeLoc;
            } else {
                TextUtil.send(
                        visitor,
                        "<red>The warp location is not safe! Please contact the claim owner.");
                return;
            }
        }

        visitor.teleport(warpLocation);

        if (cost > 0 && !visitor.getUniqueId().equals(claim.getOwnerUuid())) {
            TextUtil.send(
                    visitor,
                    "<green>Teleported to <white>"
                            + claim.getName()
                            + " <gray>(-"
                            + (int) cost
                            + " coins)");
        } else {
            TextUtil.send(visitor, "<green>Teleported to <white>" + claim.getName());
        }

        // Show welcome message if present
        if (claim.getWelcomeMessage() != null && !claim.getWelcomeMessage().isEmpty()) {
            TextUtil.send(visitor, "<yellow>" + claim.getWelcomeMessage());
        }
    }

    /** Finds a safe teleport location near the given coordinates. */
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

    /** Checks if a location is safe for teleportation. */
    private boolean isSafeLocation(Location location) {
        Block ground = location.getBlock().getRelative(0, -1, 0);
        Block feet = location.getBlock();
        Block head = location.getBlock().getRelative(0, 1, 0);

        if (!feet.isPassable() || !head.isPassable()) {
            return false;
        }

        Material groundMaterial = ground.getType();
        return ground.isSolid()
                && groundMaterial != Material.LAVA
                && groundMaterial != Material.CACTUS
                && groundMaterial != Material.MAGMA_BLOCK
                && groundMaterial != Material.CAMPFIRE
                && groundMaterial != Material.SOUL_CAMPFIRE
                && groundMaterial != Material.FIRE;
    }

    // Pending input handling for chat-based input

    public void awaitDescriptionInput(Player player, ClaimWarp warp) {
        pendingInputs.put(
                player.getUniqueId(), new PendingInput(PendingInputType.DESCRIPTION, warp));
        TextUtil.send(player, "<yellow>Type the warp description in chat:");
        TextUtil.send(player, "<gray>(Type 'cancel' to cancel)");
    }

    public void awaitAllowlistInput(Player player, ClaimWarp warp) {
        pendingInputs.put(player.getUniqueId(), new PendingInput(PendingInputType.ALLOWLIST, warp));
        TextUtil.send(player, "<yellow>Type a player name to add to the allowlist:");
        TextUtil.send(player, "<gray>(Type 'cancel' to cancel)");
    }

    public void awaitBlocklistInput(Player player, ClaimWarp warp) {
        pendingInputs.put(player.getUniqueId(), new PendingInput(PendingInputType.BLOCKLIST, warp));
        TextUtil.send(player, "<yellow>Type a player name to add to the blocklist:");
        TextUtil.send(player, "<gray>(Type 'cancel' to cancel)");
    }

    public void awaitCostInput(Player player, ClaimWarp warp) {
        pendingInputs.put(player.getUniqueId(), new PendingInput(PendingInputType.COST, warp));
        TextUtil.send(player, "<yellow>Type the visit cost in coins (or 0 for free):");
        TextUtil.send(player, "<gray>(Type 'cancel' to cancel)");
    }

    public PendingInput getPendingInput(UUID playerUuid) {
        return pendingInputs.remove(playerUuid);
    }

    public boolean hasPendingInput(UUID playerUuid) {
        return pendingInputs.containsKey(playerUuid);
    }

    /** Clears all pending inputs for a player. Call on player quit to prevent memory leaks. */
    public void clearPendingInputs(UUID playerUuid) {
        pendingInputs.remove(playerUuid);
    }

    public static class PendingInput {
        private final PendingInputType type;
        private final ClaimWarp warp;

        public PendingInput(PendingInputType type, ClaimWarp warp) {
            this.type = type;
            this.warp = warp;
        }

        public PendingInputType getType() {
            return type;
        }

        public ClaimWarp getWarp() {
            return warp;
        }
    }

    public enum PendingInputType {
        DESCRIPTION,
        ALLOWLIST,
        BLOCKLIST,
        COST
    }
}
