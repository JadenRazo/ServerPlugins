package net.serverplugins.commands;

import java.util.List;
import net.serverplugins.api.messages.PluginMessenger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public class CommandsConfig {

    private final ServerCommands plugin;
    private final FileConfiguration config;
    private final PluginMessenger messenger;

    public CommandsConfig(ServerCommands plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        this.messenger =
                new PluginMessenger(
                        config, "messages", "<gradient:#ff6b6b:#feca57>[Commands]</gradient> ");
    }

    public String getDiscordLink() {
        return config.getString("links.discord", "https://discord.gg/serverplugins");
    }

    public String getStoreLink() {
        return config.getString("links.store", "https://store.example.com");
    }

    public String getWebsiteLink() {
        return config.getString("links.website", "https://example.com");
    }

    public String getRulesLink() {
        return config.getString("links.rules", "https://example.com/rules");
    }

    public List<String> getVoteLinks() {
        return config.getStringList("links.vote");
    }

    public Location getSpawnLocation() {
        String worldName = config.getString("spawn.world", "spawn");
        World world = null;

        // Use Bukkit's world loading (works with all world management plugins)
        world = Bukkit.getWorld(worldName);
        if (world != null) {
            plugin.getLogger().info("Spawn world '" + worldName + "' loaded successfully");
        }

        if (world == null) {
            plugin.getLogger().severe("Spawn world '" + worldName + "' not found!");
            plugin.getLogger().severe("Please update config.yml with a valid world name.");
            plugin.getLogger()
                    .severe(
                            "Available worlds: "
                                    + Bukkit.getWorlds().stream()
                                            .map(World::getName)
                                            .reduce((a, b) -> a + ", " + b)
                                            .orElse("none"));
            world = Bukkit.getWorlds().get(0);
            plugin.getLogger().warning("Using fallback world: " + world.getName());
        }

        double x = config.getDouble("spawn.x", 0.5);
        double y = config.getDouble("spawn.y", 100);
        double z = config.getDouble("spawn.z", 0.5);
        float yaw = (float) config.getDouble("spawn.yaw", 0);
        float pitch = (float) config.getDouble("spawn.pitch", 0);

        plugin.getLogger()
                .info(
                        "Spawn location: world="
                                + world.getName()
                                + " x="
                                + x
                                + " y="
                                + y
                                + " z="
                                + z
                                + " yaw="
                                + yaw
                                + " pitch="
                                + pitch);

        return new Location(world, x, y, z, yaw, pitch);
    }

    public int getTeleportDelay() {
        return config.getInt("teleport.delay", 3);
    }

    public boolean cancelOnMove() {
        return config.getBoolean("teleport.cancel-on-move", true);
    }

    public String getMessage(String key) {
        String prefix = config.getString("messages.prefix", "");
        String message = config.getString("messages." + key, key);
        return prefix + message;
    }

    public String getRawMessage(String key) {
        return config.getString("messages." + key, key);
    }

    public String getTeleportWarmupMessage(String destination, int seconds) {
        String message =
                config.getString(
                        "teleport.warmup-message",
                        "<gray>Teleporting to <yellow>{destination}</yellow> in <yellow>{seconds}</yellow> seconds...");
        return message.replace("{destination}", destination)
                .replace("{seconds}", String.valueOf(seconds));
    }

    public String getTeleportSuccessMessage(String destination) {
        String message =
                config.getString(
                        "teleport.success-message", "<green>Teleported to {destination}!</green>");
        return message.replace("{destination}", destination);
    }

    public String getTeleportCancelledMessage() {
        return config.getString(
                "teleport.cancelled-message", "<red>Teleport cancelled - you moved!</red>");
    }

    public String getTeleportCountdownMessage(int seconds) {
        String message =
                config.getString("teleport.countdown-message", "<yellow>{seconds}...</yellow>");
        return message.replace("{seconds}", String.valueOf(seconds));
    }

    public List<String> getRules() {
        return config.getStringList("messages.rules");
    }

    // Ban messages
    public String getBanMessage() {
        return config.getString(
                "moderation.ban-message",
                "<red>You have been permanently banned from the server.\n<gray>Reason: <white>{reason}\n<gray>Banned by: <white>{staff}");
    }

    public String getTempBanMessage() {
        return config.getString(
                "moderation.tempban-message",
                "<red>You are temporarily banned from the server.\n<gray>Duration: <white>{duration}\n<gray>Reason: <white>{reason}\n<gray>Banned by: <white>{staff}\n<gray>Expires: <white>{expiry}");
    }

    public String getBanBroadcast() {
        return config.getString(
                "moderation.ban-broadcast",
                "<gold>{staff}</gold> <gray>banned</gray> <red>{player}</red> <gray>for</gray> <white>{reason}");
    }

    public String getTempBanBroadcast() {
        return config.getString(
                "moderation.tempban-broadcast",
                "<gold>{staff}</gold> <gray>temp-banned</gray> <red>{player}</red> <gray>for</gray> <yellow>{duration}</yellow> <gray>-</gray> <white>{reason}");
    }

    public String getUnbanBroadcast() {
        return config.getString(
                "moderation.unban-broadcast",
                "<gold>{staff}</gold> <gray>unbanned</gray> <green>{player}");
    }

    /**
     * Gets the PluginMessenger for sending standardized messages.
     *
     * @return The PluginMessenger instance
     */
    public PluginMessenger getMessenger() {
        return messenger;
    }

    /** Reloads the messenger cache after config reload. */
    public void reload() {
        messenger.reload();
    }
}
