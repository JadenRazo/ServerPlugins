package net.serverplugins.mobhealth.listeners;

import net.serverplugins.mobhealth.ServerMobHealth;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public class CleanupListener implements Listener {

    private final ServerMobHealth plugin;

    public CleanupListener(ServerMobHealth plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        plugin.getManager().removeDisplay(entity.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        event.getWorld()
                .getLivingEntities()
                .forEach(entity -> plugin.getManager().removeDisplay(entity.getUniqueId()));
    }
}
