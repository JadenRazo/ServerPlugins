package net.serverplugins.items.commands;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.items.ServerItems;
import net.serverplugins.items.managers.FurnitureManager;
import net.serverplugins.items.models.CustomFurniture;
import net.serverplugins.items.models.FurnitureInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;

public class FurnitureCommand implements CommandExecutor, TabCompleter {

    private final ServerItems plugin;
    private final FurnitureManager furnitureManager;

    public FurnitureCommand(ServerItems plugin) {
        this.plugin = plugin;
        this.furnitureManager = plugin.getFurnitureManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "place" -> handlePlace(player, args);
            case "remove" -> handleRemove(player);
            case "list" -> handleList(player);
            default -> sendHelp(player);
        }

        return true;
    }

    private void handlePlace(Player player, String[] args) {
        if (!player.hasPermission("serveritems.place")) {
            CommonMessages.NO_PERMISSION.send(player);
            return;
        }

        if (args.length < 2) {
            TextUtil.sendError(player, "Usage: /wfurniture place <id>");
            return;
        }

        String furnitureId = args[1].toLowerCase();
        CustomFurniture def = furnitureManager.getDefinition(furnitureId);
        if (def == null) {
            TextUtil.sendError(player, "Furniture <white>" + furnitureId + "</white> not found.");
            return;
        }

        org.bukkit.block.Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) {
            TextUtil.sendError(player, "Look at a block to place furniture.");
            return;
        }

        // Place on top of the targeted block
        org.bukkit.Location placeLoc = targetBlock.getLocation().add(0.5, 1, 0.5);

        float yaw = player.getLocation().getYaw();
        if (def.getRotationType() == CustomFurniture.RotationType.NONE) {
            yaw = 0;
        }

        furnitureManager.placeFurniture(def, placeLoc, yaw, player.getUniqueId());
        TextUtil.sendSuccess(player, "Placed <white>" + def.getId() + "</white>.");
    }

    private void handleRemove(Player player) {
        if (!player.hasPermission("serveritems.place")) {
            CommonMessages.NO_PERMISSION.send(player);
            return;
        }

        // Find furniture the player is looking at
        Entity target = player.getTargetEntity(5);
        if (target == null) {
            TextUtil.sendError(player, "Look at a furniture piece to remove it.");
            return;
        }

        FurnitureInstance instance = null;
        if (target instanceof ItemDisplay) {
            instance = furnitureManager.getFurnitureByDisplay(target.getUniqueId());
        } else if (target instanceof Interaction) {
            instance = furnitureManager.getFurnitureByInteraction(target.getUniqueId());
        }

        if (instance == null) {
            TextUtil.sendError(player, "That's not a custom furniture piece.");
            return;
        }

        furnitureManager.removeFurniture(instance.getDisplayEntityUuid());
        TextUtil.sendSuccess(player, "Furniture removed.");
    }

    private void handleList(Player player) {
        if (!player.hasPermission("serveritems.admin")) {
            CommonMessages.NO_PERMISSION.send(player);
            return;
        }

        var defs = furnitureManager.getAllDefinitions();
        if (defs.isEmpty()) {
            TextUtil.sendWarning(player, "No furniture definitions registered.");
            return;
        }

        TextUtil.send(player, "<gold>Furniture Definitions:</gold>");
        for (CustomFurniture def : defs) {
            TextUtil.send(
                    player,
                    " <gray>- <white>"
                            + def.getId()
                            + "</white> <dark_gray>["
                            + def.getItem().getMaterial().name()
                            + "]</dark_gray>");
        }
    }

    private void sendHelp(Player player) {
        TextUtil.send(player, "<gold>Furniture Commands:</gold>");
        TextUtil.send(player, " <gray>/wfurniture place <id> <dark_gray>- Place furniture");
        TextUtil.send(player, " <gray>/wfurniture remove <dark_gray>- Remove targeted furniture");
        TextUtil.send(player, " <gray>/wfurniture list <dark_gray>- List definitions");
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filterStartsWith(List.of("place", "remove", "list"), args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("place")) {
            return filterStartsWith(
                    furnitureManager.getAllDefinitions().stream()
                            .map(CustomFurniture::getId)
                            .collect(Collectors.toList()),
                    args[1]);
        }

        return Collections.emptyList();
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}
