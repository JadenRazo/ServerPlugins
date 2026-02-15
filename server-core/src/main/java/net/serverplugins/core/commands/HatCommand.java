package net.serverplugins.core.commands;

import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.core.ServerCore;
import net.serverplugins.core.features.HatFeature;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class HatCommand implements CommandExecutor {

    private final ServerCore plugin;

    public HatCommand(ServerCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("servercore.hat")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        HatFeature hatFeature = (HatFeature) plugin.getFeatures().get("hat");
        if (hatFeature == null || !hatFeature.isEnabled()) {
            plugin.getCoreConfig()
                    .getMessenger()
                    .send(
                            player,
                            "feature-disabled",
                            net.serverplugins.api.messages.Placeholder.of("feature", "hat"));
            return true;
        }

        hatFeature.wearHat(player);
        return true;
    }
}
