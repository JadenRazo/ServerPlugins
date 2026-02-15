package net.serverplugins.npcs.commands;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.npcs.ServerNpcs;
import net.serverplugins.npcs.dialog.Dialog;
import net.serverplugins.npcs.dialog.DialogChoice;
import net.serverplugins.npcs.dialog.DialogNode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class DialogCommand implements CommandExecutor, TabCompleter {

    private final ServerNpcs plugin;

    public DialogCommand(ServerNpcs plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getNpcsConfig()
                    .getMessenger()
                    .sendError(sender, "Only players can use this command!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            plugin.getNpcsConfig().getMessenger().sendError(player, "Usage: /dialog <dialog-id>");
            return true;
        }

        // Handle /dialog choose <dialog-id> <choice-index>
        if (args[0].equalsIgnoreCase("choose") && args.length >= 3) {
            return handleChoose(player, args[1], args[2]);
        }

        String dialogId = args[0];
        plugin.getDialogManager().showDialog(player, dialogId);

        return true;
    }

    private boolean handleChoose(Player player, String dialogId, String indexStr) {
        int choiceIndex;
        try {
            choiceIndex = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            return true;
        }

        Dialog dialog = plugin.getDialogManager().getDialog(dialogId);
        if (dialog == null) {
            return true;
        }

        DialogNode rootNode = dialog.getRootNode();
        if (rootNode == null || !rootNode.hasChoices()) {
            return true;
        }

        List<DialogChoice> choices = rootNode.getChoices();
        if (choiceIndex < 0 || choiceIndex >= choices.size()) {
            return true;
        }

        DialogChoice choice = choices.get(choiceIndex);

        if (choice.hasPermission() && !player.hasPermission(choice.getPermission())) {
            plugin.getNpcsConfig().getMessenger().send(player, "no-permission-choice");
            return true;
        }

        plugin.getDialogManager().executeChoice(player, choice);
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}
