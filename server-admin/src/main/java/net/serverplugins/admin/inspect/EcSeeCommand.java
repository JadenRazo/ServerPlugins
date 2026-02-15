package net.serverplugins.admin.inspect;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.messages.MessageBuilder;
import net.serverplugins.api.messages.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class EcSeeCommand implements CommandExecutor, TabCompleter {

    private final ServerAdmin plugin;

    public EcSeeCommand(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("serveradmin.invsee")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        if (args.length < 1) {
            CommonMessages.INVALID_USAGE.send(player, Placeholder.of("usage", "/ecsee <player>"));
            return true;
        }

        // Try to find online player first
        Player target = Bukkit.getPlayer(args[0]);

        if (target != null) {
            openOnlineEnderChest(player, target);
        } else {
            // Check if offline inspection is supported
            if (!plugin.getInspectManager().isOfflineSupported()) {
                plugin.getAdminConfig()
                        .getMessenger()
                        .sendError(
                                player,
                                "Player is not online. Offline inspection requires NBTAPI plugin.");
                return true;
            }

            // Notify user that we're searching
            plugin.getAdminConfig()
                    .getMessenger()
                    .sendWarning(player, "Searching for offline player...");

            // Perform blocking offline player lookup asynchronously
            String targetName = args[0];
            Bukkit.getScheduler()
                    .runTaskAsynchronously(
                            plugin,
                            () -> {
                                @SuppressWarnings("deprecation")
                                OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
                                boolean hasPlayed = offlineTarget.hasPlayedBefore();

                                // Return to main thread to show results
                                Bukkit.getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    // Check if viewer is still online
                                                    if (!player.isOnline()) {
                                                        return;
                                                    }

                                                    if (hasPlayed) {
                                                        openOfflineEnderChest(
                                                                player, offlineTarget);
                                                    } else {
                                                        CommonMessages.PLAYER_NOT_FOUND.send(
                                                                player);
                                                    }
                                                });
                            });
        }

        return true;
    }

    private void openOnlineEnderChest(Player viewer, Player target) {
        // Register for tracking
        plugin.getInspectManager().openEcSee(viewer, target);

        // Open the target's ender chest directly
        viewer.openInventory(target.getEnderChest());
        MessageBuilder.create()
                .prefix(plugin.getAdminConfig().getMessenger().getPrefix())
                .success("Viewing ")
                .highlight(target.getName())
                .success("'s ender chest")
                .send(viewer);
    }

    private void openOfflineEnderChest(Player viewer, OfflinePlayer target) {
        plugin.getAdminConfig()
                .getMessenger()
                .sendWarning(viewer, "Loading offline player ender chest...");

        UUID uuid = target.getUniqueId();
        String targetName = target.getName() != null ? target.getName() : uuid.toString();
        boolean canEdit = viewer.hasPermission("serveradmin.invsee.modify");

        plugin.getInspectManager()
                .getOfflineHandler()
                .loadEnderChestAsync(uuid)
                .thenAccept(
                        contents -> {
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                if (contents == null) {
                                                    plugin.getAdminConfig()
                                                            .getMessenger()
                                                            .sendError(
                                                                    viewer,
                                                                    "Failed to load offline ender chest data for "
                                                                            + targetName);
                                                    return;
                                                }

                                                // Create display inventory with "(Offline)" in
                                                // title
                                                String title =
                                                        ChatColor.DARK_GRAY
                                                                + "EC: "
                                                                + ChatColor.WHITE
                                                                + targetName
                                                                + ChatColor.GRAY
                                                                + " (Offline)";
                                                Inventory inv =
                                                        Bukkit.createInventory(null, 27, title);

                                                for (int i = 0;
                                                        i < Math.min(27, contents.length);
                                                        i++) {
                                                    inv.setItem(i, contents[i]);
                                                }

                                                // Register session
                                                plugin.getInspectManager()
                                                        .openOfflineEcSee(
                                                                viewer,
                                                                uuid,
                                                                targetName,
                                                                inv,
                                                                canEdit);

                                                viewer.openInventory(inv);
                                                MessageBuilder builder =
                                                        MessageBuilder.create()
                                                                .prefix(
                                                                        plugin.getAdminConfig()
                                                                                .getMessenger()
                                                                                .getPrefix())
                                                                .success("Viewing ")
                                                                .highlight(targetName)
                                                                .success("'s offline ender chest");
                                                if (!canEdit) {
                                                    builder.info(" (read-only)");
                                                }
                                                builder.send(viewer);
                                            });
                        })
                .exceptionally(
                        ex -> {
                            Bukkit.getScheduler()
                                    .runTask(
                                            plugin,
                                            () -> {
                                                plugin.getAdminConfig()
                                                        .getMessenger()
                                                        .sendError(
                                                                viewer,
                                                                "Error loading offline ender chest: "
                                                                        + ex.getMessage());
                                            });
                            return null;
                        });
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
