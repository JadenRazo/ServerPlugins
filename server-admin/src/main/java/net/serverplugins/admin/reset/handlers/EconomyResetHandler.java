package net.serverplugins.admin.reset.handlers;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.serverplugins.admin.ServerAdmin;
import net.serverplugins.admin.reset.ResetResult;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyResetHandler implements ResetHandler {

    private final ServerAdmin plugin;

    public EconomyResetHandler(ServerAdmin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<ResetResult> execute(
            UUID targetUuid, String targetName, Player staff) {
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        RegisteredServiceProvider<?> provider =
                                Bukkit.getServicesManager()
                                        .getRegistration(
                                                Class.forName(
                                                        "net.milkbowl.vault.economy.Economy"));

                        if (provider == null) {
                            return ResetResult.failure("Economy (Vault) not available");
                        }

                        Object economy = provider.getProvider();
                        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUuid);

                        Method getBalance =
                                economy.getClass().getMethod("getBalance", OfflinePlayer.class);
                        double currentBalance = (Double) getBalance.invoke(economy, target);

                        double startingBalance =
                                plugin.getConfig().getDouble("reset.economy-starting-balance", 0.0);

                        if (currentBalance > 0) {
                            Method withdraw =
                                    economy.getClass()
                                            .getMethod(
                                                    "withdrawPlayer",
                                                    OfflinePlayer.class,
                                                    double.class);
                            withdraw.invoke(economy, target, currentBalance);
                        }

                        if (startingBalance > 0) {
                            Method deposit =
                                    economy.getClass()
                                            .getMethod(
                                                    "depositPlayer",
                                                    OfflinePlayer.class,
                                                    double.class);
                            deposit.invoke(economy, target, startingBalance);
                        }

                        String details =
                                String.format("$%.2f -> $%.2f", currentBalance, startingBalance);
                        return ResetResult.success(details);
                    } catch (ClassNotFoundException e) {
                        return ResetResult.failure("Vault not available");
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to reset economy: " + e.getMessage());
                        return ResetResult.failure("Error resetting economy: " + e.getMessage());
                    }
                });
    }
}
