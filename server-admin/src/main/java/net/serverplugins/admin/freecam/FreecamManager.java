package net.serverplugins.admin.freecam;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.vanish.VanishMode;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manages freecam mode for players. Uses server-side spectator mode for true noclip functionality.
 * Ghost entity shows player's position to others.
 */
public class FreecamManager {

    private final ServerAdmin plugin;
    private final Map<UUID, FreecamSession> activeSessions;
    private FreecamPacketHandler packetHandler;

    public FreecamManager(ServerAdmin plugin) {
        this.plugin = plugin;
        this.activeSessions = new ConcurrentHashMap<>();

        // Initialize packet handler if ProtocolLib is available
        if (plugin.isProtocolLibEnabled()) {
            try {
                this.packetHandler = new FreecamPacketHandler(plugin);
                plugin.getLogger().info("Freecam packet handler initialized");
            } catch (NoClassDefFoundError | Exception e) {
                plugin.getLogger()
                        .warning("Failed to initialize freecam packet handler: " + e.getMessage());
            }
        }
    }

    /**
     * Starts freecam mode for a player. If already in freecam, this will exit instead.
     *
     * @return true if freecam was started, false if exited or failed
     */
    public boolean startFreecam(Player player) {
        if (isInFreecam(player)) {
            return false;
        }

        // Store current state
        boolean wasVanished = plugin.getVanishManager().isVanished(player);
        FreecamSession session =
                new FreecamSession(
                        player.getUniqueId(),
                        player.getLocation().clone(),
                        player.getGameMode(),
                        player.isFlying(),
                        player.getAllowFlight(),
                        wasVanished,
                        player.getInventory().getArmorContents(),
                        player.getInventory().getItemInMainHand());

        activeSessions.put(player.getUniqueId(), session);

        // Spawn ghost entity at current location (for other players)
        if (packetHandler != null) {
            UUID ghostUuid = UUID.randomUUID();
            session.setGhostEntityUuid(ghostUuid);
            int ghostId =
                    packetHandler.spawnGhostEntity(
                            player,
                            session.getOriginalLocation(),
                            ghostUuid,
                            session.getArmorContents(),
                            session.getMainHandItem());
            session.setGhostEntityId(ghostId);
        }

        // Vanish the player (FULL mode so invisible to everyone)
        if (!wasVanished) {
            plugin.getVanishManager().vanish(player, VanishMode.FULL);
        }

        // Use actual spectator mode for true noclip
        // This is the only reliable way to get proper noclip behavior
        player.setGameMode(GameMode.SPECTATOR);

        // Start action bar task
        startActionBarTask(player, session);

        return true;
    }

    /**
     * Stops freecam mode for a player.
     *
     * @return true if freecam was stopped, false if not in freecam
     */
    public boolean stopFreecam(Player player) {
        FreecamSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) {
            return false;
        }

        // Cancel action bar task
        cancelActionBarTask(session);

        // Remove ghost entity
        if (packetHandler != null && session.hasGhostEntity()) {
            packetHandler.removeGhostEntity(session.getGhostEntityId(), player);
        }

        // Restore previous game mode first (before teleport to avoid issues)
        player.setGameMode(session.getPreviousGameMode());

        // Teleport back to original location
        player.teleport(session.getOriginalLocation());

        // Restore flight state based on original gamemode
        if (session.getPreviousGameMode() == GameMode.CREATIVE
                || session.getPreviousGameMode() == GameMode.SPECTATOR) {
            player.setAllowFlight(true);
            player.setFlying(session.wasFlying());
        } else {
            player.setAllowFlight(session.wasAllowFlight());
            player.setFlying(session.wasFlying() && session.wasAllowFlight());
        }

        // Restore vanish state
        if (!session.wasVanished() && plugin.getVanishManager().isVanished(player)) {
            plugin.getVanishManager().unvanish(player);
        }

        // Clear action bar
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));

        return true;
    }

    /** Checks if a player is currently in freecam mode. */
    public boolean isInFreecam(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    /** Gets the freecam session for a player. */
    public FreecamSession getSession(Player player) {
        return activeSessions.get(player.getUniqueId());
    }

    /** Gets the freecam session by UUID. */
    public FreecamSession getSession(UUID uuid) {
        return activeSessions.get(uuid);
    }

    /** Starts the action bar display task for freecam mode. */
    private void startActionBarTask(Player player, FreecamSession session) {
        sendActionBar(player);

        BukkitTask task =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    if (!player.isOnline() || !isInFreecam(player)) {
                                        cancelActionBarTask(session);
                                        return;
                                    }
                                    sendActionBar(player);
                                },
                                0L,
                                40L); // Every 2 seconds

        session.setActionBarTaskId(task.getTaskId());
    }

    /** Sends the freecam action bar message to a player. */
    private void sendActionBar(Player player) {
        String message =
                "<gold>Freecam Mode<gray> | <yellow>/freecam<gray> to exit | <green>Noclip Active";
        TextUtil.sendActionBar(player, message);
    }

    /** Cancels the action bar task for a session. */
    private void cancelActionBarTask(FreecamSession session) {
        if (session != null && session.hasActionBarTask()) {
            Bukkit.getScheduler().cancelTask(session.getActionBarTaskId());
            session.setActionBarTaskId(-1);
        }
    }

    /** Handles player quit while in freecam. */
    public void handleQuit(Player player) {
        FreecamSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            cancelActionBarTask(session);

            // Remove ghost entity for all viewers
            if (packetHandler != null && session.hasGhostEntity()) {
                packetHandler.removeGhostEntity(session.getGhostEntityId(), player);
            }
        }
    }

    /** Handles a new player joining - spawns ghost entities for them. */
    public void handleJoin(Player newPlayer) {
        // Spawn ghost entities for all active freecam sessions
        for (FreecamSession session : activeSessions.values()) {
            if (!session.hasGhostEntity() || packetHandler == null) continue;

            Player freecamPlayer = Bukkit.getPlayer(session.getPlayerId());
            if (freecamPlayer == null || freecamPlayer.equals(newPlayer)) continue;

            packetHandler.spawnGhostForViewer(
                    newPlayer,
                    session.getGhostEntityId(),
                    session.getGhostEntityUuid(),
                    session.getOriginalLocation(),
                    freecamPlayer.getName(),
                    session.getArmorContents(),
                    session.getMainHandItem());
        }
    }

    /** Shuts down the freecam manager, restoring all players. */
    public void shutdown() {
        for (UUID uuid : activeSessions.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                stopFreecam(player);
            }
        }
        activeSessions.clear();
    }

    /** Gets the packet handler, or null if ProtocolLib is unavailable. */
    public FreecamPacketHandler getPacketHandler() {
        return packetHandler;
    }
}
