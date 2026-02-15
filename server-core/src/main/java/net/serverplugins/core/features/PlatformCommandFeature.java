package net.serverplugins.core.features;

import java.util.UUID;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.core.ServerCore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlatformCommandFeature extends Feature implements CommandExecutor {

    private static final String BEDROCK_PREFIX = "00000000-0000-0000-";

    public PlatformCommandFeature(ServerCore plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Platform Command";
    }

    @Override
    public String getDescription() {
        return "Run commands based on player platform (Java/Bedrock)";
    }

    @Override
    protected void onEnable() {
        if (plugin.getCommand("javacommand") != null) {
            plugin.getCommand("javacommand").setExecutor(this);
        }
        if (plugin.getCommand("bedrockcommand") != null) {
            plugin.getCommand("bedrockcommand").setExecutor(this);
        }
        if (plugin.getCommand("platform") != null) {
            plugin.getCommand("platform").setExecutor(this);
        }
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args) {
        if (!isEnabled()) return false;

        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("platform")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Console platform: Server");
                return true;
            }

            String platform = isBedrockPlayer(player) ? "Bedrock" : "Java";
            TextUtil.send(player, "<gray>Your platform: <aqua>" + platform);
            return true;
        }

        if (!(sender instanceof Player player)) {
            TextUtil.send(sender, "<red>This command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            TextUtil.send(player, "<red>Usage: /" + label + " <command>");
            return true;
        }

        boolean isBedrock = isBedrockPlayer(player);
        String commandToRun = String.join(" ", args);

        if (cmdName.equals("javacommand")) {
            if (!isBedrock) {
                // Java player - execute the command
                Bukkit.dispatchCommand(player, commandToRun);
            }
        } else if (cmdName.equals("bedrockcommand")) {
            if (isBedrock) {
                // Bedrock player - execute the command
                Bukkit.dispatchCommand(player, commandToRun);
            }
        }

        return true;
    }

    /**
     * Check if a player is on Bedrock (via Geyser/Floodgate) Detection methods: 1. Check Floodgate
     * API if available 2. Check UUID prefix (Floodgate uses 00000000-0000-0000-xxxx format)
     */
    public static boolean isBedrockPlayer(Player player) {
        UUID uuid = player.getUniqueId();

        // Check Floodgate API if available
        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            try {
                Class<?> floodgateApi = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Object instance = floodgateApi.getMethod("getInstance").invoke(null);
                Boolean isBedrock =
                        (Boolean)
                                floodgateApi
                                        .getMethod("isFloodgatePlayer", UUID.class)
                                        .invoke(instance, uuid);
                return isBedrock != null && isBedrock;
            } catch (Exception ignored) {
                // Fall through to UUID check
            }
        }

        // Fallback: Check UUID prefix
        // Floodgate offline mode UUIDs start with 00000000-0000-0000-
        String uuidStr = uuid.toString();
        return uuidStr.startsWith(BEDROCK_PREFIX);
    }
}
