package net.serverplugins.commands.commands;

import java.util.List;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class RulesCommand implements CommandExecutor {

    private final ServerCommands plugin;

    public RulesCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        TextUtil.send(sender, "");
        TextUtil.send(sender, plugin.getCommandsConfig().getRawMessage("rules-header"));
        TextUtil.send(sender, "");

        List<String> rules = plugin.getCommandsConfig().getRules();
        for (String rule : rules) {
            TextUtil.send(sender, rule);
        }

        TextUtil.send(sender, "");

        return true;
    }
}
