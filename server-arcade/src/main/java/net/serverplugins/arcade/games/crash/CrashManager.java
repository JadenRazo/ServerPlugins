package net.serverplugins.arcade.games.crash;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameResult;
import net.serverplugins.arcade.utils.AuditLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class CrashManager {

    // Game states
    public enum State {
        WAITING, // Post-crash cooldown
        BETTING, // 15s betting window
        RUNNING, // Game active, multiplier climbing
        CRASHED // Just crashed, showing results
    }

    private final ServerArcade plugin;
    private final Map<UUID, CrashBet> activeBets = new ConcurrentHashMap<>();
    // SECURITY: Use SecureRandom instead of Random for unpredictable outcomes
    // This is a temporary fix until provably fair system is implemented
    private final SecureRandom random = new SecureRandom();
    private final List<Double> recentCrashes = Collections.synchronizedList(new ArrayList<>());

    private double currentMultiplier = 1.0;
    private double crashPoint;
    private State currentState = State.WAITING;
    private int bettingTimeRemaining = 0;
    private BukkitTask gameTask;

    public CrashManager(ServerArcade plugin) {
        this.plugin = plugin;
        startCycle(); // Start the game loop
    }

    /** Start the complete game cycle (waiting -> betting -> running -> crashed) */
    private void startCycle() {
        // Start with betting phase
        enterBettingPhase();
    }

    /** Enter the betting phase (15 second window) */
    private void enterBettingPhase() {
        currentState = State.BETTING;
        currentMultiplier = 1.0;
        bettingTimeRemaining =
                plugin.getCrashType().getBettingDuration() / 20; // Convert ticks to seconds

        // Generate crash point for this round
        crashPoint = generateCrashPoint();

        // Countdown timer (updates every second)
        gameTask =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    bettingTimeRemaining--;

                                    // Notify active GUIs about countdown
                                    CrashGameGui.updateAllGuis();

                                    if (bettingTimeRemaining <= 0) {
                                        if (gameTask != null) {
                                            gameTask.cancel();
                                            gameTask = null;
                                        }
                                        startGame();
                                    }
                                },
                                0L,
                                20L); // Every 1 second (20 ticks)
    }

    /** Start the actual game (multiplier climbing) */
    public void startGame() {
        currentState = State.RUNNING;
        currentMultiplier = 1.0;

        int tickRate = plugin.getArcadeConfig().getCrashTickRate();

        gameTask =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    // Multiplier growth with acceleration
                                    currentMultiplier += 0.01 * (1 + (currentMultiplier - 1) * 0.1);

                                    if (currentMultiplier >= crashPoint) {
                                        crash();
                                        return;
                                    }

                                    broadcastMultiplier();
                                },
                                0L,
                                tickRate);
    }

    private double generateCrashPoint() {
        double houseEdge = plugin.getArcadeConfig().getCrashHouseEdge();
        double maxMultiplier = plugin.getArcadeConfig().getCrashMaxMultiplier();

        double r = random.nextDouble();
        if (r < houseEdge) {
            return 1.0;
        }

        double crash = 1.0 / (1.0 - r);
        return Math.min(crash, maxMultiplier);
    }

    public boolean placeBet(UUID playerId, double amount) {
        // Only allow betting during BETTING or early RUNNING state
        if (currentState != State.BETTING
                && !(currentState == State.RUNNING && currentMultiplier < 1.5)) {
            return false;
        }

        CrashBet newBet = new CrashBet(playerId, amount);
        // Atomic putIfAbsent - only succeeds if key doesn't exist
        boolean success = activeBets.putIfAbsent(playerId, newBet) == null;

        if (success) {
            Player player = Bukkit.getPlayer(playerId);
            String playerName = player != null ? player.getName() : "Unknown";
            AuditLogger.logPlayerAction(
                    "crash",
                    playerId,
                    playerName,
                    "BET_PLACED",
                    String.format(
                            "Amount: $%d, Multiplier: %.2fx", (int) amount, currentMultiplier));
        }

        return success;
    }

    public GameResult cashOut(UUID playerId) {
        CrashBet bet = activeBets.remove(playerId);
        if (bet == null || bet.isCashedOut()) return null;

        bet.cashOut(currentMultiplier);
        int payout = (int) (bet.getAmount() * bet.getCashOutMultiplier());

        // Log cashout
        Player player = Bukkit.getPlayer(playerId);
        String playerName = player != null ? player.getName() : "Unknown";
        AuditLogger.logCrashGame(
                playerId,
                playerName,
                (int) bet.getAmount(),
                crashPoint,
                bet.getCashOutMultiplier(),
                payout);

        return GameResult.win(bet.getAmount(), bet.getCashOutMultiplier());
    }

    private void crash() {
        currentState = State.CRASHED;
        if (gameTask != null) {
            gameTask.cancel();
            gameTask = null;
        }

        // Add to crash history
        recentCrashes.add(crashPoint);
        if (recentCrashes.size() > 50) {
            recentCrashes.remove(0);
        }

        // Log all losing bets
        for (CrashBet bet : activeBets.values()) {
            if (!bet.isCashedOut()) {
                Player player = Bukkit.getPlayer(bet.getPlayerId());
                if (player != null) {
                    TextUtil.send(
                            player,
                            plugin.getArcadeConfig()
                                    .getMessage("lose")
                                    .replace("${amount}", String.format("%.0f", bet.getAmount())));

                    // Log the loss
                    AuditLogger.logCrashGame(
                            bet.getPlayerId(),
                            player.getName(),
                            (int) bet.getAmount(),
                            crashPoint,
                            null,
                            0);

                    // Track statistics
                    if (plugin.getStatisticsTracker() != null) {
                        plugin.getStatisticsTracker()
                                .recordCrashGame(
                                        bet.getPlayerId(),
                                        player.getName(),
                                        (int) bet.getAmount(),
                                        0.0,
                                        0,
                                        false);
                    }
                }
            }
        }

        broadcastCrash();

        // Notify all open GUIs about the crash
        CrashGameGui.onGameCrash(crashPoint);

        // Log the game crash event
        AuditLogger.logGameEvent(
                "crash",
                "GAME_CRASHED",
                String.format("CrashPoint: %.2fx, ActiveBets: %d", crashPoint, activeBets.size()));

        activeBets.clear();

        // Wait 2 seconds, then start new betting phase
        int waitTicks = plugin.getCrashType().getTimeBetweenGames();
        Bukkit.getScheduler().runTaskLater(plugin, this::enterBettingPhase, waitTicks);
    }

    private void broadcastMultiplier() {
        String message =
                String.format("<yellow>Crash: <green>%.2fx</green></yellow>", currentMultiplier);
        for (UUID playerId : activeBets.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                TextUtil.sendActionBar(player, message);
            }
        }
    }

    private void broadcastCrash() {
        String message = String.format("<red>CRASHED at %.2fx!</red>", crashPoint);
        for (UUID playerId : activeBets.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                TextUtil.send(player, message);
            }
        }
    }

    public void shutdown() {
        if (gameTask != null) {
            gameTask.cancel();
        }
        for (CrashBet bet : activeBets.values()) {
            Player player = Bukkit.getPlayer(bet.getPlayerId());
            if (player != null && ServerArcade.getEconomy() != null) {
                ServerArcade.getEconomy().depositPlayer(player, bet.getAmount());
            }
        }
        activeBets.clear();
    }

    public boolean isGameRunning() {
        return currentState == State.RUNNING;
    }

    public double getCurrentMultiplier() {
        return currentMultiplier;
    }

    public boolean hasBet(UUID playerId) {
        return activeBets.containsKey(playerId);
    }

    public State getCurrentState() {
        return currentState;
    }

    public int getBettingTimeRemaining() {
        return bettingTimeRemaining;
    }

    public List<Double> getRecentCrashes() {
        return new ArrayList<>(recentCrashes);
    }

    /** Get RTP (Return to Player) percentage from recent games */
    public double getRecentRTP() {
        if (recentCrashes.isEmpty()) return 0.0;

        double sum = 0.0;
        for (double crash : recentCrashes) {
            sum += crash;
        }
        double avgCrash = sum / recentCrashes.size();

        // RTP = (average crash - 1) / average crash * 100
        // This represents the expected return on $1 bet
        return (avgCrash - 1.0) / avgCrash * 100.0;
    }

    public void removePlayer(UUID playerId) {
        CrashBet bet = activeBets.remove(playerId);
        if (bet != null && !bet.isCashedOut()) {
            // Refund if not cashed out
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && ServerArcade.getEconomy() != null) {
                ServerArcade.getEconomy().depositPlayer(player, bet.getAmount());
            }
        }
    }

    public static class CrashBet {
        private final UUID playerId;
        private final double amount;
        private boolean cashedOut = false;
        private double cashOutMultiplier = 0;

        public CrashBet(UUID playerId, double amount) {
            this.playerId = playerId;
            this.amount = amount;
        }

        public void cashOut(double multiplier) {
            this.cashedOut = true;
            this.cashOutMultiplier = multiplier;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public double getAmount() {
            return amount;
        }

        public boolean isCashedOut() {
            return cashedOut;
        }

        public double getCashOutMultiplier() {
            return cashOutMultiplier;
        }
    }
}
