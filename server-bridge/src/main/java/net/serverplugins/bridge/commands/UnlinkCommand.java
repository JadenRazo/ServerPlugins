package net.serverplugins.bridge.commands;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import net.serverplugins.bridge.ServerBridge;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class UnlinkCommand implements CommandExecutor {

    private final ServerBridge plugin;

    public UnlinkCommand(ServerBridge plugin) {
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
                                String uuid = player.getUniqueId().toString();

                                if (!isLinked(uuid)) {
                                    plugin.getServer()
                                            .getScheduler()
                                            .runTask(
                                                    plugin,
                                                    () -> {
                                                        messenger.send(player, "unlink-not-linked");
                                                    });
                                    return;
                                }

                                unlinkAccount(uuid);

                                plugin.getServer()
                                        .getScheduler()
                                        .runTask(
                                                plugin,
                                                () -> {
                                                    messenger.send(player, "unlink-success");
                                                });

                            } catch (SQLException e) {
                                plugin.getLogger()
                                        .severe("Database error during unlink: " + e.getMessage());
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

    private boolean isLinked(String uuid) throws SQLException {
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

    private void unlinkAccount(String uuid) throws SQLException {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "DELETE FROM linked_accounts WHERE minecraft_uuid = ?")) {
            stmt.setString(1, uuid);
            stmt.executeUpdate();
        }
    }
}
