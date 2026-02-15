package net.serverplugins.filter.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.serverplugins.filter.ServerFilter;
import net.serverplugins.filter.data.FilterLevel;
import net.serverplugins.filter.data.FilterPreferenceManager;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FilterPlaceholderExpansion extends PlaceholderExpansion {

    private final ServerFilter plugin;
    private final FilterPreferenceManager preferences;

    public FilterPlaceholderExpansion(ServerFilter plugin, FilterPreferenceManager preferences) {
        this.plugin = plugin;
        this.preferences = preferences;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "filter";
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
        if (player == null) {
            return "";
        }

        FilterLevel level = preferences.getFilterLevel(player.getUniqueId());

        return switch (params.toLowerCase()) {
            case "level" -> level.name();
            case "level_display" -> level.getDisplayName();
            case "level_description" -> level.getDescription();
            case "level_lower" -> level.name().toLowerCase();
            default -> null;
        };
    }
}
