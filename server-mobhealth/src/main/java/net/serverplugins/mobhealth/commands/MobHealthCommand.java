package net.serverplugins.mobhealth.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.mobhealth.ServerMobHealth;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class MobHealthCommand implements CommandExecutor, TabCompleter {

    private final ServerMobHealth plugin;

    public MobHealthCommand(ServerMobHealth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            TextUtil.send(
                    sender,
                    "<gold>ServerMobHealth <gray>v"
                            + plugin.getPluginMeta().getVersion()
                            + " <dark_gray>| <white>"
                            + plugin.getManager().getActiveDisplayCount()
                            + " active displays");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                if (!sender.hasPermission("mobhealth.admin")) {
                    CommonMessages.NO_PERMISSION.send(sender);
                    return true;
                }
                plugin.reloadConfiguration();
                plugin.getMobHealthConfig().getMessenger().send(sender, "reloaded");
            }
            case "toggle" -> {
                if (!sender.hasPermission("mobhealth.toggle")) {
                    CommonMessages.NO_PERMISSION.send(sender);
                    return true;
                }
                boolean toggled = plugin.togglePlayer(sender.getName());
                plugin.getMobHealthConfig()
                        .getMessenger()
                        .send(sender, toggled ? "toggled-on" : "toggled-off");
            }
            case "clear" -> {
                if (!sender.hasPermission("mobhealth.admin")) {
                    CommonMessages.NO_PERMISSION.send(sender);
                    return true;
                }
                plugin.getManager().removeAll();
                plugin.getMobHealthConfig().getMessenger().send(sender, "cleared");
            }
            default ->
                    TextUtil.send(
                            sender,
                            "<gold>Usage: <white>/" + label + " <gray>[reload|toggle|clear]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            if (sender.hasPermission("mobhealth.admin")) {
                if ("reload".startsWith(partial)) completions.add("reload");
                if ("clear".startsWith(partial)) completions.add("clear");
            }
            if (sender.hasPermission("mobhealth.toggle")) {
                if ("toggle".startsWith(partial)) completions.add("toggle");
            }
        }
        return completions;
    }
}
