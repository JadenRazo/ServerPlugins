package net.serverplugins.arcade.commands;

import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.machines.Machine;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

public class SpawnSeatCommand implements CommandExecutor {

    private final ServerArcade plugin;

    public SpawnSeatCommand(ServerArcade plugin) {
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

        Location loc = player.getLocation();

        // Spawn the seat armor stand
        ArmorStand seat = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);

        // Configure as invisible seat
        seat.setVisible(false);
        seat.setInvulnerable(true);
        seat.setCollidable(false);
        seat.setGravity(false);
        seat.setPersistent(true);
        seat.setBasePlate(false);
        seat.setSmall(false);

        // Add persistent data tags
        String machineId = "debug_seat_" + System.currentTimeMillis();
        seat.getPersistentDataContainer()
                .set(Machine.MACHINE_ENTITY_KEY, PersistentDataType.STRING, machineId);
        seat.getPersistentDataContainer()
                .set(Machine.MACHINE_SEAT_KEY, PersistentDataType.INTEGER, 1);

        // Add scoreboard tag for identification
        seat.addScoreboardTag("arcade_seat_1");

        TextUtil.sendSuccess(player, "Slot machine seat spawned at your location!");
        TextUtil.send(player, "<gray>ID: <white>" + machineId);
        TextUtil.send(
                player,
                "<gray>Right-click to sit, or use <white>/kill @e[type=armor_stand,distance=..2] <gray>to remove.");

        return true;
    }
}
