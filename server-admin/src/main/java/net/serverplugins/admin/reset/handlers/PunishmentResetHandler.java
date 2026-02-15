package net.serverplugins.admin.reset.handlers;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.punishment.PunishmentRepository;
import net.serverplugins.admin.reset.ResetResult;
import org.bukkit.entity.Player;

public class PunishmentResetHandler implements ResetHandler {

    private final ServerAdmin plugin;
    private final PunishmentRepository repository;

    public PunishmentResetHandler(ServerAdmin plugin, PunishmentRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    @Override
    public CompletableFuture<ResetResult> execute(
            UUID targetUuid, String targetName, Player staff) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        int count = repository.getPunishmentCount(targetUuid);

                        repository.resetAllOffenses(targetUuid);

                        return ResetResult.success("Cleared " + count + " punishment records");
                    } catch (Exception e) {
                        plugin.getLogger()
                                .warning("Failed to reset punishments: " + e.getMessage());
                        return ResetResult.failure(
                                "Error resetting punishments: " + e.getMessage());
                    }
                });
    }
}
