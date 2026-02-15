package net.serverplugins.core.features;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.SessionManager;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.core.ServerCore;
import net.serverplugins.core.features.worldguard.RandomTpHandler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;

public class WorldGuardFlagsFeature extends Feature implements Listener {

    // Custom flags
    public static StateFlag FLAG_FLY;
    public static StateFlag FLAG_GODMODE;
    public static StateFlag FLAG_RANDOM_TP;
    public static StateFlag FLAG_BREAK_CUSTOM_1;
    public static StateFlag FLAG_BREAK_CUSTOM_2;

    private Set<String> customBlocks1 = new HashSet<>();
    private Set<String> customBlocks2 = new HashSet<>();
    private String rtpWorld;
    private String blockBreakMessage;

    // Location-based cache for player flag states
    private final Map<UUID, CachedFlagState> playerFlagCache = new ConcurrentHashMap<>();

    /**
     * Cached flag state for a player at a specific location. Cache is valid for 500ms and only if
     * player is within 1 block of cached location.
     */
    private static class CachedFlagState {
        private final long timestamp;
        private final int blockX;
        private final int blockY;
        private final int blockZ;
        private final boolean canFly;
        private final boolean hasRtp;

        public CachedFlagState(Location location, boolean canFly, boolean hasRtp) {
            this.timestamp = System.currentTimeMillis();
            this.blockX = location.getBlockX();
            this.blockY = location.getBlockY();
            this.blockZ = location.getBlockZ();
            this.canFly = canFly;
            this.hasRtp = hasRtp;
        }

        /**
         * Check if cache is still valid for the given location. Cache is valid if less than 500ms
         * old AND player is within 1 block.
         */
        public boolean isValidFor(Location location) {
            if (System.currentTimeMillis() - timestamp > 500) {
                return false;
            }

            int dx = Math.abs(location.getBlockX() - blockX);
            int dy = Math.abs(location.getBlockY() - blockY);
            int dz = Math.abs(location.getBlockZ() - blockZ);

            return dx <= 1 && dy <= 1 && dz <= 1;
        }
    }

    public WorldGuardFlagsFeature(ServerCore plugin) {
        super(plugin);
        loadConfig();
    }

    /** This must be called BEFORE WorldGuard loads (in onLoad()) */
    public static void registerFlags() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();
        try {
            StateFlag flyFlag = new StateFlag("fly", false);
            StateFlag godmodeFlag = new StateFlag("godmode", false);
            StateFlag rtpFlag = new StateFlag("random-tp", false);
            StateFlag breakCustom1 = new StateFlag("break-custom-1", false);
            StateFlag breakCustom2 = new StateFlag("break-custom-2", false);

            registry.register(flyFlag);
            registry.register(godmodeFlag);
            registry.register(rtpFlag);
            registry.register(breakCustom1);
            registry.register(breakCustom2);

            FLAG_FLY = flyFlag;
            FLAG_GODMODE = godmodeFlag;
            FLAG_RANDOM_TP = rtpFlag;
            FLAG_BREAK_CUSTOM_1 = breakCustom1;
            FLAG_BREAK_CUSTOM_2 = breakCustom2;

            Bukkit.getLogger().info("[ServerCore] Registered WorldGuard custom flags");
        } catch (FlagConflictException e) {
            // Flag already exists, get existing ones
            Flag<?> existing = registry.get("fly");
            if (existing instanceof StateFlag) FLAG_FLY = (StateFlag) existing;

            existing = registry.get("godmode");
            if (existing instanceof StateFlag) FLAG_GODMODE = (StateFlag) existing;

            existing = registry.get("random-tp");
            if (existing instanceof StateFlag) FLAG_RANDOM_TP = (StateFlag) existing;

            existing = registry.get("break-custom-1");
            if (existing instanceof StateFlag) FLAG_BREAK_CUSTOM_1 = (StateFlag) existing;

            existing = registry.get("break-custom-2");
            if (existing instanceof StateFlag) FLAG_BREAK_CUSTOM_2 = (StateFlag) existing;

            Bukkit.getLogger().info("[ServerCore] Using existing WorldGuard flags");
        }
    }

    private void loadConfig() {
        customBlocks1.clear();
        customBlocks2.clear();

        List<String> blocks1 =
                plugin.getConfig().getStringList("settings.worldguard-flags.custom-blocks.1");
        List<String> blocks2 =
                plugin.getConfig().getStringList("settings.worldguard-flags.custom-blocks.2");

        customBlocks1.addAll(blocks1);
        customBlocks2.addAll(blocks2);

        rtpWorld = plugin.getConfig().getString("settings.worldguard-flags.rtp-world", "playworld");
        blockBreakMessage =
                plugin.getConfig()
                        .getString(
                                "settings.worldguard-flags.block-break-message",
                                "<red>You cannot break this block here!");
    }

    @Override
    public String getName() {
        return "WorldGuard Flags";
    }

    @Override
    public String getDescription() {
        return "Adds custom WorldGuard flags: fly, godmode, random-tp, break-custom";
    }

    @Override
    protected void onEnable() {
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            initializeWorldGuardFeature();
        } else if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            // WorldGuard is loaded but not yet enabled (load order timing)
            // Wait for it to enable before registering listeners
            plugin.getServer()
                    .getPluginManager()
                    .registerEvents(
                            new Listener() {
                                @EventHandler
                                public void onPluginEnable(PluginEnableEvent event) {
                                    if (event.getPlugin().getName().equals("WorldGuard")) {
                                        initializeWorldGuardFeature();
                                        HandlerList.unregisterAll(this);
                                    }
                                }
                            },
                            plugin);
        } else {
            plugin.getLogger().warning("WorldGuard not found, disabling WorldGuardFlagsFeature");
            setEnabled(false);
        }
    }

    private void initializeWorldGuardFeature() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Register RandomTpHandler session handler if BetterRTP is enabled
        if (Bukkit.getPluginManager().isPluginEnabled("BetterRTP")
                && plugin.getConfig()
                        .getBoolean("settings.worldguard-flags.random-tp.enabled", true)) {
            try {
                SessionManager sessionManager =
                        WorldGuard.getInstance().getPlatform().getSessionManager();
                sessionManager.registerHandler(RandomTpHandler.FACTORY, null);
                plugin.getLogger().info("BetterRTP integration enabled for random-tp flag");
            } catch (Exception e) {
                plugin.getLogger()
                        .warning("Failed to register BetterRTP session handler: " + e.getMessage());
            }
        }

        plugin.getLogger().info("WorldGuard flags feature enabled");
    }

    /**
     * Initialize WorldGuard session on player join to prevent NPE in Session.testMoveTo(). This
     * fixes a race condition where players move before their session is properly initialized. Runs
     * synchronously at LOWEST priority to ensure it happens before any movement.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isEnabled()) return;
        initializeWorldGuardSession(event.getPlayer());
    }

    /**
     * Failsafe: Initialize session on first move if not already done. Runs at LOWEST priority
     * before WorldGuard's handler (which is NORMAL/HIGH).
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMoveInit(PlayerMoveEvent event) {
        if (!isEnabled()) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Quick check - if player is in our cache, session is already initialized
        if (playerFlagCache.containsKey(playerId)) return;

        // Initialize session before WorldGuard processes this event
        initializeWorldGuardSession(player);
    }

    /** Initialize WorldGuard session for a player synchronously. */
    private void initializeWorldGuardSession(Player player) {
        try {
            WorldGuardPlugin wgPlugin = WorldGuardPlugin.inst();
            if (wgPlugin == null) return;

            LocalPlayer wgPlayer = wgPlugin.wrapPlayer(player);
            if (wgPlayer == null) return;

            SessionManager sessionManager =
                    WorldGuard.getInstance().getPlatform().getSessionManager();
            Session session = sessionManager.get(wgPlayer);
            if (session == null) return;

            // Force session initialization by calling testMoveTo with current location
            Location loc = player.getLocation();
            com.sk89q.worldedit.util.Location weLoc = BukkitAdapter.adapt(loc);

            // Initialize the session's location state - this sets lastValid
            session.testMoveTo(wgPlayer, weLoc, MoveType.OTHER_CANCELLABLE, true);

            // Add to cache to mark as initialized
            playerFlagCache.put(player.getUniqueId(), new CachedFlagState(loc, false, false));

        } catch (Exception e) {
            // Silently ignore - WorldGuard will handle it
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!isEnabled()) return;

        Location to = event.getTo();
        if (to == null) return;

        Location from = event.getFrom();
        // Skip if only rotation changed (same block X/Y/Z)
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        Location loc = to;

        // Check cache first
        CachedFlagState cachedState = playerFlagCache.get(playerId);
        boolean canFly;
        boolean hasRtp;

        if (cachedState != null && cachedState.isValidFor(loc)) {
            // Use cached state
            canFly = cachedState.canFly;
            hasRtp = cachedState.hasRtp;
        } else {
            // Cache miss - query WorldGuard
            canFly = FLAG_FLY != null && testFlag(player, loc, FLAG_FLY);
            hasRtp = FLAG_RANDOM_TP != null && testFlag(player, loc, FLAG_RANDOM_TP);

            // Update cache
            playerFlagCache.put(playerId, new CachedFlagState(loc, canFly, hasRtp));
        }

        // Apply fly flag
        if (FLAG_FLY != null) {
            if (canFly && !player.getAllowFlight()) {
                player.setAllowFlight(true);
            } else if (!canFly
                    && player.getAllowFlight()
                    && !player.hasPermission("servercore.fly.bypass")) {
                player.setAllowFlight(false);
                player.setFlying(false);
            }
        }

        // Note: random-tp flag is now handled by RandomTpHandler session handler
        // when BetterRTP is enabled. Fallback logic kept for when BetterRTP is not available.
        if (FLAG_RANDOM_TP != null && hasRtp) {
            if (!Bukkit.getPluginManager().isPluginEnabled("BetterRTP")) {
                teleportRandomly(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!isEnabled()) return;

        // Clean up cache for disconnecting player
        playerFlagCache.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!isEnabled()) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (FLAG_GODMODE == null) return;

        if (testFlag(player, player.getLocation(), FLAG_GODMODE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isEnabled()) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location loc = block.getLocation();

        // Check break-custom-1 flag (crops)
        if (FLAG_BREAK_CUSTOM_1 != null && testFlag(player, loc, FLAG_BREAK_CUSTOM_1)) {
            if (!canBreakCustomBlock(block, customBlocks1)) {
                event.setCancelled(true);
                if (!blockBreakMessage.isEmpty()) {
                    TextUtil.send(player, blockBreakMessage);
                }
                return;
            }
        }

        // Check break-custom-2 flag (ores)
        if (FLAG_BREAK_CUSTOM_2 != null && testFlag(player, loc, FLAG_BREAK_CUSTOM_2)) {
            if (!canBreakCustomBlock(block, customBlocks2)) {
                event.setCancelled(true);
                if (!blockBreakMessage.isEmpty()) {
                    TextUtil.send(player, blockBreakMessage);
                }
            }
        }
    }

    private boolean canBreakCustomBlock(Block block, Set<String> allowedBlocks) {
        String blockType = block.getType().name().toLowerCase();
        BlockData data = block.getBlockData();

        for (String allowed : allowedBlocks) {
            String allowedLower = allowed.toLowerCase();

            // Check if it has a state requirement (e.g., "carrots[age=7]")
            if (allowedLower.contains("[")) {
                String[] parts = allowedLower.split("\\[");
                String materialPart = parts[0];
                String statePart = parts[1].replace("]", "");

                if (!blockType.equals(materialPart)) continue;

                // Check age state for crops
                if (statePart.startsWith("age=") && data instanceof Ageable ageable) {
                    int requiredAge = Integer.parseInt(statePart.substring(4));
                    if (ageable.getAge() == requiredAge) {
                        return true;
                    }
                }
            } else {
                // Simple material check
                if (blockType.equals(allowedLower)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void teleportRandomly(Player player) {
        World world = Bukkit.getWorld(rtpWorld);
        if (world == null) {
            world = player.getWorld();
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int x = random.nextInt(-5000, 5001);
        int z = random.nextInt(-5000, 5001);
        int y = world.getHighestBlockYAt(x, z) + 1;

        Location destination = new Location(world, x + 0.5, y, z + 0.5);
        player.teleportAsync(destination)
                .thenAccept(
                        success -> {
                            if (success) {
                                TextUtil.send(player, "<green>You have been randomly teleported!");
                            }
                        });
    }

    private boolean testFlag(Player player, Location location, StateFlag flag) {
        if (flag == null || player == null || location == null) {
            return false;
        }
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            if (container == null) return false;

            RegionQuery query = container.createQuery();
            com.sk89q.worldedit.util.Location weLoc = BukkitAdapter.adapt(location);

            WorldGuardPlugin wgPlugin = WorldGuardPlugin.inst();
            if (wgPlugin == null) return false;

            LocalPlayer wgPlayer = wgPlugin.wrapPlayer(player);
            if (wgPlayer == null) return false;

            ApplicableRegionSet regions = query.getApplicableRegions(weLoc);
            return regions.testState(wgPlayer, flag);
        } catch (Exception e) {
            // Silently fail to avoid spamming console on every player move
            return false;
        }
    }

    public void reload() {
        loadConfig();
    }
}
