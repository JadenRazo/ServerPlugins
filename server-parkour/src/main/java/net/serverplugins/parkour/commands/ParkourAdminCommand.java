package net.serverplugins.parkour.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.MessageBuilder;
import net.serverplugins.api.messages.PluginMessenger;
import net.serverplugins.parkour.ServerParkour;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class ParkourAdminCommand implements CommandExecutor, TabCompleter {

    private final ServerParkour plugin;

    public ParkourAdminCommand(ServerParkour plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("serverparkour.admin")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload" -> reloadConfig(sender);
            case "setnpc" -> setNpcLocation(sender);
            case "removenpc" -> removeNpc(sender);
            case "createhologram" -> createHologram(sender);
            case "removeall" -> removeAllGames(sender);
            case "sethighscore" -> setHighscore(sender, args);
            case "help" -> showHelp(sender);
            default -> showHelp(sender);
        }

        return true;
    }

    private void reloadConfig(CommandSender sender) {
        plugin.reloadConfiguration();
        PluginMessenger messenger = plugin.getParkourConfig().getMessenger();
        messenger.sendSuccess(sender, "Parkour configuration reloaded!");
    }

    private void setNpcLocation(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return;
        }

        if (plugin.getNpcManager() == null) {
            plugin.getParkourConfig()
                    .getMessenger()
                    .sendError(sender, "FancyNpcs is not available.");
            return;
        }

        // Update NPC location to player's current location
        plugin.getNpcManager().setLocation(player.getLocation());
        plugin.getParkourConfig()
                .getMessenger()
                .sendSuccess(sender, "NPC location updated to your current location!");

        // Also update hologram if available
        if (plugin.getHologramManager() != null) {
            plugin.getHologramManager().updateHologram();
        }
    }

    private void removeNpc(CommandSender sender) {
        if (plugin.getNpcManager() == null) {
            plugin.getParkourConfig()
                    .getMessenger()
                    .sendError(sender, "FancyNpcs is not available.");
            return;
        }

        plugin.getNpcManager().removeNpc();
        plugin.getParkourConfig().getMessenger().sendSuccess(sender, "NPC removed!");
    }

    private void createHologram(CommandSender sender) {
        if (plugin.getHologramManager() == null) {
            plugin.getParkourConfig()
                    .getMessenger()
                    .sendError(sender, "DecentHolograms is not available.");
            return;
        }

        plugin.getHologramManager().createHologram();
        plugin.getParkourConfig().getMessenger().sendSuccess(sender, "Hologram created/updated!");
    }

    private void removeAllGames(CommandSender sender) {
        int count = plugin.getParkourManager().getActivePlayerCount();
        plugin.getParkourManager().endAllGames();
        plugin.getParkourConfig()
                .getMessenger()
                .sendSuccess(sender, "Ended " + count + " active parkour games.");
    }

    private void setHighscore(CommandSender sender, String[] args) {
        PluginMessenger messenger = plugin.getParkourConfig().getMessenger();
        if (args.length < 3) {
            messenger.sendError(sender, "Usage: /pka sethighscore <player> <score>");
            return;
        }

        String playerName = args[1];
        int score;
        try {
            score = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            messenger.sendError(sender, "Invalid score: " + args[2]);
            return;
        }

        if (plugin.getDatabase() == null) {
            CommonMessages.DATABASE_ERROR.send(sender);
            return;
        }

        // Get player UUID
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        UUID uuid = target.getUniqueId();

        messenger.sendInfo(sender, "Setting highscore for " + playerName + " to " + score + "...");
        plugin.getLogger().info("Setting highscore: " + playerName + " -> " + score);

        plugin.getDatabase()
                .setHighscore(uuid, playerName, score)
                .thenAccept(
                        success -> {
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (success) {
                                                    messenger.sendSuccess(
                                                            sender,
                                                            "Set "
                                                                    + playerName
                                                                    + "'s highscore to "
                                                                    + score);
                                                    plugin.getLogger()
                                                            .info(
                                                                    "Successfully set highscore for "
                                                                            + playerName
                                                                            + " to "
                                                                            + score);
                                                } else {
                                                    messenger.sendError(
                                                            sender, "Failed to set highscore.");
                                                    plugin.getLogger()
                                                            .warning(
                                                                    "Failed to set highscore for "
                                                                            + playerName);
                                                }
                                            });
                        });
    }

    private void showHelp(CommandSender sender) {
        MessageBuilder.create()
                .newLine()
                .text("<light_purple><bold>Parkour Admin Commands</bold></light_purple>")
                .newLine()
                .command("/pka reload")
                .space()
                .info("- Reload configuration")
                .newLine()
                .command("/pka setnpc")
                .space()
                .info("- Set NPC to your location")
                .newLine()
                .command("/pka removenpc")
                .space()
                .info("- Remove the NPC")
                .newLine()
                .command("/pka createhologram")
                .space()
                .info("- Create/update hologram")
                .newLine()
                .command("/pka removeall")
                .space()
                .info("- End all active games")
                .newLine()
                .command("/pka sethighscore <player> <score>")
                .space()
                .info("- Set player highscore")
                .newLine()
                .send(sender);
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("serverparkour.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> completions =
                    new ArrayList<>(
                            Arrays.asList(
                                    "reload",
                                    "setnpc",
                                    "removenpc",
                                    "createhologram",
                                    "removeall",
                                    "sethighscore",
                                    "help"));
            String input = args[0].toLowerCase();
            completions.removeIf(s -> !s.startsWith(input));
            return completions;
        }
        return new ArrayList<>();
    }
}
