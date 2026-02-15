package net.serverplugins.api.gems;

import java.util.List;
import java.util.UUID;
import org.bukkit.OfflinePlayer;

public class GemsProvider {

    private final GemsRepository repository;

    public GemsProvider(GemsRepository repository) {
        this.repository = repository;
    }

    public int getBalance(UUID playerId) {
        return repository.getBalance(playerId);
    }

    public int getBalance(OfflinePlayer player) {
        return getBalance(player.getUniqueId());
    }

    public boolean has(UUID playerId, int amount) {
        return repository.has(playerId, amount);
    }

    public boolean has(OfflinePlayer player, int amount) {
        return has(player.getUniqueId(), amount);
    }

    public boolean deposit(UUID playerId, int amount) {
        return repository.deposit(playerId, amount);
    }

    public boolean deposit(OfflinePlayer player, int amount) {
        return deposit(player.getUniqueId(), amount);
    }

    public boolean withdraw(UUID playerId, int amount) {
        return repository.withdraw(playerId, amount);
    }

    public boolean withdraw(OfflinePlayer player, int amount) {
        return withdraw(player.getUniqueId(), amount);
    }

    public boolean setBalance(UUID playerId, int amount) {
        return repository.setBalance(playerId, amount);
    }

    public boolean setBalance(OfflinePlayer player, int amount) {
        return setBalance(player.getUniqueId(), amount);
    }

    public boolean transfer(UUID from, UUID to, int amount) {
        if (!repository.withdraw(from, amount)) {
            return false;
        }
        if (!repository.deposit(to, amount)) {
            // Refund on failure
            repository.deposit(from, amount);
            return false;
        }
        return true;
    }

    public String format(int amount) {
        return amount + (amount == 1 ? " Gem" : " Gems");
    }

    public List<GemsRepository.GemsEntry> getTopBalances(int limit) {
        return repository.getTopBalances(limit);
    }
}
