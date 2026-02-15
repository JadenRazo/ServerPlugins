package net.serverplugins.keys.commands;

import static net.serverplugins.api.messages.ColorScheme.EMPHASIS;
import static net.serverplugins.api.messages.ColorScheme.HIGHLIGHT;
import static net.serverplugins.api.messages.ColorScheme.INFO;
import static net.serverplugins.api.messages.ColorScheme.WARNING;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.Placeholder;
import net.serverplugins.keys.KeysConfig;
import net.serverplugins.keys.ServerKeys;
import net.serverplugins.keys.managers.KeyManager;
import net.serverplugins.keys.models.KeyTransaction;
import net.serverplugins.keys.models.KeyType;
import net.serverplugins.keys.repository.KeysRepository;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/**
 * Admin command for key management. All database reads are async to avoid blocking the main thread.
 * Usage: /keyadmin <give|history|reload> [args...]
 */
public class KeyAdminCommand implements CommandExecutor, TabCompleter {

    private final ServerKeys plugin;
    private final KeysConfig config;
    private final KeyManager keyManager;
    private final KeysRepository repository;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd HH:mm");

    public KeyAdminCommand(
            ServerKeys plugin,
            KeysConfig config,
            KeyManager keyManager,
            KeysRepository repository) {
        this.plugin = plugin;
        this.config = config;
        this.keyManager = keyManager;
        this.repository = repository;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("serverkeys.admin")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        String subcommand = args[0].toLowerCase();

        return switch (subcommand) {
            case "give" -> handleGive(sender, args);
            case "history" -> handleHistory(sender, args);
            case "reload" -> handleReload(sender);
            default -> {
                sendUsage(sender);
                yield true;
            }
        };
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("serverkeys.admin.give")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        // /keyadmin give <player> <type> <key> [amount]
        if (args.length < 4) {
            config.getMessenger()
                    .sendError(
                            sender,
                            "Usage: /keyadmin give <player> <crate|dungeon> <keyname> [amount]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            CommonMessages.PLAYER_NOT_FOUND.send(sender);
            return true;
        }

        KeyType type = KeyType.fromString(args[2]);
        if (type == null) {
            config.getMessenger().send(sender, "invalid-key-type");
            return true;
        }

        String keyName = args[3].toLowerCase();
        if (!config.isValidKey(type, keyName)) {
            config.getMessenger().send(sender, "invalid-key-name", Placeholder.of("key", keyName));
            return true;
        }

        int amount = 1;
        if (args.length >= 5) {
            try {
                amount = Integer.parseInt(args[4]);
                if (amount < 1) amount = 1;
                if (amount > 64) amount = 64;
            } catch (NumberFormatException e) {
                config.getMessenger()
                        .sendError(sender, "Amount must be a number between 1 and 64!");
                return true;
            }
        }

        boolean success =
                keyManager.giveKey(target, type, keyName, amount, "ADMIN:" + sender.getName());

        if (success) {
            config.getMessenger()
                    .send(
                            sender,
                            "key-given",
                            Placeholder.of("amount", String.valueOf(amount)),
                            Placeholder.of("key", config.getKeyDisplay(type, keyName)),
                            Placeholder.of("player", target.getName()));
        } else {
            config.getMessenger()
                    .sendError(sender, "Failed to give key. Check console for errors.");
        }

        return true;
    }

    private boolean handleHistory(CommandSender sender, String[] args) {
        if (!sender.hasPermission("serverkeys.admin.history")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length >= 2) {
            // Player-specific history
            Player target = Bukkit.getPlayer(args[1]);
            UUID targetUuid;
            String targetName;

            if (target != null) {
                targetUuid = target.getUniqueId();
                targetName = target.getName();
            } else {
                @SuppressWarnings("deprecation")
                var offline = Bukkit.getOfflinePlayer(args[1]);
                if (!offline.hasPlayedBefore()) {
                    CommonMessages.PLAYER_NOT_FOUND.send(sender);
                    return true;
                }
                targetUuid = offline.getUniqueId();
                targetName = offline.getName() != null ? offline.getName() : args[1];
            }

            // Async fetch player history
            repository
                    .getPlayerHistoryAsync(targetUuid, 20)
                    .thenAccept(
                            history -> {
                                displayHistory(sender, history, targetName);
                            });
        } else {
            // Recent server-wide history - async fetch
            repository
                    .getRecentHistoryAsync(20)
                    .thenAccept(
                            history -> {
                                displayHistory(sender, history, "Recent");
                            });
        }

        return true;
    }

    /** Display history results on main thread. */
    private void displayHistory(CommandSender sender, List<KeyTransaction> history, String title) {
        Bukkit.getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            config.getMessenger()
                                    .sendRaw(
                                            sender,
                                            EMPHASIS
                                                    + "<bold>=== Key History: "
                                                    + title
                                                    + " ===</bold>");

                            if (history.isEmpty()) {
                                config.getMessenger().sendRaw(sender, INFO + "No history found.");
                                return;
                            }

                            for (KeyTransaction tx : history) {
                                String date = dateFormat.format(tx.getCreatedAt());
                                String keyDisplay =
                                        config.getKeyDisplay(tx.getKeyType(), tx.getKeyName());
                                String source =
                                        tx.getSource() != null
                                                ? " " + INFO + "(" + tx.getSource() + ")"
                                                : "";

                                config.getMessenger()
                                        .sendRaw(
                                                sender,
                                                INFO
                                                        + "["
                                                        + date
                                                        + "] "
                                                        + HIGHLIGHT
                                                        + tx.getUsername()
                                                        + " "
                                                        + WARNING
                                                        + tx.getAction()
                                                        + " "
                                                        + HIGHLIGHT
                                                        + tx.getAmount()
                                                        + "x "
                                                        + keyDisplay
                                                        + source);
                            }
                        });
    }

    private boolean handleReload(CommandSender sender) {
        plugin.reload();
        config.getMessenger().send(sender, "reload-success");
        return true;
    }

    private void sendUsage(CommandSender sender) {
        config.getMessenger().sendRaw(sender, EMPHASIS + "<bold>=== Key Admin Commands ===</bold>");
        config.getMessenger()
                .sendRaw(
                        sender,
                        WARNING
                                + "/keyadmin give <player> <type> <key> [amount] "
                                + INFO
                                + "- Give keys");
        config.getMessenger()
                .sendRaw(
                        sender,
                        WARNING + "/keyadmin history [player] " + INFO + "- View key history");
        config.getMessenger()
                .sendRaw(sender, WARNING + "/keyadmin reload " + INFO + "- Reload configuration");
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(List.of("give", "history", "reload"));
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("history")) {
                Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            completions.add("crate");
            completions.add("dungeon");
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            KeyType type = KeyType.fromString(args[2]);
            if (type != null) {
                completions.addAll(config.getKeys(type));
            }
        } else if (args.length == 5 && args[0].equalsIgnoreCase("give")) {
            completions.addAll(List.of("1", "5", "10", "25", "64"));
        }

        String current = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(current)).toList();
    }
}
