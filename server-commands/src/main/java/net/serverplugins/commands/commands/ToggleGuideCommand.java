package net.serverplugins.commands.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Command to toggle the survival guide item on/off Usage: /toggleguide */
public class ToggleGuideCommand implements CommandExecutor {

    private final ServerCommands plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ToggleGuideCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        var joinItemListener = plugin.getJoinItemListener();
        var repository = plugin.getRepository();

        if (joinItemListener == null || !joinItemListener.isEnabled()) {
            player.sendMessage(
                    miniMessage.deserialize("<red>The survival guide feature is not enabled."));
            return true;
        }

        if (repository == null) {
            player.sendMessage(
                    miniMessage.deserialize(
                            "<red>Database error. Please contact an administrator."));
            return true;
        }

        // Get current state
        boolean currentlyEnabled = repository.getSurvivalGuideEnabled(player.getUniqueId());
        boolean newState = !currentlyEnabled;

        // Toggle the state
        repository.setSurvivalGuideEnabled(player.getUniqueId(), player.getName(), newState);

        if (newState) {
            // Enable - give the item
            joinItemListener.giveJoinItem(player);
            player.sendMessage(miniMessage.deserialize("<green>Survival guide enabled!"));
            player.sendMessage(
                    miniMessage.deserialize(
                            "<gray>The guide has been added to your inventory (slot 8)."));
            player.sendMessage(
                    miniMessage.deserialize("<gray>Right-click it to open the server menu."));
        } else {
            // Disable - remove the item
            boolean removed = joinItemListener.removeJoinItem(player);
            player.sendMessage(miniMessage.deserialize("<green>Survival guide disabled!"));
            if (removed) {
                player.sendMessage(
                        miniMessage.deserialize(
                                "<gray>The guide has been removed from your inventory."));
            }
            player.sendMessage(
                    miniMessage.deserialize("<gray>It will not come back automatically."));
            player.sendMessage(
                    miniMessage.deserialize(
                            "<gray>Use <white>/toggleguide</white> again to re-enable it, or <white>/menu</white> to access the server menu."));
        }

        return true;
    }
}
