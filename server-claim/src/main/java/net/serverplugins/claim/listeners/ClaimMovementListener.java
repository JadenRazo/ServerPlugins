package net.serverplugins.claim.listeners;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.title.Title;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.cache.LocationResultCache;
import net.serverplugins.claim.models.Claim;
import net.serverplugins.claim.models.ClaimPermission;
import net.serverplugins.claim.models.ClaimedChunk;
import net.serverplugins.claim.models.PlayerClaimData;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.metadata.MetadataValue;

public class ClaimMovementListener implements Listener {

    private final ServerClaim plugin;

    // Track which claim each player is currently in
    private final Map<UUID, Integer> playerCurrentClaim = new ConcurrentHashMap<>();

    // Track players with auto-claim mode enabled
    private final Set<UUID> autoClaimPlayers = ConcurrentHashMap.newKeySet();

    // Track players with claim fly enabled
    private final Set<UUID> claimFlyPlayers = ConcurrentHashMap.newKeySet();

    // Track playtime sessions for XP grants
    private final Map<UUID, PlaytimeSession> playtimeSessions = new ConcurrentHashMap<>();

    // Track pending owner name lookups to prevent duplicate async tasks
    private final Set<Integer> pendingOwnerLookups = ConcurrentHashMap.newKeySet();

    // Location result cache for debounced owner name lookups (5 second TTL)
    private final LocationResultCache locationCache = new LocationResultCache();

    /** Tracks a player's playtime session in a claim for XP grants. */
    private record PlaytimeSession(int claimId, long startTimeMillis) {
        public int getMinutesPlayed() {
            return (int) ((System.currentTimeMillis() - startTimeMillis) / 60000);
        }
    }

    public ClaimMovementListener(ServerClaim plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only check on chunk changes (more efficient)
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) {
            return;
        }

        Player player = event.getPlayer();
        Chunk fromChunk = event.getFrom().getChunk();
        Chunk toChunk = event.getTo().getChunk();

        // Single claim lookups for the entire event
        Claim fromClaim = plugin.getClaimManager().getClaimAt(fromChunk);
        Claim toClaim = plugin.getClaimManager().getClaimAt(toChunk);

        // Check if player can enter the destination claim
        if (!canEnterClaim(player, toClaim)) {
            event.setCancelled(true);
            return;
        }

        // Handle claim entry/exit notifications
        handleClaimTransition(player, fromClaim, toClaim);

        // Handle auto-claim mode
        if (autoClaimPlayers.contains(player.getUniqueId())) {
            handleAutoClaim(player, toChunk, toClaim);
        }

        // Handle claim fly
        handleClaimFly(player, toClaim);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Chunk fromChunk = event.getFrom().getChunk();
        Chunk toChunk = event.getTo().getChunk();

        // Skip if same chunk
        if (fromChunk.equals(toChunk)) {
            return;
        }

        // Single claim lookups for the entire event
        Claim fromClaim = plugin.getClaimManager().getClaimAt(fromChunk);
        Claim toClaim = plugin.getClaimManager().getClaimAt(toChunk);

        // Check if player can enter the destination claim (applies to ALL teleports)
        if (toClaim != null) {
            // Check if player is banned
            if (toClaim.isBanned(player.getUniqueId())
                    && !player.hasPermission("serverclaim.bypass")) {
                event.setCancelled(true);
                TextUtil.send(player, "<red>You are banned from this claim!");
                return;
            }

            // Check ENTER_CLAIM permission
            if (!toClaim.isOwner(player.getUniqueId())
                    && !toClaim.hasPermission(player.getUniqueId(), ClaimPermission.ENTER_CLAIM)
                    && !player.hasPermission("serverclaim.bypass")) {

                event.setCancelled(true);
                TextUtil.send(player, "<red>You don't have permission to enter this claim!");
                return;
            }

            // Additional teleport protection check (owner setting)
            if (toClaim.isTeleportProtected()
                    && !toClaim.isOwner(player.getUniqueId())
                    && !toClaim.isTrusted(player.getUniqueId())
                    && !player.hasPermission("serverclaim.bypass")) {

                // Only block non-plugin teleports (allow /tp, /home, etc.)
                if (event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN
                        && event.getCause() != PlayerTeleportEvent.TeleportCause.COMMAND) {
                    event.setCancelled(true);
                    TextUtil.send(player, "<red>This claim has teleport protection enabled!");
                    return;
                }
            }
        }

        // Handle claim transition for teleports (reuse pre-fetched claims)
        handleClaimTransition(player, fromClaim, toClaim);
        handleClaimFly(player, toClaim);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        playerCurrentClaim.remove(uuid);
        autoClaimPlayers.remove(uuid);

        // Grant playtime XP for active session
        PlaytimeSession session = playtimeSessions.remove(uuid);
        if (session != null && plugin.getLevelManager() != null) {
            int minutes = session.getMinutesPlayed();
            if (minutes > 0) {
                plugin.getLevelManager().trackPlaytime(session.claimId(), uuid, minutes);
            }
        }

        // Disable flight if they had claim fly
        if (claimFlyPlayers.remove(uuid)) {
            Player player = event.getPlayer();
            if (player.getGameMode() != GameMode.CREATIVE
                    && player.getGameMode() != GameMode.SPECTATOR) {
                player.setAllowFlight(false);
                player.setFlying(false);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = player.getLocation().getChunk();
        Claim claim = plugin.getClaimManager().getClaimAt(chunk);

        if (claim != null) {
            playerCurrentClaim.put(player.getUniqueId(), claim.getId());

            // Start playtime session for XP tracking
            playtimeSessions.put(
                    player.getUniqueId(),
                    new PlaytimeSession(claim.getId(), System.currentTimeMillis()));

            // Check if player is banned from this claim - eject them immediately
            if (claim.isBanned(player.getUniqueId())
                    && !player.hasPermission("serverclaim.bypass")) {
                // Teleport to spawn immediately (same tick) to prevent movement
                Location spawn = player.getWorld().getSpawnLocation();
                player.teleport(spawn);
                TextUtil.send(
                        player,
                        "<red>You were moved to spawn because you are banned from that claim!");
            }
        }
    }

    /**
     * Check if a player can enter a claim (accepts pre-fetched claim to avoid redundant lookups)
     */
    private boolean canEnterClaim(Player player, Claim claim) {
        // Admin bypass
        if (player.hasPermission("serverclaim.bypass")) {
            return true;
        }

        if (claim == null) {
            return true; // Wilderness - anyone can enter
        }

        // Check if player is banned from this claim
        if (claim.isBanned(player.getUniqueId())) {
            TextUtil.send(player, "<red>You are banned from this claim!");
            return false;
        }

        // Owner and trusted always allowed
        if (claim.isOwner(player.getUniqueId()) || claim.isTrusted(player.getUniqueId())) {
            return true;
        }

        // Check ENTER_CLAIM permission
        if (!claim.hasPermission(player.getUniqueId(), ClaimPermission.ENTER_CLAIM)) {
            TextUtil.send(player, "<red>You don't have permission to enter this claim!");
            return false;
        }

        return true;
    }

    private void handleClaimTransition(Player player, Claim fromClaim, Claim toClaim) {
        int fromClaimId = fromClaim != null ? fromClaim.getId() : -1;
        int toClaimId = toClaim != null ? toClaim.getId() : -1;

        // Same claim (or both wilderness), no transition
        if (fromClaimId == toClaimId) {
            return;
        }

        UUID playerUuid = player.getUniqueId();

        // Left a claim
        if (fromClaim != null && toClaim == null) {
            sendExitNotification(player, fromClaim);
            playerCurrentClaim.remove(playerUuid);

            // End playtime session and grant XP
            endPlaytimeSession(playerUuid, fromClaim.getId());
        }
        // Entered a claim from wilderness
        else if (fromClaim == null && toClaim != null) {
            sendEntryNotification(player, toClaim);
            playerCurrentClaim.put(playerUuid, toClaim.getId());

            // Start new playtime session
            startPlaytimeSession(playerUuid, toClaim.getId());
        }
        // Moved between two different claims
        else if (fromClaim != null && toClaim != null) {
            sendExitNotification(player, fromClaim);
            sendEntryNotification(player, toClaim);
            playerCurrentClaim.put(playerUuid, toClaim.getId());

            // End old session, start new one
            endPlaytimeSession(playerUuid, fromClaim.getId());
            startPlaytimeSession(playerUuid, toClaim.getId());
        }
    }

    /** Start tracking playtime for a player in a claim. */
    private void startPlaytimeSession(UUID playerUuid, int claimId) {
        playtimeSessions.put(playerUuid, new PlaytimeSession(claimId, System.currentTimeMillis()));
    }

    /** End playtime tracking and grant XP for time spent. */
    private void endPlaytimeSession(UUID playerUuid, int claimId) {
        PlaytimeSession session = playtimeSessions.remove(playerUuid);
        if (session != null && session.claimId() == claimId && plugin.getLevelManager() != null) {
            int minutes = session.getMinutesPlayed();
            if (minutes > 0) {
                plugin.getLevelManager().trackPlaytime(claimId, playerUuid, minutes);
            }
        }
    }

    private void sendEntryNotification(Player player, Claim claim) {
        // Check if admin is vanished - skip notification
        if (isVanished(player)) {
            return;
        }

        String ownerName = getOwnerName(claim);
        String claimName = claim.getName();
        String welcomeMessage = claim.getWelcomeMessage();

        // Show title
        Title title =
                Title.title(
                        TextUtil.parse("<green>Entering Claim"),
                        TextUtil.parse("<gray>" + claimName + " <dark_gray>- <white>" + ownerName),
                        Title.Times.times(
                                Duration.ofMillis(200),
                                Duration.ofMillis(2000),
                                Duration.ofMillis(500)));
        player.showTitle(title);

        // Show custom welcome message if set
        if (welcomeMessage != null && !welcomeMessage.isEmpty()) {
            TextUtil.send(player, "<gold>" + welcomeMessage);
        }
    }

    private void sendExitNotification(Player player, Claim claim) {
        // Check if admin is vanished - skip notification
        if (isVanished(player)) {
            return;
        }

        Title title =
                Title.title(
                        TextUtil.parse("<red>Leaving Claim"),
                        TextUtil.parse("<gray>Entering Wilderness"),
                        Title.Times.times(
                                Duration.ofMillis(200),
                                Duration.ofMillis(1500),
                                Duration.ofMillis(500)));
        player.showTitle(title);
    }

    private void handleAutoClaim(Player player, Chunk chunk, Claim existingClaim) {
        // Check if chunk is already claimed (using pre-fetched claim)
        if (existingClaim != null) {
            return; // Already claimed
        }

        // Attempt to claim
        plugin.getClaimManager()
                .claimChunk(player, chunk)
                .thenAccept(
                        result -> {
                            if (result.success()) {
                                TextUtil.send(
                                        player,
                                        "<green>Auto-claimed chunk at "
                                                + chunk.getX()
                                                + ", "
                                                + chunk.getZ());
                            } else if (result.messageKey().equals("no-remaining-chunks")) {
                                // Disable auto-claim when out of chunks
                                autoClaimPlayers.remove(player.getUniqueId());
                                TextUtil.send(
                                        player,
                                        "<yellow>Auto-claim disabled: No remaining chunks!");
                            }
                            // Silently fail for other reasons (not adjacent, etc.)
                        });
    }

    private void handleClaimFly(Player player, Claim claim) {
        // Skip if player is in creative/spectator
        if (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // Skip if player has admin fly permission - don't interfere with their flight
        if (hasAdminFly(player)) {
            return;
        }

        // Check if player has claim fly permission (rank-restricted)
        if (!player.hasPermission("serverclaim.fly")) {
            return;
        }

        // If in their own claim or trusted with fly permission
        boolean canFly =
                claim != null
                        && (claim.isOwner(player.getUniqueId())
                                || claim.isTrusted(player.getUniqueId()));

        if (canFly) {
            // Use atomic add() - returns true if element wasn't already present
            if (claimFlyPlayers.add(player.getUniqueId())) {
                player.setAllowFlight(true);
                TextUtil.send(player, "<green>Claim fly enabled!");
            }
        } else {
            // Use atomic remove() - returns true if element was present
            if (claimFlyPlayers.remove(player.getUniqueId())) {
                player.setAllowFlight(false);
                if (player.isFlying()) {
                    player.setFlying(false);
                    TextUtil.send(player, "<yellow>Claim fly disabled - you left your claim!");
                }
            }
        }
    }

    /**
     * Check if player has admin fly permission (essentials.fly or similar) This prevents claim fly
     * from interfering with admin flight
     */
    private boolean hasAdminFly(Player player) {
        return player.hasPermission("essentials.fly")
                || player.hasPermission("cmi.command.fly")
                || player.hasPermission("serverclaim.bypass");
    }

    private boolean isVanished(Player player) {
        // Check common vanish plugins via metadata
        for (MetadataValue meta : player.getMetadata("vanished")) {
            if (meta.asBoolean()) return true;
        }
        // Also check Essentials vanish
        if (player.hasMetadata("vanished")) {
            return true;
        }
        return false;
    }

    /**
     * Get the owner name for a claim with debounced caching. Uses location cache to avoid repeated
     * lookups during player movement.
     */
    private String getOwnerName(Claim claim) {
        // Check location cache first (5 second TTL)
        if (!claim.getChunks().isEmpty()) {
            ClaimedChunk firstChunk = claim.getChunks().get(0);
            LocationResultCache.CachedResult cached =
                    locationCache.get(
                            firstChunk.getWorld(), firstChunk.getChunkX(), firstChunk.getChunkZ());
            if (cached != null) {
                return cached.getOwnerName();
            }
        }

        // Use cached name if available
        String cachedName = claim.getCachedOwnerName();
        if (cachedName != null && !cachedName.isEmpty()) {
            // Update location cache
            if (!claim.getChunks().isEmpty()) {
                ClaimedChunk firstChunk = claim.getChunks().get(0);
                locationCache.put(
                        firstChunk.getWorld(),
                        firstChunk.getChunkX(),
                        firstChunk.getChunkZ(),
                        cachedName,
                        claim.getName());
            }
            return cachedName;
        }

        // Debounce: only one lookup per claim at a time
        // add() returns true if the element wasn't already present
        if (pendingOwnerLookups.add(claim.getId())) {
            plugin.getServer()
                    .getScheduler()
                    .runTaskAsynchronously(
                            plugin,
                            () -> {
                                try {
                                    PlayerClaimData playerData =
                                            plugin.getClaimManager()
                                                    .getPlayerData(claim.getOwnerUuid());
                                    String ownerName =
                                            playerData != null
                                                    ? playerData.getUsername()
                                                    : "Unknown";
                                    claim.setCachedOwnerName(ownerName);

                                    // Update location cache for all chunks in this claim
                                    for (ClaimedChunk chunk : claim.getChunks()) {
                                        locationCache.put(
                                                chunk.getWorld(),
                                                chunk.getChunkX(),
                                                chunk.getChunkZ(),
                                                ownerName,
                                                claim.getName());
                                    }
                                } finally {
                                    pendingOwnerLookups.remove(claim.getId());
                                }
                            });
        }

        return "Loading...";
    }

    // Public methods for managing auto-claim
    public void toggleAutoClaim(Player player) {
        UUID uuid = player.getUniqueId();
        if (autoClaimPlayers.contains(uuid)) {
            autoClaimPlayers.remove(uuid);
            TextUtil.send(player, "<yellow>Auto-claim mode disabled.");
        } else {
            autoClaimPlayers.add(uuid);
            TextUtil.send(player, "<green>Auto-claim mode enabled! Walk to claim adjacent chunks.");
        }
    }

    public boolean isAutoClaimEnabled(Player player) {
        return autoClaimPlayers.contains(player.getUniqueId());
    }

    // Public method for toggling claim fly manually
    public void toggleClaimFly(Player player) {
        if (!player.hasPermission("serverclaim.fly")) {
            TextUtil.send(player, "<red>You don't have permission to use claim fly!");
            return;
        }

        UUID uuid = player.getUniqueId();
        Chunk chunk = player.getLocation().getChunk();
        Claim claim = plugin.getClaimManager().getClaimAt(chunk);

        boolean inOwnClaim = claim != null && (claim.isOwner(uuid) || claim.isTrusted(uuid));

        if (!inOwnClaim) {
            TextUtil.send(player, "<red>You can only enable claim fly in your own claim!");
            return;
        }

        // Use atomic remove/add - remove() returns true if element was present
        if (claimFlyPlayers.remove(uuid)) {
            player.setAllowFlight(false);
            player.setFlying(false);
            TextUtil.send(player, "<yellow>Claim fly disabled.");
        } else {
            claimFlyPlayers.add(uuid);
            player.setAllowFlight(true);
            TextUtil.send(player, "<green>Claim fly enabled!");
        }
    }
}
