package net.serverplugins.commands.commands;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** Command to manage the survival guide item Usage: /guide [remove] */
public class GuideCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public GuideCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        // If no arguments, show usage
        if (args.length == 0) {
            player.sendMessage(miniMessage.deserialize("<yellow>Usage: /guide [remove|give]"));
            return true;
        }

        // Handle subcommands
        String subcommand = args[0].toLowerCase();

        if (subcommand.equals("remove")
                || subcommand.equals("delete")
                || subcommand.equals("disable")) {
            handleRemove(player);
            return true;
        } else if (subcommand.equals("give")
                || subcommand.equals("restore")
                || subcommand.equals("enable")
                || subcommand.equals("add")) {
            handleGive(player);
            return true;
        }

        // Unknown subcommand
        player.sendMessage(
                miniMessage.deserialize("<red>Unknown subcommand. Usage: /guide [remove|give]"));
        return true;
    }

    private void handleRemove(Player player) {
        var joinItemListener = plugin.getJoinItemListener();

        if (joinItemListener == null || !joinItemListener.isEnabled()) {
            player.sendMessage(
                    miniMessage.deserialize("<red>The survival guide feature is not enabled."));
            return;
        }

        // Remove the item from inventory
        boolean removed = joinItemListener.removeJoinItem(player);

        // Save preference to database
        var repository = plugin.getRepository();
        if (repository != null) {
            repository.setSurvivalGuideEnabled(player.getUniqueId(), player.getName(), false);
        }

        if (removed) {
            player.sendMessage(
                    miniMessage.deserialize("<green>Survival guide removed from your inventory!"));
            player.sendMessage(
                    miniMessage.deserialize("<yellow>It will not come back automatically."));
            player.sendMessage(
                    miniMessage.deserialize(
                            "<gray>Use <white>/guide give</white> to restore it, or <white>/menu</white> to access the server menu."));
        } else {
            player.sendMessage(
                    miniMessage.deserialize(
                            "<yellow>You don't have a survival guide in your inventory, but your preference has been saved."));
            player.sendMessage(
                    miniMessage.deserialize("<gray>Use <white>/guide give</white> to restore it."));
        }
    }

    private void handleGive(Player player) {
        var joinItemListener = plugin.getJoinItemListener();

        if (joinItemListener == null || !joinItemListener.isEnabled()) {
            player.sendMessage(
                    miniMessage.deserialize("<red>The survival guide feature is not enabled."));
            return;
        }

        // Give the item to the player
        joinItemListener.giveJoinItem(player);

        // Save preference to database
        var repository = plugin.getRepository();
        if (repository != null) {
            repository.setSurvivalGuideEnabled(player.getUniqueId(), player.getName(), true);
        }

        player.sendMessage(
                miniMessage.deserialize("<green>Survival guide added to your inventory!"));
        player.sendMessage(
                miniMessage.deserialize("<gray>Right-click it to open the server menu."));
        player.sendMessage(
                miniMessage.deserialize(
                        "<gray>Use <white>/guide remove</white> to disable it again."));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("remove");
            completions.add("delete");
            completions.add("disable");
            completions.add("give");
            completions.add("restore");
            completions.add("enable");
            completions.add("add");
        }

        return completions;
    }
}
