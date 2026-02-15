package net.serverplugins.commands.commands;

import java.util.*;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class WarpCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;
    private final Map<UUID, BukkitRunnable> pendingTeleports = new HashMap<>();
    private final Map<UUID, Location> startLocations = new HashMap<>();

    public WarpCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("servercommands.warp")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        if (args.length < 1) {
            Set<String> warps = plugin.getWarpManager().getWarpNames();
            if (warps.isEmpty()) {
                TextUtil.sendError(player, "No warps are available!");
            } else {
                TextUtil.send(player, "<gold>Available warps: <yellow>" + String.join(", ", warps));
            }
            return true;
        }

        String warpName = args[0].toLowerCase();
        Location warp = plugin.getWarpManager().getWarp(warpName);

        if (warp == null) {
            TextUtil.sendError(player, "Warp '" + warpName + "' not found!");
            return true;
        }

        if (warp.getWorld() == null) {
            TextUtil.sendError(player, "The world for this warp no longer exists!");
            return true;
        }

        if (pendingTeleports.containsKey(player.getUniqueId())) {
            TextUtil.sendError(player, "You already have a pending teleport!");
            return true;
        }

        int delay = plugin.getCommandsConfig().getTeleportDelay();

        if (delay <= 0 || player.hasPermission("servercommands.teleport.bypass")) {
            teleport(player, warp, warpName);
            return true;
        }

        startLocations.put(player.getUniqueId(), player.getLocation().clone());

        String warmupMsg = plugin.getCommandsConfig().getTeleportWarmupMessage(warpName, delay);
        TextUtil.send(player, warmupMsg);

        BukkitRunnable task =
                new BukkitRunnable() {
                    int secondsLeft = delay;

                    @Override
                    public void run() {
                        if (!player.isOnline()) {
                            cleanup(player.getUniqueId());
                            cancel();
                            return;
                        }

                        if (plugin.getCommandsConfig().cancelOnMove()) {
                            Location start = startLocations.get(player.getUniqueId());
                            if (start != null && hasMoved(player.getLocation(), start)) {
                                TextUtil.send(
                                        player,
                                        plugin.getCommandsConfig().getTeleportCancelledMessage());
                                cleanup(player.getUniqueId());
                                cancel();
                                return;
                            }
                        }

                        if (secondsLeft <= 0) {
                            teleport(player, warp, warpName);
                            cleanup(player.getUniqueId());
                            cancel();
                        } else {
                            TextUtil.send(
                                    player,
                                    plugin.getCommandsConfig()
                                            .getTeleportCountdownMessage(secondsLeft));
                            secondsLeft--;
                        }
                    }
                };

        pendingTeleports.put(player.getUniqueId(), task);
        task.runTaskTimer(plugin, 20L, 20L);

        return true;
    }

    private void teleport(Player player, Location location, String warpName) {
        plugin.getPlayerDataManager()
                .getPlayerData(player.getUniqueId())
                .setLastLocation(player.getLocation());
        player.teleport(location);
        TextUtil.send(player, plugin.getCommandsConfig().getTeleportSuccessMessage(warpName));
    }

    private boolean hasMoved(Location current, Location start) {
        return current.getBlockX() != start.getBlockX()
                || current.getBlockY() != start.getBlockY()
                || current.getBlockZ() != start.getBlockZ();
    }

    private void cleanup(UUID uuid) {
        pendingTeleports.remove(uuid);
        startLocations.remove(uuid);
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return plugin.getWarpManager().getWarpNames().stream()
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
