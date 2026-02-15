package net.serverplugins.core.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.api.messages.CommonMessages;
import net.serverplugins.api.utils.LegacyText;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.core.ServerCore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

/**
 * Command to give players spawners with specific entity types. Usage: /givespawner <player>
 * <mobtype> [amount]
 */
public class GiveSpawnerCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "servercore.givespawner";
    private final ServerCore plugin;

    // List of spawnable mob types for tab completion
    private static final List<String> SPAWNABLE_MOBS =
            Arrays.stream(EntityType.values())
                    .filter(EntityType::isSpawnable)
                    .filter(EntityType::isAlive)
                    .map(e -> e.name().toLowerCase())
                    .sorted()
                    .collect(Collectors.toList());

    public GiveSpawnerCommand(ServerCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Debug logging for shop integration troubleshooting
        Bukkit.getLogger()
                .info(
                        "[GiveSpawner] Command executed by: "
                                + sender.getName()
                                + " with args: "
                                + String.join(" ", args));

        // Allow console to bypass permission (for shop plugins running commands)
        boolean isConsole = sender instanceof ConsoleCommandSender;
        if (!isConsole && !sender.hasPermission(PERMISSION)) {
            CommonMessages.NO_PERMISSION.send(sender);
            return true;
        }

        if (args.length < 2) {
            Bukkit.getLogger().info("[GiveSpawner] Not enough arguments provided");
            TextUtil.send(
                    sender, ColorScheme.ERROR + "Usage: /givespawner <player> <mobtype> [amount]");
            TextUtil.send(sender, ColorScheme.INFO + "Example: /givespawner Steve pig 1");
            return true;
        }

        // Parse player
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            Bukkit.getLogger().info("[GiveSpawner] Player not found: " + args[0]);
            plugin.getCoreConfig().getMessenger().sendError(sender, "Player not found: " + args[0]);
            return true;
        }

        // Parse entity type
        EntityType entityType;
        try {
            entityType = EntityType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getCoreConfig().getMessenger().sendError(sender, "Invalid mob type: " + args[1]);
            TextUtil.send(
                    sender,
                    ColorScheme.INFO
                            + "Valid types include: pig, chicken, zombie, skeleton, creeper, etc.");
            return true;
        }

        // Validate it's a spawnable entity
        if (!entityType.isSpawnable() || !entityType.isAlive()) {
            plugin.getCoreConfig()
                    .getMessenger()
                    .sendError(sender, "This entity type cannot be used in spawners: " + args[1]);
            return true;
        }

        // Parse amount
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 1) amount = 1;
                if (amount > 64) amount = 64;
            } catch (NumberFormatException e) {
                plugin.getCoreConfig()
                        .getMessenger()
                        .sendError(sender, "Amount must be a number: " + args[2]);
                return true;
            }
        }

        // Create and give the spawner
        ItemStack spawnerItem = createSpawnerItem(entityType, amount);
        target.getInventory().addItem(spawnerItem);

        String mobName = formatEntityName(entityType);

        // Log successful spawner give
        Bukkit.getLogger()
                .info(
                        "[GiveSpawner] Gave "
                                + amount
                                + "x "
                                + mobName
                                + " Spawner to "
                                + target.getName());

        // Silent for console commands (shop usage), notify if sender is different
        if (sender instanceof Player && !sender.equals(target)) {
            plugin.getCoreConfig()
                    .getMessenger()
                    .sendSuccess(
                            sender,
                            "Gave "
                                    + target.getName()
                                    + " "
                                    + amount
                                    + "x "
                                    + mobName
                                    + " Spawner");
        }

        return true;
    }

    private ItemStack createSpawnerItem(EntityType entityType, int amount) {
        ItemStack item = new ItemStack(Material.SPAWNER, amount);
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();

        if (meta != null) {
            CreatureSpawner spawnerState = (CreatureSpawner) meta.getBlockState();
            spawnerState.setSpawnedType(entityType);
            meta.setBlockState(spawnerState);

            // Set display name to match silk-touched spawners
            String mobName = formatEntityName(entityType);
            meta.setDisplayName(LegacyText.colorize("&e" + mobName + " Spawner"));

            item.setItemMeta(meta);
        }

        return item;
    }

    private String formatEntityName(EntityType type) {
        String name = type.name().toLowerCase().replace("_", " ");
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : name.toCharArray()) {
            if (c == ' ') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Player names
            Bukkit.getOnlinePlayers().forEach(p -> completions.add(p.getName()));
        } else if (args.length == 2) {
            // Mob types
            completions.addAll(SPAWNABLE_MOBS);
        } else if (args.length == 3) {
            // Amount
            completions.add("1");
            completions.add("16");
            completions.add("32");
            completions.add("64");
        }

        // Filter based on current input
        String current = args[args.length - 1].toLowerCase();
        return completions.stream()
                .filter(s -> s.toLowerCase().startsWith(current))
                .limit(50)
                .toList();
    }
}
