package net.serverplugins.admin.reset.handlers;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.reset.ResetResult;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class ClaimResetHandler implements ResetHandler {

    private final ServerAdmin plugin;

    public ClaimResetHandler(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<ResetResult> execute(
            UUID targetUuid, String targetName, Player staff) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        Plugin claimPlugin = Bukkit.getPluginManager().getPlugin("ServerClaim");
                        if (claimPlugin == null || !claimPlugin.isEnabled()) {
                            return ResetResult.failure("ServerClaim plugin not available");
                        }

                        Method getClaimManager =
                                claimPlugin.getClass().getMethod("getClaimManager");
                        Object claimManager = getClaimManager.invoke(claimPlugin);

                        Method getPlayerClaims =
                                claimManager.getClass().getMethod("getPlayerClaims", UUID.class);
                        Object claims = getPlayerClaims.invoke(claimManager, targetUuid);

                        int claimsDeleted = 0;
                        if (claims instanceof java.util.Collection<?> claimList) {
                            Method deleteClaim =
                                    claimManager.getClass().getMethod("deleteClaim", Object.class);
                            for (Object claim : claimList) {
                                try {
                                    deleteClaim.invoke(claimManager, claim);
                                    claimsDeleted++;
                                } catch (Exception e) {
                                    plugin.getLogger()
                                            .warning("Failed to delete claim: " + e.getMessage());
                                }
                            }
                        }

                        return ResetResult.success("Deleted " + claimsDeleted + " claims");
                    } catch (NoSuchMethodException e) {
                        return ResetResult.failure("ServerClaim API not compatible");
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to reset claims: " + e.getMessage());
                        return ResetResult.failure("Error resetting claims: " + e.getMessage());
                    }
                });
    }
}
