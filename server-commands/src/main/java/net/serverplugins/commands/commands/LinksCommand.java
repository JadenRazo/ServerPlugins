package net.serverplugins.commands.commands;

import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class LinksCommand implements CommandExecutor {

    private final ServerCommands plugin;

    public LinksCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        TextUtil.send(sender, "");
        TextUtil.send(sender, plugin.getCommandsConfig().getRawMessage("links-header"));
        TextUtil.send(sender, "");

        String discordMsg =
                plugin.getCommandsConfig()
                        .getRawMessage("links-discord")
                        .replace("{url}", plugin.getCommandsConfig().getDiscordLink());
        TextUtil.send(sender, discordMsg);

        String storeMsg =
                plugin.getCommandsConfig()
                        .getRawMessage("links-store")
                        .replace("{url}", plugin.getCommandsConfig().getStoreLink());
        TextUtil.send(sender, storeMsg);

        String websiteMsg =
                plugin.getCommandsConfig()
                        .getRawMessage("links-website")
                        .replace("{url}", plugin.getCommandsConfig().getWebsiteLink());
        TextUtil.send(sender, websiteMsg);

        TextUtil.send(sender, "");

        return true;
    }
}
