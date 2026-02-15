package net.serverplugins.admin.staffchat;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.redis.AdminRedisHandler;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class StaffChatManager {

    private final ServerAdmin plugin;
    private final Set<UUID> toggledPlayers;
    private AdminRedisHandler adminRedisHandler;

    public StaffChatManager(ServerAdmin plugin) {
        this.plugin = plugin;
        this.toggledPlayers = ConcurrentHashMap.newKeySet();
    }

    public void sendMessage(Player sender, String message) {
        String format =
                plugin.getAdminConfig()
                        .getStaffChatFormat()
                        .replace("%player%", sender.getName())
                        .replace("%message%", message);

        // Send to all players with staffchat permission on this server
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("serveradmin.staffchat")) {
                TextUtil.send(player, format);
            }
        }

        // Log to console
        plugin.getLogger().info("[StaffChat] " + sender.getName() + ": " + message);

        // Publish to Redis for cross-server routing
        if (adminRedisHandler != null && adminRedisHandler.isAvailable()) {
            adminRedisHandler.publishStaffChat(sender.getName(), message);
        }
    }

    /**
     * Handle incoming staff chat message from another server via Redis.
     *
     * @param sender The staff member's name
     * @param serverName The origin server name
     * @param message The chat message
     */
    public void handleCrossServerMessage(String sender, String serverName, String message) {
        // Get the server name from this server's config to prevent echo loops
        String thisServer = plugin.getConfig().getString("server-name", "smp").toLowerCase();

        // Ignore messages from this server to prevent duplicates
        if (serverName.equalsIgnoreCase(thisServer)) {
            return;
        }

        // Format with server tag
        String serverTag = formatServerName(serverName);
        String format =
                plugin.getAdminConfig()
                        .getStaffChatFormat()
                        .replace("%player%", sender)
                        .replace("%message%", message);

        // Prepend server tag before the message
        String crossServerFormat =
                "<dark_gray>[<gold>" + serverTag + "</gold>]</dark_gray> " + format;

        // Send to all staff on this server
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("serveradmin.staffchat")) {
                TextUtil.send(player, crossServerFormat);
            }
        }
    }

    /**
     * Format server name for display.
     *
     * @param serverName Raw server name
     * @return Formatted server name (SMP, Lobby, etc.)
     */
    private String formatServerName(String serverName) {
        if (serverName == null || serverName.isEmpty()) {
            return "Unknown";
        }
        if (serverName.length() <= 3) {
            return serverName.toUpperCase();
        }
        return serverName.substring(0, 1).toUpperCase() + serverName.substring(1).toLowerCase();
    }

    /**
     * Set the AdminRedisHandler for cross-server staff chat.
     *
     * @param handler Redis handler instance
     */
    public void setAdminRedisHandler(AdminRedisHandler handler) {
        this.adminRedisHandler = handler;
    }

    public void toggleStaffChat(Player player) {
        UUID uuid = player.getUniqueId();
        if (toggledPlayers.contains(uuid)) {
            toggledPlayers.remove(uuid);
        } else {
            toggledPlayers.add(uuid);
        }
    }

    public boolean isToggled(Player player) {
        return toggledPlayers.contains(player.getUniqueId());
    }

    public boolean isToggled(UUID uuid) {
        return toggledPlayers.contains(uuid);
    }

    public void handlePlayerQuit(UUID uuid) {
        toggledPlayers.remove(uuid);
    }
}
