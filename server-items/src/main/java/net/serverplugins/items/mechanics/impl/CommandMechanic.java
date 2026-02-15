package net.serverplugins.items.mechanics.impl;

import java.util.List;
import net.serverplugins.items.mechanics.Mechanic;
import net.serverplugins.items.models.CustomItem;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public class CommandMechanic extends Mechanic {

    private final List<String> playerCommands;
    private final List<String> consoleCommands;

    public CommandMechanic(ConfigurationSection config) {
        this.playerCommands = config.getStringList("player");
        this.consoleCommands = config.getStringList("console");
    }

    @Override
    public String getId() {
        return "command";
    }

    @Override
    public void onRightClick(
            Player player, CustomItem item, ItemStack stack, PlayerInteractEvent event) {
        for (String cmd : playerCommands) {
            String parsed =
                    cmd.replace("{player}", player.getName()).replace("{item}", item.getId());
            player.performCommand(parsed);
        }

        for (String cmd : consoleCommands) {
            String parsed =
                    cmd.replace("{player}", player.getName()).replace("{item}", item.getId());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }
}
