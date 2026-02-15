package net.serverplugins.admin.reset;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.punishment.PunishmentRepository;
import net.serverplugins.admin.reset.handlers.ClaimResetHandler;
import net.serverplugins.admin.reset.handlers.EconomyResetHandler;
import net.serverplugins.admin.reset.handlers.PlaytimeResetHandler;
import net.serverplugins.admin.reset.handlers.PunishmentResetHandler;
import net.serverplugins.admin.reset.handlers.RankResetHandler;
import net.serverplugins.admin.reset.handlers.ResetHandler;
import org.bukkit.entity.Player;

public class ResetManager {

    private final ServerAdmin plugin;
    private final PunishmentRepository repository;
    private final Map<ResetType, ResetHandler> handlers = new EnumMap<>(ResetType.class);

    public ResetManager(ServerAdmin plugin, PunishmentRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        registerHandlers();
    }

    private void registerHandlers() {
        handlers.put(ResetType.CLAIMS, new ClaimResetHandler(plugin));
        handlers.put(ResetType.ECONOMY, new EconomyResetHandler(plugin));
        handlers.put(ResetType.PLAYTIME, new PlaytimeResetHandler(plugin));
        handlers.put(ResetType.RANK, new RankResetHandler(plugin));
        handlers.put(ResetType.PUNISHMENTS, new PunishmentResetHandler(plugin, repository));
    }

    public CompletableFuture<ResetResult> reset(
            UUID targetUuid, String targetName, ResetType type, Player staff) {
        if (type == ResetType.ALL) {
            return resetAll(targetUuid, targetName, staff);
        }

        ResetHandler handler = handlers.get(type);
        if (handler == null) {
            return CompletableFuture.completedFuture(
                    ResetResult.failure("No handler for type: " + type.name()));
        }

        return handler.execute(targetUuid, targetName, staff)
                .thenApply(
                        result -> {
                            if (result.isSuccess()) {
                                logReset(targetUuid, targetName, staff, type, result.getDetails());
                            }
                            return result;
                        });
    }

    private CompletableFuture<ResetResult> resetAll(
            UUID targetUuid, String targetName, Player staff) {
        List<CompletableFuture<ResetResult>> futures = new ArrayList<>();
        List<String> results = new ArrayList<>();

        for (ResetType type : ResetType.values()) {
            if (type == ResetType.ALL) continue;

            ResetHandler handler = handlers.get(type);
            if (handler != null) {
                futures.add(
                        handler.execute(targetUuid, targetName, staff)
                                .thenApply(
                                        result -> {
                                            if (result.isSuccess()) {
                                                results.add(
                                                        type.getDisplayName()
                                                                + ": "
                                                                + result.getDetails());
                                            }
                                            return result;
                                        }));
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(
                        v -> {
                            String details = String.join(", ", results);
                            logReset(targetUuid, targetName, staff, ResetType.ALL, details);
                            return ResetResult.success("All data reset");
                        });
    }

    private void logReset(
            UUID targetUuid, String targetName, Player staff, ResetType type, String details) {
        repository.logReset(
                targetUuid,
                targetName,
                staff != null ? staff.getUniqueId() : null,
                staff != null ? staff.getName() : "Console",
                type.name(),
                details);
    }

    public double getStartingBalance() {
        return plugin.getConfig().getDouble("reset.economy-starting-balance", 0.0);
    }

    public String getDefaultRank() {
        return plugin.getConfig().getString("reset.default-rank", "default");
    }
}
