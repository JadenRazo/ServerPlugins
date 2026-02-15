package net.serverplugins.keys.commands;

import static net.serverplugins.api.messages.ColorScheme.EMPHASIS;
import static net.serverplugins.api.messages.ColorScheme.HIGHLIGHT;
import static net.serverplugins.api.messages.ColorScheme.INFO;
import static net.serverplugins.api.messages.ColorScheme.WARNING;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.keys.KeysConfig;
import net.serverplugins.keys.ServerKeys;
import net.serverplugins.keys.cache.StatsCache;
import net.serverplugins.keys.gui.KeyBalanceGui;
import net.serverplugins.keys.models.KeyStats;
import net.serverplugins.keys.models.KeyType;
import net.serverplugins.keys.repository.KeysRepository;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Command to view key statistics. Uses StatsCache for non-blocking GUI display. Usage: /keys
 * [player]
 */
public class KeysCommand implements CommandExecutor, TabCompleter {

    private final ServerKeys plugin;
    private final KeysConfig config;
    private final KeysRepository repository;
    private final StatsCache statsCache;

    public KeysCommand(
            ServerKeys plugin,
            KeysConfig config,
            KeysRepository repository,
            StatsCache statsCache) {
        this.plugin = plugin;
        this.config = config;
        this.repository = repository;
        this.statsCache = statsCache;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("serverkeys.keys")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        UUID targetUuid;
        String targetName;

        if (args.length >= 1) {
            // Viewing another player's stats
            if (!sender.hasPermission("serverkeys.keys.others")) {
                CommonMessages.NO_PERMISSION.send(sender);
                return true;
            }

            // Try online player first
            Player target = Bukkit.getPlayer(args[0]);
            if (target != null) {
                targetUuid = target.getUniqueId();
                targetName = target.getName();
            } else {
                // Try offline player
                @SuppressWarnings("deprecation")
                OfflinePlayer offline = Bukkit.getOfflinePlayer(args[0]);
                if (offline.hasPlayedBefore() || offline.isOnline()) {
                    targetUuid = offline.getUniqueId();
                    targetName = offline.getName() != null ? offline.getName() : args[0];
                } else {
                    CommonMessages.PLAYER_NOT_FOUND.send(sender);
                    return true;
                }
            }
        } else {
            // Viewing own stats
            if (!(sender instanceof Player player)) {
                CommonMessages.CONSOLE_ONLY.send(sender);
                return true;
            }
            targetUuid = player.getUniqueId();
            targetName = player.getName();
        }

        // Open GUI if sender is a player, otherwise show text (async for console)
        if (sender instanceof Player player) {
            // GUI uses cache - non-blocking
            KeyBalanceGui gui =
                    new KeyBalanceGui(plugin, config, statsCache, player, targetUuid, targetName);
            gui.open();
        } else {
            // Console: use async to fetch data
            showTextStatsAsync(sender, targetUuid, targetName);
        }

        return true;
    }

    /** Show text stats using async database query. Results are displayed when ready. */
    private void showTextStatsAsync(CommandSender sender, UUID targetUuid, String targetName) {
        repository
                .getPlayerStatsAsync(targetUuid)
                .thenAccept(
                        stats -> {
                            // Format and send on main thread to be safe
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                config.getMessenger()
                                                        .sendRaw(
                                                                sender,
                                                                EMPHASIS
                                                                        + "<bold>=== Key Stats: "
                                                                        + targetName
                                                                        + " ===</bold>");

                                                if (stats.isEmpty()) {
                                                    config.getMessenger()
                                                            .sendRaw(
                                                                    sender,
                                                                    INFO + "No key history found.");
                                                    return;
                                                }

                                                int totalReceived = 0;
                                                int totalUsed = 0;

                                                config.getMessenger().sendRaw(sender, "");
                                                config.getMessenger()
                                                        .sendRaw(sender, EMPHASIS + "Crate Keys:");
                                                for (KeyStats stat : stats) {
                                                    if (stat.getKeyType() == KeyType.CRATE) {
                                                        config.getMessenger()
                                                                .sendRaw(
                                                                        sender,
                                                                        WARNING
                                                                                + "- "
                                                                                + config
                                                                                        .getKeyDisplay(
                                                                                                stat
                                                                                                        .getKeyType(),
                                                                                                stat
                                                                                                        .getKeyName())
                                                                                + ": "
                                                                                + HIGHLIGHT
                                                                                + stat
                                                                                        .getTotalReceived()
                                                                                + " received, "
                                                                                + stat
                                                                                        .getTotalUsed()
                                                                                + " used");
                                                        totalReceived += stat.getTotalReceived();
                                                        totalUsed += stat.getTotalUsed();
                                                    }
                                                }

                                                config.getMessenger().sendRaw(sender, "");
                                                config.getMessenger()
                                                        .sendRaw(
                                                                sender, EMPHASIS + "Dungeon Keys:");
                                                for (KeyStats stat : stats) {
                                                    if (stat.getKeyType() == KeyType.DUNGEON) {
                                                        config.getMessenger()
                                                                .sendRaw(
                                                                        sender,
                                                                        WARNING
                                                                                + "- "
                                                                                + config
                                                                                        .getKeyDisplay(
                                                                                                stat
                                                                                                        .getKeyType(),
                                                                                                stat
                                                                                                        .getKeyName())
                                                                                + ": "
                                                                                + HIGHLIGHT
                                                                                + stat
                                                                                        .getTotalReceived()
                                                                                + " received, "
                                                                                + stat
                                                                                        .getTotalUsed()
                                                                                + " used");
                                                        totalReceived += stat.getTotalReceived();
                                                        totalUsed += stat.getTotalUsed();
                                                    }
                                                }

                                                config.getMessenger().sendRaw(sender, "");
                                                config.getMessenger()
                                                        .sendRaw(
                                                                sender,
                                                                EMPHASIS
                                                                        + "Total: "
                                                                        + HIGHLIGHT
                                                                        + totalReceived
                                                                        + " received, "
                                                                        + totalUsed
                                                                        + " used");
                                            });
                        });
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1 && sender.hasPermission("serverkeys.keys.others")) {
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        }

        String current = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(current)).toList();
    }
}
