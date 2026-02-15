package net.serverplugins.npcs.listeners;

import net.serverplugins.npcs.ServerNpcs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class NpcInteractionListener implements Listener {

    private final ServerNpcs plugin;

    public NpcInteractionListener(ServerNpcs plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!plugin.getNpcsConfig().isFancyNpcsIntegrationEnabled()) {
            return;
        }

        // Check if the entity is a FancyNpc
        // This would require FancyNpcs API integration
        // For now, this is a placeholder for the integration

        /*
        Example FancyNpcs integration:

        if (event.getRightClicked() instanceof NPC) {
            NPC npc = (NPC) event.getRightClicked();
            String npcName = npc.getName();

            Npc serverNpc = plugin.getNpcManager().getNpc(npcName);
            if (serverNpc != null) {
                event.setCancelled(true);
                plugin.getDialogManager().showDialog(event.getPlayer(), serverNpc.getDialogId());
            }
        }
        */
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Close any active dialogs
        if (plugin.getDialogManager().isInDialog(player)) {
            plugin.getDialogManager().closeDialog(player);
        }
    }
}
