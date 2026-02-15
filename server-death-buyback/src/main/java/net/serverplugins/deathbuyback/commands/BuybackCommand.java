package net.serverplugins.deathbuyback.commands;

import java.util.Collections;
import java.util.List;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.deathbuyback.ServerDeathBuyback;
import net.serverplugins.deathbuyback.gui.BuybackMenuGui;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BuybackCommand implements CommandExecutor, TabCompleter {

    private final ServerDeathBuyback plugin;

    public BuybackCommand(ServerDeathBuyback plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        // Use CommonMessages for standard "players only" message
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        // Use CommonMessages for standard "no permission" message
        if (!player.hasPermission("deathbuyback.use")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        // Check if player has any slots
        int maxSlots = plugin.getDeathInventoryManager().getMaxSlots(player);
        if (maxSlots <= 0) {
            // Use PluginMessenger for plugin-specific message
            plugin.getDeathBuybackConfig().getMessenger().send(player, "feature-disabled");
            return true;
        }

        // Open the buyback menu
        new BuybackMenuGui(plugin, player).open(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        return Collections.emptyList();
    }
}
