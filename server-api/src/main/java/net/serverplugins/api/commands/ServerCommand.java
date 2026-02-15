package net.serverplugins.api.commands;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.api.ServerAPI;
import net.serverplugins.api.utils.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class ServerCommand implements CommandExecutor, TabCompleter, PluginMessageListener {

    private static final List<String> SERVERS = Arrays.asList("smp", "lobby", "dev");
    private static final String ADMIN_PERMISSION = "serverapi.server.admin";
    private final ServerAPI plugin;

    // Track pending server info requests
    private final Map<UUID, String> pendingServerRequests = new ConcurrentHashMap<>();

    public ServerCommand(ServerAPI plugin) {
        this.plugin = plugin;
        // Register as plugin message listener
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, "BungeeCord", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            TextUtil.sendError(sender, "This command can only be used by players.");
            return true;
        }

        // /transfer command is admin-only shortcut
        boolean isTransferCmd = label.equalsIgnoreCase("transfer");

        if (args.length < 1) {
            showUsage(player, isTransferCmd);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        // Handle info subcommand
        if (subCommand.equals("info")) {
            if (!player.hasPermission(ADMIN_PERMISSION)) {
                TextUtil.send(player, "<red>You don't have permission to check player servers.");
                return true;
            }

            if (args.length < 2) {
                // Show current server for self
                requestServerInfo(player, player.getName());
            } else {
                // Show server for target player
                requestServerInfo(player, args[1]);
            }
            return true;
        }

        // Handle list subcommand - list players on a server
        if (subCommand.equals("list")) {
            if (!player.hasPermission(ADMIN_PERMISSION)) {
                TextUtil.send(player, "<red>You don't have permission to list server players.");
                return true;
            }

            if (args.length < 2) {
                TextUtil.send(player, "<red>Usage: /server list <server>");
                return true;
            }

            requestPlayerList(player, args[1]);
            return true;
        }

        // Check if it's a valid server name
        if (!SERVERS.contains(subCommand)) {
            TextUtil.send(player, "<red>Unknown server: " + subCommand);
            TextUtil.send(player, "<gray>Available: " + String.join(", ", SERVERS));
            return true;
        }

        String serverName = subCommand;

        // /server <server> - transfer self
        if (args.length == 1) {
            TextUtil.send(player, "<gray>Connecting to <yellow>" + serverName + "<gray>...");
            transferPlayer(player, serverName);
            return true;
        }

        // Admin commands below require permission
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            TextUtil.send(player, "<red>You don't have permission to transfer other players.");
            return true;
        }

        // Debug logging
        plugin.getLogger()
                .info(
                        "[ServerCmd] Admin transfer: "
                                + player.getName()
                                + " -> "
                                + args[1]
                                + " to "
                                + serverName);

        String targetArg = args[1].toLowerCase();

        // /server <server> all - transfer all players
        if (targetArg.equals("all")) {
            int count = 0;
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (!target.equals(player)) {
                    transferPlayerOther(player, target.getName(), serverName);
                    count++;
                }
            }
            TextUtil.send(
                    player,
                    "<green>Transferring <yellow>"
                            + count
                            + " <green>players to <yellow>"
                            + serverName);
            // Transfer self last
            plugin.getServer()
                    .getScheduler()
                    .runTaskLater(
                            plugin,
                            () -> {
                                transferPlayer(player, serverName);
                            },
                            20L);
            return true;
        }

        // /server <server> <player> - transfer specific player
        // Use original case for player name lookup
        String targetName = args[1];
        Player target = Bukkit.getPlayer(targetName);
        if (target != null) {
            // Player is on this server
            TextUtil.send(
                    player,
                    "<green>Transferring <yellow>"
                            + target.getName()
                            + " <green>to <yellow>"
                            + serverName);
            transferPlayer(target, serverName);
        } else {
            // Player might be on another server - use ConnectOther
            TextUtil.send(
                    player,
                    "<green>Attempting to transfer <yellow>"
                            + targetName
                            + " <green>to <yellow>"
                            + serverName);
            transferPlayerOther(player, targetName, serverName);
        }
        return true;
    }

    private void showUsage(Player player, boolean isTransferCmd) {
        if (isTransferCmd) {
            // /transfer command - admin only
            TextUtil.send(player, "<yellow>Transfer Commands:");
            TextUtil.send(player, "<gray>/transfer <server> <player> <white>- Transfer a player");
            TextUtil.send(player, "<gray>/transfer <server> all <white>- Transfer all players");
            TextUtil.send(player, "<gray>/transfer info [player] <white>- Check player's server");
            TextUtil.send(player, "<gray>/transfer list <server> <white>- List players on server");
            TextUtil.send(player, "<gray>Available servers: <yellow>" + String.join(", ", SERVERS));
        } else {
            TextUtil.send(player, "<yellow>Server Commands:");
            TextUtil.send(player, "<gray>/server <server> <white>- Connect to a server");
            if (player.hasPermission(ADMIN_PERMISSION)) {
                TextUtil.send(
                        player, "<gray>/transfer <server> <player> <white>- Transfer a player");
                TextUtil.send(player, "<gray>/transfer <server> all <white>- Transfer all players");
                TextUtil.send(
                        player, "<gray>/transfer info [player] <white>- Check player's server");
                TextUtil.send(
                        player, "<gray>/transfer list <server> <white>- List players on server");
            }
            TextUtil.send(player, "<gray>Available servers: <yellow>" + String.join(", ", SERVERS));
        }
    }

    public static void transferPlayer(Player player, String serverName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(serverName);
        player.sendPluginMessage(ServerAPI.getInstance(), "BungeeCord", out.toByteArray());
    }

    /** Transfer another player to a server (works across servers via proxy) */
    public static void transferPlayerOther(Player sender, String targetName, String serverName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("ConnectOther");
        out.writeUTF(targetName);
        out.writeUTF(serverName);
        sender.sendPluginMessage(ServerAPI.getInstance(), "BungeeCord", out.toByteArray());
    }

    /** Request which server a player is on */
    private void requestServerInfo(Player requester, String targetName) {
        pendingServerRequests.put(requester.getUniqueId(), targetName);

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("ServerIP");
        out.writeUTF(targetName);
        requester.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());

        // Also request the server name directly
        ByteArrayDataOutput out2 = ByteStreams.newDataOutput();
        out2.writeUTF("GetServer");
        requester.sendPluginMessage(plugin, "BungeeCord", out2.toByteArray());

        // Fallback message after delay if no response
        plugin.getServer()
                .getScheduler()
                .runTaskLater(
                        plugin,
                        () -> {
                            if (pendingServerRequests.containsKey(requester.getUniqueId())) {
                                pendingServerRequests.remove(requester.getUniqueId());
                                if (requester.isOnline()) {
                                    TextUtil.send(
                                            requester,
                                            "<gray>Could not determine server for <yellow>"
                                                    + targetName);
                                }
                            }
                        },
                        40L);
    }

    /** Request list of players on a server */
    private void requestPlayerList(Player requester, String serverName) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("PlayerList");
        out.writeUTF(serverName);
        requester.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals("BungeeCord")) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        String subchannel = in.readUTF();

        if (subchannel.equals("GetServer")) {
            String serverName = in.readUTF();
            UUID requesterId = player.getUniqueId();
            String targetName = pendingServerRequests.remove(requesterId);

            if (targetName != null && player.isOnline()) {
                if (targetName.equalsIgnoreCase(player.getName())) {
                    TextUtil.send(player, "<gray>You are on server: <yellow>" + serverName);
                } else {
                    TextUtil.send(
                            player,
                            "<yellow>" + targetName + " <gray>is on server: <yellow>" + serverName);
                }
            }
        } else if (subchannel.equals("PlayerList")) {
            String serverName = in.readUTF();
            String[] playerNames = in.readUTF().split(", ");

            if (player.isOnline()) {
                TextUtil.send(
                        player,
                        "<yellow>" + serverName + " <gray>(" + playerNames.length + " players):");
                if (playerNames.length > 0 && !playerNames[0].isEmpty()) {
                    TextUtil.send(player, "<white>" + String.join(", ", playerNames));
                } else {
                    TextUtil.send(player, "<gray>No players online");
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            // Add server names
            SERVERS.stream().filter(s -> s.startsWith(input)).forEach(completions::add);

            // Add subcommands for admins
            if (sender.hasPermission(ADMIN_PERMISSION)) {
                if ("info".startsWith(input)) completions.add("info");
                if ("list".startsWith(input)) completions.add("list");
            }
            return completions;
        }

        if (args.length == 2) {
            String firstArg = args[0].toLowerCase();
            String input = args[1].toLowerCase();

            // If first arg is a server and sender is admin, suggest players and "all"
            if (SERVERS.contains(firstArg) && sender.hasPermission(ADMIN_PERMISSION)) {
                if ("all".startsWith(input)) completions.add("all");
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(input))
                        .forEach(completions::add);
            }

            // If first arg is "info" or "list", suggest players or servers
            if (firstArg.equals("info") && sender.hasPermission(ADMIN_PERMISSION)) {
                Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(input))
                        .forEach(completions::add);
            }

            if (firstArg.equals("list") && sender.hasPermission(ADMIN_PERMISSION)) {
                SERVERS.stream().filter(s -> s.startsWith(input)).forEach(completions::add);
            }
        }

        return completions;
    }
}
