package net.serverplugins.backpacks.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.PluginMessenger;
import net.serverplugins.backpacks.ServerBackpacks;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class BackpackCommand implements CommandExecutor, TabCompleter {

    private final ServerBackpacks plugin;

    public BackpackCommand(ServerBackpacks plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PluginMessenger messenger = plugin.getBackpacksConfig().getMessenger();

        // Handle admin cache commands
        if (args.length > 0 && args[0].equalsIgnoreCase("cache")) {
            if (!sender.hasPermission("serverbackpacks.admin")) {
                CommonMessages.NO_PERMISSION.send(sender);
                return true;
            }

            if (args.length > 1 && args[1].equalsIgnoreCase("clear")) {
                plugin.getBackpackManager().clearCache();
                messenger.sendSuccess(sender, "Backpack inventory cache cleared!");
                return true;
            }

            if (args.length > 1 && args[1].equalsIgnoreCase("stats")) {
                var stats = plugin.getBackpackManager().getCacheStats();
                messenger.sendRaw(sender, "<gold><bold>Backpack Cache Statistics:");
                messenger.sendRaw(
                        sender,
                        ColorScheme.INFO
                                + "Enabled: "
                                + ColorScheme.HIGHLIGHT
                                + stats.get("enabled"));
                messenger.sendRaw(
                        sender,
                        ColorScheme.INFO
                                + "Cached Items: "
                                + ColorScheme.HIGHLIGHT
                                + stats.get("cached_items"));
                return true;
            }

            messenger.sendRaw(sender, ColorScheme.WARNING + "Usage: /backpack cache <clear|stats>");
            return true;
        }

        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("serverbackpacks.use")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();

        if (plugin.getBackpackManager().isBackpack(mainHand)) {
            int slot = player.getInventory().getHeldItemSlot();
            plugin.getBackpackManager().openBackpack(player, mainHand, slot);
            return true;
        }

        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && plugin.getBackpackManager().isBackpack(item)) {
                if (args.length > 0) {
                    String type = plugin.getBackpackManager().getBackpackType(item);
                    if (!type.equalsIgnoreCase(args[0])) continue;
                }

                plugin.getBackpackManager().openBackpack(player, item, i);
                return true;
            }
        }

        messenger.sendError(player, "You don't have a backpack in your inventory!");
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();

            // Add cache command for admins
            if (sender.hasPermission("serverbackpacks.admin")) {
                completions.add("cache");
            }

            // Add backpack types
            completions.addAll(plugin.getBackpacksConfig().getBackpackTypes().keySet());

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("cache")) {
            if (sender.hasPermission("serverbackpacks.admin")) {
                List<String> subCommands = new ArrayList<>();
                subCommands.add("clear");
                subCommands.add("stats");
                return subCommands.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}
