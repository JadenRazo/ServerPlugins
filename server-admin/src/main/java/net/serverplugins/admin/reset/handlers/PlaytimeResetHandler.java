package net.serverplugins.admin.reset.handlers;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.reset.ResetResult;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class PlaytimeResetHandler implements ResetHandler {

    private final ServerAdmin plugin;

    public PlaytimeResetHandler(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<ResetResult> execute(
            UUID targetUuid, String targetName, Player staff) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        Plugin commandsPlugin =
                                Bukkit.getPluginManager().getPlugin("ServerCommands");
                        if (commandsPlugin == null || !commandsPlugin.isEnabled()) {
                            return ResetResult.failure("ServerCommands plugin not available");
                        }

                        try {
                            Method getPlaytimeManager =
                                    commandsPlugin.getClass().getMethod("getPlaytimeManager");
                            Object playtimeManager = getPlaytimeManager.invoke(commandsPlugin);

                            Method resetPlaytime =
                                    playtimeManager
                                            .getClass()
                                            .getMethod("resetPlaytime", UUID.class);
                            resetPlaytime.invoke(playtimeManager, targetUuid);

                            return ResetResult.success("Playtime reset to 0");
                        } catch (NoSuchMethodException e) {
                            Method getRepository =
                                    commandsPlugin.getClass().getMethod("getRepository");
                            Object repository = getRepository.invoke(commandsPlugin);

                            Method savePlaytime =
                                    repository
                                            .getClass()
                                            .getMethod("savePlaytime", UUID.class, long.class);
                            savePlaytime.invoke(repository, targetUuid, 0L);

                            return ResetResult.success("Playtime reset to 0");
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to reset playtime: " + e.getMessage());
                        return ResetResult.failure("Error resetting playtime: " + e.getMessage());
                    }
                });
    }
}
