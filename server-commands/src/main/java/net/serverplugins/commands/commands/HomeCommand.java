package net.serverplugins.commands.commands;

import java.util.*;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.data.PlayerDataManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class HomeCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;
    private final Map<UUID, BukkitRunnable> pendingTeleports = new HashMap<>();
    private final Map<UUID, Location> startLocations = new HashMap<>();

    public HomeCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("servercommands.home")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        PlayerDataManager.PlayerData data =
                plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String homeName = args.length > 0 ? args[0].toLowerCase() : "home";

        Location home = data.getHome(homeName);
        if (home == null) {
            TextUtil.sendError(
                    player,
                    "Home '"
                            + homeName
                            + "' not found! Use "
                            + ColorScheme.COMMAND
                            + "/sethome"
                            + ColorScheme.ERROR
                            + " to create one.");
            return true;
        }

        if (home.getWorld() == null) {
            TextUtil.sendError(player, "The world for this home no longer exists!");
            return true;
        }

        if (pendingTeleports.containsKey(player.getUniqueId())) {
            TextUtil.sendError(player, "You already have a pending teleport!");
            return true;
        }

        int delay = plugin.getCommandsConfig().getTeleportDelay();

        if (delay <= 0 || player.hasPermission("servercommands.teleport.bypass")) {
            teleport(player, home, homeName);
            return true;
        }

        startLocations.put(player.getUniqueId(), player.getLocation().clone());

        String warmupMsg =
                plugin.getCommandsConfig()
                        .getTeleportWarmupMessage("home '" + homeName + "'", delay);
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
                            teleport(player, home, homeName);
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

    private void teleport(Player player, Location location, String homeName) {
        plugin.getPlayerDataManager()
                .getPlayerData(player.getUniqueId())
                .setLastLocation(player.getLocation());
        player.teleport(location);
        TextUtil.send(
                player,
                plugin.getCommandsConfig().getTeleportSuccessMessage("home '" + homeName + "'"));
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
        if (!(sender instanceof Player player)) return Collections.emptyList();
        if (args.length == 1) {
            PlayerDataManager.PlayerData data =
                    plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
            return new ArrayList<>(data.getHomes().keySet())
                    .stream()
                            .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                            .toList();
        }
        return Collections.emptyList();
    }
}
