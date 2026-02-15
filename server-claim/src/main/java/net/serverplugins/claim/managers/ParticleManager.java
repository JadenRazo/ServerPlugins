package net.serverplugins.claim.managers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimProfile;
import net.serverplugins.claim.models.DustEffect;
import net.serverplugins.claim.models.ProfileColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class ParticleManager {

    private final ServerClaim plugin;
    private BukkitTask animatedTask;
    private BukkitTask staticTask;
    private final AtomicLong currentTick = new AtomicLong(0);
    private final AtomicLong animatedModeTick = new AtomicLong(0);

    // Track player Y positions - only update when they move significantly
    private final Map<UUID, Double> cachedPlayerY = new HashMap<>();

    // Track last render time for animated mode throttling
    private final Map<UUID, Long> lastPlayerRenderTick = new HashMap<>();

    // Track player chunk positions for optimization
    private final ConcurrentHashMap<UUID, Long> lastPlayerChunkKey = new ConcurrentHashMap<>();

    // Frozen static mode: renders every 5 ticks (0.25s) with size 1.5 particles (min lifetime ~6
    // ticks)
    // This guarantees continuous overlap so particles never visibly blink or despawn
    // Y position only updates when player moves vertically (0.75+ blocks)
    private static final int STATIC_MODE_INTERVAL_TICKS =
            5; // Frozen mode: 5 ticks for seamless overlap
    private static final int ANIMATED_MODE_INTERVAL_TICKS =
            60; // Animated mode: 60 ticks (3s) - reduced frequency
    private static final int ANIMATED_PARTICLE_REFRESH = 60; // Re-render animated every 60 ticks

    public ParticleManager(ServerClaim plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Animated particles task - runs every 60 ticks (3s)
        animatedTask =
                Bukkit.getScheduler()
                        .runTaskTimerAsynchronously(
                                plugin,
                                () -> renderParticlesForMode(false),
                                20L,
                                ANIMATED_MODE_INTERVAL_TICKS);

        // Frozen static task - runs every 5 ticks (0.25s) to keep particles permanently visible
        // DUST particles with size 1.5 have a minimum lifetime of ~6 ticks, so 5-tick refresh
        // guarantees seamless overlap with no visible blinking or despawning
        staticTask =
                Bukkit.getScheduler()
                        .runTaskTimerAsynchronously(
                                plugin,
                                () -> renderParticlesForMode(true),
                                20L,
                                STATIC_MODE_INTERVAL_TICKS);
    }

    public void stop() {
        if (animatedTask != null) {
            animatedTask.cancel();
            animatedTask = null;
        }
        if (staticTask != null) {
            staticTask.cancel();
            staticTask = null;
        }
        cachedPlayerY.clear();
        lastPlayerRenderTick.clear();
        lastPlayerChunkKey.clear();
    }

    private void renderParticlesForMode(boolean staticModeOnly) {
        // Update tick counters (only animated mode uses throttling)
        if (!staticModeOnly) {
            currentTick.incrementAndGet();
            animatedModeTick.addAndGet(ANIMATED_MODE_INTERVAL_TICKS);
        }

        RewardsManager rewardsManager = plugin.getRewardsManager();

        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID playerUuid = player.getUniqueId();

            // Early exit if player has particles disabled (check both global and profile settings)
            boolean particlesEnabled = true;
            if (rewardsManager != null) {
                // First check global setting
                particlesEnabled = rewardsManager.areParticlesEnabled(playerUuid);

                // If player is in their own claim, check their active profile's setting
                if (particlesEnabled) {
                    Location loc = player.getLocation();
                    int chunkX = loc.getBlockX() >> 4;
                    int chunkZ = loc.getBlockZ() >> 4;
                    Claim playerClaim =
                            plugin.getClaimManager()
                                    .getClaimAt(loc.getWorld().getName(), chunkX, chunkZ);

                    if (playerClaim != null && playerClaim.getOwnerUuid().equals(playerUuid)) {
                        ClaimProfile activeProfile = playerClaim.getActiveProfile();
                        if (activeProfile != null) {
                            // Profile setting overrides global setting when in own claim
                            particlesEnabled = activeProfile.isParticlesEnabled();
                        }
                    }
                }
            }

            if (!particlesEnabled) {
                continue;
            }

            // Filter by mode - static task only renders for static mode players, animated task for
            // others
            boolean playerStaticMode =
                    rewardsManager != null && rewardsManager.isStaticParticleMode(playerUuid);
            if (staticModeOnly != playerStaticMode) {
                continue;
            }

            renderParticlesForPlayer(player, playerStaticMode);
        }
    }

    private void renderParticlesForPlayer(Player player, boolean playerStaticMode) {
        UUID playerUuid = player.getUniqueId();
        Location loc = player.getLocation();

        // Calculate current chunk position for optimization
        int currentChunkX = loc.getBlockX() >> 4;
        int currentChunkZ = loc.getBlockZ() >> 4;
        long currentChunkKey = ((long) currentChunkX << 32) | (currentChunkZ & 0xFFFFFFFFL);

        if (playerStaticMode) {
            // Frozen mode: ALWAYS re-render every tick to maintain seamless particle presence
            // No skip logic - particles must be refreshed before they despawn
            lastPlayerChunkKey.put(playerUuid, currentChunkKey);
        } else {
            // For animated mode, always update chunk position
            lastPlayerChunkKey.put(playerUuid, currentChunkKey);

            // For animated mode, check if enough time passed for refresh
            Long lastRenderTick = lastPlayerRenderTick.get(playerUuid);
            long currentTick = animatedModeTick.get();

            // If not enough time passed, skip rendering
            if (lastRenderTick != null
                    && (currentTick - lastRenderTick) < ANIMATED_PARTICLE_REFRESH) {
                return; // Skip - particles still animating
            }
            // Enough time passed - render new animated frame
            lastPlayerRenderTick.put(playerUuid, animatedModeTick.get());
        }

        int viewDistance = plugin.getClaimConfig().getParticleViewDistance();
        int totalClaimCount = plugin.getClaimManager().getTotalClaimCount();
        int playerChunkX = loc.getBlockX() >> 4;
        int playerChunkZ = loc.getBlockZ() >> 4;
        int chunkRadius = viewDistance / 16;
        String worldName = loc.getWorld().getName();

        // Create local cache for nearby claimed chunks
        Map<String, Claim> claimCache = new HashMap<>();

        // First pass: populate cache with all claimed chunks in view radius
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;

                Claim claim = plugin.getClaimManager().getClaimAt(worldName, chunkX, chunkZ);
                if (claim != null) {
                    claimCache.put(chunkX + ":" + chunkZ, claim);
                }
            }
        }

        // Early exit if no claims in area
        if (claimCache.isEmpty()) {
            return;
        }

        Set<ChunkBorder> borders = new HashSet<>();

        // Second pass: only process claimed chunks and check borders using cache
        for (Map.Entry<String, Claim> entry : claimCache.entrySet()) {
            String[] coords = entry.getKey().split(":");
            int chunkX = Integer.parseInt(coords[0]);
            int chunkZ = Integer.parseInt(coords[1]);
            Claim claim = entry.getValue();

            // Skip if per-claim particles are disabled
            if (!claim.isParticleEnabled()) {
                continue;
            }

            collectBorders(
                    claim,
                    chunkX,
                    chunkZ,
                    borders,
                    player,
                    claimCache,
                    playerStaticMode,
                    totalClaimCount);
        }

        // player.spawnParticle() just sends a network packet - safe from async on Paper/Purpur
        renderBorders(player, borders, totalClaimCount);
    }

    private void collectBorders(
            Claim claim,
            int chunkX,
            int chunkZ,
            Set<ChunkBorder> borders,
            Player viewer,
            Map<String, Claim> claimCache,
            boolean staticMode,
            int totalClaimCount) {
        Color color;
        DustEffect effect = null;
        double customDensity = 1.0; // Default multiplier
        boolean useCustomColors =
                false; // Track if using custom colors (dust effects/profile colors)

        // 1. Get claim's active profile
        ClaimProfile activeProfile = claim.getActiveProfile();
        RewardsManager rewardsManager = plugin.getRewardsManager();
        UUID ownerUuid = claim.getOwnerUuid();

        // Custom reward settings only apply when viewing your own claims
        boolean isOwnClaim = viewer.getUniqueId().equals(ownerUuid);

        DustEffect profileEffect = null;
        ProfileColor profileColor = null;
        DustEffect globalEffect = null;
        ProfileColor globalColor = null;

        // 2. Only load custom particle settings for the claim owner
        if (isOwnClaim) {
            if (activeProfile != null) {
                profileEffect = activeProfile.getSelectedDustEffect();
                profileColor = activeProfile.getSelectedProfileColor();
            }

            // 3. Get global rewards as fallback (separate from profile)
            if (rewardsManager != null && ownerUuid != null) {
                globalEffect = rewardsManager.getSelectedDustEffect(ownerUuid);
                globalColor = rewardsManager.getSelectedProfileColor(ownerUuid);
            }
        }

        // 4. Apply particle settings with verification and color resolution
        // Priority: Profile effect > Global effect > Profile color > Global color > Default
        DustEffect finalEffect = null;
        ProfileColor finalColor = null;

        // Check profile effect first
        if (profileEffect != null
                && rewardsManager != null
                && rewardsManager.hasUnlockedDustEffect(ownerUuid, profileEffect)) {
            finalEffect = profileEffect;
            useCustomColors = true;
        }
        // Fallback to global effect
        else if (globalEffect != null
                && rewardsManager != null
                && rewardsManager.hasUnlockedDustEffect(ownerUuid, globalEffect)) {
            finalEffect = globalEffect;
            useCustomColors = true;
        }
        // Check profile color
        else if (profileColor != null
                && rewardsManager != null
                && rewardsManager.hasUnlockedProfileColor(ownerUuid, profileColor)) {
            finalColor = profileColor;
            useCustomColors = true;
        }
        // Fallback to global color
        else if (globalColor != null
                && rewardsManager != null
                && rewardsManager.hasUnlockedProfileColor(ownerUuid, globalColor)) {
            finalColor = globalColor;
            useCustomColors = true;
        }

        // Apply the selected settings
        Color fallbackColor = null;
        if (finalEffect != null) {
            effect = finalEffect;
            customDensity = finalEffect.getRecommendedDensity();
            // Don't compute color here - it will be calculated per-frame in renderBorders()
            // This allows gradients and animations to work properly
        } else if (finalColor != null) {
            // Using profile or global color - store as fallback
            fallbackColor = finalColor.getBukkitColor();
        } else {
            // Fall back to claim's assigned color (use default EGG_CRACK particle)
            fallbackColor =
                    claim.getColor() != null
                            ? claim.getColor().getBukkitColor()
                            : ProfileColor.WHITE.getBukkitColor();
            useCustomColors = false; // Using default claim color - use EGG_CRACK
        }

        // Use cache for adjacency checks instead of repeated getClaimAt calls
        if (!isClaimedByOwner(claim, chunkX - 1, chunkZ, claimCache)) {
            borders.add(
                    new ChunkBorder(
                            chunkX,
                            chunkZ,
                            BorderSide.WEST,
                            effect,
                            fallbackColor,
                            claim.getWorld(),
                            staticMode,
                            customDensity,
                            useCustomColors));
        }
        if (!isClaimedByOwner(claim, chunkX + 1, chunkZ, claimCache)) {
            borders.add(
                    new ChunkBorder(
                            chunkX,
                            chunkZ,
                            BorderSide.EAST,
                            effect,
                            fallbackColor,
                            claim.getWorld(),
                            staticMode,
                            customDensity,
                            useCustomColors));
        }
        if (!isClaimedByOwner(claim, chunkX, chunkZ - 1, claimCache)) {
            borders.add(
                    new ChunkBorder(
                            chunkX,
                            chunkZ,
                            BorderSide.NORTH,
                            effect,
                            fallbackColor,
                            claim.getWorld(),
                            staticMode,
                            customDensity,
                            useCustomColors));
        }
        if (!isClaimedByOwner(claim, chunkX, chunkZ + 1, claimCache)) {
            borders.add(
                    new ChunkBorder(
                            chunkX,
                            chunkZ,
                            BorderSide.SOUTH,
                            effect,
                            fallbackColor,
                            claim.getWorld(),
                            staticMode,
                            customDensity,
                            useCustomColors));
        }
    }

    private boolean isClaimedByOwner(
            Claim claim, int chunkX, int chunkZ, Map<String, Claim> claimCache) {
        // Check if the adjacent chunk is part of the same claim
        // First check the cache to avoid additional lookups
        Claim adjacentClaim = claimCache.get(chunkX + ":" + chunkZ);

        // If not in cache, it's not claimed in the view radius
        if (adjacentClaim == null) {
            return false;
        }

        // Check if it's the same claim
        return adjacentClaim.equals(claim) || claim.containsChunk(chunkX, chunkZ);
    }

    private void renderBorders(Player player, Set<ChunkBorder> borders, int totalClaimCount) {
        double baseDensity = plugin.getClaimConfig().getParticleDensity();
        World world = player.getWorld();

        // Get current tick for animated color calculation
        long currentTickValue = currentTick.get();

        // Determine performance tier based on total claim count
        PerformanceTier tier = getPerformanceTier(totalClaimCount);

        // Get or update cached Y position (player's feet)
        UUID playerUuid = player.getUniqueId();
        double currentFeetY = player.getLocation().getY();
        Double cachedFeetY = cachedPlayerY.get(playerUuid);

        // Update cached Y only if player moved significantly (0.75+ blocks) or no cache exists
        double playerFeetY;

        if (cachedFeetY == null || Math.abs(currentFeetY - cachedFeetY) > 0.75) {
            // Y position changed - update cache and render immediately at new height
            // No delay needed - old particles at old Y will naturally despawn in ~0.75s
            // New particles appear instantly at new Y for smooth transition
            playerFeetY = currentFeetY;
            cachedPlayerY.put(playerUuid, playerFeetY);
        } else {
            // Use cached Y - particles stay at same height
            playerFeetY = cachedFeetY;
        }

        // Render from feet to head (2 blocks tall)
        double playerHeadY = playerFeetY + 2.0;

        for (ChunkBorder border : borders) {
            if (!border.world.equals(world.getName())) continue;

            int baseX = border.chunkX << 4;
            int baseZ = border.chunkZ << 4;

            // Apply performance tier multiplier to density
            double effectiveDensity = baseDensity * tier.densityMultiplier * border.customDensity();

            // Check if this effect needs per-Y color calculation (rainbow gradient)
            boolean isRainbowGradient =
                    border.effect() == DustEffect.RAINBOW
                            && (!border.staticMode() || border.effect().isAnimated());

            // For LOW tier, only render corners for better performance
            if (tier == PerformanceTier.LOW) {
                renderCornersWithGradient(
                        player,
                        baseX,
                        baseZ,
                        playerFeetY,
                        playerHeadY,
                        effectiveDensity,
                        border,
                        currentTickValue,
                        isRainbowGradient);
            } else {
                // Render minimal particles at key points from feet to head
                // With density 16.0, only corners (0, 16) will spawn
                switch (border.side()) {
                    case NORTH -> {
                        // North border: Z is fixed at chunk boundary, X at key points
                        for (double x = 0; x <= 16; x += effectiveDensity) {
                            // Spawn particles every 1 block vertically from feet to head
                            for (double y = playerFeetY; y <= playerHeadY; y += 1.0) {
                                Color color =
                                        getColorForPosition(
                                                border, x, currentTickValue, isRainbowGradient);
                                spawnParticle(
                                        player,
                                        color,
                                        baseX + x,
                                        y,
                                        baseZ,
                                        border.staticMode(),
                                        border.useCustomColors());
                            }
                        }
                    }
                    case SOUTH -> {
                        // South border: Z is fixed at chunk boundary + 16, X at key points
                        for (double x = 0; x <= 16; x += effectiveDensity) {
                            for (double y = playerFeetY; y <= playerHeadY; y += 1.0) {
                                Color color =
                                        getColorForPosition(
                                                border, x, currentTickValue, isRainbowGradient);
                                spawnParticle(
                                        player,
                                        color,
                                        baseX + x,
                                        y,
                                        baseZ + 16,
                                        border.staticMode(),
                                        border.useCustomColors());
                            }
                        }
                    }
                    case WEST -> {
                        // West border: X is fixed at chunk boundary, Z at key points
                        for (double z = 0; z <= 16; z += effectiveDensity) {
                            for (double y = playerFeetY; y <= playerHeadY; y += 1.0) {
                                Color color =
                                        getColorForPosition(
                                                border, z, currentTickValue, isRainbowGradient);
                                spawnParticle(
                                        player,
                                        color,
                                        baseX,
                                        y,
                                        baseZ + z,
                                        border.staticMode(),
                                        border.useCustomColors());
                            }
                        }
                    }
                    case EAST -> {
                        // East border: X is fixed at chunk boundary + 16, Z at key points
                        for (double z = 0; z <= 16; z += effectiveDensity) {
                            for (double y = playerFeetY; y <= playerHeadY; y += 1.0) {
                                Color color =
                                        getColorForPosition(
                                                border, z, currentTickValue, isRainbowGradient);
                                spawnParticle(
                                        player,
                                        color,
                                        baseX + 16,
                                        y,
                                        baseZ + z,
                                        border.staticMode(),
                                        border.useCustomColors());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Calculate color for a specific position, supporting horizontal rainbow gradients.
     *
     * @param horizontalOffset Position along the border (0-16 for chunk edge)
     */
    private Color getColorForPosition(
            ChunkBorder border, double horizontalOffset, long tick, boolean isRainbowGradient) {
        if (isRainbowGradient) {
            // Calculate horizontal position (0.0 to 1.0) for rainbow cycling
            double horizontalPosition = horizontalOffset / 16.0;
            return border.effect().getColorAtPosition(horizontalPosition, tick);
        }

        // Non-gradient effects - calculate color normally
        if (border.effect() != null) {
            if (border.staticMode() && border.effect().isAnimated()) {
                return border.effect().getStaticColor();
            } else if (border.effect().isAnimated()) {
                return border.effect().getColorAtTick(tick);
            } else {
                return border.effect().getBaseColor();
            }
        } else if (border.fallbackColor() != null) {
            return border.fallbackColor();
        } else {
            return Color.WHITE;
        }
    }

    /**
     * Renders only corner particles for LOW performance tier with horizontal rainbow gradient
     * support. Creates corner indicators at chunk borders without rendering full lines.
     */
    private void renderCornersWithGradient(
            Player player,
            int baseX,
            int baseZ,
            double playerFeetY,
            double playerHeadY,
            double density,
            ChunkBorder border,
            long tick,
            boolean isRainbowGradient) {
        // Render minimal corner particles from feet to head
        switch (border.side()) {
            case NORTH -> {
                for (double y = playerFeetY; y <= playerHeadY; y += 1.0) {
                    Color colorStart = getColorForPosition(border, 0, tick, isRainbowGradient);
                    Color colorEnd = getColorForPosition(border, 16, tick, isRainbowGradient);
                    spawnParticle(
                            player,
                            colorStart,
                            baseX,
                            y,
                            baseZ,
                            border.staticMode(),
                            border.useCustomColors());
                    spawnParticle(
                            player,
                            colorEnd,
                            baseX + 16,
                            y,
                            baseZ,
                            border.staticMode(),
                            border.useCustomColors());
                }
            }
            case SOUTH -> {
                for (double y = playerFeetY; y <= playerHeadY; y += 1.0) {
                    Color colorStart = getColorForPosition(border, 0, tick, isRainbowGradient);
                    Color colorEnd = getColorForPosition(border, 16, tick, isRainbowGradient);
                    spawnParticle(
                            player,
                            colorStart,
                            baseX,
                            y,
                            baseZ + 16,
                            border.staticMode(),
                            border.useCustomColors());
                    spawnParticle(
                            player,
                            colorEnd,
                            baseX + 16,
                            y,
                            baseZ + 16,
                            border.staticMode(),
                            border.useCustomColors());
                }
            }
            case WEST -> {
                for (double y = playerFeetY; y <= playerHeadY; y += 1.0) {
                    Color colorStart = getColorForPosition(border, 0, tick, isRainbowGradient);
                    Color colorEnd = getColorForPosition(border, 16, tick, isRainbowGradient);
                    spawnParticle(
                            player,
                            colorStart,
                            baseX,
                            y,
                            baseZ,
                            border.staticMode(),
                            border.useCustomColors());
                    spawnParticle(
                            player,
                            colorEnd,
                            baseX,
                            y,
                            baseZ + 16,
                            border.staticMode(),
                            border.useCustomColors());
                }
            }
            case EAST -> {
                for (double y = playerFeetY; y <= playerHeadY; y += 1.0) {
                    Color colorStart = getColorForPosition(border, 0, tick, isRainbowGradient);
                    Color colorEnd = getColorForPosition(border, 16, tick, isRainbowGradient);
                    spawnParticle(
                            player,
                            colorStart,
                            baseX + 16,
                            y,
                            baseZ,
                            border.staticMode(),
                            border.useCustomColors());
                    spawnParticle(
                            player,
                            colorEnd,
                            baseX + 16,
                            y,
                            baseZ + 16,
                            border.staticMode(),
                            border.useCustomColors());
                }
            }
        }
    }

    /** Determines the performance tier based on total server claim count. */
    private PerformanceTier getPerformanceTier(int totalClaimCount) {
        if (totalClaimCount < 20) {
            return PerformanceTier.HIGH;
        } else if (totalClaimCount < 100) {
            return PerformanceTier.MEDIUM;
        } else {
            return PerformanceTier.LOW;
        }
    }

    /** Performance tiers that adjust particle density based on server load. */
    private enum PerformanceTier {
        HIGH(1.0), // <20 claims: Full density
        MEDIUM(0.5), // 20-99 claims: 50% density
        LOW(0.25); // 100+ claims: 25% density, corners only

        final double densityMultiplier;

        PerformanceTier(double densityMultiplier) {
            this.densityMultiplier = densityMultiplier;
        }
    }

    private void spawnParticle(
            Player player,
            Color color,
            double x,
            double y,
            double z,
            boolean staticMode,
            boolean useCustomColors) {
        // Frozen mode uses size 1.5 for longer particle lifetime (~6+ ticks minimum)
        // which guarantees overlap with the 5-tick refresh interval, eliminating blinking
        // Normal mode uses size 1.0 (standard dust particle)
        float particleSize = staticMode ? 1.5f : 1.0f;
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, particleSize);
        player.spawnParticle(
                Particle.DUST,
                x,
                y,
                z,
                0, // Count 0: spawns exactly 1 particle, offsets become velocity
                0.0, // velocityX = 0 (frozen in place)
                0.0, // velocityY = 0 (frozen in place)
                0.0, // velocityZ = 0 (frozen in place)
                0.0, // speed = 0
                dustOptions);
    }

    private enum BorderSide {
        NORTH,
        SOUTH,
        EAST,
        WEST
    }

    private record ChunkBorder(
            int chunkX,
            int chunkZ,
            BorderSide side,
            DustEffect effect,
            Color fallbackColor,
            String world,
            boolean staticMode,
            double customDensity,
            boolean useCustomColors) {}
}
