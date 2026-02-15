package net.serverplugins.admin.vanish;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.redis.VanishSyncPublisher;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class VanishManager {

    private final ServerAdmin plugin;
    private final Map<UUID, VanishState> vanishedPlayers;
    private final Set<UUID> alertReceivers;

    // Track silent container opens - maps block location to the vanished player opening it
    private final Map<Location, UUID> silentContainerOpens;

    // Cache of staff players with vanish.see permission for performance optimization
    private final Set<UUID> staffWithSeePermission;

    // ProtocolLib handler - only initialized if ProtocolLib is available
    private Object packetHandler;

    // Vanish sync publisher for Redis (nullable - vanish works without Redis)
    private VanishSyncPublisher vanishSyncPublisher;

    public VanishManager(ServerAdmin plugin) {
        this.plugin = plugin;
        this.vanishedPlayers = new ConcurrentHashMap<>();
        this.alertReceivers = ConcurrentHashMap.newKeySet();
        this.silentContainerOpens = new ConcurrentHashMap<>();
        this.staffWithSeePermission = ConcurrentHashMap.newKeySet();

        // Only initialize ProtocolLib handler if available
        if (plugin.isProtocolLibEnabled()) {
            try {
                packetHandler = new VanishPacketHandler(plugin, silentContainerOpens, this::canSee);
            } catch (NoClassDefFoundError | Exception e) {
                plugin.getLogger()
                        .warning(
                                "Failed to initialize ProtocolLib packet handler: "
                                        + e.getMessage());
            }
        }
    }

    /**
     * Cache a staff player's vanish.see permission. Should be called when a player joins or when
     * their permissions change.
     */
    public void cacheStaffPlayer(Player player) {
        if (player.hasPermission("serveradmin.vanish.see")) {
            staffWithSeePermission.add(player.getUniqueId());
        } else {
            staffWithSeePermission.remove(player.getUniqueId());
        }
    }

    /** Remove a player from the staff permission cache. Should be called when a player quits. */
    public void removeFromStaffCache(UUID uuid) {
        staffWithSeePermission.remove(uuid);
    }

    /**
     * Register a silent container open for a vanished player. Called from VanishInteractionListener
     * when a vanished player opens a container.
     */
    public void registerSilentContainerOpen(Player player, Block block) {
        if (!isVanished(player) || !plugin.getAdminConfig().vanishSilentChest()) {
            return;
        }

        Location loc = block.getLocation();
        silentContainerOpens.put(loc, player.getUniqueId());

        // Remove after a delay (container close)
        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            silentContainerOpens.remove(loc);
                        },
                        200L); // 10 seconds
    }

    /** Unregister a silent container open (called when player closes inventory). */
    public void unregisterSilentContainerOpen(Location location) {
        silentContainerOpens.remove(location);
    }

    /** Check if a location has a silent container open by a vanished player. */
    public boolean isSilentContainerOpen(Location location) {
        return silentContainerOpens.containsKey(location);
    }

    /** Get the UUID of the vanished player opening a container at a location. */
    public UUID getSilentContainerOpener(Location location) {
        return silentContainerOpens.get(location);
    }

    public void vanish(Player player, VanishMode mode) {
        if (mode == VanishMode.OFF) {
            unvanish(player);
            return;
        }

        UUID uuid = player.getUniqueId();
        VanishState state = vanishedPlayers.get(uuid);

        if (state == null) {
            state = new VanishState(uuid, mode);
            state.setWasFlying(player.isFlying());
            state.setWasAllowFlight(player.getAllowFlight());
            state.setPreviousGameMode(player.getGameMode());
            vanishedPlayers.put(uuid, state);
        } else {
            state.setMode(mode);
        }

        // Enable flight if configured
        if (plugin.getAdminConfig().enableFlight()) {
            player.setAllowFlight(true);
            player.setFlying(true);
        }

        // Hide player from others
        hideFromPlayers(player, mode);

        // Send message
        String message =
                plugin.getAdminConfig()
                        .getVanishEnabledMsg()
                        .replace("%mode%", mode.name().toLowerCase());
        TextUtil.send(player, plugin.getAdminConfig().getPrefix() + message);

        // Publish vanish state change to Redis (if available)
        if (vanishSyncPublisher != null) {
            vanishSyncPublisher.publishVanishUpdate(
                    player.getUniqueId(), player.getName(), true, mode.name());
        }
    }

    public void unvanish(Player player) {
        UUID uuid = player.getUniqueId();
        VanishState state = vanishedPlayers.remove(uuid);

        if (state == null) {
            return;
        }

        // Restore flight state
        player.setAllowFlight(state.wasAllowFlight());
        player.setFlying(state.wasFlying());

        // Show player to everyone
        showToAllPlayers(player);

        // Send message
        TextUtil.send(
                player,
                plugin.getAdminConfig().getPrefix()
                        + plugin.getAdminConfig().getVanishDisabledMsg());

        // Publish vanish state change to Redis (if available)
        if (vanishSyncPublisher != null) {
            vanishSyncPublisher.publishVanishUpdate(
                    player.getUniqueId(), player.getName(), false, "OFF");
        }
    }

    public void toggleVanish(Player player) {
        if (isVanished(player)) {
            unvanish(player);
        } else {
            vanish(player, plugin.getAdminConfig().getDefaultVanishMode());
        }
    }

    public boolean isVanished(Player player) {
        return isVanished(player.getUniqueId());
    }

    public boolean isVanished(UUID uuid) {
        return vanishedPlayers.containsKey(uuid);
    }

    public VanishState getVanishState(Player player) {
        return vanishedPlayers.get(player.getUniqueId());
    }

    public VanishMode getVanishMode(Player player) {
        VanishState state = vanishedPlayers.get(player.getUniqueId());
        return state != null ? state.getMode() : VanishMode.OFF;
    }

    public boolean canSee(Player viewer, Player target) {
        if (!isVanished(target)) {
            return true;
        }

        VanishState state = vanishedPlayers.get(target.getUniqueId());
        if (state == null) {
            return true;
        }

        switch (state.getMode()) {
            case FULL:
                return false;
            case STAFF:
                return staffWithSeePermission.contains(viewer.getUniqueId());
            default:
                return true;
        }
    }

    private void hideFromPlayers(Player vanished, VanishMode mode) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(vanished)) continue;

            // Optimize based on vanish mode
            boolean shouldHide;
            if (mode == VanishMode.FULL) {
                // FULL mode: hide from everyone, skip permission checks entirely
                shouldHide = true;
            } else if (mode == VanishMode.STAFF) {
                // STAFF mode: use cached permission set instead of hasPermission()
                shouldHide = !staffWithSeePermission.contains(online.getUniqueId());
            } else {
                // OFF or unknown mode
                shouldHide = false;
            }

            if (shouldHide) {
                online.hidePlayer(plugin, vanished);
            } else {
                online.showPlayer(plugin, vanished);
            }
        }
    }

    private void showToAllPlayers(Player player) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(player)) continue;
            online.showPlayer(plugin, player);
        }
    }

    public void updateVisibilityForPlayer(Player player) {
        for (Map.Entry<UUID, VanishState> entry : vanishedPlayers.entrySet()) {
            Player vanished = Bukkit.getPlayer(entry.getKey());
            if (vanished == null || vanished.equals(player)) continue;

            if (canSee(player, vanished)) {
                player.showPlayer(plugin, vanished);
            } else {
                player.hidePlayer(plugin, vanished);
            }
        }
    }

    public void handlePlayerJoin(Player player) {
        // Cache the player's staff permission status
        cacheStaffPlayer(player);

        // Apply vanish visibility for new player
        updateVisibilityForPlayer(player);

        // If player had persistent vanish, restore it
        // For now we just update visibility for new players
    }

    public void handlePlayerQuit(Player player) {
        // Remove from staff permission cache
        removeFromStaffCache(player.getUniqueId());

        // Remove vanish state if not persisted
        if (!plugin.getAdminConfig().persistOnRelog()) {
            vanishedPlayers.remove(player.getUniqueId());
        }
    }

    public Collection<UUID> getVanishedPlayers() {
        return Collections.unmodifiableSet(vanishedPlayers.keySet());
    }

    public void shutdown() {
        // Unvanish all players on shutdown
        for (UUID uuid : new HashSet<>(vanishedPlayers.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                unvanish(player);
            }
        }
        vanishedPlayers.clear();
    }

    /**
     * Sets the vanish sync publisher for Redis integration.
     *
     * @param publisher VanishSyncPublisher instance (can be null if Redis is unavailable)
     */
    public void setVanishSyncPublisher(VanishSyncPublisher publisher) {
        this.vanishSyncPublisher = publisher;
    }
}
