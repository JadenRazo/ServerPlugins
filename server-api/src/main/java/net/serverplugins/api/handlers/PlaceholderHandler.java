package net.serverplugins.api.handlers;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.serverplugins.api.utils.GeyserUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Handler for PlaceholderAPI integration and custom placeholder processing. */
public final class PlaceholderHandler {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%([^%]+)%");
    private static Boolean papiAvailable = null;

    private PlaceholderHandler() {}

    /**
     * Check if PlaceholderAPI is available.
     *
     * @return true if PlaceholderAPI is installed and enabled
     */
    public static boolean isPlaceholderAPIAvailable() {
        if (papiAvailable == null) {
            papiAvailable = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        }
        return papiAvailable;
    }

    /**
     * Parse placeholders in a string for a player.
     *
     * @param player The player context
     * @param text The text containing placeholders
     * @return The text with placeholders replaced
     */
    public static String parse(Player player, String text) {
        if (text == null || text.isEmpty()) return text;

        // First apply built-in placeholders
        text = applyBuiltInPlaceholders(player, text);

        // Then apply PlaceholderAPI if available
        if (isPlaceholderAPIAvailable() && player != null) {
            try {
                text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            } catch (Exception ignored) {
                // PlaceholderAPI not working, continue without it
            }
        }

        return text;
    }

    /**
     * Parse placeholders in a string for an offline player.
     *
     * @param player The offline player context
     * @param text The text containing placeholders
     * @return The text with placeholders replaced
     */
    public static String parse(OfflinePlayer player, String text) {
        if (text == null || text.isEmpty()) return text;

        // First apply built-in placeholders
        if (player.isOnline()) {
            text = applyBuiltInPlaceholders(player.getPlayer(), text);
        }

        // Then apply PlaceholderAPI if available
        if (isPlaceholderAPIAvailable()) {
            try {
                text = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            } catch (Exception ignored) {
                // PlaceholderAPI not working, continue without it
            }
        }

        return text;
    }

    /**
     * Parse placeholders in a list of strings.
     *
     * @param player The player context
     * @param texts The list of texts
     * @return The list with placeholders replaced
     */
    public static List<String> parse(Player player, List<String> texts) {
        if (texts == null) return List.of();
        return texts.stream().map(text -> parse(player, text)).collect(Collectors.toList());
    }

    /** Apply built-in placeholders that don't require PlaceholderAPI. */
    private static String applyBuiltInPlaceholders(Player player, String text) {
        if (player == null) return text;

        text = text.replace("%player%", player.getName());
        text = text.replace("%player_name%", player.getName());
        text = text.replace("%player_displayname%", player.getDisplayName());
        text = text.replace("%player_uuid%", player.getUniqueId().toString());
        text = text.replace("%player_world%", player.getWorld().getName());

        // Platform placeholders
        text = text.replace("%player_platform%", GeyserUtils.getPlatformName(player));
        text =
                text.replace(
                        "%player_is_bedrock%", String.valueOf(GeyserUtils.isBedrockPlayer(player)));

        // Server placeholders
        text = text.replace("%server_name%", Bukkit.getServer().getName());
        text = text.replace("%server_online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        text = text.replace("%server_max_players%", String.valueOf(Bukkit.getMaxPlayers()));

        // Player stats
        text = text.replace("%player_health%", String.valueOf((int) player.getHealth()));
        text = text.replace("%player_max_health%", String.valueOf((int) player.getMaxHealth()));
        text = text.replace("%player_food%", String.valueOf(player.getFoodLevel()));
        text = text.replace("%player_level%", String.valueOf(player.getLevel()));
        text = text.replace("%player_exp%", String.valueOf((int) (player.getExp() * 100)));
        text = text.replace("%player_gamemode%", player.getGameMode().name().toLowerCase());

        // Location
        text = text.replace("%player_x%", String.valueOf(player.getLocation().getBlockX()));
        text = text.replace("%player_y%", String.valueOf(player.getLocation().getBlockY()));
        text = text.replace("%player_z%", String.valueOf(player.getLocation().getBlockZ()));

        return text;
    }

    /**
     * Check if a string contains any placeholders.
     *
     * @param text The text to check
     * @return true if the text contains placeholders
     */
    public static boolean containsPlaceholders(String text) {
        if (text == null) return false;
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        return matcher.find();
    }

    /** Reset cached state. Useful if plugins reload. */
    public static void reset() {
        papiAvailable = null;
    }
}
