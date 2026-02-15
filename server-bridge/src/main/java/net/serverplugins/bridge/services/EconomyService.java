package net.serverplugins.bridge.services;

import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.serverplugins.bridge.ServerBridge;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public class EconomyService {

    private final ServerBridge plugin;
    private final Economy economy;

    public EconomyService(ServerBridge plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public boolean deposit(String uuidString, double amount) {
        if (economy == null) {
            plugin.getLogger().warning("Economy not available for deposit.");
            return false;
        }

        try {
            UUID uuid = UUID.fromString(uuidString);
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

            EconomyResponse response = economy.depositPlayer(player, amount);

            if (response.transactionSuccess()) {
                plugin.getLogger().info("Deposited $" + amount + " to " + player.getName());
                return true;
            } else {
                plugin.getLogger()
                        .warning(
                                "Failed to deposit to "
                                        + player.getName()
                                        + ": "
                                        + response.errorMessage);
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error depositing to " + uuidString + ": " + e.getMessage());
            return false;
        }
    }

    public boolean withdraw(String uuidString, double amount) {
        if (economy == null) {
            plugin.getLogger().warning("Economy not available for withdrawal.");
            return false;
        }

        try {
            UUID uuid = UUID.fromString(uuidString);
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);

            if (!economy.has(player, amount)) {
                return false;
            }

            EconomyResponse response = economy.withdrawPlayer(player, amount);
            return response.transactionSuccess();
        } catch (Exception e) {
            plugin.getLogger()
                    .warning("Error withdrawing from " + uuidString + ": " + e.getMessage());
            return false;
        }
    }

    public double getBalance(String uuidString) {
        if (economy == null) {
            return 0;
        }

        try {
            UUID uuid = UUID.fromString(uuidString);
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            return economy.getBalance(player);
        } catch (Exception e) {
            plugin.getLogger()
                    .warning("Error getting balance for " + uuidString + ": " + e.getMessage());
            return 0;
        }
    }
}
