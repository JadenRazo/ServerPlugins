package net.serverplugins.commands.data;

import java.time.Instant;
import java.util.Date;
import net.serverplugins.commands.ServerCommands;
import org.bukkit.BanList;
import org.bukkit.Bukkit;

public class BanManager {

    private final ServerCommands plugin;

    public BanManager(ServerCommands plugin) {
        this.plugin = plugin;
    }

    /** Ban a player permanently */
    public void ban(String playerName, String reason, String source) {
        BanList banList = Bukkit.getBanList(BanList.Type.NAME);
        banList.addBan(playerName, reason, (Date) null, source);
    }

    /** Ban a player temporarily */
    public void tempBan(String playerName, long durationMillis, String reason, String source) {
        BanList banList = Bukkit.getBanList(BanList.Type.NAME);
        Date expiration = Date.from(Instant.now().plusMillis(durationMillis));
        banList.addBan(playerName, reason, expiration, source);
    }

    /** Unban a player */
    public void unban(String playerName) {
        BanList banList = Bukkit.getBanList(BanList.Type.NAME);
        banList.pardon(playerName);
    }

    /** Check if a player is banned */
    public boolean isBanned(String playerName) {
        BanList banList = Bukkit.getBanList(BanList.Type.NAME);
        return banList.isBanned(playerName);
    }

    /** Get ban reason */
    public String getBanReason(String playerName) {
        BanList banList = Bukkit.getBanList(BanList.Type.NAME);
        if (banList.getBanEntry(playerName) != null) {
            return banList.getBanEntry(playerName).getReason();
        }
        return null;
    }

    /** Parse duration string (e.g., "1d", "12h", "30m") into milliseconds */
    public long parseDuration(String duration) throws IllegalArgumentException {
        if (duration == null || duration.isEmpty()) {
            throw new IllegalArgumentException("Duration cannot be empty");
        }

        duration = duration.toLowerCase().trim();
        String unit = duration.substring(duration.length() - 1);
        String numberStr = duration.substring(0, duration.length() - 1);

        try {
            long number = Long.parseLong(numberStr);

            return switch (unit) {
                case "s" -> number * 1000; // seconds
                case "m" -> number * 1000 * 60; // minutes
                case "h" -> number * 1000 * 60 * 60; // hours
                case "d" -> number * 1000 * 60 * 60 * 24; // days
                case "w" -> number * 1000 * 60 * 60 * 24 * 7; // weeks
                default ->
                        throw new IllegalArgumentException(
                                "Invalid duration unit: " + unit + ". Use s, m, h, d, or w");
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid duration format: " + duration);
        }
    }

    /** Format duration milliseconds into readable string */
    public String formatDuration(long millis) {
        long days = millis / (1000 * 60 * 60 * 24);
        long hours = (millis % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60);
        long minutes = (millis % (1000 * 60 * 60)) / (1000 * 60);

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append(" day").append(days != 1 ? "s" : "");
        }
        if (hours > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(hours).append(" hour").append(hours != 1 ? "s" : "");
        }
        if (minutes > 0) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(minutes).append(" minute").append(minutes != 1 ? "s" : "");
        }
        if (sb.length() == 0) {
            sb.append("less than a minute");
        }
        return sb.toString();
    }
}
