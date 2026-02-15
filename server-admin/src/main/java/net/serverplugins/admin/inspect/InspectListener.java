package net.serverplugins.admin.inspect;

import java.util.Map;
import java.util.UUID;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class InspectListener implements Listener {

    private final ServerAdmin plugin;

    public InspectListener(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (plugin.getInspectManager() == null) {
            return;
        }

        if (plugin.getInspectManager().hasActiveSession(player.getUniqueId())) {
            InspectSession session = plugin.getInspectManager().getSession(player.getUniqueId());

            // Save offline sessions before closing
            if (session != null && session.isOffline() && session.canEdit()) {
                plugin.getInspectManager().saveOfflineSession(player);
            }

            plugin.getInspectManager().closeInspect(player);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (plugin.getInspectManager() == null) {
            return;
        }

        UUID joiningId = event.getPlayer().getUniqueId();

        // Check if any staff has this player's offline session open
        for (Map.Entry<UUID, InspectSession> entry :
                plugin.getInspectManager().getActiveSessions().entrySet()) {
            InspectSession session = entry.getValue();
            if (session.isOffline() && session.getTargetId().equals(joiningId)) {
                Player viewer = Bukkit.getPlayer(entry.getKey());
                if (viewer != null && viewer.isOnline()) {
                    String targetName =
                            session.getTargetName() != null
                                    ? session.getTargetName()
                                    : joiningId.toString();
                    TextUtil.send(
                            viewer,
                            plugin.getAdminConfig().getPrefix()
                                    + "<yellow>"
                                    + targetName
                                    + " <yellow>has come online. Saving changes and closing inventory...");
                    viewer.closeInventory(); // This triggers onInventoryClose which saves
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (plugin.getInspectManager() == null) {
            return;
        }

        InspectSession session = plugin.getInspectManager().getSession(player.getUniqueId());
        if (session == null) {
            return;
        }

        // Check if clicking in the inspect inventory
        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            if (session.getType() == InspectSession.Type.INVSEE) {
                // Block slots 36-44 and 49-52 (separator rows)
                int slot = event.getSlot();
                if ((slot >= 36 && slot <= 44) || (slot >= 49 && slot <= 52)) {
                    event.setCancelled(true);
                    return;
                }

                // Check if session allows editing (set when opened) or has modify permission
                if (!session.canEdit() && !player.hasPermission("serveradmin.invsee.modify")) {
                    event.setCancelled(true);
                    return;
                }

                // For offline sessions, just mark as dirty (save happens on close)
                if (session.isOffline()) {
                    session.markDirty();
                    return;
                }

                // Handle the modification after the event (for online players only)
                int rawSlot = event.getRawSlot();
                plugin.getServer()
                        .getScheduler()
                        .runTask(
                                plugin,
                                () -> {
                                    plugin.getInspectManager()
                                            .handleInventoryChange(
                                                    player, rawSlot, event.getCurrentItem());
                                });
            }
            // EcSee allows direct modification if session allows or has permission
            else if (session.getType() == InspectSession.Type.ECSEE) {
                if (!session.canEdit() && !player.hasPermission("serveradmin.invsee.modify")) {
                    event.setCancelled(true);
                    return;
                }

                // For offline sessions, mark as dirty
                if (session.isOffline()) {
                    session.markDirty();
                }
            }
        }
    }
}
