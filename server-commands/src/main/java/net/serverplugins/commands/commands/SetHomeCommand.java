package net.serverplugins.commands.commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.data.PlayerDataManager;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class SetHomeCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    // Common icon materials for homes
    private static final List<String> COMMON_ICONS =
            Arrays.asList(
                    "RED_BED",
                    "WHITE_BED",
                    "BLUE_BED",
                    "GREEN_BED",
                    "YELLOW_BED",
                    "OAK_DOOR",
                    "SPRUCE_DOOR",
                    "BIRCH_DOOR",
                    "JUNGLE_DOOR",
                    "CHEST",
                    "ENDER_CHEST",
                    "BARREL",
                    "CAMPFIRE",
                    "LANTERN",
                    "TORCH",
                    "GRASS_BLOCK",
                    "COBBLESTONE",
                    "STONE_BRICKS",
                    "DIAMOND_BLOCK",
                    "GOLD_BLOCK",
                    "IRON_BLOCK",
                    "BEACON",
                    "LODESTONE",
                    "COMPASS",
                    "NETHERRACK",
                    "END_STONE",
                    "WARPED_STEM");

    public SetHomeCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getCommandsConfig().getMessenger().send(sender, "players-only");
            return true;
        }

        if (!player.hasPermission("servercommands.sethome")) {
            plugin.getCommandsConfig().getMessenger().send(player, "no-permission");
            return true;
        }

        String homeName = args.length > 0 ? args[0].toLowerCase() : "home";

        if (!homeName.matches("^[a-zA-Z0-9_-]+$")) {
            plugin.getCommandsConfig().getMessenger().send(player, "home-invalid-name");
            return true;
        }

        if (homeName.length() > 16) {
            plugin.getCommandsConfig().getMessenger().send(player, "home-name-too-long");
            return true;
        }

        // Parse optional icon
        Material icon = Material.RED_BED;
        if (args.length > 1) {
            String iconArg = args[1].toUpperCase();
            try {
                Material parsed = Material.valueOf(iconArg);
                if (parsed.isItem()) {
                    icon = parsed;
                } else {
                    plugin.getCommandsConfig().getMessenger().send(player, "home-invalid-icon");
                }
            } catch (IllegalArgumentException e) {
                plugin.getCommandsConfig()
                        .getMessenger()
                        .send(
                                player,
                                "home-invalid-material",
                                net.serverplugins.api.messages.Placeholder.of("material", args[1]));
            }
        }

        PlayerDataManager.PlayerData data =
                plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());

        int maxHomes = getMaxHomes(player);
        boolean isUpdate = data.hasHome(homeName);

        if (!isUpdate && data.getHomeCount() >= maxHomes) {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .send(
                            player,
                            "home-max-reached",
                            net.serverplugins.api.messages.Placeholder.of(
                                    "max", String.valueOf(maxHomes)));
            return true;
        }

        // Use new method with icon support
        data.setHome(homeName, player.getLocation(), icon, null);
        plugin.getPlayerDataManager().savePlayerData(player.getUniqueId());

        if (isUpdate) {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .send(
                            player,
                            "home-updated",
                            net.serverplugins.api.messages.Placeholder.of("home", homeName));
        } else {
            plugin.getCommandsConfig()
                    .getMessenger()
                    .send(
                            player,
                            "home-set",
                            net.serverplugins.api.messages.Placeholder.of("home", homeName),
                            net.serverplugins.api.messages.Placeholder.of(
                                    "count", String.valueOf(data.getHomeCount())),
                            net.serverplugins.api.messages.Placeholder.of(
                                    "max", String.valueOf(maxHomes)));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();

        if (args.length == 2) {
            // Suggest common icon materials
            String input = args[1].toUpperCase();
            return COMMON_ICONS.stream()
                    .filter(mat -> mat.startsWith(input))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    private int getMaxHomes(Player player) {
        if (player.hasPermission("servercommands.homes.unlimited")) return Integer.MAX_VALUE;

        for (int i = 100; i >= 1; i--) {
            if (player.hasPermission("servercommands.homes." + i)) {
                return i;
            }
        }

        return plugin.getConfig().getInt("homes.default-max", 3);
    }
}
