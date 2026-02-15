package net.serverplugins.admin.freeze;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public class FreezeManager {

    private final ServerAdmin plugin;
    private final Map<UUID, FreezeData> frozenPlayers;
    private final Map<UUID, BukkitTask> timeoutTasks;

    public FreezeManager(ServerAdmin plugin) {
        this.plugin = plugin;
        this.frozenPlayers = new HashMap<>();
        this.timeoutTasks = new HashMap<>();
    }

    public void freeze(Player target, Player staff, String reason) {
        UUID uuid = target.getUniqueId();

        if (frozenPlayers.containsKey(uuid)) {
            return; // Already frozen
        }

        FreezeData data =
                new FreezeData(
                        target.getLocation(),
                        staff.getUniqueId(),
                        reason,
                        System.currentTimeMillis());
        frozenPlayers.put(uuid, data);

        // Apply effects
        if (plugin.getAdminConfig().freezeBlindness()) {
            target.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false));
        }
        if (plugin.getAdminConfig().freezeSlowness()) {
            target.addPotionEffect(
                    new PotionEffect(
                            PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 255, false, false));
        }

        // Send messages
        String freezeMsg =
                plugin.getAdminConfig()
                        .getFreezeMsg()
                        .replace("%staff%", staff.getName())
                        .replace("%reason%", reason != null ? reason : "No reason specified");
        TextUtil.send(target, freezeMsg);

        // Set up auto-unfreeze timeout
        int timeoutMinutes = plugin.getAdminConfig().getFreezeTimeout();
        if (timeoutMinutes > 0) {
            BukkitTask task =
                    Bukkit.getScheduler()
                            .runTaskLater(
                                    plugin,
                                    () -> {
                                        unfreeze(target, null, true);
                                    },
                                    timeoutMinutes * 60 * 20L);
            timeoutTasks.put(uuid, task);
        }

        // Notify staff
        notifyStaff(
                staff.getName()
                        + " froze "
                        + target.getName()
                        + (reason != null ? " - " + reason : ""));
    }

    public void unfreeze(Player target, Player staff, boolean timeout) {
        UUID uuid = target.getUniqueId();

        if (!frozenPlayers.containsKey(uuid)) {
            return; // Not frozen
        }

        frozenPlayers.remove(uuid);

        // Cancel timeout task
        BukkitTask task = timeoutTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }

        // Remove effects
        target.removePotionEffect(PotionEffectType.BLINDNESS);
        target.removePotionEffect(PotionEffectType.SLOWNESS);

        // Send messages
        String unfreezeMsg = plugin.getAdminConfig().getUnfreezeMsg();
        TextUtil.send(target, unfreezeMsg);

        // Notify staff
        if (timeout) {
            notifyStaff(target.getName() + " was automatically unfrozen (timeout)");
        } else if (staff != null) {
            notifyStaff(staff.getName() + " unfroze " + target.getName());
        }
    }

    public boolean isFrozen(Player player) {
        return frozenPlayers.containsKey(player.getUniqueId());
    }

    public boolean isFrozen(UUID uuid) {
        return frozenPlayers.containsKey(uuid);
    }

    public FreezeData getFreezeData(UUID uuid) {
        return frozenPlayers.get(uuid);
    }

    public boolean isCommandAllowed(String command) {
        String cmd = command.toLowerCase();
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        // Get the base command (first word)
        String baseCmd = cmd.split(" ")[0];

        for (String allowed : plugin.getAdminConfig().getFreezeAllowedCommands()) {
            String allowedBase = allowed.toLowerCase();
            if (allowedBase.startsWith("/")) {
                allowedBase = allowedBase.substring(1);
            }
            if (baseCmd.equals(allowedBase)) {
                return true;
            }
        }
        return false;
    }

    private void notifyStaff(String message) {
        String formatted =
                plugin.getAdminConfig().getPrefix() + "<yellow>[Freeze]</yellow> <white>" + message;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("serveradmin.freeze")) {
                TextUtil.send(player, formatted);
            }
        }
        plugin.getLogger().info("[Freeze] " + message);
    }

    public void handleDisconnect(Player player) {
        UUID uuid = player.getUniqueId();
        if (frozenPlayers.containsKey(uuid)) {
            // Cancel timeout task
            BukkitTask task = timeoutTasks.remove(uuid);
            if (task != null) {
                task.cancel();
            }
            // Keep them frozen - they'll be re-frozen on rejoin
            notifyStaff(player.getName() + " disconnected while frozen");
        }
    }

    public void handleJoin(Player player) {
        UUID uuid = player.getUniqueId();
        if (frozenPlayers.containsKey(uuid)) {
            // Re-apply effects
            if (plugin.getAdminConfig().freezeBlindness()) {
                player.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false));
            }
            if (plugin.getAdminConfig().freezeSlowness()) {
                player.addPotionEffect(
                        new PotionEffect(
                                PotionEffectType.SLOWNESS, Integer.MAX_VALUE, 255, false, false));
            }

            String freezeMsg =
                    plugin.getAdminConfig()
                            .getFreezeMsg()
                            .replace("%staff%", "Staff")
                            .replace("%reason%", "You are still frozen from before");
            TextUtil.send(player, freezeMsg);

            notifyStaff(player.getName() + " rejoined while frozen");
        }
    }

    public void shutdown() {
        // Cancel all timeout tasks
        for (BukkitTask task : timeoutTasks.values()) {
            task.cancel();
        }
        timeoutTasks.clear();

        // Unfreeze all players
        for (UUID uuid : frozenPlayers.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                player.removePotionEffect(PotionEffectType.SLOWNESS);
            }
        }
        frozenPlayers.clear();
    }
}
