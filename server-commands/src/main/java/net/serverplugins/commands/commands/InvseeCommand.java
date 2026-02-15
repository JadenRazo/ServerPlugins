package net.serverplugins.commands.commands;

import java.util.Collections;
import java.util.List;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import net.serverplugins.commands.gui.InvseeGui;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class InvseeCommand implements CommandExecutor, TabCompleter {

    private final ServerCommands plugin;

    public InvseeCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("servercommands.invsee")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        if (args.length < 1) {
            TextUtil.sendError(player, "Usage: /invsee <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            TextUtil.sendError(player, "Player not found!");
            return true;
        }

        if (target.equals(player)) {
            TextUtil.sendError(player, "You cannot view your own inventory with this command!");
            return true;
        }

        // Determine if player can edit - needs servercommands.invsee.edit permission
        boolean canEdit = player.hasPermission("servercommands.invsee.edit");

        // Try to delegate to ServerAdmin if available
        Plugin serverAdmin = Bukkit.getPluginManager().getPlugin("ServerAdmin");
        if (serverAdmin != null && serverAdmin.isEnabled()) {
            try {
                openViaServerAdmin(player, target, canEdit);
                if (canEdit) {
                    TextUtil.sendSuccess(
                            player,
                            "Opened " + target.getName() + "'s inventory. <gray>(Edit mode)");
                } else {
                    TextUtil.sendSuccess(
                            player,
                            "Opened " + target.getName() + "'s inventory. <gray>(View only)");
                }
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
        InvseeGui gui = new InvseeGui(plugin, player, target, canEdit);
        gui.open();

        if (canEdit) {
            TextUtil.sendSuccess(
                    player, "Opened " + target.getName() + "'s inventory. <gray>(Edit mode)");
        } else {
            TextUtil.sendSuccess(
                    player, "Opened " + target.getName() + "'s inventory. <gray>(View only)");
        }

        return true;
    }

    /** Opens inventory view via ServerAdmin's InspectManager */
    private void openViaServerAdmin(Player viewer, Player target, boolean canEdit) {
        net.serverplugins.admin.ServerAdmin adminPlugin =
                (net.serverplugins.admin.ServerAdmin)
                        Bukkit.getPluginManager().getPlugin("ServerAdmin");

        // Create the inventory display (same layout as ServerAdmin's InvSeeCommand)
        String title = ChatColor.DARK_GRAY + "Inv: " + ChatColor.WHITE + target.getName();
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // Copy main inventory (slots 0-35)
        ItemStack[] contents = target.getInventory().getContents();
        for (int i = 0; i < Math.min(36, contents.length); i++) {
            inv.setItem(i, contents[i]);
        }

        // Add armor in row 5 (slots 45-48)
        ItemStack[] armor = target.getInventory().getArmorContents();
        inv.setItem(45, armor[3]); // Helmet
        inv.setItem(46, armor[2]); // Chestplate
        inv.setItem(47, armor[1]); // Leggings
        inv.setItem(48, armor[0]); // Boots

        // Offhand in slot 53
        inv.setItem(53, target.getInventory().getItemInOffHand());

        // Register with InspectManager using canEdit parameter
        adminPlugin.getInspectManager().openInvSee(viewer, target, inv, canEdit);

        viewer.openInventory(inv);
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
