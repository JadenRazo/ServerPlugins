package net.serverplugins.core.features;

import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.core.ServerCore;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class WorldBorderFeature extends Feature implements Listener {

    public WorldBorderFeature(ServerCore plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "World Border Protection";
    }

    @Override
    public String getDescription() {
        return "Prevents ender pearl teleportation through world borders";
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!isEnabled()) return;

        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && cause != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            return;
        }

        Location to = event.getTo();
        if (to == null || to.getWorld() == null) return;

        WorldBorder border = to.getWorld().getWorldBorder();
        if (!border.isInside(to)) {
            event.setCancelled(true);
            TextUtil.sendError(event.getPlayer(), "You can't teleport beyond the world border.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!isEnabled()) return;
        if (!(event.getEntity() instanceof EnderPearl pearl)) return;
        if (!(pearl.getShooter() instanceof Player player)) return;

        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        WorldBorder border = world.getWorldBorder();
        Location center = border.getCenter();
        double radius = border.getSize() / 2.0;

        double distX = Math.abs(loc.getX() - center.getX());
        double distZ = Math.abs(loc.getZ() - center.getZ());

        // If the player is within 16 blocks of the border edge, cancel the throw
        double bufferToEdgeX = radius - distX;
        double bufferToEdgeZ = radius - distZ;

        if (bufferToEdgeX < 16 || bufferToEdgeZ < 16) {
            // Check if the pearl is heading outward
            var velocity = pearl.getVelocity();
            boolean headingOut = false;

            if (bufferToEdgeX < 16) {
                double dirX = loc.getX() - center.getX();
                if ((dirX > 0 && velocity.getX() > 0) || (dirX < 0 && velocity.getX() < 0)) {
                    headingOut = true;
                }
            }
            if (bufferToEdgeZ < 16) {
                double dirZ = loc.getZ() - center.getZ();
                if ((dirZ > 0 && velocity.getZ() > 0) || (dirZ < 0 && velocity.getZ() < 0)) {
                    headingOut = true;
                }
            }

            if (headingOut) {
                event.setCancelled(true);
                TextUtil.sendError(player, "You can't throw ender pearls beyond the world border.");
            }
        }
    }
}
