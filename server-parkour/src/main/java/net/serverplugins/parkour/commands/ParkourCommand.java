package net.serverplugins.parkour.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.MessageBuilder;
import net.serverplugins.api.messages.PluginMessenger;
import net.serverplugins.parkour.ServerParkour;
import net.serverplugins.parkour.data.ParkourDatabase;
import net.serverplugins.parkour.gui.ParkourGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class ParkourCommand implements CommandExecutor, TabCompleter {

    private final ServerParkour plugin;

    public ParkourCommand(ServerParkour plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (args.length == 0) {
            // Default: open GUI if not playing, show help if playing
            if (!plugin.getParkourManager().isPlaying(player)) {
                openGui(player);
            } else {
                showHelp(player);
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "gui", "menu" -> openGui(player);
            case "start", "play" -> startGame(player);
            case "stop", "quit", "leave" -> stopGame(player);
            case "stats" -> showStats(player);
            case "top", "leaderboard", "lb" -> showLeaderboard(player);
            case "help" -> showHelp(player);
            default -> showHelp(player);
        }

        return true;
    }

    private void openGui(Player player) {
        PluginMessenger messenger = plugin.getParkourConfig().getMessenger();
        if (plugin.getParkourManager().isPlaying(player)) {
            messenger.send(player, "already-playing");
            return;
        }
        new ParkourGui(plugin).open(player);
    }

    private void startGame(Player player) {
        PluginMessenger messenger = plugin.getParkourConfig().getMessenger();
        if (plugin.getParkourManager().isPlaying(player)) {
            messenger.send(player, "already-playing");
            return;
        }

        if (plugin.getParkourManager().startGame(player)) {
            // Game started successfully (message sent in session)
        }
    }

    private void stopGame(Player player) {
        PluginMessenger messenger = plugin.getParkourConfig().getMessenger();
        if (!plugin.getParkourManager().isPlaying(player)) {
            messenger.send(player, "not-playing");
            return;
        }

        plugin.getParkourManager().endGame(player);
    }

    private void showStats(Player player) {
        if (plugin.getDatabase() == null) {
            CommonMessages.DATABASE_ERROR.send(player);
            return;
        }

        int highscore = plugin.getDatabase().getHighscore(player.getUniqueId());
        int xp = plugin.getDatabase().getXp(player.getUniqueId());
        int level = plugin.getDatabase().getLevel(player.getUniqueId());
        int xpForNextLevel = ParkourDatabase.getXpForLevel(level + 1);
        int rank = plugin.getDatabase().getCachedRank(player.getUniqueId());
        String rankDisplay = rank > 0 ? "#" + rank : "Unranked";

        MessageBuilder.create()
                .newLine()
                .prefix("<light_purple><bold>Parkour Stats</bold></light_purple>")
                .newLine()
                .arrow()
                .info("Your Highscore: ")
                .emphasis(highscore + " blocks")
                .newLine()
                .arrow()
                .info("Leaderboard Rank: ")
                .emphasis(rankDisplay)
                .newLine()
                .arrow()
                .info("Lobby Level: ")
                .success(String.valueOf(level))
                .newLine()
                .arrow()
                .info("Lobby XP: ")
                .emphasis(xp + "/" + xpForNextLevel)
                .newLine()
                .send(player);
    }

    private void showLeaderboard(Player player) {
        if (plugin.getDatabase() == null) {
            CommonMessages.DATABASE_ERROR.send(player);
            return;
        }

        MessageBuilder.create()
                .newLine()
                .text("<light_purple><bold>Parkour Leaderboard</bold></light_purple>")
                .newLine()
                .info("Loading...")
                .send(player);

        plugin.getDatabase()
                .getTopScores(10)
                .thenAccept(
                        entries -> {
                            plugin.getServer()
                                    .getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                MessageBuilder builder =
                                                        MessageBuilder.create()
                                                                .newLine()
                                                                .text(
                                                                        "<light_purple><bold>Parkour Leaderboard</bold></light_purple> <gray>(Top 10)</gray>")
                                                                .newLine();

                                                if (entries.isEmpty()) {
                                                    builder.info("No scores yet. Be the first!");
                                                } else {
                                                    int rank = 1;
                                                    for (ParkourDatabase.LeaderboardEntry entry :
                                                            entries) {
                                                        String rankColor =
                                                                switch (rank) {
                                                                    case 1 -> ColorScheme.EMPHASIS;
                                                                    case 2 -> ColorScheme.HIGHLIGHT;
                                                                    case 3 -> ColorScheme.ERROR;
                                                                    default -> ColorScheme.INFO;
                                                                };
                                                        builder.newLine()
                                                                .colored("#" + rank, rankColor)
                                                                .space()
                                                                .highlight(entry.username())
                                                                .space()
                                                                .secondary("- ")
                                                                .emphasis(
                                                                        entry.score() + " blocks");
                                                        rank++;
                                                    }
                                                }
                                                builder.newLine().send(player);
                                            });
                        });
    }

    private void showHelp(Player player) {
        MessageBuilder.create()
                .newLine()
                .text("<light_purple><bold>Parkour Commands</bold></light_purple>")
                .newLine()
                .command("/parkour")
                .space()
                .info("- Open parkour menu")
                .newLine()
                .command("/parkour start")
                .space()
                .info("- Start a parkour game")
                .newLine()
                .command("/parkour stop")
                .space()
                .info("- Stop current game")
                .newLine()
                .command("/parkour stats")
                .space()
                .info("- View your stats")
                .newLine()
                .command("/parkour top")
                .space()
                .info("- View leaderboard")
                .newLine()
                .send(player);
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions =
                    new ArrayList<>(Arrays.asList("gui", "start", "stop", "stats", "top", "help"));
            String input = args[0].toLowerCase();
            completions.removeIf(s -> !s.startsWith(input));
            return completions;
        }
        return new ArrayList<>();
    }
}
