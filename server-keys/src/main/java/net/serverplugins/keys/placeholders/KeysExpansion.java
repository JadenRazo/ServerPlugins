package net.serverplugins.keys.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.serverplugins.keys.ServerKeys;
import net.serverplugins.keys.cache.StatsCache;
import net.serverplugins.keys.models.KeyType;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for ServerKeys. Uses in-memory cache to avoid blocking main thread on
 * placeholder requests.
 *
 * <p>Available placeholders: - %serverkeys_total% - Total keys received (all types) -
 * %serverkeys_total_crate% - Total crate keys received - %serverkeys_total_dungeon% - Total dungeon
 * keys received - %serverkeys_received_crate_<key>% - Specific crate key received count -
 * %serverkeys_received_dungeon_<key>% - Specific dungeon key received count
 */
public class KeysExpansion extends PlaceholderExpansion {

    private final ServerKeys plugin;
    private final StatsCache statsCache;

    public KeysExpansion(ServerKeys plugin, StatsCache statsCache) {
        this.plugin = plugin;
        this.statsCache = statsCache;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "serverkeys";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ServerPlugins";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "0";

        // All lookups use the in-memory cache - no DB blocking

        // %serverkeys_total%
        if (params.equalsIgnoreCase("total")) {
            return String.valueOf(statsCache.getTotalReceived(player.getUniqueId()));
        }

        // %serverkeys_total_crate%
        if (params.equalsIgnoreCase("total_crate")) {
            return String.valueOf(
                    statsCache.getTotalReceivedByType(player.getUniqueId(), KeyType.CRATE));
        }

        // %serverkeys_total_dungeon%
        if (params.equalsIgnoreCase("total_dungeon")) {
            return String.valueOf(
                    statsCache.getTotalReceivedByType(player.getUniqueId(), KeyType.DUNGEON));
        }

        // %serverkeys_received_crate_<key>%
        if (params.startsWith("received_crate_")) {
            String keyName = params.substring("received_crate_".length());
            return String.valueOf(
                    statsCache.getReceivedForKey(player.getUniqueId(), KeyType.CRATE, keyName));
        }

        // %serverkeys_received_dungeon_<key>%
        if (params.startsWith("received_dungeon_")) {
            String keyName = params.substring("received_dungeon_".length());
            return String.valueOf(
                    statsCache.getReceivedForKey(player.getUniqueId(), KeyType.DUNGEON, keyName));
        }

        return null;
    }
}
