package net.serverplugins.arcade.games.global;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.milkbowl.vault.economy.Economy;
import net.serverplugins.api.utils.TextUtil;
import net.serverplugins.arcade.ServerArcade;
import net.serverplugins.arcade.games.GameType;
import net.serverplugins.arcade.machines.Machine;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Base class for global multiplayer games (Jackpot, Crash). These games run on a timer with
 * multiple players betting into a shared pool.
 */
public abstract class GlobalGameType extends GameType {

    public enum State {
        WAITING, // Waiting for next round
        BETTING, // Accepting bets
        RUNNING // Game in progress
    }

    protected State gameState = State.WAITING;
    protected final Map<Player, Integer> players = new ConcurrentHashMap<>();
    protected int totalBets = 0;
    protected int timeLeft = 0;

    // Config
    protected int bettingDuration = 600; // 30 seconds
    protected int timeBetweenGames = 120; // 6 seconds
    protected int timeAddedOnBet = 5; // Time added when new bet placed

    // GUI titles for different states
    protected String bettingGuiTitle = "§fBetting";
    protected String waitingGuiTitle = "§fWaiting";
    protected String runningGuiTitle = "§fRunning";

    protected BukkitTask timerTask;
    protected GlobalGameMenu gameMenu;

    public GlobalGameType(ServerArcade plugin, String name, String configKey) {
        super(plugin, name, configKey);
    }

    /** Start the game timer cycle. */
    public void start() {
        if (timerTask != null) {
            timerTask.cancel();
        }

        gameState = State.WAITING;
        timeLeft = timeBetweenGames;

        timerTask =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> {
                                    timeLeft--;

                                    if (timeLeft <= 0) {
                                        switch (gameState) {
                                            case WAITING -> startBetting();
                                            case BETTING -> startGame();
                                            case RUNNING -> {} // Game handles its own end
                                        }
                                    }

                                    // Update menu if open (use refreshDisplay to preserve handlers)
                                    if (gameMenu != null) {
                                        gameMenu.refreshDisplay();
                                    }
                                },
                                20L,
                                20L); // Every second
    }

    /** Stop the game cycle. */
    public void stop() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        players.clear();
        totalBets = 0;
        gameState = State.WAITING;
    }

    /** Shutdown the game (alias for stop). */
    public void shutdown() {
        stop();
    }

    /** Recreate the menu with new title when state changes. */
    protected void refreshMenu() {
        if (gameMenu != null && gameMenu.getPlayer() != null) {
            Player viewer = gameMenu.getPlayer();
            gameMenu = createMenu();
            gameMenu.open(viewer);
        } else {
            gameMenu = null;
        }
    }

    /** Start the betting phase. */
    protected void startBetting() {
        gameState = State.BETTING;
        timeLeft = bettingDuration;
        players.clear();
        totalBets = 0;
        refreshMenu();
    }

    /** Start the actual game. */
    protected boolean startGame() {
        if (players.size() < 2) {
            // Not enough players, refund and wait
            refundAll();
            gameState = State.WAITING;
            timeLeft = timeBetweenGames;
            refreshMenu();
            return false;
        }

        gameState = State.RUNNING;
        refreshMenu();
        return true;
    }

    /** Add a bet from a player. */
    public void addBet(Player player, int amount) {
        Economy economy = ServerArcade.getEconomy();
        if (economy == null) return;

        if (economy.getBalance(player) < amount) {
            TextUtil.sendError(player, "You don't have enough money!");
            return;
        }

        // Check if already betting
        if (players.containsKey(player)) {
            int existingBet = players.get(player);
            players.put(player, existingBet + amount);
        } else {
            players.put(player, amount);
        }

        economy.withdrawPlayer(player, amount);
        totalBets += amount;

        // Add time if near end
        if (timeLeft <= 5) {
            timeLeft += timeAddedOnBet;
        }

        TextUtil.send(
                player,
                "<green>Bet placed: <gold>"
                        + amount
                        + " <gray>(Total: <gold>"
                        + players.get(player)
                        + "<gray>)");
    }

    /** Remove a player's bet and refund. */
    public void removeBet(Player player) {
        Integer bet = players.remove(player);
        if (bet != null) {
            Economy economy = ServerArcade.getEconomy();
            if (economy != null) {
                economy.depositPlayer(player, bet);
            }
            totalBets -= bet;
            TextUtil.sendSuccess(player, "Your bet has been refunded!");
        }
    }

    /** Refund all players. */
    protected void refundAll() {
        Economy economy = ServerArcade.getEconomy();
        if (economy == null) return;

        for (Map.Entry<Player, Integer> entry : players.entrySet()) {
            economy.depositPlayer(entry.getKey(), entry.getValue());
            TextUtil.sendWarning(entry.getKey(), "Not enough players. Your bet has been refunded.");
        }
        players.clear();
        totalBets = 0;
    }

    /** Get player's current bet. */
    public int getPlayerBet(Player player) {
        return players.getOrDefault(player, 0);
    }

    /** Check if player has a bet. */
    public boolean hasPlayer(Player player) {
        return players.containsKey(player);
    }

    /** Calculate player's win chance (0-100). */
    public double getWinChance(Player player) {
        if (!players.containsKey(player) || totalBets == 0) return 0;
        return (players.get(player) * 100.0) / totalBets;
    }

    @Override
    public void open(Player player, Machine machine) {
        // Seat the player at the machine
        if (machine != null) {
            machine.seatPlayer(player, 1);
        }

        if (gameMenu == null) {
            gameMenu = createMenu();
        }
        gameMenu.open(player);
    }

    /** Create the game menu. */
    protected abstract GlobalGameMenu createMenu();

    @Override
    protected void onConfigLoad(org.bukkit.configuration.ConfigurationSection config) {
        bettingDuration = config.getInt("betting_duration", bettingDuration);
        timeBetweenGames = config.getInt("time_between_games", timeBetweenGames);
        timeAddedOnBet = config.getInt("time_added_on_bet", timeAddedOnBet);

        // Load titles for each state
        org.bukkit.configuration.ConfigurationSection bettingGui =
                config.getConfigurationSection("betting_gui");
        if (bettingGui != null) {
            bettingGuiTitle = bettingGui.getString("title", bettingGuiTitle).replace("&", "§");
        }

        org.bukkit.configuration.ConfigurationSection waitingGui =
                config.getConfigurationSection("waiting_gui");
        if (waitingGui != null) {
            waitingGuiTitle = waitingGui.getString("title", waitingGuiTitle).replace("&", "§");
        }

        org.bukkit.configuration.ConfigurationSection runningGui =
                config.getConfigurationSection("gui");
        if (runningGui != null) {
            runningGuiTitle = runningGui.getString("title", runningGuiTitle).replace("&", "§");
            guiSize = runningGui.getInt("size", guiSize);
        }
    }

    @Override
    public String getGuiTitle() {
        // Return the appropriate title based on current game state
        return switch (gameState) {
            case WAITING -> waitingGuiTitle;
            case BETTING -> bettingGuiTitle;
            case RUNNING -> runningGuiTitle;
        };
    }

    // Getters
    public State getGameState() {
        return gameState;
    }

    public Map<Player, Integer> getPlayers() {
        return players;
    }

    public int getTotalBets() {
        return totalBets;
    }

    public int getTimeLeft() {
        return timeLeft;
    }

    public GlobalGameMenu getGameMenu() {
        return gameMenu;
    }

    public void setTimeLeft(int timeLeft) {
        this.timeLeft = timeLeft;
    }
}
