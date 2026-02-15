package net.serverplugins.items.listeners;

import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.items.ServerItems;
import net.serverplugins.items.managers.FurnitureManager;
import net.serverplugins.items.models.CustomFurniture;
import net.serverplugins.items.models.FurnitureInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class FurnitureListener implements Listener {

    private final ServerItems plugin;
    private final FurnitureManager furnitureManager;

    public FurnitureListener(ServerItems plugin) {
        this.plugin = plugin;
        this.furnitureManager = plugin.getFurnitureManager();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractAtEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!(entity instanceof Interaction)) return;

        FurnitureInstance instance =
                furnitureManager.getFurnitureByInteraction(entity.getUniqueId());
        if (instance == null) return;

        Player player = event.getPlayer();
        CustomFurniture def = furnitureManager.getDefinition(instance.getFurnitureId());
        if (def == null) return;

        // Handle sitting
        if (def.isSittable() && player.isSneaking()) {
            seatPlayer(player, instance, def);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();
        if (!(target instanceof ItemDisplay)) return;
        if (!(event.getDamager() instanceof Player player)) return;

        FurnitureInstance instance = furnitureManager.getFurnitureByDisplay(target.getUniqueId());
        if (instance == null) return;

        if (!player.hasPermission("serveritems.place")) {
            event.setCancelled(true);
            return;
        }

        // Remove furniture on hit (sneaking)
        if (player.isSneaking()) {
            furnitureManager.removeFurniture(target.getUniqueId());
            TextUtil.sendSuccess(player, "Furniture removed.");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        String world = event.getWorld().getName();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin, () -> furnitureManager.loadChunkFurniture(world, chunkX, chunkZ));
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        String world = event.getWorld().getName();
        furnitureManager.unloadChunkFurniture(
                world, event.getChunk().getX(), event.getChunk().getZ());
    }

    private void seatPlayer(Player player, FurnitureInstance instance, CustomFurniture def) {
        org.bukkit.Location seatLoc = instance.getLocation().clone();
        seatLoc.setY(seatLoc.getY() + def.getSitHeight());
        seatLoc.setYaw(instance.getYaw());

        // Spawn invisible armor stand for seating
        org.bukkit.entity.ArmorStand seat =
                seatLoc.getWorld()
                        .spawn(
                                seatLoc,
                                org.bukkit.entity.ArmorStand.class,
                                stand -> {
                                    stand.setVisible(false);
                                    stand.setGravity(false);
                                    stand.setSmall(true);
                                    stand.setMarker(true);
                                    stand.setPersistent(false);
                                });

        seat.addPassenger(player);

        // Auto-remove seat when player dismounts
        plugin.getServer()
                .getScheduler()
                .runTaskTimer(
                        plugin,
                        task -> {
                            if (!seat.isValid()
                                    || seat.getPassengers().isEmpty()
                                    || !player.isOnline()) {
                                seat.remove();
                                task.cancel();
                            }
                        },
                        20L,
                        20L);
    }
}
