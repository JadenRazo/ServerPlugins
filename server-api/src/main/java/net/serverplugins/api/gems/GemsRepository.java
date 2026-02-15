package net.serverplugins.api.gems;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.serverplugins.api.database.Database;

public class GemsRepository {

    private final Database database;

    public GemsRepository(Database database) {
        this.database = database;
    }

    public int getBalance(UUID playerId) {
        return database.query(
                "SELECT balance FROM server_gems WHERE player_uuid = ?",
                rs -> rs.next() ? rs.getInt("balance") : 0,
                playerId.toString());
    }

    public boolean has(UUID playerId, int amount) {
        return getBalance(playerId) >= amount;
    }

    public boolean deposit(UUID playerId, int amount) {
        if (amount <= 0) return false;
        try {
            database.execute(
                    "INSERT INTO server_gems (player_uuid, balance, total_earned) VALUES (?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE balance = balance + ?, total_earned = total_earned + ?",
                    playerId.toString(),
                    amount,
                    (long) amount,
                    amount,
                    (long) amount);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean withdraw(UUID playerId, int amount) {
        if (amount <= 0) return false;
        try {
            int rows =
                    database.executeUpdate(
                            "UPDATE server_gems SET balance = balance - ?, total_spent = total_spent + ? "
                                    + "WHERE player_uuid = ? AND balance >= ?",
                            amount,
                            (long) amount,
                            playerId.toString(),
                            amount);
            return rows > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean setBalance(UUID playerId, int amount) {
        if (amount < 0) return false;
        try {
            database.execute(
                    "INSERT INTO server_gems (player_uuid, balance) VALUES (?, ?) "
                            + "ON DUPLICATE KEY UPDATE balance = ?",
                    playerId.toString(),
                    amount,
                    amount);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public List<GemsEntry> getTopBalances(int limit) {
        return database.query(
                "SELECT player_uuid, balance FROM server_gems ORDER BY balance DESC LIMIT ?",
                rs -> {
                    List<GemsEntry> entries = new ArrayList<>();
                    while (rs.next()) {
                        entries.add(
                                new GemsEntry(
                                        UUID.fromString(rs.getString("player_uuid")),
                                        rs.getInt("balance")));
                    }
                    return entries;
                },
                limit);
    }

    public record GemsEntry(UUID uuid, int balance) {}
}
