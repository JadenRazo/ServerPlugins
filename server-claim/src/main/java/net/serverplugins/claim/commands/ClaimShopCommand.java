package net.serverplugins.claim.commands;

import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.claim.ServerClaim;
import net.serverplugins.claim.gui.ChunkShopGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ClaimShopCommand implements CommandExecutor {

    private final ServerClaim plugin;

    public ClaimShopCommand(ServerClaim plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("serverclaim.claim")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        // Check if chunk pool is cached, if not load it asynchronously
        if (plugin.getClaimManager().getPlayerChunkPool(player.getUniqueId()) == null) {
            TextUtil.send(player, "<gray>Loading chunk shop...");
            plugin.getServer()
                    .getScheduler()
                    .runTaskAsynchronously(
                            plugin,
                            () -> {
                                try {
                                    plugin.getClaimManager()
                                            .getPlayerChunkPool(player.getUniqueId());
                                    // Now open GUI on main thread
                                    plugin.getServer()
                                            .getScheduler()
                                            .runTask(
                                                    plugin,
                                                    () -> {
                                                        new ChunkShopGui(plugin, player).open();
                                                    });
                                } catch (Exception e) {
                                    plugin.getLogger()
                                            .severe(
                                                    "Failed to load chunk pool for "
                                                            + player.getName()
                                                            + ": "
                                                            + e.getMessage());
                                    plugin.getServer()
                                            .getScheduler()
                                            .runTask(
                                                    plugin,
                                                    () -> {
                                                        TextUtil.send(
                                                                player,
                                                                "<red>Failed to load chunk shop data. Please try again.");
                                                    });
                                }
                            });
        } else {
            new ChunkShopGui(plugin, player).open();
        }
        return true;
    }
}
