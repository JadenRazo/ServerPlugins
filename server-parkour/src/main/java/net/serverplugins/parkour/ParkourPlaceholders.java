package net.serverplugins.parkour;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.serverplugins.parkour.data.ParkourDatabase;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ParkourPlaceholders extends PlaceholderExpansion {

    private final ServerParkour plugin;

    public ParkourPlaceholders(ServerParkour plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "serverparkour";
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
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        // Server-wide placeholders (don't need player)
        if (params.equalsIgnoreCase("online")) {
            return String.valueOf(plugin.getServer().getOnlinePlayers().size());
        }

        // Leaderboard placeholders (server-wide, don't need player)
        if (params.startsWith("top_")) {
            return handleLeaderboardPlaceholder(params);
        }

        if (player == null) {
            return "";
        }

        if (params.equalsIgnoreCase("highscore")) {
            if (plugin.getDatabase() != null) {
                int highscore = plugin.getDatabase().getHighscore(player.getUniqueId());
                return String.valueOf(highscore);
            }
            return "0";
        }

        if (params.equalsIgnoreCase("xp")) {
            if (plugin.getDatabase() != null) {
                int xp = plugin.getDatabase().getXp(player.getUniqueId());
                return String.valueOf(xp);
            }
            return "0";
        }

        if (params.equalsIgnoreCase("level")) {
            if (plugin.getDatabase() != null) {
                int level = plugin.getDatabase().getLevel(player.getUniqueId());
                return String.valueOf(level);
            }
            return "1";
        }

        if (params.equalsIgnoreCase("playing")) {
            return plugin.getParkourManager().isPlaying(player) ? "Yes" : "No";
        }

        if (params.equalsIgnoreCase("rank")) {
            if (plugin.getDatabase() != null) {
                int rank = plugin.getDatabase().getCachedRank(player.getUniqueId());
                return rank > 0 ? String.valueOf(rank) : "---";
            }
            return "---";
        }

        return null;
    }

    private String handleLeaderboardPlaceholder(String params) {
        if (plugin.getDatabase() == null) {
            return "";
        }

        // Format: top_1_name, top_1_score, top_2_name, etc.
        String[] parts = params.split("_");
        if (parts.length != 3) {
            return null;
        }

        int rank;
        try {
            rank = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }

        if (rank < 1 || rank > 10) {
            return null;
        }

        ParkourDatabase.LeaderboardEntry entry = plugin.getDatabase().getLeaderboardEntry(rank);
        String type = parts[2].toLowerCase();

        if (entry == null) {
            // No entry for this rank - return placeholder fallback
            return switch (type) {
                case "name" -> "---";
                case "score" -> "-";
                default -> null;
            };
        }

        return switch (type) {
            case "name" -> entry.username();
            case "score" -> String.valueOf(entry.score());
            default -> null;
        };
    }
}
