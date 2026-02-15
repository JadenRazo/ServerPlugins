package net.serverplugins.keys.commands;

import static net.serverplugins.api.messages.ColorScheme.EMPHASIS;
import static net.serverplugins.api.messages.ColorScheme.HIGHLIGHT;
import static net.serverplugins.api.messages.ColorScheme.INFO;
import static net.serverplugins.api.messages.ColorScheme.WARNING;

import java.util.ArrayList;
import java.util.List;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.keys.KeysConfig;
import net.serverplugins.keys.ServerKeys;
import net.serverplugins.keys.managers.KeyManager;
import net.serverplugins.keys.models.KeyType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** Command to give keys to all online players. Usage: /keyall <crate|dungeon> <keyname> [amount] */
public class KeyAllCommand implements CommandExecutor, TabCompleter {

    private final ServerKeys plugin;
    private final KeysConfig config;
    private final KeyManager keyManager;

    public KeyAllCommand(ServerKeys plugin, KeysConfig config, KeyManager keyManager) {
        this.plugin = plugin;
        this.config = config;
        this.keyManager = keyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("serverkeys.keyall")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        // Parse key type
        KeyType type = KeyType.fromString(args[0]);
        if (type == null) {
            config.getMessenger().sendError(sender, "Invalid key type. Use 'crate' or 'dungeon'.");
            sendUsage(sender);
            return true;
        }

        // Parse key name
        String keyName = args[1].toLowerCase();
        if (!config.isValidKey(type, keyName)) {
            config.getMessenger().sendError(sender, "Invalid key: " + keyName);
            config.getMessenger()
                    .sendInfo(sender, "Available: " + keyManager.getAvailableKeysFormatted(type));
            return true;
        }

        // Parse amount
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 1) amount = 1;
                if (amount > 64) amount = 64;
            } catch (NumberFormatException e) {
                config.getMessenger()
                        .sendError(sender, "Amount must be a number between 1 and 64!");
                return true;
            }
        }

        // Execute distribution
        int count =
                keyManager.giveToAllWithBroadcast(
                        type, keyName, amount, "ADMIN:" + sender.getName());

        if (count == 0) {
            config.getMessenger().sendWarning(sender, "No players online to receive keys.");
        } else {
            plugin.getLogger()
                    .info(
                            sender.getName()
                                    + " distributed "
                                    + amount
                                    + "x "
                                    + keyName
                                    + " "
                                    + type.name().toLowerCase()
                                    + " keys to "
                                    + count
                                    + " players");
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        config.getMessenger().sendRaw(sender, EMPHASIS + "<bold>=== Key All Command ===</bold>");
        config.getMessenger()
                .sendRaw(
                        sender,
                        WARNING
                                + "Usage: "
                                + HIGHLIGHT
                                + "/keyall <crate|dungeon> <keyname> [amount]");
        config.getMessenger().sendRaw(sender, "");
        config.getMessenger().sendRaw(sender, EMPHASIS + "Crate Keys:");
        for (String key : config.getCrateKeys()) {
            config.getMessenger()
                    .sendRaw(
                            sender,
                            WARNING
                                    + "- "
                                    + key
                                    + " "
                                    + INFO
                                    + "- "
                                    + config.getKeyDescription(KeyType.CRATE, key));
        }
        config.getMessenger().sendRaw(sender, "");
        config.getMessenger().sendRaw(sender, EMPHASIS + "Dungeon Keys:");
        for (String key : config.getDungeonKeys()) {
            config.getMessenger()
                    .sendRaw(
                            sender,
                            WARNING
                                    + "- "
                                    + key
                                    + " "
                                    + INFO
                                    + "- "
                                    + config.getKeyDescription(KeyType.DUNGEON, key));
        }
        config.getMessenger().sendRaw(sender, "");
        config.getMessenger().sendRaw(sender, EMPHASIS + "Examples:");
        config.getMessenger().sendRaw(sender, HIGHLIGHT + "/keyall crate daily");
        config.getMessenger().sendRaw(sender, HIGHLIGHT + "/keyall dungeon easy 2");
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("crate");
            completions.add("dungeon");
        } else if (args.length == 2) {
            KeyType type = KeyType.fromString(args[0]);
            if (type != null) {
                completions.addAll(config.getKeys(type));
            }
        } else if (args.length == 3) {
            completions.addAll(List.of("1", "5", "10", "25", "64"));
        }

        String current = args[args.length - 1].toLowerCase();
        return completions.stream().filter(s -> s.toLowerCase().startsWith(current)).toList();
    }
}
