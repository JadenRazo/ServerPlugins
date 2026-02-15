package net.serverplugins.core.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.core.ServerCore;
import net.serverplugins.core.features.Feature;
import net.serverplugins.core.features.HammerPickaxeFeature;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class HammerPickaxeCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "servercore.hammerpick.give";
    private final ServerCore plugin;

    public HammerPickaxeCommand(ServerCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean isConsole = sender instanceof ConsoleCommandSender;
        if (!isConsole && !sender.hasPermission(PERMISSION)) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("give")) {
            TextUtil.sendError(sender, "Usage: /hammerpick give <player> [tier]");
            TextUtil.send(sender, ColorScheme.INFO + "Example: /hammerpick give Steve 2");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            plugin.getCoreConfig().getMessenger().sendError(sender, "Player not found: " + args[1]);
            return true;
        }

        int tier = 1;
        if (args.length >= 3) {
            try {
                tier = Integer.parseInt(args[2]);
                if (tier < 1) {
                    plugin.getCoreConfig()
                            .getMessenger()
                            .sendError(sender, "Tier must be at least 1");
                    return true;
                }
            } catch (NumberFormatException e) {
                plugin.getCoreConfig()
                        .getMessenger()
                        .sendError(sender, "Tier must be a number: " + args[2]);
                return true;
            }
        }

        // Validate tier exists in config
        ConfigurationSection tiersSection =
                plugin.getConfig().getConfigurationSection("settings.hammer-pickaxe.tiers");
        if (tiersSection == null) {
            plugin.getCoreConfig()
                    .getMessenger()
                    .sendError(sender, "No hammer pickaxe tiers configured!");
            return true;
        }

        if (!tiersSection.contains(String.valueOf(tier))) {
            plugin.getCoreConfig()
                    .getMessenger()
                    .sendError(
                            sender,
                            "Invalid tier: "
                                    + tier
                                    + ". Available: "
                                    + tiersSection.getKeys(false));
            return true;
        }

        Feature feature = plugin.getFeatures().get("hammer-pickaxe");
        if (!(feature instanceof HammerPickaxeFeature hammerFeature)) {
            plugin.getCoreConfig()
                    .getMessenger()
                    .sendError(sender, "Hammer pickaxe feature is not available.");
            return true;
        }

        ItemStack hammerPick = hammerFeature.createHammerPickaxe(tier);
        Map<Integer, ItemStack> overflow = target.getInventory().addItem(hammerPick);
        if (!overflow.isEmpty()) {
            overflow.values()
                    .forEach(
                            item ->
                                    target.getWorld()
                                            .dropItemNaturally(target.getLocation(), item));
            TextUtil.sendWarning(sender, "Inventory full, item was dropped on the ground.");
        }

        Bukkit.getLogger()
                .info(
                        "[HammerPickaxe] Gave Tier "
                                + tier
                                + " Hammer Pickaxe to "
                                + target.getName()
                                + " (by "
                                + sender.getName()
                                + ")");

        if (sender instanceof Player && !sender.equals(target)) {
            TextUtil.sendSuccess(target, "You received a Tier " + tier + " Hammer Pickaxe!");
            TextUtil.sendSuccess(
                    sender, "Gave " + target.getName() + " a Tier " + tier + " Hammer Pickaxe.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("give");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            ConfigurationSection tiersSection =
                    plugin.getConfig().getConfigurationSection("settings.hammer-pickaxe.tiers");
            if (tiersSection != null) {
                completions.addAll(tiersSection.getKeys(false));
            }
        }

        String current = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(current)).toList();
    }
}
