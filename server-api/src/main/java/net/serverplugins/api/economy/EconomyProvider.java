package net.serverplugins.api.economy;

import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyProvider {

    private final Economy economy;

    public EconomyProvider() {
        RegisteredServiceProvider<Economy> rsp =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        this.economy = (rsp != null) ? rsp.getProvider() : null;
    }

    public boolean isAvailable() {
        return economy != null;
    }

    public double getBalance(OfflinePlayer player) {
        return isAvailable() ? economy.getBalance(player) : 0.0;
    }

    public double getBalance(UUID uuid) {
        return getBalance(Bukkit.getOfflinePlayer(uuid));
    }

    public boolean has(OfflinePlayer player, double amount) {
        return isAvailable() && economy.has(player, amount);
    }

    public boolean has(UUID uuid, double amount) {
        return has(Bukkit.getOfflinePlayer(uuid), amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        return isAvailable() && economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean withdraw(UUID uuid, double amount) {
        return withdraw(Bukkit.getOfflinePlayer(uuid), amount);
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        return isAvailable() && economy.depositPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(UUID uuid, double amount) {
        return deposit(Bukkit.getOfflinePlayer(uuid), amount);
    }

    public boolean setBalance(OfflinePlayer player, double amount) {
        if (!isAvailable()) return false;
        double current = getBalance(player);
        if (current > amount) return withdraw(player, current - amount);
        else if (current < amount) return deposit(player, amount - current);
        return true;
    }

    public String format(double amount) {
        return isAvailable() ? economy.format(amount) : String.valueOf(amount);
    }

    public String getCurrencyName() {
        return isAvailable() ? economy.currencyNameSingular() : "money";
    }

    public Economy getEconomy() {
        return economy;
    }
}
