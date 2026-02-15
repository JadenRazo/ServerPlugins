package net.serverplugins.core.placeholders;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.serverplugins.core.ServerCore;
import net.serverplugins.core.features.Feature;
import net.serverplugins.core.features.PerPlayerFeature;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CorePlaceholderExpansion extends PlaceholderExpansion {

    private final ServerCore plugin;

    public CorePlaceholderExpansion(ServerCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "servercore";
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
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        // %servercore_status_<feature>%
        if (params.startsWith("status_")) {
            String featureName = params.substring(7); // Remove "status_" prefix
            Feature feature = plugin.getFeatures().get(featureName.toLowerCase());

            if (feature == null) {
                return null;
            }

            boolean enabled = feature.isEnabled();

            // Check for per-player toggle if applicable
            if (player != null && player.isOnline() && feature instanceof PerPlayerFeature) {
                PerPlayerFeature ppf = (PerPlayerFeature) feature;
                enabled = ppf.isEnabledForPlayer((Player) player);
            }

            return enabled
                    ? plugin.getConfig()
                            .getString("settings.placeholders.enabled-text", "<green>Enabled")
                    : plugin.getConfig()
                            .getString("settings.placeholders.disabled-text", "<red>Disabled");
        }

        // %servercore_points%
        if (params.equals("points")) {
            if (player == null) return "0";

            String skriptVar =
                    plugin.getConfig()
                            .getString("settings.placeholders.points-variable", "points::{uuid}");
            String placeholder =
                    "%" + skriptVar.replace("{uuid}", player.getUniqueId().toString()) + "%";

            // Parse Skript variable via PlaceholderAPI
            return PlaceholderAPI.setPlaceholders(player, placeholder);
        }

        return null;
    }
}
