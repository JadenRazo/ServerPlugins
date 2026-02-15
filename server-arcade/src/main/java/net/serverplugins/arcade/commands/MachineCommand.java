package net.serverplugins.arcade.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameType;
import net.serverplugins.arcade.machines.Direction;
import net.serverplugins.arcade.machines.Machine;
import net.serverplugins.arcade.machines.MachineManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MachineCommand implements CommandExecutor, TabCompleter {

    private final ServerArcade plugin;

    public MachineCommand(ServerArcade plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {

        if (!(sender instanceof Player player)) {
            CommonMessages.PLAYERS_ONLY.send(sender);
            return true;
        }

        if (!player.hasPermission("serverarcade.machine.admin")) {
            CommonMessages.NO_PERMISSION.send(player);
            return true;
        }

        MachineManager manager = plugin.getMachineManager();
        if (manager == null) {
            TextUtil.sendError(player, "Machine system is not enabled.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "give" -> handleGive(player, args);
            case "place" -> handlePlace(player, args);
            case "remove" -> handleRemove(player, args);
            case "cleanup" -> handleCleanup(player, args);
            case "forceremove" -> handleForceRemove(player);
            case "list" -> handleList(player);
            case "reload" -> handleReload(player);
            default -> sendHelp(player);
        }

        return true;
    }

    private void handlePlace(Player player, String[] args) {
        if (args.length < 2) {
            TextUtil.sendError(player, "Usage: /arcademachine place <type>");
            TextUtil.send(
                    player, ColorScheme.INFO + "Types: " + String.join(", ", getGameTypeNames()));
            return;
        }

        String typeName = args[1].toLowerCase();
        GameType gameType = plugin.getGameType(typeName);

        if (gameType == null) {
            TextUtil.sendError(player, "Unknown game type: " + args[1]);
            TextUtil.send(
                    player,
                    ColorScheme.INFO + "Available types: " + String.join(", ", getGameTypeNames()));
            return;
        }

        // Get block player is looking at
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || targetBlock.getType().isAir()) {
            TextUtil.sendError(player, "You must be looking at a block to place a machine.");
            return;
        }

        // Place on top of targeted block - add Y+1 for block position, centered on block
        Location location = targetBlock.getLocation().add(0.5, 1, 0.5);

        // Machine faces towards player based on relative positions (like DreamArcade)
        Direction direction = Direction.getDirectionFromPlayer(player.getLocation(), location);

        MachineManager manager = plugin.getMachineManager();
        Machine machine = manager.createMachine(gameType, location, direction, player);

        if (machine == null) {
            TextUtil.sendError(player, "Could not place machine here. Location may be blocked.");
            return;
        }

        TextUtil.sendSuccess(player, "Placed " + gameType.getName() + " machine!");
    }

    private void handleGive(Player player, String[] args) {
        // Usage: /arcademachine give <player> <type>
        // Or: /arcademachine give <type> (gives to self)

        if (args.length < 2) {
            TextUtil.sendError(player, "Usage: /arcademachine give [player] <type>");
            TextUtil.send(
                    player, ColorScheme.INFO + "Types: " + String.join(", ", getGameTypeNames()));
            return;
        }

        String targetPlayerName;
        String typeName;

        if (args.length >= 3) {
            // /arcademachine give <player> <type>
            targetPlayerName = args[1];
            typeName = args[2].toLowerCase();
        } else {
            // /arcademachine give <type>
            targetPlayerName = player.getName();
            typeName = args[1].toLowerCase();
        }

        Player target = org.bukkit.Bukkit.getPlayer(targetPlayerName);
        if (target == null) {
            TextUtil.sendError(player, "Player not found: " + targetPlayerName);
            return;
        }

        GameType gameType = plugin.getGameType(typeName);
        if (gameType == null) {
            TextUtil.sendError(player, "Unknown game type: " + typeName);
            TextUtil.send(
                    player,
                    ColorScheme.INFO + "Available types: " + String.join(", ", getGameTypeNames()));
            return;
        }

        // Create machine item (stick with custom_model_data + PersistentData)
        org.bukkit.inventory.ItemStack machineItem =
                gameType.getMachineStructure().getPlacementItem().clone();
        org.bukkit.inventory.meta.ItemMeta meta = machineItem.getItemMeta();

        if (meta == null) {
            TextUtil.sendError(player, "Failed to create machine item.");
            return;
        }

        // Store game type in PersistentDataContainer
        meta.getPersistentDataContainer()
                .set(
                        Machine.MACHINE_ITEM_KEY,
                        org.bukkit.persistence.PersistentDataType.STRING,
                        gameType.getConfigKey());
        machineItem.setItemMeta(meta);

        target.getInventory().addItem(machineItem);

        if (target.equals(player)) {
            TextUtil.sendSuccess(player, "Received " + gameType.getName() + " machine!");
        } else {
            TextUtil.sendSuccess(
                    player, "Gave " + gameType.getName() + " machine to " + target.getName());
            TextUtil.sendSuccess(
                    target,
                    "Received " + gameType.getName() + " machine from " + player.getName() + "!");
        }
    }

    private void handleRemove(Player player, String[] args) {
        MachineManager manager = plugin.getMachineManager();

        // If player is looking at a machine
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock != null) {
            Machine machine = manager.getMachineByBlock(targetBlock);
            if (machine != null) {
                // Check permission for others' machines
                if (!machine.getPlacedBy().equals(player.getUniqueId())
                        && !player.hasPermission("serverarcade.machine.break.others")) {
                    CommonMessages.NO_PERMISSION.send(player);
                    return;
                }

                manager.removeMachine(machine.getId());
                TextUtil.sendSuccess(player, "Machine removed!");
                return;
            }
        }

        TextUtil.sendError(player, "No machine found. Look at a machine to remove it.");
        TextUtil.send(
                player,
                ColorScheme.INFO
                        + "Tip: Use "
                        + ColorScheme.COMMAND
                        + "/arcademachine forceremove "
                        + ColorScheme.INFO
                        + "for orphaned machines.");
    }

    private void handleCleanup(Player player, String[] args) {
        int radius = 10;
        if (args.length >= 2) {
            try {
                radius = Integer.parseInt(args[1]);
                radius = Math.min(radius, 50); // Cap at 50 blocks
            } catch (NumberFormatException e) {
                TextUtil.sendWarning(player, "Invalid radius. Using default: 10");
            }
        }

        Location loc = player.getLocation();
        MachineManager manager = plugin.getMachineManager();

        // First, properly remove all machines through the manager (cleans up world + state)
        int machinesRemoved = 0;
        for (Machine machine : new java.util.ArrayList<>(manager.getAllMachines())) {
            if (machine.getLocation().getWorld().equals(loc.getWorld())
                    && machine.getLocation().distance(loc) <= radius) {
                manager.removeMachine(machine.getId());
                machinesRemoved++;
            }
        }

        // Then remove any orphaned armor stands with machine keys (not in manager)
        int orphanedRemoved = 0;
        for (Entity entity : loc.getNearbyEntities(radius, radius, radius)) {
            if (entity.getType() == EntityType.ARMOR_STAND) {
                ArmorStand stand = (ArmorStand) entity;
                if (stand.getPersistentDataContainer()
                        .has(Machine.MACHINE_ENTITY_KEY, PersistentDataType.STRING)) {
                    stand.remove();
                    orphanedRemoved++;
                }
            }
        }

        // Remove any remaining barrier blocks (orphaned from removed machines)
        int blockRadius = Math.min(radius, 20);
        int blocksRemoved = 0;
        for (int x = -blockRadius; x <= blockRadius; x++) {
            for (int y = -blockRadius; y <= blockRadius; y++) {
                for (int z = -blockRadius; z <= blockRadius; z++) {
                    Block block = loc.clone().add(x, y, z).getBlock();
                    if (block.getType() == Material.BARRIER) {
                        block.setType(Material.AIR);
                        blocksRemoved++;
                    }
                }
            }
        }

        TextUtil.sendSuccess(player, "Cleanup complete!");
        TextUtil.send(
                player,
                ColorScheme.INFO
                        + "Removed "
                        + ColorScheme.EMPHASIS
                        + machinesRemoved
                        + ColorScheme.INFO
                        + " machines, "
                        + ColorScheme.EMPHASIS
                        + orphanedRemoved
                        + ColorScheme.INFO
                        + " orphaned entities, and "
                        + ColorScheme.EMPHASIS
                        + blocksRemoved
                        + ColorScheme.INFO
                        + " barrier blocks.");
    }

    private void handleForceRemove(Player player) {
        // First check for armor stand entity player is looking at
        Entity targetEntity = getTargetEntity(player, 5);
        if (targetEntity instanceof ArmorStand stand) {
            if (stand.getPersistentDataContainer()
                    .has(Machine.MACHINE_ENTITY_KEY, PersistentDataType.STRING)) {
                String machineId =
                        stand.getPersistentDataContainer()
                                .get(Machine.MACHINE_ENTITY_KEY, PersistentDataType.STRING);

                // Try to remove from manager first
                MachineManager manager = plugin.getMachineManager();
                if (manager != null && machineId != null) {
                    Machine machine = manager.getMachine(machineId);
                    if (machine != null) {
                        manager.removeMachine(machineId);
                        TextUtil.sendSuccess(player, "Machine removed from database and world!");
                        return;
                    }
                }

                // If not in manager, just remove the entity and nearby related entities
                removeOrphanedMachine(stand.getLocation(), machineId);
                TextUtil.sendSuccess(player, "Orphaned machine entity removed!");
                return;
            }
            // Regular armor stand with no machine data
            stand.remove();
            TextUtil.sendSuccess(player, "Armor stand removed!");
            return;
        }

        // Check for barrier block
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock != null && targetBlock.getType() == Material.BARRIER) {
            // Check if there's a machine here
            MachineManager manager = plugin.getMachineManager();
            if (manager != null) {
                Machine machine = manager.getMachineByBlock(targetBlock);
                if (machine != null) {
                    manager.removeMachine(machine.getId());
                    TextUtil.sendSuccess(player, "Machine removed!");
                    return;
                }
            }

            // Just remove the barrier block
            targetBlock.setType(Material.AIR);
            TextUtil.sendSuccess(player, "Barrier block removed!");
            return;
        }

        TextUtil.sendError(player, "No machine entity or barrier block found.");
        TextUtil.send(
                player, ColorScheme.INFO + "Look directly at an armor stand or barrier block.");
    }

    private void removeOrphanedMachine(Location loc, String machineId) {
        // Remove all entities with this machine ID
        for (Entity entity : loc.getNearbyEntities(5, 5, 5)) {
            if (entity.getType() == EntityType.ARMOR_STAND) {
                ArmorStand stand = (ArmorStand) entity;
                if (stand.getPersistentDataContainer()
                        .has(Machine.MACHINE_ENTITY_KEY, PersistentDataType.STRING)) {
                    String id =
                            stand.getPersistentDataContainer()
                                    .get(Machine.MACHINE_ENTITY_KEY, PersistentDataType.STRING);
                    if (machineId == null || machineId.equals(id)) {
                        stand.remove();
                    }
                }
            }
        }

        // Remove nearby barrier blocks (within 3 blocks)
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    Block block = loc.clone().add(x, y, z).getBlock();
                    if (block.getType() == Material.BARRIER) {
                        block.setType(Material.AIR);
                    }
                }
            }
        }
    }

    private Entity getTargetEntity(Player player, int range) {
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (entity.getType() == EntityType.ARMOR_STAND) {
                // Check if player is looking at this entity
                Location eyeLoc = player.getEyeLocation();
                var direction = eyeLoc.getDirection();
                var toEntity =
                        entity.getLocation().add(0, 1, 0).toVector().subtract(eyeLoc.toVector());

                if (direction.angle(toEntity) < Math.toRadians(10)) {
                    double distance = eyeLoc.distance(entity.getLocation());
                    if (distance <= range) {
                        return entity;
                    }
                }
            }
        }
        return null;
    }

    private void handleList(Player player) {
        MachineManager manager = plugin.getMachineManager();
        var machines = manager.getAllMachines();

        if (machines.isEmpty()) {
            TextUtil.send(player, ColorScheme.WARNING + "No machines placed.");
            return;
        }

        TextUtil.send(player, "<gold>=== Arcade Machines ===");
        for (Machine machine : machines) {
            Location loc = machine.getLocation();
            String typeName =
                    machine.getGameType() != null ? machine.getGameType().getName() : "Unknown";
            TextUtil.send(
                    player,
                    String.format(
                            "<gray>- <yellow>%s <gray>at <white>%s <gray>(%.0f, %.0f, %.0f)",
                            typeName,
                            loc.getWorld().getName(),
                            loc.getX(),
                            loc.getY(),
                            loc.getZ()));
        }
        TextUtil.send(player, "<gold>Total: " + machines.size() + " machines");
    }

    private void handleReload(Player player) {
        plugin.reloadConfiguration();
        MachineManager manager = plugin.getMachineManager();
        if (manager != null) {
            manager.reload();
        }
        TextUtil.sendSuccess(player, "Configuration reloaded!");
    }

    private void sendHelp(Player player) {
        TextUtil.send(player, "<gold>=== Arcade Machine Commands ===");
        TextUtil.send(
                player,
                "<yellow>/arcademachine give [player] <type> <gray>- Get a machine item to place");
        TextUtil.send(
                player, "<yellow>/arcademachine place <type> <gray>- Place a machine directly");
        TextUtil.send(
                player,
                "<yellow>/arcademachine remove <gray>- Remove the machine you're looking at");
        TextUtil.send(
                player,
                "<yellow>/arcademachine forceremove <gray>- Force remove any machine/armor stand");
        TextUtil.send(
                player,
                "<yellow>/arcademachine cleanup [radius] <gray>- Remove all machines in radius");
        TextUtil.send(player, "<yellow>/arcademachine list <gray>- List all machines");
        TextUtil.send(player, "<yellow>/arcademachine reload <gray>- Reload configuration");
        TextUtil.send(
                player,
                ColorScheme.INFO + "Available types: " + String.join(", ", getGameTypeNames()));
    }

    private List<String> getGameTypeNames() {
        return plugin.getGameTypes().keySet().stream().collect(Collectors.toList());
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String alias,
            @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(
                    List.of("give", "place", "remove", "forceremove", "cleanup", "list", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("place")) {
            completions.addAll(getGameTypeNames());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            // Can be either game type or player name
            completions.addAll(getGameTypeNames());
            completions.addAll(
                    org.bukkit.Bukkit.getOnlinePlayers().stream()
                            .map(org.bukkit.entity.Player::getName)
                            .collect(Collectors.toList()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // If 3 args, second is player name, third is game type
            completions.addAll(getGameTypeNames());
        } else if (args.length == 2 && args[0].equalsIgnoreCase("cleanup")) {
            completions.addAll(List.of("5", "10", "20", "30", "50"));
        }

        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(c -> c.toLowerCase().startsWith(lastArg))
                .collect(Collectors.toList());
    }
}
