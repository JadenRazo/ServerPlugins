package net.serverplugins.api.gems;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GemsPlaceholderExpansion extends PlaceholderExpansion {

    private final GemsProvider gemsProvider;
    private final String version;

    public GemsPlaceholderExpansion(GemsProvider gemsProvider, String version) {
        this.gemsProvider = gemsProvider;
        this.version = version;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "serverplugins";
    }

    @Override
    public @NotNull String getAuthor() {
        return "ServerPlugins";
    }

    @Override
    public @NotNull String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "0";

        // %serverplugins_gems%
        if (params.equalsIgnoreCase("gems")) {
            return String.valueOf(gemsProvider.getBalance(player));
        }

        // %serverplugins_gems_formatted%
        if (params.equalsIgnoreCase("gems_formatted")) {
            return gemsProvider.format(gemsProvider.getBalance(player));
        }

        return null;
    }
}
