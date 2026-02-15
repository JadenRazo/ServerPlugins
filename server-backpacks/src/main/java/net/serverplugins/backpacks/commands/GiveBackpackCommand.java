package net.serverplugins.backpacks.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.api.messages.PluginMessenger;
import net.serverplugins.backpacks.BackpacksConfig;
import net.serverplugins.backpacks.ServerBackpacks;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class GiveBackpackCommand implements CommandExecutor, TabCompleter {

    private final ServerBackpacks plugin;

    public GiveBackpackCommand(ServerBackpacks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("serverbackpacks.admin")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        PluginMessenger messenger = plugin.getBackpacksConfig().getMessenger();

        if (args.length < 2) {
            messenger.sendRaw(sender, ColorScheme.ERROR + "Usage: /givebackpack <player> <type>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            CommonMessages.PLAYER_NOT_FOUND.send(sender);
            return true;
        }

        BackpacksConfig.BackpackType type = plugin.getBackpacksConfig().getBackpackType(args[1]);
        if (type == null) {
            messenger.sendError(sender, "Unknown backpack type: " + args[1]);
            messenger.sendRaw(
                    sender,
                    ColorScheme.WARNING
                            + "Available types: "
                            + String.join(
                                    ", ", plugin.getBackpacksConfig().getBackpackTypes().keySet()));
            return true;
        }

        ItemStack backpack = plugin.getBackpackManager().createBackpack(type);
        target.getInventory().addItem(backpack);

        messenger.send(
                sender,
                "given",
                Placeholder.of("type", type.displayName()),
                Placeholder.of("player", target.getName()));

        messenger.send(target, "received", Placeholder.of("type", type.displayName()));

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return plugin.getBackpacksConfig().getBackpackTypes().keySet().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
