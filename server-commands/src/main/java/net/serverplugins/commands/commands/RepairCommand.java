package net.serverplugins.commands.commands;

import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

public class RepairCommand implements CommandExecutor {

    private final ServerCommands plugin;

    public RepairCommand(ServerCommands plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("servercommands.repair")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        boolean all = args.length > 0 && args[0].equalsIgnoreCase("all");

        if (all) {
            if (!player.hasPermission("servercommands.repair.all")) {
                TextUtil.sendError(player, "You don't have permission to repair all items!");
                return true;
            }

            int repaired = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (repairItem(item)) repaired++;
            }
            for (ItemStack item : player.getInventory().getArmorContents()) {
                if (repairItem(item)) repaired++;
            }
            if (repairItem(player.getInventory().getItemInOffHand())) repaired++;

            if (repaired > 0) {
                TextUtil.sendSuccess(player, "Repaired " + repaired + " item(s)!");
            } else {
                TextUtil.sendError(player, "No items needed repairing!");
            }
        } else {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType().isAir()) {
                TextUtil.sendError(player, "You must be holding an item to repair!");
                return true;
            }

            if (repairItem(item)) {
                TextUtil.sendSuccess(player, "Item repaired!");
            } else {
                TextUtil.sendError(
                        player, "This item cannot be repaired or is already fully repaired!");
            }
        }

        return true;
    }

    private boolean repairItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (!(item.getItemMeta() instanceof Damageable damageable)) return false;
        if (damageable.getDamage() == 0) return false;

        damageable.setDamage(0);
        item.setItemMeta(damageable);
        return true;
    }
}
