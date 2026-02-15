package net.serverplugins.arcade.commands;

import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.machines.Machine;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public class RemoveSeatCommand implements CommandExecutor {

    private final ServerArcade plugin;

    public RemoveSeatCommand(ServerArcade plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            TextUtil.sendError(sender, "This command can only be used by players.");
            return true;
        }

        if (!player.hasPermission("serverarcade.admin")) {
            TextUtil.sendError(player, "You don't have permission to use this command.");
            return true;
        }

        int radius = 3;
        if (args.length > 0) {
            try {
                radius = Integer.parseInt(args[0]);
                if (radius < 1) radius = 1;
                if (radius > 50) radius = 50;
            } catch (NumberFormatException ignored) {
            }
        }

        int removed = 0;
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity.getType() != EntityType.ARMOR_STAND) continue;

            ArmorStand stand = (ArmorStand) entity;

            // Check if it's a machine seat
            if (stand.getPersistentDataContainer()
                    .has(Machine.MACHINE_SEAT_KEY, PersistentDataType.INTEGER)) {
                // Eject any passengers first
                stand.getPassengers().forEach(stand::removePassenger);
                stand.remove();
                removed++;
            }
        }

        if (removed > 0) {
            TextUtil.sendSuccess(
                    player,
                    "Removed <white>"
                            + removed
                            + "<green> seat(s) within <white>"
                            + radius
                            + "<green> blocks.");
        } else {
            TextUtil.send(player, "<red>No seats found within <white>" + radius + " <red>blocks.");
        }

        return true;
    }
}
