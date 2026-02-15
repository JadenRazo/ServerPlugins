package net.serverplugins.admin.reset.handlers;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serverplugins.admin.reset.ResetResult;
import org.bukkit.entity.Player;

public interface ResetHandler {
    CompletableFuture<ResetResult> execute(UUID targetUuid, String targetName, Player staff);
}
