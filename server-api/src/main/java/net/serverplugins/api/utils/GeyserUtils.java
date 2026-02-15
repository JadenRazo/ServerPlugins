package net.serverplugins.api.utils;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Utility class for detecting Bedrock players connected via Geyser/Floodgate. */
public final class GeyserUtils {

    private static final String BEDROCK_UUID_PREFIX = "00000000-0000-0000-";
    private static Boolean floodgateAvailable = null;

    private GeyserUtils() {}

    /**
     * Check if a player is connected via Bedrock (Geyser/Floodgate).
     *
     * @param player The player to check
     * @return true if the player is on Bedrock Edition
     */
    public static boolean isBedrockPlayer(Player player) {
        if (player == null) return false;
        return isBedrockPlayer(player.getUniqueId());
    }

    /**
     * Check if a UUID belongs to a Bedrock player.
     *
     * @param uuid The UUID to check
     * @return true if the UUID is a Bedrock player UUID
     */
    public static boolean isBedrockPlayer(UUID uuid) {
        if (uuid == null) return false;

        // Try Floodgate API first
        if (isFloodgateAvailable()) {
            try {
                return checkFloodgateApi(uuid);
            } catch (Exception ignored) {
                // Fall through to UUID check
            }
        }

        // Fallback: Check UUID prefix
        // Floodgate/Geyser uses UUIDs starting with 00000000-0000-0000-
        return uuid.toString().startsWith(BEDROCK_UUID_PREFIX);
    }

    /**
     * Check if Floodgate plugin is available.
     *
     * @return true if Floodgate is installed and enabled
     */
    public static boolean isFloodgateAvailable() {
        if (floodgateAvailable == null) {
            floodgateAvailable = Bukkit.getPluginManager().isPluginEnabled("floodgate");
        }
        return floodgateAvailable;
    }

    /**
     * Check if Geyser plugin is available.
     *
     * @return true if Geyser is installed and enabled
     */
    public static boolean isGeyserAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")
                || Bukkit.getPluginManager().isPluginEnabled("Geyser-Velocity")
                || Bukkit.getPluginManager().isPluginEnabled("Geyser-BungeeCord");
    }

    /**
     * Get the Bedrock username for a player (without the prefix). Only works if Floodgate is
     * available.
     *
     * @param player The player
     * @return The Bedrock username, or the Java username if not on Bedrock
     */
    public static String getBedrockUsername(Player player) {
        if (!isBedrockPlayer(player)) {
            return player.getName();
        }

        // Try to get the real Bedrock username from Floodgate
        if (isFloodgateAvailable()) {
            try {
                Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
                Object api = apiClass.getMethod("getInstance").invoke(null);
                Object floodgatePlayer =
                        apiClass.getMethod("getPlayer", UUID.class)
                                .invoke(api, player.getUniqueId());

                if (floodgatePlayer != null) {
                    Class<?> playerClass =
                            Class.forName("org.geysermc.floodgate.api.player.FloodgatePlayer");
                    return (String) playerClass.getMethod("getUsername").invoke(floodgatePlayer);
                }
            } catch (Exception ignored) {
                // Fall through
            }
        }

        // Remove common Bedrock prefixes
        String name = player.getName();
        if (name.startsWith(".")) {
            return name.substring(1);
        }
        if (name.startsWith("*")) {
            return name.substring(1);
        }

        return name;
    }

    /**
     * Get the platform name for a player.
     *
     * @param player The player
     * @return "Bedrock" or "Java"
     */
    public static String getPlatformName(Player player) {
        return isBedrockPlayer(player) ? "Bedrock" : "Java";
    }

    private static boolean checkFloodgateApi(UUID uuid) throws Exception {
        Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
        Object api = apiClass.getMethod("getInstance").invoke(null);
        Boolean result =
                (Boolean) apiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(api, uuid);
        return result != null && result;
    }

    /** Reset cached state. Useful if plugins reload. */
    public static void reset() {
        floodgateAvailable = null;
    }
}
