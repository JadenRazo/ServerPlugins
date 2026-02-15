package net.serverplugins.claim.utils;

import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.economy.EconomyProvider;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimedChunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** Utility class for warping players to their claims. */
public class ClaimWarpUtil {

    private static final double WARP_COST = 25.0;

    /**
     * Warps a player to their claim with a cost of 25 coins. Finds a safe location in the claim and
     * teleports the player.
     *
     * @param plugin The ServerClaim plugin instance
     * @param player The player to warp
     * @param claim The claim to warp to
     * @return true if warp was successful, false otherwise
     */
    public static boolean warpToClaim(ServerClaim plugin, Player player, Claim claim) {
        if (claim.getChunks().isEmpty()) {
            TextUtil.send(player, "<red>This claim has no chunks to warp to!");
            return false;
        }

        // Check economy
        EconomyProvider economy = ServerAPI.getInstance().getEconomyProvider();
        if (economy == null || !economy.isAvailable()) {
            TextUtil.send(player, "<red>Economy system is not available!");
            return false;
        }

        // Check balance
        if (!economy.has(player, WARP_COST)) {
            TextUtil.send(
                    player,
                    "<red>You need <yellow>"
                            + (int) WARP_COST
                            + " coins<red> to warp to your claim!");
            TextUtil.send(
                    player,
                    "<gray>Your balance: <white>" + economy.format(economy.getBalance(player)));
            return false;
        }

        // Get warp location (center of first chunk)
        ClaimedChunk chunk = claim.getChunks().get(0);
        World world = plugin.getServer().getWorld(claim.getWorld());

        if (world == null) {
            TextUtil.send(player, "<red>The world for this claim is not loaded!");
            return false;
        }

        // Calculate center of chunk and find safe location
        int x = (chunk.getChunkX() * 16) + 8;
        int z = (chunk.getChunkZ() * 16) + 8;

        Location warpLoc = findSafeLocation(world, x, z);

        if (warpLoc == null) {
            TextUtil.send(player, "<red>Could not find a safe location to warp to!");
            TextUtil.send(player, "<gray>The area may be covered in lava or other hazards.");
            return false;
        }

        // Withdraw money
        if (economy.withdraw(player, WARP_COST)) {
            player.teleport(warpLoc);
            TextUtil.send(
                    player, "<green>Warped to your claim! <gray>(-" + (int) WARP_COST + " coins)");
            return true;
        } else {
            TextUtil.send(player, "<red>Failed to process payment!");
            return false;
        }
    }

    /**
     * Finds a safe teleport location near the given coordinates. Prioritizes: flat solid ground >
     * water > any non-lava surface
     *
     * @return Safe location or null if none found
     */
    private static Location findSafeLocation(World world, int centerX, int centerZ) {
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

    /**
     * Gets the warp cost in coins.
     *
     * @return The warp cost (25 coins)
     */
    public static double getWarpCost() {
        return WARP_COST;
    }
}
