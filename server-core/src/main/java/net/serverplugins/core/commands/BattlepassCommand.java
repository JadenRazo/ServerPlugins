package net.serverplugins.core.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.core.ServerCore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BattlepassCommand implements CommandExecutor, TabExecutor {

    private final ServerCore plugin;

    public BattlepassCommand(ServerCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        // Permission check
        if (!sender.hasPermission("servercore.admin")
                && !sender.hasPermission("servercore.battlepass.reset")) {
            TextUtil.send(sender, "<red>You don't have permission to use this command.");
            return true;
        }

        // Usage check
        if (args.length < 2 || !args[0].equalsIgnoreCase("reset")) {
            TextUtil.send(sender, "<red>Usage: /battlepass reset <player|*> [confirm]");
            return true;
        }

        String targetName = args[1];

        // Wildcard with confirmation
        if (targetName.equals("*")) {
            if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
                TextUtil.send(
                        sender,
                        "<yellow><bold>WARNING: This will reset ALL players' battlepass progress!");
                TextUtil.send(
                        sender,
                        "<yellow>Type <white>/battlepass reset * confirm <yellow>to proceed.");
                return true;
            }

            // Reset all players
            TextUtil.send(sender, "<yellow>Resetting battlepass for all players...");
            resetAllPlayers(sender);
            return true;
        }

        // Single player reset
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            TextUtil.send(sender, "<red>Player not found: " + targetName);
            return true;
        }

        resetPlayer(sender, target);
        return true;
    }

    private CompletableFuture<Void> resetPlayer(CommandSender sender, OfflinePlayer target) {
        LuckPerms api = LuckPermsProvider.get();

        return api.getUserManager()
                .loadUser(target.getUniqueId())
                .thenAcceptAsync(
                        user -> {
                            if (user == null) {
                                Bukkit.getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    TextUtil.send(
                                                            sender,
                                                            "<red>Failed to load user data for "
                                                                    + target.getName());
                                                });
                                return;
                            }

                            int removed = 0;

                            // Remove challenge.d.1-47
                            for (int i = 1; i <= 47; i++) {
                                if (user.data()
                                        .remove(Node.builder("challenge.d." + i).build())
                                        .wasSuccessful()) {
                                    removed++;
                                }
                            }

                            // Remove challenge.p.1-47
                            for (int i = 1; i <= 47; i++) {
                                if (user.data()
                                        .remove(Node.builder("challenge.p." + i).build())
                                        .wasSuccessful()) {
                                    removed++;
                                }
                            }

                            // Remove reward.t.1-4
                            for (int i = 1; i <= 4; i++) {
                                if (user.data()
                                        .remove(Node.builder("reward.t." + i).build())
                                        .wasSuccessful()) {
                                    removed++;
                                }
                            }

                            // Remove rift.take.1-26
                            for (int i = 1; i <= 26; i++) {
                                if (user.data()
                                        .remove(Node.builder("rift.take." + i).build())
                                        .wasSuccessful()) {
                                    removed++;
                                }
                            }

                            // Remove rift.ready.1-26
                            for (int i = 1; i <= 26; i++) {
                                if (user.data()
                                        .remove(Node.builder("rift.ready." + i).build())
                                        .wasSuccessful()) {
                                    removed++;
                                }
                            }

                            // Save changes
                            api.getUserManager().saveUser(user);

                            final int finalRemoved = removed;

                            // Execute bpreset command and send messages on main thread
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                Bukkit.dispatchCommand(
                                                        Bukkit.getConsoleSender(),
                                                        "bpreset " + target.getName());
                                                TextUtil.send(
                                                        sender,
                                                        "<green>Reset battlepass for <white>"
                                                                + target.getName()
                                                                + " <gray>("
                                                                + finalRemoved
                                                                + " permissions removed)");
                                            });
                        })
                .exceptionally(
                        throwable -> {
                            plugin.getLogger()
                                    .severe(
                                            "Error resetting battlepass for "
                                                    + target.getName()
                                                    + ": "
                                                    + throwable.getMessage());
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                TextUtil.send(
                                                        sender,
                                                        "<red>Error resetting battlepass for "
                                                                + target.getName());
                                            });
                            return null;
                        });
    }

    private void resetAllPlayers(CommandSender sender) {
        CompletableFuture.runAsync(
                () -> {
                    LuckPerms api = LuckPermsProvider.get();

                    // Get all unique users from LuckPerms (more efficient than
                    // Bukkit.getOfflinePlayers())
                    api.getUserManager()
                            .getUniqueUsers()
                            .thenAcceptAsync(
                                    uuids -> {
                                        List<CompletableFuture<Void>> futures = new ArrayList<>();

                                        for (java.util.UUID uuid : uuids) {
                                            @SuppressWarnings("deprecation")
                                            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                                            futures.add(resetPlayer(sender, player));
                                        }

                                        final int total = uuids.size();

                                        // Wait for all resets to complete
                                        CompletableFuture.allOf(
                                                        futures.toArray(new CompletableFuture[0]))
                                                .thenRunAsync(
                                                        () -> {
                                                            Bukkit.getScheduler()
                                                                    .runTask(
                                                                            plugin,
                                                                            () -> {
                                                                                TextUtil.send(
                                                                                        sender,
                                                                                        "<green>Battlepass reset completed for <white>"
                                                                                                + total
                                                                                                + " <green>players.");
                                                                            });
                                                        })
                                                .exceptionally(
                                                        throwable -> {
                                                            plugin.getLogger()
                                                                    .severe(
                                                                            "Error during mass battlepass reset: "
                                                                                    + throwable
                                                                                            .getMessage());
                                                            Bukkit.getScheduler()
                                                                    .runTask(
                                                                            plugin,
                                                                            () -> {
                                                                                TextUtil.send(
                                                                                        sender,
                                                                                        "<red>Error occurred during mass battlepass reset. Check console for details.");
                                                                            });
                                                            return null;
                                                        });
                                    })
                            .exceptionally(
                                    throwable -> {
                                        plugin.getLogger()
                                                .severe(
                                                        "Error fetching unique users: "
                                                                + throwable.getMessage());
                                        Bukkit.getScheduler()
                                                .runTask(
                                                        plugin,
                                                        () -> {
                                                            TextUtil.send(
                                                                    sender,
                                                                    "<red>Error fetching player list. Check console for details.");
                                                        });
                                        return null;
                                    });
                });
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reset");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("*");
            Bukkit.getOnlinePlayers().forEach(p -> suggestions.add(p.getName()));
            return suggestions;
        }

        if (args.length == 3 && args[1].equals("*")) {
            return Arrays.asList("confirm");
        }

        return new ArrayList<>();
    }
}
