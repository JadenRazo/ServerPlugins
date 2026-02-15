package net.serverplugins.admin.alts;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.MessageBuilder;
import net.serverplugins.api.messages.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class AltsCommand implements CommandExecutor, TabCompleter {

    private final ServerAdmin plugin;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm");

    public AltsCommand(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("serveradmin.alts")) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (plugin.getAltManager() == null || !plugin.getAltManager().isDatabaseAvailable()) {
            plugin.getAdminConfig()
                    .getMessenger()
                    .sendError(sender, "Alt detection is not enabled.");
            return true;
        }

        if (args.length < 1) {
            CommonMessages.INVALID_USAGE.send(sender, Placeholder.of("usage", "/alts <player>"));
            return true;
        }

        String targetName = args[0];

        // Use async API from AltManager
        plugin.getAltManager()
                .getAltsByName(targetName)
                .thenAccept(
                        alts -> {
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (alts.isEmpty()) {
                                                    MessageBuilder.create()
                                                            .prefix(
                                                                    plugin.getAdminConfig()
                                                                            .getMessenger()
                                                                            .getPrefix())
                                                            .info("No alt accounts found for ")
                                                            .highlight(targetName)
                                                            .send(sender);
                                                    return;
                                                }

                                                MessageBuilder.create()
                                                        .secondary(
                                                                "                                        ")
                                                        .send(sender);
                                                MessageBuilder.create()
                                                        .emphasis("Alt accounts for ")
                                                        .highlight(targetName)
                                                        .info(" (" + alts.size() + " found)")
                                                        .send(sender);
                                                sender.sendMessage("");

                                                for (AltAccount alt : alts) {
                                                    MessageBuilder builder =
                                                            MessageBuilder.create().text("  ");

                                                    // Status indicator
                                                    if (alt.isBanned()) {
                                                        builder.error("\u2716 "); // X mark
                                                    } else if (alt.isOnline()) {
                                                        builder.success("\u25CF "); // Filled circle
                                                    } else {
                                                        builder.info("\u25CB "); // Empty circle
                                                    }

                                                    // Name
                                                    if (alt.isBanned()) {
                                                        builder.error(alt.getName() + " (BANNED)");
                                                    } else if (alt.isOnline()) {
                                                        builder.success(alt.getName())
                                                                .info(" (online)");
                                                    } else {
                                                        builder.highlight(alt.getName());
                                                        if (alt.getLastSeen() > 0) {
                                                            builder.info(
                                                                    " ("
                                                                            + dateFormat.format(
                                                                                    new Date(
                                                                                            alt
                                                                                                    .getLastSeen()))
                                                                            + ")");
                                                        }
                                                    }

                                                    builder.send(sender);
                                                }

                                                MessageBuilder.create()
                                                        .secondary(
                                                                "                                        ")
                                                        .send(sender);
                                            });
                        })
                .exceptionally(
                        ex -> {
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () ->
                                                    plugin.getAdminConfig()
                                                            .getMessenger()
                                                            .sendError(
                                                                    sender,
                                                                    "Failed to fetch alt accounts: "
                                                                            + ex.getMessage()));
                            return null;
                        });

        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command cmd, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(partial)) {
                    completions.add(player.getName());
                }
            }
        }

        return completions;
    }
}
