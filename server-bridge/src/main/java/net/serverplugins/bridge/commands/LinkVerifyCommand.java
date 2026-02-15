package net.serverplugins.bridge.commands;

import java.sql.*;
import net.serverplugins.api.messages.ColorScheme;
import net.serverplugins.bridge.ServerBridge;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LinkVerifyCommand implements CommandExecutor {

    private final ServerBridge plugin;

    public LinkVerifyCommand(ServerBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            String[] args) {
        var messenger = plugin.getBridgeConfig().getMessenger();

        if (!(sender instanceof Player player)) {
            messenger.sendError(sender, "This command can only be used by players.");
            return true;
        }

        if (args.length != 1) {
            messenger.sendInfo(
                    sender,
                    "Usage: " + ColorScheme.wrap("/verifylink <code>", ColorScheme.COMMAND));
            return true;
        }

        String code = args[0].toUpperCase();

        if (!plugin.getDatabaseManager().isAvailable()) {
            messenger.sendError(
                    sender, "Database connection not available. Please try again later.");
            return true;
        }

        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            try {
                                LinkCodeData codeData = validateCode(code);

                                if (codeData == null) {
                                    plugin.getServer()
                                            .getScheduler()
                                            .runTask(
                                                    plugin,
                                                    () -> {
                                                        messenger.send(player, "link-invalid");
                                                    });
                                    return;
                                }

                                if (isAlreadyLinked(player.getUniqueId().toString())) {
                                    plugin.getServer()
                                            .getScheduler()
                                            .runTask(
                                                    plugin,
                                                    () -> {
                                                        messenger.send(
                                                                player, "link-already-linked");
                                                    });
                                    return;
                                }

                                completeLink(
                                        codeData.discordId,
                                        player.getUniqueId().toString(),
                                        player.getName());
                                deleteCode(code);

                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    messenger.send(player, "link-success");
                                                });

                                plugin.getRedisClient()
                                        .publishLinkComplete(
                                                code,
                                                codeData.discordId,
                                                player.getUniqueId().toString(),
                                                player.getName(),
                                                true);

                            } catch (SQLException e) {
                                plugin.getLogger()
                                        .severe(
                                                "Database error during link verification: "
                                                        + e.getMessage());
                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    messenger.sendError(
                                                            player,
                                                            "An error occurred. Please try again later.");
                                                });
                            }
                        });

        return true;
    }

    private LinkCodeData validateCode(String code) throws SQLException {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "SELECT discord_id, discord_username FROM link_codes WHERE code = ? AND expires_at > NOW()")) {
            stmt.setString(1, code);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new LinkCodeData(
                            rs.getLong("discord_id"), rs.getString("discord_username"));
                }
            }
        }
        return null;
    }

    private boolean isAlreadyLinked(String uuid) throws SQLException {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "SELECT 1 FROM linked_accounts WHERE minecraft_uuid = ?")) {
            stmt.setString(1, uuid);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void completeLink(long discordId, String uuid, String username) throws SQLException {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                """
                     INSERT INTO linked_accounts (discord_id, minecraft_uuid, minecraft_username)
                     VALUES (?, ?, ?)
                     ON DUPLICATE KEY UPDATE
                         minecraft_uuid = VALUES(minecraft_uuid),
                         minecraft_username = VALUES(minecraft_username),
                         updated_at = CURRENT_TIMESTAMP
                     """)) {
            stmt.setLong(1, discordId);
            stmt.setString(2, uuid);
            stmt.setString(3, username);
            stmt.executeUpdate();
        }
    }

    private void deleteCode(String code) throws SQLException {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement("DELETE FROM link_codes WHERE code = ?")) {
            stmt.setString(1, code);
            stmt.executeUpdate();
        }
    }

    private record LinkCodeData(long discordId, String discordUsername) {}
}
