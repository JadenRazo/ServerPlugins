package net.serverplugins.commands.commands;

import java.util.List;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class VoteCommand implements CommandExecutor {

    private final ServerCommands plugin;

    public VoteCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        TextUtil.send(sender, "");
        TextUtil.send(sender, "<gold><bold>Vote for ServerPlugins!</bold></gold>");
        TextUtil.send(sender, "");
        TextUtil.send(sender, "<gray>Support us by voting on these sites:");
        TextUtil.send(sender, "");

        List<String> voteLinks = plugin.getCommandsConfig().getVoteLinks();
        int index = 1;
        for (String link : voteLinks) {
            String message =
                    "<yellow>"
                            + index
                            + ".</yellow> <click:open_url:'"
                            + link
                            + "'><aqua>"
                            + link
                            + "</aqua></click>";
            TextUtil.send(sender, message);
            index++;
        }

        TextUtil.send(sender, "");
        TextUtil.send(sender, "<green>Thank you for your support!");
        TextUtil.send(sender, "");

        return true;
    }
}
