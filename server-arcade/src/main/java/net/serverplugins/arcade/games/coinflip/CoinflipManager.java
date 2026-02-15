package net.serverplugins.arcade.games.coinflip;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class CoinflipManager {

    private final ServerArcade plugin;
    private final Map<UUID, CoinflipGame> pendingGames = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private BukkitTask cleanupTask;

    public CoinflipManager(ServerArcade plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    private void startCleanupTask() {
        int expiryTime = plugin.getArcadeConfig().getCoinflipExpiryTime();
        cleanupTask =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    long now = System.currentTimeMillis();
                                    pendingGames
                                            .entrySet()
                                            .removeIf(
                                                    entry -> {
                                                        CoinflipGame game = entry.getValue();
                                                        if (now - game.getCreatedAt()
                                                                > expiryTime * 1000L) {
                                                            Player player =
                                                                    Bukkit.getPlayer(
                                                                            game.getCreatorId());
                                                            if (player != null
                                                                    && ServerArcade.getEconomy()
                                                                            != null) {
                                                                ServerArcade.getEconomy()
                                                                        .depositPlayer(
                                                                                player,
                                                                                game.getAmount());
                                                                TextUtil.send(
                                                                        player,
                                                                        "<yellow>Your coinflip challenge expired and your bet was returned.");
                                                            }
                                                            return true;
                                                        }
                                                        return false;
                                                    });
                                },
                                20L * 30,
                                20L * 30);
    }

    public CoinflipGame createGame(UUID creatorId, double amount) {
        if (pendingGames.containsKey(creatorId)) {
            return null;
        }

        CoinflipGame game = new CoinflipGame(creatorId, amount);
        pendingGames.put(creatorId, game);
        return game;
    }

    public CoinflipResult acceptGame(UUID accepterId, UUID creatorId) {
        CoinflipGame game = pendingGames.remove(creatorId);
        if (game == null) return null;

        boolean creatorWins = random.nextBoolean();
        UUID winnerId = creatorWins ? creatorId : accepterId;
        UUID loserId = creatorWins ? accepterId : creatorId;

        double totalPot = game.getAmount() * 2;

        return new CoinflipResult(winnerId, loserId, totalPot, creatorWins ? "Heads" : "Tails");
    }

    public boolean cancelGame(UUID creatorId) {
        CoinflipGame game = pendingGames.remove(creatorId);
        if (game != null) {
            Player player = Bukkit.getPlayer(creatorId);
            if (player != null && ServerArcade.getEconomy() != null) {
                ServerArcade.getEconomy().depositPlayer(player, game.getAmount());
            }
            return true;
        }
        return false;
    }

    public Collection<CoinflipGame> getPendingGames() {
        return pendingGames.values();
    }

    public CoinflipGame getGame(UUID creatorId) {
        return pendingGames.get(creatorId);
    }

    public boolean hasGame(UUID creatorId) {
        return pendingGames.containsKey(creatorId);
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        for (CoinflipGame game : pendingGames.values()) {
            Player player = Bukkit.getPlayer(game.getCreatorId());
            if (player != null && ServerArcade.getEconomy() != null) {
                ServerArcade.getEconomy().depositPlayer(player, game.getAmount());
            }
        }
        pendingGames.clear();
    }

    public static class CoinflipGame {
        private final UUID creatorId;
        private final double amount;
        private final long createdAt;

        public CoinflipGame(UUID creatorId, double amount) {
            this.creatorId = creatorId;
            this.amount = amount;
            this.createdAt = System.currentTimeMillis();
        }

        public UUID getCreatorId() {
            return creatorId;
        }

        public double getAmount() {
            return amount;
        }

        public long getCreatedAt() {
            return createdAt;
        }
    }

    public record CoinflipResult(UUID winnerId, UUID loserId, double pot, String result) {}
}
