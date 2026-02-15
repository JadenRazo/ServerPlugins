package net.serverplugins.npcs.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.messages.MessageBuilder;
import net.serverplugins.npcs.ServerNpcs;
import net.serverplugins.npcs.models.Npc;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class NpcCommand implements CommandExecutor, TabCompleter {

    private final ServerNpcs plugin;

    public NpcCommand(ServerNpcs plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                return handleReload(sender);
            case "list":
                return handleList(sender);
            case "info":
                return handleInfo(sender, args);
            case "talk":
                return handleTalk(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("servernpcs.admin")) {
            plugin.getNpcsConfig().getMessenger().send(sender, "no-permission");
            return true;
        }

        plugin.reloadConfiguration();
        plugin.getNpcsConfig().getMessenger().send(sender, "reload-success");
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!sender.hasPermission("servernpcs.admin")) {
            plugin.getNpcsConfig().getMessenger().send(sender, "no-permission");
            return true;
        }

        MessageBuilder builder =
                MessageBuilder.create()
                        .prefix(plugin.getNpcsConfig().getMessenger().getPrefix())
                        .emphasis("NPCs:")
                        .newLine();

        if (plugin.getNpcManager().getAllNpcs().isEmpty()) {
            builder.info("No NPCs configured");
        } else {
            plugin.getNpcManager()
                    .getAllNpcs()
                    .forEach(
                            (id, npc) -> {
                                builder.bullet()
                                        .emphasis(id)
                                        .info(" (Dialog: ")
                                        .highlight(npc.getDialogId())
                                        .info(")")
                                        .newLine();
                            });
        }

        builder.newLine()
                .emphasis("Dialogs:")
                .newLine()
                .info("Total: ")
                .emphasis(String.valueOf(plugin.getDialogManager().getDialogCount()));

        builder.send(sender);
        return true;
    }

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("servernpcs.admin")) {
            plugin.getNpcsConfig().getMessenger().send(sender, "no-permission");
            return true;
        }

        if (args.length < 2) {
            plugin.getNpcsConfig().getMessenger().sendError(sender, "Usage: /npc info <npc-id>");
            return true;
        }

        String npcId = args[1];
        Npc npc = plugin.getNpcManager().getNpc(npcId);

        if (npc == null) {
            plugin.getNpcsConfig().getMessenger().send(sender, "npc-not-found");
            return true;
        }

        MessageBuilder.create()
                .prefix(plugin.getNpcsConfig().getMessenger().getPrefix())
                .emphasis("NPC Info: ")
                .highlight(npcId)
                .newLine()
                .arrow()
                .info("Name: ")
                .highlight(npc.getName())
                .newLine()
                .arrow()
                .info("Display Name: ")
                .highlight(npc.getDisplayName())
                .newLine()
                .arrow()
                .info("Dialog ID: ")
                .highlight(npc.getDialogId())
                .send(sender);

        return true;
    }

    private boolean handleTalk(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getNpcsConfig()
                    .getMessenger()
                    .sendError(sender, "Only players can use this command!");
            return true;
        }

        if (args.length < 2) {
            plugin.getNpcsConfig().getMessenger().sendError(sender, "Usage: /npc talk <npc-id>");
            return true;
        }

        Player player = (Player) sender;
        String npcId = args[1];
        Npc npc = plugin.getNpcManager().getNpc(npcId);

        if (npc == null) {
            // Try to open dialog directly
            plugin.getDialogManager().showDialog(player, npcId);
        } else {
            plugin.getDialogManager().showDialog(player, npc.getDialogId());
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        MessageBuilder.create()
                .prefix(plugin.getNpcsConfig().getMessenger().getPrefix())
                .emphasis("ServerNpcs Commands:")
                .newLine()
                .command("/npc reload")
                .info(" - Reload configuration")
                .newLine()
                .command("/npc list")
                .info(" - List all NPCs")
                .newLine()
                .command("/npc info <id>")
                .info(" - Get NPC info")
                .newLine()
                .command("/npc talk <id>")
                .info(" - Talk to an NPC")
                .send(sender);
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload", "list", "info", "talk").stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("talk")) {
                return plugin.getNpcManager().getAllNpcs().keySet().stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}
