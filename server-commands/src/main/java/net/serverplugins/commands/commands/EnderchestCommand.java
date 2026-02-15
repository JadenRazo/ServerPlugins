package net.serverplugins.commands.commands;

import java.util.Collections;
import java.util.List;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.gui.EnderchestGui;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class EnderchestCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public EnderchestCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("servercommands.enderchest")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        Player target;
        boolean isOthers = false;
        if (args.length > 0) {
            if (!player.hasPermission("servercommands.enderchest.others")) {
                TextUtil.sendError(
                        player, "You don't have permission to view others' enderchests!");
                return true;
            }
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                TextUtil.sendError(player, "Player not found!");
                return true;
            }
            isOthers = !target.equals(player);
        } else {
            target = player;
        }

        // Determine if player can edit
        // Own enderchest: needs servercommands.enderchest.edit
        // Others' enderchest: needs servercommands.enderchest.edit.others
        boolean canEdit;
        if (isOthers) {
            canEdit = player.hasPermission("servercommands.enderchest.edit.others");
        } else {
            canEdit = player.hasPermission("servercommands.enderchest.edit");
        }

        // Try to delegate to ServerAdmin if available
        Plugin serverAdmin = Bukkit.getPluginManager().getPlugin("ServerAdmin");
        if (serverAdmin != null && serverAdmin.isEnabled()) {
            try {
                openViaServerAdmin(player, target, canEdit);
                sendSuccessMessage(player, target, canEdit);
                return true;
            } catch (Exception e) {
                // Fall back to standalone GUI if ServerAdmin integration fails
                plugin.getLogger()
                        .warning(
                                "Failed to delegate to ServerAdmin, using fallback: "
                                        + e.getMessage());
            }
        }

        // Fallback: Use standalone GUI
        EnderchestGui gui = new EnderchestGui(plugin, player, target, canEdit);
        gui.open();
        sendSuccessMessage(player, target, canEdit);

        return true;
    }

    /** Opens enderchest via ServerAdmin's InspectManager */
    private void openViaServerAdmin(Player viewer, Player target, boolean canEdit) {
        net.serverplugins.admin.ServerAdmin adminPlugin =
                (net.serverplugins.admin.ServerAdmin)
                        Bukkit.getPluginManager().getPlugin("ServerAdmin");

        // Register with InspectManager using canEdit parameter
        adminPlugin.getInspectManager().openEcSee(viewer, target, canEdit);

        // Open the target's ender chest directly
        viewer.openInventory(target.getEnderChest());
    }

    private void sendSuccessMessage(Player player, Player target, boolean canEdit) {
        if (target.equals(player)) {
            if (canEdit) {
                TextUtil.sendSuccess(player, "Opened your enderchest. <gray>(Edit mode)");
            } else {
                TextUtil.sendSuccess(player, "Opened your enderchest. <gray>(View only)");
            }
        } else {
            if (canEdit) {
                TextUtil.sendSuccess(
                        player, "Opened " + target.getName() + "'s enderchest. <gray>(Edit mode)");
            } else {
                TextUtil.sendSuccess(
                        player, "Opened " + target.getName() + "'s enderchest. <gray>(View only)");
            }
        }
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("servercommands.enderchest.others")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
