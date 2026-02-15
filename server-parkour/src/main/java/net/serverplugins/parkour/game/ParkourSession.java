package net.serverplugins.parkour.game;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.api.messages.PluginMessenger;
import net.serverplugins.parkour.ParkourConfig;
import net.serverplugins.parkour.ServerParkour;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class ParkourSession {

    private final ServerParkour plugin;
    private final Player player;
    private final ParkourConfig config;
    private final int laneOffset;

    private int score;
    private boolean active;
    private boolean hasDoubleJump;
    private long startTime;

    // Block history - tracks ALL blocks we've placed in order
    private final List<Block> history = new ArrayList<>();

    // Last index in history where player was standing
    private int lastPositionIndex = 0;

    // How many blocks ahead to keep generated
    private static final int BLOCK_LEAD = 1;

    // Original block data for restoration
    private final Map<String, BlockData> originalBlocks = new HashMap<>();

    // Heading direction
    private Vector heading;

    // Block type of the most recently generated block
    private BlockType lastGeneratedType = BlockType.NORMAL;

    // Track block types for each block in history
    private final List<BlockType> blockTypes = new ArrayList<>();

    // Flag to generate next block backward
    private boolean generateBackward = false;

    // TNT block timers - maps block location key to remaining ticks
    private final Map<String, Integer> tntTimers = new HashMap<>();
    private static final int TNT_FUSE_TICKS = 40; // 2 seconds

    public ParkourSession(ServerParkour plugin, Player player, int laneOffset) {
        this.plugin = plugin;
        this.player = player;
        this.config = plugin.getParkourConfig();
        this.laneOffset = laneOffset;
        this.score = 0;
        this.active = false;
        this.hasDoubleJump = false;
        this.heading = player.getLocation().getDirection().setY(0).normalize();
    }

    public void start() {
        active = true;
        startTime = System.currentTimeMillis();
        history.clear();
        blockTypes.clear();
        tntTimers.clear();
        lastPositionIndex = 0;
        generateBackward = false;

        // Use NPC teleport location as base, offset by player's lane to isolate players
        Location baseLocation = config.getNpcTeleportLocation();
        Location startBlock =
                new Location(
                        baseLocation.getWorld(),
                        baseLocation.getBlockX() + laneOffset,
                        240,
                        baseLocation.getBlockZ());

        // Place spawn block and add to history
        placeBlock(startBlock, Material.QUARTZ_BLOCK);
        history.add(startBlock.getBlock());
        blockTypes.add(BlockType.NORMAL); // Spawn block is normal

        // Teleport player centered on spawn block
        Location playerSpawn = startBlock.clone().add(0.5, 1.0, 0.5);
        playerSpawn.setYaw(player.getLocation().getYaw());
        playerSpawn.setPitch(0);
        player.teleport(playerSpawn);

        config.getMessenger().send(player, "game-start");

        // Generate ONE target block - now we have exactly 2 blocks in history
        generate();
    }

    /**
     * Called every tick by the listener. This is the main game loop. Uses index-based tracking like
     * InfiniteParkour plugin.
     */
    public void tick() {
        if (!active || history.isEmpty()) return;

        // Update action bar with current score
        sendActionBar();

        // Process TNT timers
        processTntTimers();

        // Get block below player
        Block blockBelow = player.getLocation().subtract(0, 1, 0).getBlock();

        // Check if it's AIR (player jumping/falling)
        if (blockBelow.getType() == Material.AIR) {
            return;
        }

        // Find this block in our history
        int currentIndex = history.indexOf(blockBelow);

        // Not on a parkour block
        if (currentIndex == -1) {
            return;
        }

        // Calculate how far player moved forward
        int deltaFromLast = currentIndex - lastPositionIndex;

        // Player hasn't moved forward (or moved backward)
        if (deltaFromLast <= 0) {
            return;
        }

        // SUCCESS - Player moved forward to a new block!

        // Get the type of block we just landed on
        BlockType landedBlockType = BlockType.NORMAL;
        if (currentIndex < blockTypes.size()) {
            landedBlockType = blockTypes.get(currentIndex);
        }

        // Check if we landed on a backward block - next block should spawn behind
        if (landedBlockType == BlockType.BACKWARD) {
            generateBackward = true;
        }

        // Remove ALL blocks behind player and clean up history list
        for (int i = currentIndex - 1; i >= 0; i--) {
            Block oldBlock = history.get(i);
            if (oldBlock.getType() != Material.AIR) {
                restoreBlock(oldBlock.getLocation());
            }
        }

        // Clean up history - remove old entries to prevent list from growing unbounded
        if (currentIndex > 0) {
            history.subList(0, currentIndex).clear();
            blockTypes.subList(0, currentIndex).clear();
            // After cleanup, player is now at index 0
            lastPositionIndex = 0;
        } else {
            lastPositionIndex = currentIndex;
        }

        // Score for each block advanced (batch XP award to reduce async tasks)
        score += deltaFromLast;
        awardXp(deltaFromLast);

        // Handle special block effect for the block we just landed on
        handleBlockEffect(landedBlockType);

        // Generate new blocks if needed to maintain lead (after handling effects)
        // Use lastPositionIndex since it's updated after cleanup (player is at index 0 after
        // cleanup)
        int blocksAhead = history.size() - 1 - lastPositionIndex;
        if (blocksAhead < BLOCK_LEAD) {
            generate();
        }
    }

    /**
     * Generates exactly ONE block ahead (or behind if generateBackward) of the last block in
     * history.
     */
    private void generate() {
        if (history.isEmpty()) return;

        Block latest = history.get(history.size() - 1);
        Location latestLoc = latest.getLocation();

        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Determine direction - backward or forward
        Vector direction = heading.clone();
        boolean isBackward = generateBackward;
        if (isBackward) {
            // Reverse direction for backward block
            direction = heading.clone().multiply(-1);
        }

        // Try multiple times to find a valid location
        Location loc = null;
        int maxAttempts = 8;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            // Height change - for backward, prefer same level or down for easier jumps
            int heightRoll = random.nextInt(100);
            int heightChange;
            if (isBackward) {
                // Backward blocks: mostly same level, sometimes down, rarely up
                if (heightRoll < 20) heightChange = -1;
                else if (heightRoll < 90) heightChange = 0;
                else heightChange = 1;
            } else {
                if (heightRoll < 15) heightChange = -1;
                else if (heightRoll < 75) heightChange = 0;
                else heightChange = 1;
            }

            // Distance based on height - backward blocks need MORE distance to not overlap
            int distance;
            if (isBackward) {
                // Backward: minimum 3 blocks away to ensure no overlap
                if (heightChange == 1) {
                    distance = random.nextInt(3, 4); // 3 for going up
                } else if (heightChange == 0) {
                    distance = random.nextInt(3, 5); // 3-4 for same level
                } else {
                    distance = random.nextInt(3, 5); // 3-4 for going down
                }
            } else {
                if (heightChange == 1) {
                    distance = random.nextInt(1, 3); // 1-2 for going up
                } else if (heightChange == 0) {
                    distance = random.nextInt(2, 4); // 2-3 for same level
                } else {
                    distance = random.nextInt(2, 5); // 2-4 for going down
                }
            }

            // Slight sideways offset - vary more on retries
            int sideways = random.nextInt(3 + attempt) - (1 + attempt / 2);
            Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX());

            double nextX =
                    latestLoc.getX()
                            + (direction.getX() * distance)
                            + (perpendicular.getX() * sideways);
            double nextZ =
                    latestLoc.getZ()
                            + (direction.getZ() * distance)
                            + (perpendicular.getZ() * sideways);
            double nextY = latestLoc.getY() + heightChange;

            Location candidate =
                    new Location(latestLoc.getWorld(), Math.round(nextX), nextY, Math.round(nextZ));

            // Check if the location is valid (air or replaceable) AND not adjacent to current block
            if (isValidPlacement(candidate, latestLoc)) {
                loc = candidate;
                break;
            }
        }

        // If all attempts failed, force place at a safe location
        if (loc == null) {
            // Fallback: place further ahead at same height
            int fallbackDist = isBackward ? 4 : 3;
            double nextX = latestLoc.getX() + (direction.getX() * fallbackDist);
            double nextZ = latestLoc.getZ() + (direction.getZ() * fallbackDist);
            loc =
                    new Location(
                            latestLoc.getWorld(),
                            Math.round(nextX),
                            latestLoc.getY(),
                            Math.round(nextZ));
        }

        // Select block type - don't generate backward blocks when already going backward
        if (isBackward) {
            // When generating the "go back" block, make it a normal block
            lastGeneratedType = BlockType.NORMAL;
            // Reset the flag - after landing on this block, we go forward again
            generateBackward = false;
        } else {
            lastGeneratedType = selectBlockType(random);
        }

        Material material = selectMaterial(lastGeneratedType, random);

        placeBlock(loc, material);
        history.add(loc.getBlock());
        blockTypes.add(lastGeneratedType);

        // Adjust heading slightly (only when going forward)
        if (!isBackward) {
            adjustHeading(random);
        }
    }

    /**
     * Check if a location is valid for placing a parkour block. Must be air/replaceable AND not
     * directly adjacent to the source block.
     */
    private boolean isValidPlacement(Location loc, Location sourceBlock) {
        Block block = loc.getBlock();
        Material type = block.getType();

        // Must be air or already one of our parkour blocks
        boolean isAir =
                (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR);
        boolean isOurBlock = originalBlocks.containsKey(locKey(loc));

        if (!isAir && !isOurBlock) {
            return false;
        }

        // Must not be directly adjacent to source block (would cause player to stand on both)
        double dx = Math.abs(loc.getBlockX() - sourceBlock.getBlockX());
        double dy = Math.abs(loc.getBlockY() - sourceBlock.getBlockY());
        double dz = Math.abs(loc.getBlockZ() - sourceBlock.getBlockZ());

        // If within 1 block in all dimensions, it's adjacent - reject
        if (dx <= 1 && dy <= 1 && dz <= 1) {
            return false;
        }

        return true;
    }

    private void adjustHeading(ThreadLocalRandom random) {
        double rotation = (random.nextDouble() - 0.5) * 0.4;
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        double newX = heading.getX() * cos - heading.getZ() * sin;
        double newZ = heading.getX() * sin + heading.getZ() * cos;
        heading = new Vector(newX, 0, newZ).normalize();
    }

    private BlockType selectBlockType(ThreadLocalRandom random) {
        int total =
                config.getBackwardBlockWeight()
                        + config.getSpeedBlockWeight()
                        + config.getDoubleJumpBlockWeight()
                        + config.getSlimeBlockWeight()
                        + config.getTntBlockWeight();

        for (ParkourConfig.WeightedBlock wb : config.getNormalBlocks()) {
            total += wb.getWeight();
        }

        int roll = random.nextInt(total);
        int cumulative = 0;

        cumulative += config.getBackwardBlockWeight();
        if (roll < cumulative) return BlockType.BACKWARD;

        cumulative += config.getSpeedBlockWeight();
        if (roll < cumulative) return BlockType.SPEED;

        cumulative += config.getDoubleJumpBlockWeight();
        if (roll < cumulative) return BlockType.DOUBLE_JUMP;

        cumulative += config.getSlimeBlockWeight();
        if (roll < cumulative) return BlockType.SLIME;

        cumulative += config.getTntBlockWeight();
        if (roll < cumulative) return BlockType.TNT;

        return BlockType.NORMAL;
    }

    private Material selectMaterial(BlockType type, ThreadLocalRandom random) {
        return switch (type) {
            case BACKWARD -> config.getBackwardBlockMaterial();
            case SPEED -> config.getSpeedBlockMaterial();
            case DOUBLE_JUMP -> config.getDoubleJumpBlockMaterial();
            case SLIME -> Material.SLIME_BLOCK;
            case TNT -> Material.TNT;
            default -> selectNormalMaterial(random);
        };
    }

    private Material selectNormalMaterial(ThreadLocalRandom random) {
        List<ParkourConfig.WeightedBlock> blocks = config.getNormalBlocks();
        int totalWeight = blocks.stream().mapToInt(ParkourConfig.WeightedBlock::getWeight).sum();
        int roll = random.nextInt(totalWeight);

        int cumulative = 0;
        for (ParkourConfig.WeightedBlock wb : blocks) {
            cumulative += wb.getWeight();
            if (roll < cumulative) {
                return wb.getMaterial();
            }
        }
        return Material.STONE;
    }

    private String locKey(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private void placeBlock(Location location, Material material) {
        Block block = location.getBlock();
        String key = locKey(location);

        if (!originalBlocks.containsKey(key)) {
            originalBlocks.put(key, block.getBlockData().clone());
        }

        block.setType(material);
    }

    private void restoreBlock(Location location) {
        if (location == null) return;

        Block block = location.getBlock();
        String key = locKey(location);

        BlockData original = originalBlocks.remove(key);
        if (original != null) {
            block.setBlockData(original);
        } else {
            block.setType(Material.AIR);
        }
    }

    private void handleBlockEffect(BlockType type) {
        PluginMessenger messenger = config.getMessenger();
        switch (type) {
            case SPEED -> {
                messenger.send(player, "speed-block");
                player.addPotionEffect(
                        new PotionEffect(PotionEffectType.SPEED, 100, 1, false, false));
            }
            case DOUBLE_JUMP -> {
                messenger.send(player, "double-jump-block");
                hasDoubleJump = true;
                player.setAllowFlight(true);
            }
            case SLIME -> {
                messenger.send(player, "slime-block");
                // Bounce player up
                player.setVelocity(player.getVelocity().setY(1.0));
            }
            case BACKWARD -> {
                // Player landed on backward block - next block spawns behind!
                messenger.send(player, "backward-block");
            }
            case TNT -> {
                // Start TNT fuse when player lands on it
                messenger.send(player, "tnt-block");
                Block blockBelow = player.getLocation().subtract(0, 1, 0).getBlock();
                String key = locKey(blockBelow.getLocation());
                if (!tntTimers.containsKey(key)) {
                    tntTimers.put(key, TNT_FUSE_TICKS);
                    // Play fuse sound
                    player.playSound(blockBelow.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
                }
            }
            default -> {}
        }
    }

    /** Process TNT block timers - decrement and explode when timer reaches 0. */
    private void processTntTimers() {
        if (tntTimers.isEmpty()) return;

        Iterator<Map.Entry<String, Integer>> iterator = tntTimers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            int remaining = entry.getValue() - 1;

            if (remaining <= 0) {
                // TNT explodes!
                iterator.remove();
                String key = entry.getKey();

                // Parse location from key
                String[] parts = key.split(",");
                if (parts.length == 3) {
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    Location loc = new Location(player.getWorld(), x, y, z);

                    // Create harmless explosion effect
                    player.getWorld()
                            .spawnParticle(Particle.EXPLOSION, loc.clone().add(0.5, 0.5, 0.5), 1);
                    player.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);

                    // Remove the block (restore original)
                    restoreBlock(loc);

                    // Remove from history if present
                    Block block = loc.getBlock();
                    int index = history.indexOf(block);
                    if (index >= 0) {
                        history.remove(index);
                        if (index < blockTypes.size()) {
                            blockTypes.remove(index);
                        }
                        // Adjust lastPositionIndex if needed
                        if (index < lastPositionIndex) {
                            lastPositionIndex--;
                        }
                    }
                }
            } else {
                entry.setValue(remaining);
            }
        }
    }

    public boolean useDoubleJump() {
        if (hasDoubleJump) {
            hasDoubleJump = false;
            player.setAllowFlight(false);
            return true;
        }
        return false;
    }

    private void awardXp(int blocksAdvanced) {
        if (!config.isXpEnabled() || plugin.getDatabase() == null) return;

        int xpGained = config.getXpPerBlock() * blocksAdvanced;

        // Check for milestones within the range of blocks we just passed
        int milestoneInterval = config.getMilestoneInterval();
        if (milestoneInterval > 0) {
            int startScore = score - blocksAdvanced;
            int milestonesHit = (score / milestoneInterval) - (startScore / milestoneInterval);
            if (milestonesHit > 0) {
                xpGained += config.getMilestoneBonus() * milestonesHit;
                // Show milestone for the highest one reached
                int highestMilestone = (score / milestoneInterval) * milestoneInterval;
                config.getMessenger()
                        .send(
                                player,
                                "milestone-reached",
                                Placeholder.of("milestone", String.valueOf(highestMilestone)));
            }
        }

        // Award XP asynchronously
        final int finalXp = xpGained;
        plugin.getDatabase().addXp(player.getUniqueId(), player.getName(), finalXp);
    }

    public void end(boolean fell) {
        if (!active) return;
        active = false;

        // Show WASTED if player fell, then clear action bar
        if (fell) {
            showWastedEffect();
        } else {
            clearActionBar();
        }

        // Remove all blocks in history
        for (Block block : history) {
            if (block.getType() != Material.AIR) {
                restoreBlock(block.getLocation());
            }
        }
        history.clear();
        originalBlocks.clear();

        // Reset player state
        player.removePotionEffect(PotionEffectType.SPEED);
        hasDoubleJump = false;
        player.setAllowFlight(false);

        // Teleport back with 180 degree rotation
        Location teleportLoc = config.getNpcTeleportLocation().clone();
        teleportLoc.setYaw(teleportLoc.getYaw() + 180);
        player.teleport(teleportLoc);

        // Save score and check for new highscore asynchronously
        final int finalScore = score;
        final UUID uuid = player.getUniqueId();
        final String playerName = player.getName();
        final long gameDuration = System.currentTimeMillis() - startTime;

        // Send game over message immediately - no waiting for DB
        config.getMessenger()
                .send(player, "game-end", Placeholder.of("score", String.valueOf(finalScore)));

        if (plugin.getDatabase() != null) {
            // Save game history async
            plugin.getDatabase().saveGameHistory(uuid, finalScore, gameDuration);

            // Try to save highscore async (atomic - only saves if higher)
            plugin.getDatabase()
                    .saveHighscoreIfHigher(uuid, playerName, finalScore)
                    .thenAccept(
                            wasNewHighscore -> {
                                if (wasNewHighscore) {
                                    // Send celebration message on main thread
                                    plugin.getServer()
                                            .getScheduler()
                                            .runTask(
                                                    plugin,
                                                    () -> {
                                                        if (player.isOnline()) {
                                                            config.getMessenger()
                                                                    .send(
                                                                            player,
                                                                            "new-highscore",
                                                                            Placeholder.of(
                                                                                    "score",
                                                                                    String.valueOf(
                                                                                            finalScore)));
                                                            // Award personal best bonus XP
                                                            if (config.isXpEnabled()) {
                                                                plugin.getDatabase()
                                                                        .addXp(
                                                                                uuid,
                                                                                playerName,
                                                                                config
                                                                                        .getPersonalBestBonus());
                                                            }
                                                        }
                                                    });
                                }
                            });
        }

        // Remove session from manager so player can start again
        plugin.getParkourManager().removeSession(player);
    }

    public void checkFall() {
        if (!active || history.isEmpty()) return;

        // Get current block player should be near
        Block currentBlock = history.get(Math.min(lastPositionIndex, history.size() - 1));

        // Fall threshold of 2 blocks - quick reset
        if (player.getLocation().getY() < currentBlock.getY() - 2) {
            end(true);
        }
    }

    /** Send action bar with current score display. */
    private void sendActionBar() {
        Component actionBar =
                Component.text()
                        .append(Component.text("Blocks: ", NamedTextColor.GRAY))
                        .append(Component.text(score, NamedTextColor.GOLD, TextDecoration.BOLD))
                        .build();
        player.sendActionBar(actionBar);
    }

    /** Show WASTED effect when player falls. */
    private void showWastedEffect() {
        Component wasted = Component.text("WASTED", NamedTextColor.DARK_RED, TextDecoration.BOLD);
        player.sendActionBar(wasted);

        // Clear action bar after a delay for smooth transition
        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            if (player.isOnline()) {
                                player.sendActionBar(Component.empty());
                            }
                        },
                        40L); // 2 seconds
    }

    /** Clear the action bar. */
    private void clearActionBar() {
        player.sendActionBar(Component.empty());
    }

    public Player getPlayer() {
        return player;
    }

    public int getScore() {
        return score;
    }

    public boolean isActive() {
        return active;
    }

    public boolean hasDoubleJump() {
        return hasDoubleJump;
    }

    public long getStartTime() {
        return startTime;
    }

    public enum BlockType {
        NORMAL,
        BACKWARD,
        SPEED,
        DOUBLE_JUMP,
        SLIME,
        TNT
    }
}
