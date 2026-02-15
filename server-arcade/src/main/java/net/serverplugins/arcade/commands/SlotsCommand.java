package net.serverplugins.arcade.commands;

import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.slots.SlotsType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SlotsCommand implements CommandExecutor {

    private final ServerArcade plugin;

    public SlotsCommand(ServerArcade plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            TextUtil.send(sender, plugin.getArcadeConfig().getMessage("players-only"));
            return true;
        }

        if (!player.hasPermission("serverarcade.command.slots")
                && !player.hasPermission("serverarcade.admin")) {
            TextUtil.send(player, plugin.getArcadeConfig().getMessage("command-no-permission"));
            return true;
        }

        if (!plugin.getArcadeConfig().isSlotsEnabled()) {
            TextUtil.send(
                    player,
                    plugin.getArcadeConfig()
                            .getMessage("game-disabled")
                            .replace("${game}", "Slots"));
            return true;
        }

        if (plugin.getExclusionManager().isExcluded(player.getUniqueId())) {
            var exclusion = plugin.getExclusionManager().getExclusion(player.getUniqueId());
            TextUtil.send(player, plugin.getArcadeConfig().getMessage("excluded"));
            if (exclusion != null) {
                TextUtil.send(
                        player,
                        plugin.getArcadeConfig()
                                .getMessage("excluded-time")
                                .replace("${time}", exclusion.getFormattedRemaining()));
                if (exclusion.getReason() != null && !exclusion.getReason().isEmpty()) {
                    TextUtil.send(
                            player,
                            plugin.getArcadeConfig()
                                    .getMessage("excluded-reason")
                                    .replace("${reason}", exclusion.getReason()));
                }
            }
            return true;
        }

        SlotsType slotsType = plugin.getSlotsType();
        if (slotsType == null) {
            TextUtil.sendError(player, "Slots is not available!");
            return true;
        }

        slotsType.open(player, null);
        return true;
    }
}
