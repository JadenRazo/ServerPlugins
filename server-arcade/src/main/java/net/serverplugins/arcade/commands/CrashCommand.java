package net.serverplugins.arcade.commands;

import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.crash.CrashType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CrashCommand implements CommandExecutor {

    private final ServerArcade plugin;

    public CrashCommand(ServerArcade plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            TextUtil.send(sender, plugin.getArcadeConfig().getMessage("players-only"));
            return true;
        }

        if (!player.hasPermission("serverarcade.command.crash")
                && !player.hasPermission("serverarcade.admin")) {
            TextUtil.send(player, plugin.getArcadeConfig().getMessage("command-no-permission"));
            return true;
        }

        if (!plugin.getArcadeConfig().isCrashEnabled()) {
            TextUtil.send(
                    player,
                    plugin.getArcadeConfig()
                            .getMessage("game-disabled")
                            .replace("${game}", "Crash"));
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

        CrashType crashType = plugin.getCrashType();
        if (crashType == null) {
            TextUtil.sendError(player, "Crash is not available!");
            return true;
        }

        crashType.open(player, null);
        return true;
    }
}
